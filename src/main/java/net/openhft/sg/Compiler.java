package net.openhft.sg;

import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.factory.TypeFactory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtScanner;
import spoon.reflect.visitor.Filter;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static net.openhft.sg.CompilationContext.namedHashedMap;
import static net.openhft.sg.Compiler.Visited.END;
import static net.openhft.sg.Compiler.Visited.IN_PROCESS;
import static net.openhft.sg.StageGraphCompilationException.sgce;
import static spoon.reflect.declaration.ModifierKind.*;

public class Compiler {
    private final CompilationContext cxt;
    private final CompilationNode root;

    private String mergedClassName;
    private String mergedClassPackage;

    public Compiler(CompilationNode root) {
        assert root.parent == null;
        cxt = root.cxt;
        this.root = root;
    }

    public Compiler setMergedClassName(String mergedClassName) {
        this.mergedClassName = mergedClassName;
        return this;
    }
    
    public Compiler setMergedClassPackage(String mergedClassPackage) {
        this.mergedClassPackage = mergedClassPackage;
        return this;
    }

    public CtClass<?> compile() {
        computeAccessPaths();
        createNodes();
        checkFieldsAssignedOnlyWithinNodes();
        linkDependencyNodes();
        checkNoCyclicNodeDeps();
        guardFieldsAccess();
        guardStageMethodCalls();
        declareAndPrepareEverything();
        cxt.allClasses().forEach(CtElement::updateAllParentsBelow);
        replaceStageRefAccesses();
        cxt.allClasses().forEach(CtElement::updateAllParentsBelow);
        removeExtraFields();
        generateGlobalClose();
        cxt.allClasses().map(Compiler::stagedClassExtensionChain)
                .forEach(ExtensionChains::mergeStagedChain);
        if (mergedClassName != null)
            root.getMergedClass().setSimpleName(mergedClassName);
        if (mergedClassPackage != null)
            root.getMergedClass().setParent(root.f.Package().get(mergedClassName));
        cxt.allClasses().forEach(CtElement::updateAllParentsBelow);
        List<CtClass<?>> mergedClasses = cxt.allCompilationNodes()
                .map(CompilationNode::getMergedClass).collect(toList());
        mergedClasses.forEach(CtElement::updateAllParentsBelow);
        root.mergeChildNodes();
        root.getMergedClass().updateAllParentsBelow();
        sortMembers();
        updateFieldTypes();
        sortFinals();
        generateFinalAccessors();
        removeAllStageAnnotations(root.getMergedClass());
        updateTypes(root.getMergedClass());
        root.getMergedClass().updateAllParentsBelow();
        return root.getMergedClass();
    }

    public void computeAccessPaths() {
        cxt.allClasses().forEach(ctC -> stagedClassExtensionChain(ctC).forEach(baseClass ->
                baseClass.getFields().forEach((CtField<?> field) -> {
                    if (field.getAnnotation(StageRef.class) != null) {
                        CtTypeReference<?> fieldClass = field.getType();
                        List<CtClass<?>> candidates = cxt.allClasses()
                                .filter(c -> fieldClass.isAssignableFrom(c.getReference()))
                                .collect(toList());
                        if (candidates.size() != 1) {
                            throw sgce("Can assign " +
                                    candidates.stream().map(CtClass::getSimpleName).collect(toList()) +
                                    " to field " + field + " in " + baseClass.getSimpleName());
                        }
                        cxt.bindReferenced(field, candidates.get(0));
                        CompilationNode referencingNode = cxt.getCompilationNode(ctC);
                        CompilationNode referencedNode = cxt.getCompilationNode(candidates.get(0));
                        if (referencedNode.parent == referencingNode &&
                                referencedNode.parentAccessField == null) {
                            referencedNode.parentAccessField = field;
                        }
                    }
                })));
        root.computeRootAccessPath();
    }

    private void createNodes() {
        cxt.allClasses().forEach(ctClass -> {
            for (CtClass<?> baseType : stagedClassExtensionChain(ctClass)) {
                baseType.getFields().stream()
                        .filter(field -> !field.hasModifier(STATIC) &&
                                !field.hasModifier(FINAL) &&
                                field.getAnnotation(StageRef.class) == null &&
                                cxt.getStageModel(field) == null)
                        .forEach(field -> new StageModel(cxt, field, ctClass));
            }
        });
        cxt.allClasses().forEach(ctClass -> {
            for (CtClass<?> baseType : stagedClassExtensionChain(ctClass)) {
                baseType.getMethods().stream()
                        .filter(m -> !m.hasModifier(STATIC) && !m.hasModifier(ABSTRACT))
                        .filter(m -> cxt.getStageModelByClose(m) == null)
                        .filter(m -> cxt.getStageModelByInitStage(m) == null)
                        .filter(m -> cxt.getStageModelByStageInit(m) == null)
                        .filter(m -> cxt.getStageModelByStageMethod(m) == null)
                        .forEach(m -> {
                            if (cxt.getMethodNode(m) == null)
                                new MethodNode(cxt, m, ctClass);
                        });
            }
        });
    }

    private void checkFieldsAssignedOnlyWithinNodes() {
        forEachBaseClass(baseType -> baseType.getElements((CtFieldAccess<?> fieldAccess) -> {
            CtField<?> field = fieldAccess.getVariable().getDeclaration();
            if (field == null)
                return false;
            StageModel model = cxt.getStageModel(field);
            if (model == null)
                return false;
            CtElement accessParent = fieldAccess.getParent();
            if (!(accessParent instanceof CtAssignment) ||
                    ((CtAssignment) accessParent).getAssigned() != fieldAccess)
                return false;
            for (CtElement p = accessParent; p != null;
                 p = p.isParentInitialized() ? p.getParent() : null) {
                if (p instanceof CtMethod) {
                    CtMethod<?> m = (CtMethod<?>) p;
                    if (cxt.getStageModelByInitStage(m) == model ||
                            cxt.getStageModelByClose(m) == model ||
                            cxt.getStageModelByStageMethod(m) == model) {
                        return false;
                    }
                }
            }
            throw sgce(field + " shouldn't be assigned outside its stage's init() or close()");
        }));
    }

    private void linkDependencyNodes() {
        cxt.allNodes().forEach(node -> node.traverseBlocksForBuildingDeps((CtExpression<?> e) -> {
            if (e instanceof CtInvocation) {
                CtInvocation<?> invocation = (CtInvocation<?>) e;
                CtExpression<?> invocationTarget = invocation.getTarget();
                if (!checkAccessedViaStageRefs(invocationTarget))
                    return;
                CtExecutableReference<?> executableRef = invocation.getExecutable();
                // if executableRef.getDeclaringType() == null, the declaring type is out of
                // classpath, hence sure doesn't participate dependency graph
                if (executableRef.getDeclaringType() != null) {
                    CtExecutable<?> executable = executableRef.getDeclaration();
                    if (executable instanceof CtMethod) {
                        CtMethod<?> method = (CtMethod<?>) executable;
                        MethodNode methodNode = cxt.getMethodNode(method);
                        if (methodNode == null) {
                            methodNode = referencedCompilationNode(node, invocationTarget)
                                    .interfaceMethodToNode.get(method);
                        }
                        if (methodNode != null && methodNode != node) {
                            node.addDependencyOrCheckSameAccess(methodNode, invocationTarget);
                            return;
                        }
                        StageModel stage = cxt.getStageModelByStageMethod(method);
                        if (stage != null && stage != node)
                            node.addDependencyOrCheckSameAccess(stage, invocationTarget);
                    }
                }
            } else if (e instanceof CtFieldAccess) {
                CtFieldAccess<?> fieldAccess = (CtFieldAccess<?>) e;
                if (!checkAccessedViaStageRefs(fieldAccess.getTarget()))
                    return;
                CtField<?> field = fieldAccess.getVariable().getDeclaration();
                if (field != null) {
                    StageModel stage = cxt.getStageModel(field);
                    if (stage != null && stage != node)
                        node.addDependencyOrCheckSameAccess(stage, fieldAccess.getTarget());
                }
            }
        }));
    }

    private CompilationNode referencedCompilationNode(
            DependencyNode node, CtExpression<?> invocationTarget) {
        CompilationNode referencedNode;
        if (invocationTarget == null || invocationTarget instanceof CtThisAccess) {
            CtClass<?> ctClass = cxt.getAnyStagedClassByDependencyNode(node);
            referencedNode = cxt.getCompilationNode(ctClass);
        } else {
            CtField field = ((CtFieldAccess) invocationTarget)
                    .getVariable().getDeclaration();
            referencedNode = cxt.getCompilationNode(cxt.getReferencedClass(field));
        }
        return referencedNode;
    }

    private boolean checkAccessedViaStageRefs(CtExpression<?> target) {
        if (target == null || target instanceof CtThisAccess)
            return true;
        if (target instanceof CtFieldAccess) {
            CtFieldAccess fieldAccess = (CtFieldAccess) target;
            CtField field = fieldAccess.getVariable().getDeclaration();
            if (field != null && field.getAnnotation(StageRef.class) != null) {
                return checkAccessedViaStageRefs(fieldAccess.getTarget());
            }
        }
        return false;
    }

    enum Visited {IN_PROCESS, END}

    private void checkNoCyclicNodeDeps() {
        Map<DependencyNode, Visited> visited = new HashMap<>();
        cxt.allNodes().filter(n -> n.getDependencies().isEmpty())
                .forEach(n -> recursiveCheckNoCyclicNodeDeps(visited, new ArrayList<>(), n));
        if (cxt.allNodes().findAny().isPresent() && visited.isEmpty())
            throw sgce("There are some nodes, but all have dependencies -- there are cycles");
    }

    private void recursiveCheckNoCyclicNodeDeps(Map<DependencyNode, Visited> visited,
                                                List<DependencyNode> chain,
                                                DependencyNode node) {
        if (visited.get(node) == IN_PROCESS)
            throw sgce(node.name + " is a part of stage dependency cycle: " + chain);
        if (visited.get(node) == END)
            return;
        visited.put(node, IN_PROCESS);
        node.getDependants().forEach(dep ->
                recursiveCheckNoCyclicNodeDeps(visited, append(chain, node), dep));
        visited.put(node, END);
    }

    private static List<DependencyNode> append(List<DependencyNode> chain, DependencyNode node) {
        ArrayList<DependencyNode> copy = new ArrayList<>(chain);
        copy.add(node);
        return copy;
    }

    private void guardFieldsAccess() {
        cxt.allNodes().forEach(node -> {
            List<CtFieldAccess<?>> fieldAccesses =
                    node.filterBlocksForBuildingDeps((CtFieldAccess<?> fa) -> true);
            fieldAccesses.forEach((CtFieldAccess fa) -> {
                CtField<?> field = fa.getVariable().getDeclaration();
                if (field != null) {
                    StageModel stage = cxt.getStageModel(field);
                    if (stage != null && stage != node) {
                        CtExpression<?> target = fa.getTarget();
                        CtTargetedExpression<?, CtExpression<?>> access =
                                stage.fieldAccess(target, field);
                        if (target != null)
                            target.setParent(access);
                        fa.replace(access);
                    }
                }
            });
        });
    }

    private void guardStageMethodCalls() {
        cxt.allNodes().forEach(node -> {
            List<CtInvocation<?>> invocations =
                    node.filterBlocksForBuildingDeps((CtInvocation<?> inv) -> true);
            invocations.forEach(invocation -> {
                // the case when the declaring type is out of classpath,
                // to avoid NPE on the next line
                if (invocation.getExecutable().getDeclaringType() == null)
                    return;
                CtExecutable<?> declaration = invocation.getExecutable().getDeclaration();
                if (declaration == null || !(declaration instanceof CtMethod))
                    return;
                CtMethod<?> method = (CtMethod<?>) declaration;
                StageModel stage = cxt.getStageModelByStageMethod(method);
                if (stage != null && stage != node) {
                    CtTargetedExpression guardedInvocation =
                            stage.guardedStageMethodCall((CtInvocation) invocation, method);
                    CtExpression<?> target = invocation.getTarget();
                    guardedInvocation.setTarget(target);
                    if (target != null)
                        target.setParent(guardedInvocation);
                    invocation.replace(guardedInvocation);
                }
            });
        });
    }

    private static class Pair<A, B> {
        final A a;
        final B b;

        Pair(A a, B b) {
            this.a = a;
            this.b = b;
        }
    }

    private void replaceStageRefAccesses() {
        List<Pair<CtClass<?>, CtFieldAccess<?>>> stageRefAccesses = cxt.allClasses()
                .flatMap(ctC -> 
                        stagedClassExtensionChain(ctC).stream()
                        .map(bc ->
                                new Pair<CtClass<?>, CtClass<?>>(ctC, bc)))
                .flatMap(p -> {
                    CtClass<?> ctC = p.a;
                    CtClass<?> baseC = p.b;
                    return baseC.getElements((CtFieldAccess<?> fieldAccess) -> {
                        CtField<?> field = fieldAccess.getVariable().getDeclaration();
                        return field != null && field.getAnnotation(StageRef.class) != null &&
                                (fieldAccess instanceof CtFieldRead ||
                                        fieldAccess instanceof CtFieldWrite);
                    }).stream().map(fa -> new Pair<CtClass<?>, CtFieldAccess<?>>(ctC, fa));
                })
                .collect(toList());

        stageRefAccesses.forEach(p -> {
            CtClass<?> classWhereAccessFound = p.a;
            CtFieldAccess stageRefAccess = p.b;
            CtField<?> field = stageRefAccess.getVariable().getDeclaration();
            assert field != null;
            CompilationNode referencingNode =
                    cxt.getCompilationNode(classWhereAccessFound);
            CompilationNode referencedNode =
                    cxt.getCompilationNode(cxt.getReferencedClass(field));
            CtExpression<?> access = referencingNode.access(referencedNode,
                    stageRefAccess instanceof CtFieldRead ? AccessType.Read :
                            AccessType.Write);
            assert access != null;
            stageRefAccess.replace(access);
        });
    }

    private void removeExtraFields() {
        forEachBaseClass(baseC -> {
            List<CtField<?>> fieldsToRemove = baseC.getFields().stream()
                    .filter(f -> f.getAnnotation(StageRef.class) != null)
                    .filter(f -> {
                        CompilationNode referencedNode =
                                cxt.getCompilationNode(cxt.getReferencedClass(f));
                        return referencedNode.parentAccessField != f;
                    })
                    .collect(toList());
            fieldsToRemove.forEach(baseC::removeField);
        });
    }

    private void sortMembers() {
        List<DependencyNode> sortedList =
                topologicallySorted(cxt.allNodes().collect(Collectors.toList()));
        for (int i = 0; i < sortedList.size(); i++) {
            cxt.setNodeOrder(sortedList.get(i), i + 1);
        }
        root.getMergedClass().getElements((CtTypeMember m) -> {
            m.setPosition(new LinedSourcePosition(m.getPosition(), cxt.getOrder(m)));
            return false;
        });
    }

    private <T extends DependencyNode> List<T> topologicallySorted(Collection<T> nodes) {
        Set<DependencyNode> visited = new HashSet<>();
        Deque<DependencyNode> sorted = new ArrayDeque<>();
        nodes.stream().filter(n -> n.getDependencies().isEmpty())
                .forEach(n -> n.visit(visited, sorted));
        sorted.retainAll(nodes);
        //noinspection unchecked
        return new ArrayList<>((Collection<? extends T>) sorted);
    }
    
    private void sortFinals() {
        List<CtField<?>> finalFields = cxt.allCompilationNodes().flatMap(n ->
                n.getMergedClass()
                        .getElements((CtField<?> field) -> field.hasModifier(FINAL)).stream())
                .collect(toList());
        Map<CtField<?>, List<CtField<?>>> dependencies = namedHashedMap();
        Map<CtField<?>, List<CtField<?>>> dependants = namedHashedMap();
        finalFields.forEach(f -> {
            dependencies.put(f, new ArrayList<>());
            dependants.put(f, new ArrayList<>());
        });
        finalFields.forEach(f -> {
            CtExpression<?> defaultExpression = f.getDefaultExpression();
            if (defaultExpression == null)
                return;

            Set<CtElement> scannedExecutables = new HashSet<>();
            Queue<CtElement> executablesToScan = new ArrayDeque<>();

            Filter<CtElement> addDependencies = (CtElement e) -> {
                if (e instanceof CtFieldAccess) {
                    CtFieldAccess<?> access = (CtFieldAccess<?>) e;
                    CtField<?> field = access.getVariable().getDeclaration();
                    List<CtField<?>> fieldDependants = dependants.get(field);
                    if (fieldDependants != null) {
                        fieldDependants.add(f);
                        dependencies.get(f).add(field);
                    }
                } else if (e instanceof CtAbstractInvocation) {
                    CtExecutable executable =
                            ((CtAbstractInvocation) e).getExecutable().getDeclaration();
                    if (executable != null && !scannedExecutables.contains(executable) &&
                            executable.getParent() != null &&
                            cxt.getCompilationNode((CtClass<?>) executable.getParent()) != null) {
                        executablesToScan.add(executable);
                    }
                }
                return false;
            };

            defaultExpression.getElements(addDependencies);
            CtType<?> fieldClass = f.getType().getDeclaration();
            if (fieldClass instanceof CtClass &&
                    cxt.getCompilationNode((CtClass<?>) fieldClass) != null) {
                fieldClass.getFields().forEach(field -> field.getElements(addDependencies));
            }
            CtElement executable;
            while ((executable = executablesToScan.poll()) != null) {
                scannedExecutables.add(executable);
                executable.getElements(addDependencies);
            }
        });
        Set<CtField<?>> visited = new HashSet<>();
        Deque<CtField<?>> sorted = new ArrayDeque<>();
        dependencies.entrySet().stream().filter(e -> e.getValue().isEmpty())
                .forEach(e -> visitField(e.getKey(), visited, sorted, dependants));
        List<CtField<?>> sortedList = new ArrayList<>(sorted);
        int order = 0;
        for (CtField field : sortedList) {
            CtExpression fieldInit = field.getDefaultExpression();
            if (fieldInit != null && !field.hasModifier(STATIC)) {
                CtType<?> declaringType = field.getDeclaringType();
                if (declaringType instanceof CtClass) {
                    CtClass<?> declaringClass = (CtClass) declaringType;
                    if (declaringClass.getConstructors().isEmpty()) {
                        Factory factory = declaringClass.getFactory();
                        CtConstructor ctr = factory.Constructor().create(declaringClass,
                                EnumSet.of(PUBLIC), emptyList(), emptySet());
                        ctr.setBody(factory.Core().createBlock());
                    } else if (declaringClass.getConstructors().size() == 1) {
                        declaringClass.getConstructors().iterator().next().setImplicit(false);
                    }
                    declaringClass.getConstructors().forEach((CtConstructor ctr) -> {
                        CtAssignment assignment = root.f.Code().createVariableAssignment(
                                field.getReference(), false, root.f.Core().clone(fieldInit));
                        ctr.getBody().addStatement(assignment);
                    });
                    field.setDefaultExpression(null);
                }
            }
            if (dependencies.get(field).isEmpty())
                order = 0;
            field.setPosition(new LinedSourcePosition(field.getPosition(), order));
            order++;
        }
    }
    
    private void generateFinalAccessors() {
        List<CtField<?>> finalFields = root.getMergedClass()
                .getElements((CtField<?> field) -> field.hasModifier(FINAL));
        finalFields.forEach(f -> {
            CtType<?> declaringType = f.getDeclaringType();
            if (declaringType.getMethodsByName(f.getSimpleName()).isEmpty()) {
                Factory factory = f.getFactory();
                CtMethod access = factory.Method().create(
                        declaringType, EnumSet.of(PUBLIC), f.getType(),
                        f.getSimpleName(), emptyList(), emptySet());
                access.setParent(declaringType);
                access.setBody((CtBlock) factory.Core().createBlock());
                CtReturn fReturn = factory.Core().createReturn();
                fReturn.setReturnedExpression(
                        factory.Code().createVariableRead(f.getReference(), false));
                access.getBody().addStatement(fReturn);
                access.setPosition(new LinedSourcePosition(
                        ((LinedSourcePosition) f.getPosition()).delegate,
                        ((LinedSourcePosition) f.getPosition()).line));
            }
        });
        
    }

    private void visitField(CtField<?> f,
                            Set<CtField<?>> visited, Deque<CtField<?>> sorted,
                            Map<CtField<?>, List<CtField<?>>> dependants) {
        if (visited.contains(f))
            return;
        visited.add(f);
        dependants.get(f).forEach(df -> visitField(df, visited, sorted, dependants));
        sorted.addFirst(f);
    }

    private void declareAndPrepareEverything() {
        cxt.allNodes().forEach(DependencyNode::declareAndPrepareAllMethods);
    }

    void forEachBaseClass(Consumer<? super CtClass<?>> action) {
        cxt.allClasses().forEach(ctClass -> stagedClassExtensionChain(ctClass).forEach(action));
    }

    static List<CtClass<?>> stagedClassExtensionChain(CtClass<?> leaf) {
        List<CtClass<?>> chain = new ArrayList<>();
        for (CtClass<?> type = leaf;
             type != null && type.getAnnotation(Staged.class) != null; ) {
            chain.add(type);
            CtTypeReference<?> superClass = type.getSuperclass();
            if (superClass == null)
                break;
            type = (CtClass<?>) superClass.getDeclaration();
        }
        return chain;
    }

    void removeAllStageAnnotations(CtElement element) {
        element.getElements(e -> {
            List<CtAnnotation<? extends Annotation>> stagedAnnotations =
                    e.getAnnotations().stream().filter(a -> {
                        CtTypeReference<? extends Annotation> annType = a.getAnnotationType();
                        TypeFactory tf = a.getFactory().Type();
                        return annType.equals(tf.createReference(Stage.class)) ||
                                annType.equals(tf.createReference(Staged.class)) ||
                                annType.equals(tf.createReference(StageRef.class));
                    }).collect(toList());
            if (!stagedAnnotations.isEmpty()) {
                stagedAnnotations.forEach(e::removeAnnotation);
            }
            return false;
        });
    }

    void updateFieldTypes() {
        root.getMergedClass().accept(new CtScanner() {

            @Override
            public <T> void visitCtFieldReference(CtFieldReference<T> ref) {
                CtTypeReference<?> declarationType = ref.getDeclaringType();
                if (declarationType != null) {
                    CtType<?> ctType = declarationType.getDeclaration();
                    if (ctType instanceof CtClass) {
                        CompilationNode node =
                                cxt.getNodeByAnyStagedClass((CtClass) ctType);
                        if (node != null) {
                            ref.setDeclaringType(node.getMergedClass().getReference());
                        }
                    }
                }
                super.visitCtFieldReference(ref);
            }
        });
    }

    void updateTypes(CtElement element) {
        element.accept(new CtScanner() {

            @Override
            public <T> void visitCtTypeReference(CtTypeReference<T> ref) {
                CtType<?> ctType = ref.getDeclaration();
                if (ctType instanceof CtClass) {
                    CompilationNode node =
                            cxt.getNodeByAnyStagedClass((CtClass) ctType);
                    if (node != null) {
                        CtClass<?> mergedClass = node.getMergedClass();
                        CtPackage mergedPackage = mergedClass.getPackage();
                        if (mergedPackage != null)
                            ref.setPackage(mergedPackage.getReference());
                        CtType<?> declaringType = mergedClass.getDeclaringType();
                        if (declaringType != null)
                            ref.setDeclaringType(declaringType.getReference());
                        ref.setSimpleName(mergedClass.getSimpleName());
                        if (node.eraseTypeParameters) {
                            ref.setActualTypeArguments(emptyList());
                        } else if (!ref.getActualTypeArguments().isEmpty()){
                            ref.setActualTypeArguments(mergedClass.getFormalTypeParameters());
                        }
                        return;
                    }
                }
                super.visitCtTypeReference(ref);
            }
        });
    }
    
    void generateGlobalClose() {
        Factory f = root.f;
        CtBlock<Void> closeBody = f.Core().createBlock();
        List<StageModel> stageModels =
                topologicallySorted(cxt.allStageModels().collect(Collectors.toList()));
        Collections.reverse(stageModels); // close dependant stages first
        stageModels.forEach(stage -> {
            CompilationNode refNode = cxt.getCompilationNode(stage.declaringType);
            CtExpression<?> access = root.access(refNode, AccessType.Read);
            closeBody.addStatement(
                    f.Code().createInvocation(access, stage.getDoCloseMethod().getReference()));
        });
        CtClass rootClass = root.classesToMerge.get(0);
        rootClass.addSuperInterface(f.Type().createReference(AutoCloseable.class));
        f.Method().create(rootClass, EnumSet.of(PUBLIC), f.Type().VOID_PRIMITIVE, "close",
                emptyList(), Collections.emptySet(), closeBody);
    }
}

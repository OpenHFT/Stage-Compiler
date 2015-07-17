package net.openhft.sg;

import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import spoon.reflect.declaration.*;

import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Stream.concat;

public class CompilationContext {

    /**
     * This is needed for identity equivalence, but predictable order of iteration
     * between Stage-Compiler runs. Default identityHashCode() is randomly generated;
     * there is only one option for -XX:hashCode=2 that gives reproducible hash codes,
     * but it returns blindly constant 1, that might be harmful for hash maps
     */
    static Hash.Strategy identityHashedEquivalence = new Hash.Strategy() {
        @Override
        public boolean equals(Object a, Object b) {
            return a == b;
        }

        @Override
        public int hashCode(Object o) {
            return o.hashCode();
        }
    };

    static Hash.Strategy<CtNamedElement> namedEquivalence =
            new Hash.Strategy<CtNamedElement>() {
        @Override
        public int hashCode(CtNamedElement element) {
            String name = element.getSimpleName();
            if (name == null) {
                System.out.println(element);
            }
            return name.hashCode();
        }

        @Override
        public boolean equals(CtNamedElement a, CtNamedElement b) {
            return a == b;
        }
    };


    static <T> Hash.Strategy<T> identityHashedEquivalence() {
        return identityHashedEquivalence;
    }

    static <K extends CtNamedElement, V> Map<K, V> namedHashedMap() {
        return new Object2ObjectOpenCustomHashMap<>((Hash.Strategy<K>) namedEquivalence);
    }

    private Map<CtField<?>, StageModel> fieldToStage = namedHashedMap();
    private Map<CtMethod<?>, MethodNode> methodToNode = namedHashedMap();
    private Map<CtMethod<Void>, StageModel> closeMethodToStage = namedHashedMap();
    private Map<CtMethod<Void>, StageModel> initMethodToStage = namedHashedMap();
    private Map<CtMethod<?>, StageModel> stageMethodsToStage = namedHashedMap();
    private Map<CtMethod<Boolean>, StageModel> stageInitMethodToStage = namedHashedMap();
    private Map<CtField<?>, CtClass<?>> stageReferencedClass = namedHashedMap();
    private Map<CtClass<?>, CompilationNode> classToCompilationNode = namedHashedMap();
    private Map<CtMethod<Void>, DependencyNode> closeDependantsToNode = namedHashedMap();
    private Map<CtMethod<?>, StageModel> accessMethodToStage = namedHashedMap();
    private Map<DependencyNode, Integer> nodeToOrder =
            new Object2ObjectOpenCustomHashMap<>(identityHashedEquivalence());

    private Map<CtClass<?>, CompilationNode> anyStagedClassToNode = namedHashedMap();
    
    public void bind(CtMethod<?> method, MethodNode node) {
        if (methodToNode.putIfAbsent(method, node) != null)
            throw new AssertionError();
    }
    
    public MethodNode getMethodNode(CtMethod<?> method) {
        return methodToNode.get(method);
    }
    
    public void bind(CtField<?> field, StageModel node) {
        if (fieldToStage.putIfAbsent(field, node) != null)
            throw new AssertionError();
    }
    
    public StageModel getStageModel(CtField<?> field) {
        return fieldToStage.get(field);
    }
    
    public void bindClose(CtMethod<Void> closeMethod, StageModel node) {
        if (closeMethodToStage.putIfAbsent(closeMethod, node) != null)
            throw new AssertionError();
    }
    
    public StageModel getStageModelByClose(CtMethod<?> closeMethod) {
        return closeMethodToStage.get(closeMethod);
    }
    
    public void bindInitStage(CtMethod<Void> initMethod, StageModel node) {
        if (initMethodToStage.putIfAbsent(initMethod, node) != null)
            throw new AssertionError(initMethod + " is already bind to stage " +
                    initMethodToStage.get(initMethod) + ", trying to bind to " + node);
    }
    
    public StageModel getStageModelByInitStage(CtMethod<?> initMethod) {
        return initMethodToStage.get(initMethod);
    }
    
    public void bindStageInit(CtMethod<Boolean> stageInitMethod, StageModel node) {
        if (stageInitMethodToStage.putIfAbsent(stageInitMethod, node) != null)
            throw new AssertionError();
    }
    
    public StageModel getStageModelByStageInit(CtMethod<?> stageInitMethod) {
        return stageInitMethodToStage.get(stageInitMethod);
    }
    
    public void bindStageMethod(CtMethod<?> stageMethod, StageModel stage) {
        if (stageMethodsToStage.putIfAbsent(stageMethod, stage) != null)
            throw new AssertionError();
    }
    
    public StageModel getStageModelByStageMethod(CtMethod<?> stageMethod) {
        return stageMethodsToStage.get(stageMethod);
    }
    
    public Stream<StageModel> allStageModels() {
        return fieldToStage.values().stream().distinct();
    }
    
    public Stream<DependencyNode> allNodes() {
        return concat(fieldToStage.values().stream(), methodToNode.values().stream()).distinct();
    }
    
    public void bind(CtClass<?> ctClass, CompilationNode compilationNode) {
        if (classToCompilationNode.putIfAbsent(ctClass, compilationNode) != null)
            throw new AssertionError();
    }
     
    public CompilationNode getCompilationNode(CtClass<?> ctClass) {
        return classToCompilationNode.get(ctClass);
    }
    
    public Stream<CtClass<?>> allClasses() {
        return classToCompilationNode.keySet().stream();
    }
    
    public Stream<CompilationNode> allCompilationNodes() {
        return classToCompilationNode.values().stream().distinct();
    }
    
    public void bindReferenced(CtField<?> stageRef, CtClass<?> referencedClass) {
        if (stageReferencedClass.putIfAbsent(stageRef, referencedClass) != null)
            throw new AssertionError();
    }
    
    public CtClass<?> getReferencedClass(CtField<?> stageRef) {
        return stageReferencedClass.get(stageRef);
    }
    
    public void bindCloseDependants(CtMethod<Void> closeDependants, DependencyNode node) {
        if (closeDependantsToNode.putIfAbsent(closeDependants, node) != null)
            throw new AssertionError();
    }
    
    public void bindAccessMethod(CtMethod<?> accessMethod, StageModel node) {
        if (accessMethodToStage.putIfAbsent(accessMethod, node) != null)
            throw new AssertionError();
    }
    
    public void setNodeOrder(DependencyNode node, int order) {
        if (nodeToOrder.putIfAbsent(node, order) != null)
            throw new AssertionError();
    }
    
    public int getOrder(CtTypeMember member) {
        if (member instanceof CtField)
            return getOrder((CtField) member);
        if (member instanceof CtMethod)
            return getOrder((CtMethod) member);
        return 0;
    }
    
    public int getOrder(CtField<?> field) {
        StageModel stageModel = getStageModel(field);
        if (stageModel != null)
            return nodeToOrder.get(stageModel) * 10;
        return 0;
    }
    
    public int getOrder(CtMethod<?> method) {
        DependencyNode d;
        if ((d = getMethodNode(method)) != null)
            return nodeToOrder.get(d) * 10;
        if ((d = getStageModelByStageInit(method)) != null)
            return nodeToOrder.get(d) * 10 + 1;
        if ((d = getStageModelByInitStage(method)) != null)
            return nodeToOrder.get(d) * 10 + 2;
        if ((d = accessMethodToStage.get(method)) != null)
            return nodeToOrder.get(d) * 10 + 3;
        if ((d = getStageModelByClose(method)) != null)
            return nodeToOrder.get(d) * 10 + 4;
        if ((d = closeDependantsToNode.get(method)) != null)
            return nodeToOrder.get(d) * 10 + 5;
        return 0;
    }
    
    public void bindAnyStagedClassToNode(CtClass<?> ctClass, CompilationNode node) {
        if (anyStagedClassToNode.putIfAbsent(ctClass, node) != null)
            throw new AssertionError();
    }
    
    public CompilationNode getNodeByAnyStagedClass(CtClass<?> ctClass) {
        return anyStagedClassToNode.get(ctClass);
        
    }
}

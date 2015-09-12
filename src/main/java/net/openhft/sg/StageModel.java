package net.openhft.sg;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.Optional.of;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static net.openhft.sg.StageGraphCompilationException.sgce;
import static net.openhft.sg.StringUtils.capitalize;
import static net.openhft.sg.StringUtils.lowercase;
import static net.openhft.sg.Compiler.stagedClassExtensionChain;
import static net.openhft.sg.SpoonUtils.clashes;
import static spoon.reflect.code.UnaryOperatorKind.NOT;
import static spoon.reflect.declaration.ModifierKind.ABSTRACT;

public class StageModel extends DependencyNode {
    private final Map<CtField<?>, CtMethod<?>> fields = new LinkedHashMap<>();
    private CtMethod<Boolean> stageInitMethod;
    private CtField<?> initField;
    private List<CtMethod<Void>> initStageMethods = new ArrayList<>();
    private CtMethod<Void> noArgInitStageMethod;
    private Map<CtMethod<?>, CtMethod<?>> stageMethods = new LinkedHashMap<>();
    private CtMethod<Void> closeMethod;
    
    public StageModel(CompilationContext cxt, CtField<?> oneField,
                      CtClass<?> declaringType) {
        super(cxt, stageName(oneField), declaringType);
        
        boolean fieldsFound = false;
        for (CtClass<?> baseType : stagedClassExtensionChain(declaringType)) {
            
            for (CtMethod<?> method : baseType.getMethods()) {

                String methodName = method.getSimpleName();
                if (methodName.equals(stageInitMethodName()) &&
                        stageInitMethod == null) {
                    if (!method.getType().equals(f().Type().BOOLEAN_PRIMITIVE))
                        throw sgce(methodName + "() return type should be boolean");
                    if (!method.getParameters().isEmpty())
                        throw sgce(methodName + "() shouldn't have parameters");
                    //noinspection unchecked
                    stageInitMethod = (CtMethod<Boolean>) method;
                    cxt.bindStageInit(stageInitMethod, this);

                } else if (!method.hasModifier(ABSTRACT) &&
                        (methodName.equals(initStageMethodPrefix()) ||
                                methodName.startsWith(initStageMethodPrefix() + "_"))) {
                    if (initStageMethods.stream().anyMatch(m -> clashes(method, m)))
                        throw sgce(methodName + "() methods clash");
                    //noinspection unchecked
                    CtMethod<Void> voidMethod = (CtMethod<Void>) method;
                    initStageMethods.add(voidMethod);
                    cxt.bindInitStage(voidMethod, this);

                } else if (methodName.equals(closeMethodName()) && closeMethod == null) {
                    if (!method.getType().equals(f().Type().VOID_PRIMITIVE))
                        throw sgce(methodName + "() should be void");
                    if (!method.getParameters().isEmpty())
                        throw sgce(methodName + "() shouldn't have parameters");
                    //noinspection unchecked
                    closeMethod = (CtMethod<Void>) method;
                    cxt.bindClose(closeMethod, this);
                } else if (method.getAnnotation(Stage.class) != null &&
                        getStageName(method).equals(name)) {
                    stageMethods.put(method, null);
                    cxt.bindStageMethod(method, this);
                }
            }
            
            boolean fieldsFoundInThisClass = false;
            for (CtField<?> field : baseType.getFields()) {
                if (capitalize(field.getSimpleName()).equals(name) ||
                        (field.getAnnotation(Stage.class) != null &&
                                getStageName(field).equals(name))) {
                    if (fieldsFound)
                        throw sgce(name + " fields cannot span several class declarations");
                    fieldsFoundInThisClass = true;
                    fields.put(field, null);
                    if (field.getDefaultExpression() != null && (
                            stageInitMethod == null || stageInitMethod.hasModifier(ABSTRACT))) {
                        if (initField != null) {
                            throw sgce("At most one " + name +
                                    " stage field could be initialized");
                        }
                        initField = field;
                    }
                    cxt.bind(field, this);
                }
            }
            fieldsFound = fieldsFoundInThisClass;
        }

        noArgInitStageMethod = initStageMethods.stream()
                .filter(m -> m.getParameters().isEmpty()).findAny().orElse(null);
        
        if ((initField != null &&
                stageInitMethod != null && !stageInitMethod.hasModifier(ABSTRACT)) ||
                (initField == null && stageInitMethod == null)) {
            throw sgce(stageInitMethodName() + "() should be declared OR one field initialized");
        }
        
        if (initField == null && closeMethod == null)
            throw sgce("If no field initialized, " + closeMethodName() + "() should be declared");
    }
    
    private static String stageName(CtField<?> field) {
        if (field.getAnnotation(Stage.class) != null)
            return getStageName(field);
        return capitalize(field.getSimpleName());
    }

    private static String getStageName(CtElement element) {
        return element.getAnnotation(element.getFactory().Type().createReference(Stage.class))
                .getElementValue("value");
    }


    public CtMethod<Boolean> getStageInitMethod() {
        if (stageInitMethod != null && !stageInitMethod.hasModifier(ABSTRACT))
            return stageInitMethod;
        assert initField != null;
        if (stageInitMethod == null) {
            stageInitMethod = createSimpleMethod(
                    f().Type().BOOLEAN_PRIMITIVE, stageInitMethodName());
            cxt.bindStageInit(stageInitMethod, this);
        } else {
            stageInitMethod.setBody(f().Core().createBlock());
            stageInitMethod.removeModifier(ABSTRACT);
        }
        
        CtExpression<?> unInitExpression = initField.getDefaultExpression();
        
        CtTypeReference<?> initFieldType = initField.getType();
        if ((unInitExpression.toString().equals("-1") ||
                unInitExpression.toString().equalsIgnoreCase("-1L")) &&
                (initFieldType.equals(f().Type().INTEGER_PRIMITIVE) ||
                        initFieldType == f().Type().LONG_PRIMITIVE)) {
            CtReturn<Object> ret = f().Core().createReturn();
            ret.setReturnedExpression(f().Code().createBinaryOperator(
                    f().Code().createVariableRead(initField.getReference(), false),
                    f().Code().createLiteral(0),
                    BinaryOperatorKind.GE));
            stageInitMethod.getBody().addStatement(ret);
            return stageInitMethod;
        }

        CtReturn<Object> ret = f().Core().createReturn();
        ret.setReturnedExpression(f().Code().createBinaryOperator(
                f().Code().createVariableRead(initField.getReference(), false),
                f().Core().clone(unInitExpression),
                BinaryOperatorKind.NE));
        stageInitMethod.getBody().addStatement(ret);
        return stageInitMethod;
    }

    private String stageInitMethodName() {
        return lowercase(name) + "Init";
    }
    
    private String closeMethodName() {
        return "close" + name;
    }

    private String initStageMethodPrefix() {
        return "init" + name;
    }

    @Override
    public Optional<CtMethod<Void>> getCloseMethod() {
        if (closeMethod != null && !closeMethod.hasModifier(ABSTRACT))
            return of(closeMethod);
        assert initField != null;
        
        if (closeMethod == null) {
            closeMethod = createSimpleMethod(f().Type().VOID_PRIMITIVE, closeMethodName());
            cxt.bindClose(closeMethod, this);
        } else {
            closeMethod.setBody(f().Core().createBlock());
            closeMethod.removeModifier(ABSTRACT);
        }
        
        closeMethod.getBody().addStatement(net.openhft.sg.SpoonUtils.reassignDefault(initField));
        return of(closeMethod);
    }

    public <T> CtTargetedExpression<T, CtExpression<?>> fieldAccess(
            CtExpression<?> target, CtField<T> field) {
        if (!fields.containsKey(field))
            throw new StageGraphCompilationException(field + " doesn't belong to " + this);
        @SuppressWarnings("unchecked")
        Map<CtField<T>, CtMethod<T>> fields = (Map<CtField<T>, CtMethod<T>>) (Map) this.fields;
        return f().Code().createInvocation(target, fields.computeIfAbsent(field, f -> {
            CtMethod<T> proxy = createSimpleMethod(f.getType(), f.getSimpleName());
            addGuardingPrologue(proxy);
            CtReturn<T> ret = f().Core().createReturn();
            ret.setReturnedExpression(f().Code().createVariableRead(f.getReference(), false));
            proxy.getBody().addStatement(ret);
            cxt.bindAccessMethod(proxy, StageModel.this);
            return proxy;
        }).getReference());
    }

    private <T> void addGuardingPrologue(CtMethod<T> proxy) {
        if (noArgInitStageMethod != null) {
            CtIf ctIf = createNotInitIf();
            ctIf.setThenStatement(f().Code().createInvocation(thisAccess(),
                    noArgInitStageMethod.getReference()));
            proxy.getBody().addStatement(ctIf);
        } else {
            CtAssert<String> ctAssert = f().Core().createAssert();
            ctAssert.setAssertExpression(createStageInitInvocation());
            ctAssert.setExpression(f().Code().createLiteral(name + " should be init"));
            proxy.getBody().addStatement(ctAssert);
        }
    }

    public <T> CtTargetedExpression<T, CtExpression<?>> guardedStageMethodCall(
            CtInvocation<T> invocation, CtMethod<T> stageMethod) {
        if (!stageMethods.containsKey(stageMethod))
            throw new StageGraphCompilationException(stageMethod + " doesn't belong to " + this);
        @SuppressWarnings("unchecked")
        Map<CtMethod<T>, CtMethod<T>> stageMethods =
                (Map<CtMethod<T>, CtMethod<T>>) (Map) this.stageMethods;
        return f().Code().createInvocation(null, stageMethods.computeIfAbsent(stageMethod, m -> {
            CtMethod<T> guarded = createSimpleMethod(m.getType(), m.getSimpleName() + "Guarded");
            addGuardingPrologue(guarded);
            guarded.setParameters(new ArrayList<>(m.getParameters()));
            List<CtExpression<?>> arguments = m.getParameters().stream()
                    .map(p -> p.getReference())
                    .map(pr -> f().Code().createVariableRead(pr, false))
                    .collect(toList());
            CtInvocation<T> innerInvocation =
                    f().Code().createInvocation(null, m.getReference(), arguments);
            if (m.getType().equals(f().Type().VOID_PRIMITIVE)) {
                guarded.getBody().addStatement(innerInvocation);
            } else {
                CtReturn<T> ctReturn = f().Core().createReturn();
                ctReturn.setReturnedExpression(innerInvocation);
                guarded.getBody().addStatement(ctReturn);
            }
            return guarded;
        }).getReference(), invocation.getArguments());
    }

    private CtThisAccess<?> thisAccess() {
        return f().Code().createThisAccess(declaringType.getReference());
    }

    private CtIf createNotInitIf() {
        CtIf ctIf = f().Core().createIf();
        CtUnaryOperator<Boolean> negInit = f().Core().createUnaryOperator();
        negInit.setKind(NOT);
        negInit.setOperand(createStageInitInvocation());
        ctIf.setCondition(negInit);
        return ctIf;
    }

    private CtInvocation<Boolean> createStageInitInvocation() {
        return f().Code().createInvocation(thisAccess(), getStageInitMethod().getReference());
    }

    @Override
    protected void doDeclareAndPrepareAllMethods() {
        getStageInitMethod();
        
        initStageMethods.forEach(initStageMethod -> {
            // rule 15
            CtBlock<Void> initStageMethodBody = initStageMethod.getBody();
            insertCloseDependantsCall(initStageMethodBody::insertEnd);
        });
        
        CtMethod<Void> closeMethod = getCloseMethod().get();
        CtBlock<Void> closeMethodBody = closeMethod.getBody();
        insertCloseDependantsCall(closeMethodBody::insertBegin);
        CtIf ctIf = createNotInitIf();
        ctIf.setThenStatement(f().Core().createReturn());
        closeMethodBody.insertBegin(ctIf);
    }

    private void insertCloseDependantsCall(Consumer<CtInvocation<?>> insert) {
        Optional<CtMethod<Void>> closeDependantsMethod = getCloseDependantsMethod();
        closeDependantsMethod.ifPresent(m ->
                insert.accept(f().Code().createInvocation(thisAccess(), m.getReference())));
    }

    @Override
    public <E extends CtElement> List<E> filterBlocksForBuildingDeps(Filter<E> filter) {
        Stream<CtMethod<?>> depsBuildingMethods =
                concat(initStageMethods.stream(), stageMethods.keySet().stream());
        return depsBuildingMethods.flatMap(initMethod -> initMethod.getElements(filter).stream())
                .collect(toList());
    }
}

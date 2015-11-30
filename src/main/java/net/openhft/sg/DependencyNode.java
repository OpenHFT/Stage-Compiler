package net.openhft.sg;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

import java.util.*;
import java.util.function.Consumer;

import static java.util.Collections.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static net.openhft.sg.StageGraphCompilationException.sgce;
import static spoon.reflect.declaration.ModifierKind.PUBLIC;

public abstract class DependencyNode {

    protected final CompilationContext cxt;
    private final Map<DependencyNode, List<CtField<?>>> dependenciesVia = new HashMap<>();
    private final Map<DependencyNode, List<CtField<?>>> dependantsVia = new HashMap<>();
    private CtMethod<Void> closeDependantsMethod;
    
    protected final String name;
    protected final CtClass<?> declaringType;
    
    private boolean declaredAndPrepared = false;

    public DependencyNode(CompilationContext cxt, String name, CtClass<?> declaringType) {
        this.cxt = cxt;
        this.name = name;
        this.declaringType = declaringType;
        cxt.bindDependencyNodeToAnyStagedClass(this, declaringType);
    }
    
    public void addDependencyOrCheckSameAccess(DependencyNode dependency, CtExpression<?> target) {
        assert dependency != this;
        List<CtField<?>> via = target != null && !(target instanceof CtThisAccess) ?
                FieldAccessChains.accessToChain((CtFieldAccess<?>) target) : emptyList();
        List<CtField<?>> alreadyVia = dependency.dependantsVia.putIfAbsent(this, via);
        if (alreadyVia == null) {
            dependenciesVia.put(dependency, via);
        } else if (!via.equals(alreadyVia)) {
            throw sgce(dependency + " should be accessed from " + this +
                    " via the same field access path " + alreadyVia +
                    ", attempted to access via " + via);
        }
    }
    
    public Collection<DependencyNode> getDependencies() {
        return unmodifiableCollection(dependenciesVia.keySet());
    }
    
    public Collection<DependencyNode> getDependants() {
        return unmodifiableCollection(dependantsVia.keySet());
    }

    /**
     * Directly or indirectly
     */
    public boolean dependsOn(DependencyNode dependency) {
        boolean dependsDirectly = dependenciesVia.containsKey(dependency);
        if (dependsDirectly)
            return true;
        for (DependencyNode reqDependency : dependency.dependantsVia.keySet()) {
            if (dependsOn(reqDependency))
                return true;
        }
        return false;
    }
    
    public final void declareAndPrepareAllMethods() {
        assert !declaredAndPrepared;
        doDeclareAndPrepareAllMethods();
        declaredAndPrepared = true;
    }
    
    @Deprecated
    protected abstract void doDeclareAndPrepareAllMethods();

    public abstract Optional<CtMethod<Void>> getCloseMethod();
    
    public abstract <E extends CtElement> List<E> filterBlocksForBuildingDeps(Filter<E> filter);
    
    public final  <E extends CtElement> void traverseBlocksForBuildingDeps(Consumer<E> action) {
        filterBlocksForBuildingDeps(consumerToFilter(action));
    }
    
    private static <E extends CtElement> Filter<E> consumerToFilter(Consumer<E> action) {
        return e -> {
            action.accept(e);
            return false;
        };
    }

    protected Optional<CtMethod<Void>> getCloseDependantsMethod() {
        if (closeDependantsMethod != null)
            return of(closeDependantsMethod);
        if (dependantsVia.isEmpty())
            return empty();
        if (!dependantsVia.keySet().stream()
                .map(DependencyNode::getCloseMethod)
                .anyMatch(Optional::isPresent)) {
            return empty();
        }
        closeDependantsMethod = createSimpleMethod(f().Type().VOID_PRIMITIVE,
                "close" + name + "Dependants");
        cxt.bindCloseDependants(closeDependantsMethod, this);
        for (DependencyNode dependant : topologicallySortedDependants()) {
            Optional<CtMethod<Void>> dependantCloseMethod = dependant.getCloseMethod();
            if (dependantCloseMethod.isPresent()) {
                net.openhft.sg.CompilationNode dependantNode = cxt.getCompilationNode(dependant.declaringType);
                net.openhft.sg.CompilationNode thisNode = cxt.getCompilationNode(declaringType);
                CtExpression<?> dependantAccess =
                        thisNode.access(dependantNode, AccessType.Read);
                closeDependantsMethod.getBody().addStatement(f().Code().createInvocation(
                        dependantAccess, dependantCloseMethod.get().getReference()));
            }
        }
        return of(closeDependantsMethod);
    }
    
    private List<DependencyNode> topologicallySortedDependants() {
        Set<DependencyNode> visited = new HashSet<>();
        Deque<DependencyNode> sorted = new ArrayDeque<>();
        visit(visited, sorted);
        sorted.retainAll(dependantsVia.keySet());
        return new ArrayList<>(sorted);
    }
    
    void visit(Set<DependencyNode> visited, Deque<DependencyNode> sorted) {
        if (visited.contains(this))
            return;
        visited.add(this);
        getDependants().forEach(d -> d.visit(visited, sorted));
        sorted.addFirst(this);
    }

    protected <T> CtMethod<T> createSimpleMethod(CtTypeReference<T> returnType, String name) {
        CtMethod<T> method = f().Method().create(
                declaringType, EnumSet.of(PUBLIC), returnType, name, emptyList(), emptySet());
        method.setParent(declaringType);
        CtBlock<T> body = f().Core().createBlock();
        method.setBody(body);
        body.setParent(method);
        return method;
    }   
    
    protected Factory f() {
        return declaringType.getFactory();
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}

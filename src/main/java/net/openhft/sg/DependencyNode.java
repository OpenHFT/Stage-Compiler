/*
 * Copyright (C) 2015,2016  higherfrequencytrading.com
 * Copyright (C) 2016 Roman Leventov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.openhft.sg;

import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

import java.util.*;
import java.util.function.Consumer;

import static java.util.Collections.*;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static spoon.reflect.declaration.ModifierKind.PUBLIC;

public abstract class DependencyNode {

    protected final CompilationContext cxt;
    private final Set<DependencyNode> dependenciesVia = new HashSet<>();
    private final Set<DependencyNode> dependantsVia = new HashSet<>();
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
        if (dependency.dependantsVia.add(this))
            dependenciesVia.add(dependency);
    }
    
    public Collection<DependencyNode> getDependencies() {
        return unmodifiableCollection(dependenciesVia);
    }
    
    public Collection<DependencyNode> getDependants() {
        return unmodifiableCollection(dependantsVia);
    }

    /**
     * Directly or indirectly
     */
    public boolean dependsOn(DependencyNode dependency) {
        boolean dependsDirectly = dependenciesVia.contains(dependency);
        if (dependsDirectly)
            return true;
        for (DependencyNode reqDependency : dependency.dependantsVia) {
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
        if (!dependantsVia.stream()
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
        sorted.retainAll(dependantsVia);
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

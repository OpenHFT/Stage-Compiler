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

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static net.openhft.sg.StringUtils.capitalize;
import static net.openhft.sg.Compiler.stagedClassExtensionChain;

public class MethodNode extends DependencyNode {

    private List<CtMethod<?>> methods = new ArrayList<>();

    public MethodNode(net.openhft.sg.CompilationContext cxt, CtMethod<?> method, CtClass<?> declaringType) {
        super(cxt, capitalize(declaringType.getSimpleName()) + capitalize(method.getSimpleName()),
                declaringType);
        stagedClassExtensionChain(declaringType).forEach(baseType ->
                baseType.getMethods().stream().filter(m -> overrides(method, m)).findAny()
                        .ifPresent(m -> {
            methods.add(m);
            cxt.bind(m, this);
        }));
        CompilationNode declaringNode = cxt.getCompilationNode(declaringType);
        declaringType.getSuperInterfaces().stream()
                .flatMap(i -> Stream.concat(Stream.of(i), i.getSuperInterfaces().stream()))
                .forEach(superInterface -> {
                    CtType<?> superInterfaceDeclaration = superInterface.getDeclaration();
                    if (superInterfaceDeclaration != null) {
                        superInterfaceDeclaration.getMethods().stream()
                                .filter(m -> overrides(method, m))
                                .forEach(m -> declaringNode.bind(m, this));
                    }
                });
    }

    static boolean overrides(CtMethod<?> m1, CtMethod<?> m2) {
        if (!m2.getSimpleName().equals(m1.getSimpleName()))
            return false;
        return m2.getParameters().equals(m1.getParameters());
    }

    @Override
    public Optional<CtMethod<Void>> getCloseMethod() {
        return getCloseDependantsMethod();
    }

    @Override
    protected void doDeclareAndPrepareAllMethods() {
        getCloseMethod();
    }

    @Override
    public <E extends CtElement> List<E> filterBlocksForBuildingDeps(Filter<E> filter) {
        return methods.stream().flatMap(m -> m.getElements(filter).stream()).collect(toList());
    }
}

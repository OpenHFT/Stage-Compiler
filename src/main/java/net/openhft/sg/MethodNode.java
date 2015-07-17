package net.openhft.sg;

import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.Filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

package net.openhft.sg;

import spoon.reflect.code.CtAssignment;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public final class SpoonUtils {
    
    public static <T> CtAssignment<T, T> reassignDefault(CtField<T> field) {
        Factory f = field.getFactory();
        return f.Code().createVariableAssignment(
                field.getReference(), false, f.Core().clone(field.getDefaultExpression()));
    }

    public static boolean clashes(CtMethod<?> a, CtMethod<?> b) {
        if (!a.getSimpleName().equals(b.getSimpleName()))
            return false;
        List<CtParameter<?>> aParams = a.getParameters();
        List<CtParameter<?>> bParams = b.getParameters();
        if (aParams.size() != bParams.size())
            return false;
        for (int i = 0; i < aParams.size(); i++) {
            CtTypeReference<?> aParam = aParams.get(i).getType();
            CtTypeReference<?> bParam = bParams.get(i).getType();
            if (aParam.isSubtypeOf(bParam))
                continue;
            if (bParam.isSubtypeOf(aParam))
                continue;
            if (isNumericPrimitive(aParam) && isNumericPrimitive(bParam))
                continue;
            // TODO update when unbox() bug fixed in Spoon
            return false;
        }
        return true;
    }

    private static boolean isNumericPrimitive(CtTypeReference<?> aParam) {
        return aParam.isPrimitive() && aParam.equals(aParam.getFactory().Type().BOOLEAN_PRIMITIVE);
    }
    
    private SpoonUtils() {}
}

package net.openhft.sg;

import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.declaration.CtField;
import spoon.reflect.factory.CoreFactory;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.reverse;

final class FieldAccessChains {
    
    public static List<CtField<?>> accessToChain(CtFieldAccess<?> access) {
        List<CtField<?>> chain = new ArrayList<>();
        for (; access != null; access = (CtFieldAccess<?>) access.getTarget()) {
            chain.add(access.getVariable().getDeclaration());
        }
        reverse(chain);
        return chain;
    }
    
    @SuppressWarnings("unchecked")
    public static CtFieldAccess<?> chainToAccess(List<CtField<?>> chain, AccessType accessType) {
        CtFieldAccess<?> access = null;
        for (int i = chain.size() - 1; i >= 0; i--) {
            CtField<?> field = chain.get(i);
            CoreFactory coreFactory = field.getFactory().Core();
            CtFieldAccess acc = accessType == AccessType.Read ? coreFactory.createFieldRead() :
                    coreFactory.createFieldWrite();
            acc.setVariable(field.getReference());
            acc.setType(field.getType());
            acc.setTarget(access);
            access = acc;
        }
        return access;
    }
    
    private FieldAccessChains() {}
}

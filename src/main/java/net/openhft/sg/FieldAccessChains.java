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

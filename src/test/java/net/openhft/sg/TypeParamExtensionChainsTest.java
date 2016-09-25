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

import org.junit.Test;
import spoon.Launcher;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;


public class TypeParamExtensionChainsTest {

    static Factory getFactoryForTest() {
        Launcher spoon = new Launcher();
        spoon.getFactory().getEnvironment().setComplianceLevel(8);
        spoon.addInputResource("src/test/java");
//                new File(StageGraphCompilationTest.class
//                        .getProtectionDomain().getCodeSource()
//                        .getLocation().getPath()).getParentFile()));
        try {
            spoon.run();
            for(CtPackage p : spoon.getFactory().Package().getAll()) {
                System.out.println("package: "+p.getQualifiedName());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return spoon.getFactory();
    }
    
    @Test
    public void testTypeParamExtensionChains() {
        CompilationNode root = CompilationNode.root(getFactoryForTest());
        root.addClassToMerge(TypeParamExtensionChainsSub.class);
        Compiler compiler = new Compiler(root);
        System.out.println(compiler.compile());
    }
    
}

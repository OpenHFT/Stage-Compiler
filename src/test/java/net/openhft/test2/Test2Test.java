/*
 * Copyright 2015 Higher Frequency Trading
 *
 *  http://www.higherfrequencytrading.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.openhft.test2;

import net.openhft.sg.Compiler;
import org.junit.Test;
import spoon.Launcher;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;

public class Test2Test {

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
    public void test() {
        net.openhft.sg.CompilationNode root = net.openhft.sg.CompilationNode.root(getFactoryForTest());
        root.addClassToMerge(Stages.class);
        root.addClassToMerge(StagesSubUser.class);
        Compiler compiler = new Compiler(root);
        System.out.println(compiler.compile());
    }
}

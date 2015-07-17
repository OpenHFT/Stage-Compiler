package net.openhft.sg;

import org.junit.Test;
import spoon.Launcher;
import spoon.reflect.factory.Factory;

public class ParametersFieldsNameCollisionTest {

    static Factory getFactoryForTest() {
        Launcher spoon = new Launcher();
        spoon.getFactory().getEnvironment().setComplianceLevel(8);
        spoon.addInputResource("src/test/java");
//                new File(StageGraphCompilationTest.class
//                        .getProtectionDomain().getCodeSource()
//                        .getLocation().getPath()).getParentFile()));
        try {
            spoon.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return spoon.getFactory();
    }

    @Test
    public void testParametersFieldsNameCollision() {
        CompilationNode root = CompilationNode.root(getFactoryForTest());
        root.addClassToMerge(ParametersFieldsNameCollision.class);
        Compiler compiler = new Compiler(root);
        System.out.println(compiler.compile());
    }
}

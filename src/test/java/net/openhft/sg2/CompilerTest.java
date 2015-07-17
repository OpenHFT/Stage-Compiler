package net.openhft.sg2;

import net.openhft.sg.Compiler;
import org.junit.Test;
import spoon.Launcher;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.factory.Factory;

public class CompilerTest {
    
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
        root.addClassToMerge(PartSub.class);
        net.openhft.sg.CompilationNode child = root.createChild();
        child.addClassToMerge(Part1.class);
        child.addClassToMerge(Part2.class);
        net.openhft.sg.Compiler compiler = new Compiler(root);
        System.out.println(compiler.compile());
    }
}

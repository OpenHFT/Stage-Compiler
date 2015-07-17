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

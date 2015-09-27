package net.openhft.sg2;

import net.openhft.sg.Stage;
import net.openhft.sg.StageRef;
import net.openhft.sg.Staged;

@Staged
public class Part1 {

    @StageRef PartSup ps;
    
    int a = -1;
    
    @Stage("Foo")
    int b = 0;
    @Stage("Foo")
    int c;
    
    void initFoo() {
        b = a + 1;
        c = b + 1;
    }
    
    @Stage("Foo")
    void privateFooMethod() {
        c = 42;
    }
}

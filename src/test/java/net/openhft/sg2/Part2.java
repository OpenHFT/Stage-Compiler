package net.openhft.sg2;

import net.openhft.sg.Staged;

@Staged
public class Part2 {
    @net.openhft.sg.StageRef
    Part1 p1;
    
    int x = 0;
    
    void initXSuffix() {
        x = p1.a + 1;
        p1.privateFooMethod();
    }
    
    void closeX() {
        System.out.println("custom close");
    }
}

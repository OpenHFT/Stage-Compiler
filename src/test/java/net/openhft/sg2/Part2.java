package net.openhft.sg2;

import net.openhft.sg.StageRef;
import net.openhft.sg.Staged;

@Staged
public class Part2 {
    @StageRef PartSub ps;
    @StageRef Part1 p1;
    
    int x = 0;
    
    void initX_Suffix() {
        x = p1.a + 1;
        p1.privateFooMethod();
    }
    
    void closeX() {
        System.out.println("custom close");
    }
}

package net.openhft.sg2;

import net.openhft.sg.Staged;

@Staged
public class PartSup {
    @net.openhft.sg.StageRef
    Part2 p2;
    
    public PartSup(Object p) {
        System.out.println(p + "sup");
    }
    
    boolean flag;
    
    boolean flagInit() {
        return flag;
    }
    
    void standAloneMethodUsing() {
        System.out.println(flag);
    }
    
    void overriden() {
        System.out.println(p2.p1.a);
    }
    
    Object overridenDependant = null;
    
    void initOverridenDependant() {
        overriden();
        overridenDependant = null;
    }
}

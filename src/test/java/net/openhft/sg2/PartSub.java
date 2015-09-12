package net.openhft.sg2;

import net.openhft.sg.Staged;

@Staged
public class PartSub extends PartSup {
    @net.openhft.sg.StageRef
    Part2 p2;

    public PartSub(PartSub sub) {
        super(sub);
        System.out.println(sub + "sub");
    }

    void initFlag_Using() {
        flag = p2.x != 0;
    }
    
    void closeFlag() {}

    @Override
    void overriden() {
        System.out.println(p2.x);
        someMethodAcceptingStageRef(p2.p1);
        super.overriden();
    }
    
    void someMethodAcceptingStageRef(Part1 p1) {
        System.out.println(p1);
    }
}

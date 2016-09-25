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

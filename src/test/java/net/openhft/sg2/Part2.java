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

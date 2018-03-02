/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.vm.types;

import org.aion.mcf.vm.types.DataWord;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class DataWordTest {

    @Test
    public void testIntToDataWord() {
        DataWord d1 = new DataWord(Integer.MIN_VALUE);
        DataWord d2 = new DataWord(1);
        DataWord d3 = new DataWord(Integer.MAX_VALUE);

        assertEquals(d1.intValue(), Integer.MIN_VALUE);
        assertEquals(d2.intValue(), 1);
        assertEquals(d3.intValue(), Integer.MAX_VALUE);
    }

    @Test
    public void testLongToDataWord() {
        DataWord d1 = new DataWord(Long.MIN_VALUE);
        DataWord d2 = new DataWord(1);
        DataWord d3 = new DataWord(Long.MAX_VALUE);

        assertEquals(d1.longValue(), Long.MIN_VALUE);
        assertEquals(d2.longValue(), 1);
        assertEquals(d3.longValue(), Long.MAX_VALUE);
    }
}

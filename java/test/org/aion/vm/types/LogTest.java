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

import org.aion.base.type.Address;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogTest {

    @Test
    public void testEncode() {
        Address address = Address.ZERO_ADDRESS();
        List<byte[]> topics = new ArrayList<byte[]>();
        topics.add(DataWord.ZERO.getData());
        byte[] data = new byte[200];

        Log info = new Log(address, topics, data);
        Log info2 = new Log(info.getEncoded());

        assertTrue(address.equals(info2.getAddress()));
        assertFalse(info2.getTopics().isEmpty());
        assertTrue(Arrays.equals(data, info2.getData()));
    }
}

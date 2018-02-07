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
package org.aion.fastvm;

import org.aion.base.type.Address;
import org.aion.types.a0.AionBlock;
import org.aion.types.a0.AionTransaction;
import org.aion.vm.types.DataWord;
import org.apache.commons.lang3.RandomUtils;

import java.util.Collections;
import java.util.List;

public class TestUtils {

    public static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(Address.ADDRESS_LEN);
        byte[] logsBloom = new byte[0];
        byte[] difficulty = new DataWord(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[0];

        // TODO: set a dummy limit of 5000000 for now
        return new AionBlock(parentHash, Address.wrap(coinbase), logsBloom, difficulty, number, timestamp, extraData, nonce,
                receiptsRoot, transactionsRoot, stateRoot, transactionsList, solutions, 0, 5000000);
    }

}

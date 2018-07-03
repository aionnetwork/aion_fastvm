/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.fastvm;

import org.aion.base.type.Address;
import org.aion.mcf.vm.types.DataWord;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
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

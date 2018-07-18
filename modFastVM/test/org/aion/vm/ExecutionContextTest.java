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
package org.aion.vm;

import static org.junit.Assert.assertEquals;

import org.aion.base.type.Address;
import org.aion.mcf.vm.types.DataWord;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;

public class ExecutionContextTest {

    @Test
    public void testToBytes() {
        byte[] txHash = RandomUtils.nextBytes(32);

        byte[] address = Hex
            .decode("1111111111111111111111111111111111111111111111111111111111111111");
        byte[] origin = Hex
            .decode("2222222222222222222222222222222222222222222222222222222222222222");
        byte[] caller = Hex
            .decode("3333333333333333333333333333333333333333333333333333333333333333");

        DataWord nrgPrice = new DataWord(Hex.decode("00000000000000000000000000000004"));
        long nrgLimit = 0x0000000000000005;

        DataWord callValue = new DataWord(Hex.decode("00000000000000000000000000000006"));
        // 0x00000001
        byte[] callData = new byte[]{0x07};

        int depth = 0x00000008;
        int kind = 0x00000009;
        int flags = 0x0000000a;

        byte[] blockCoinbase = Hex
            .decode("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
        long blockNumber = 0x000000000000000c;
        long blockTimestamp = 0x000000000000000d;
        long blockNrgLimit = 0x000000000000000e;
        DataWord blockDifficulty = new DataWord(Hex.decode("0000000000000000000000000000000f"));

        TransactionResult txResult = new TransactionResult();
        ExecutionContext ctx = new ExecutionContext(txHash, Address.wrap(address),
            Address.wrap(origin), Address.wrap(caller), nrgPrice, nrgLimit, callValue,
            callData, depth, kind, flags, Address.wrap(blockCoinbase), blockNumber, blockTimestamp,
            blockNrgLimit,
            blockDifficulty, txResult);
        String encoded = Hex.toHexString(ctx.toBytes());
        String expected =
            "111111111111111111111111111111111111111111111111111111111111111122222222222222222222222222222222222222222222222222222222222222223333333333333333333333333333333333333333333333333333333333333333"
                + "00000000000000000000000000000004"
                + "0000000000000005000000000000000000000000000000060000000107"
                + "00000008000000090000000a"
                + "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb000000000000000c000000000000000d000000000000000e0000000000000000000000000000000f";

        System.out.println(expected);
        System.out.println(encoded);
        assertEquals(expected, encoded);
    }
}

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
import org.aion.base.util.Hex;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.TransactionResult;
import org.aion.mcf.vm.types.DataWord;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CacheTest {
    private byte[] txHash = RandomUtils.nextBytes(32);
    private Address origin = Address.wrap(RandomUtils.nextBytes(32));
    private Address caller = origin;
    private Address address = Address.wrap(RandomUtils.nextBytes(32));

    private Address blockCoinbase = Address.wrap(RandomUtils.nextBytes(32));
    private long blockNumber = 1;
    private long blockTimestamp = System.currentTimeMillis() / 1000;
    private long blockNrgLimit = 5000000;
    private DataWord blockDifficulty = new DataWord(0x100000000L);

    private DataWord nrgPrice;
    private long nrgLimit;
    private DataWord callValue;
    private byte[] callData;

    private int depth = 0;
    private int kind = ExecutionContext.CREATE;
    private int flags = 0;

    private TransactionResult txResult;

    @Before
    public void setup() {
        nrgPrice = DataWord.ONE;
        nrgLimit = 20000;
        callValue = DataWord.ZERO;
        callData = new byte[0];
        txResult = new TransactionResult();
    }

    @Test
    public void testCache() {
        callData = Hex.decode("8256cff3");
        ExecutionContext ctx = new ExecutionContext(txHash, address, origin, caller, nrgPrice, nrgLimit, callValue,
                callData, depth, kind, flags, blockCoinbase, blockNumber, blockTimestamp, blockNrgLimit,
                blockDifficulty, txResult);
        FastVM vm = new FastVM();

        long t1 = System.currentTimeMillis();
        int repeat = 1000;
        for (int i = 0; i < repeat; i++) {
            byte[] code = generateContract(i);
            ExecutionResult result = vm.run(code, ctx, new DummyRepository());
            assertEquals(ExecutionResult.Code.SUCCESS, result.getCode());

            if (i % 100 == 0) {
                System.out.println(i + "/" + repeat);
            }
        }
        long t2 = System.currentTimeMillis();

        System.out.println(t2 - t1);
    }

    private byte[] generateContract(int baseSum) {
        // pragma solidity ^0.4.0;
        //
        // contract simple {
        // function f(uint n) constant returns(uint) {
        // uint sum = 0x12345678;
        // for (uint i = 0; i < n; i++) {
        // sum = sum + i;
        // }
        // return sum;
        // }
        // }
        final String contract = "60506040526000356c01000000000000000000000000900463ffffffff1680638256cff314602d575b600080fd5b3415603757600080fd5b604b60048080359060100190919050506061565b6040518082815260100191505060405180910390f35b600080600063123456789150600090505b83811015608b5780820191505b80806001019150506072565b8192505b50509190505600a165627a7a7230582031f5099d322de19215175c3f31d2afdc1cb3ce6ffd6c8541681584cad8a075c60029";
        byte[] ops = Hex.decode(contract);

        ops[104] = (byte) ((baseSum >>> 24) & 0xff);
        ops[105] = (byte) ((baseSum >>> 16) & 0xff);
        ops[106] = (byte) ((baseSum >>> 8) & 0xff);
        ops[107] = (byte) (baseSum & 0xff);
        return ops;
    }
}

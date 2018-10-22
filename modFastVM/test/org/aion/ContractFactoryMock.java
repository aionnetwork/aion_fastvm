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

package org.aion;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.IExecutionResult;
import org.aion.base.vm.IDataWord;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.vm.ExecutionContext;
import org.aion.vm.ExecutionResult;
import org.aion.vm.IContractFactory;
import org.aion.vm.IPrecompiledContract;

public class ContractFactoryMock implements IContractFactory {
    public static final String CALL_ME =
            "9999999999999999999999999999999999999999999999999999999999999999";

    /** A mocked up version of getPrecompiledContract that only returns TestPrecompiledContract. */
    @Override
    public IPrecompiledContract getPrecompiledContract(
            ExecutionContext context,
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track) {

        switch (context.address().toString()) {
            case CALL_ME:
                return new CallMePrecompiledContract();
            default:
                return null;
        }
    }

    public static class CallMePrecompiledContract implements IPrecompiledContract {
        public static final String head = "echo: ";
        public static boolean youCalledMe = false;

        /**
         * Returns a byte array that begins with the byte version of the characters in the public
         * variable 'head' followed by the bytes in input.
         */
        @Override
        public IExecutionResult execute(byte[] input, long nrgLimit) {
            youCalledMe = true;
            byte[] msg = new byte[head.getBytes().length + input.length];
            System.arraycopy(head.getBytes(), 0, msg, 0, head.getBytes().length);
            System.arraycopy(input, 0, msg, head.getBytes().length, input.length);
            return new ExecutionResult(ResultCode.SUCCESS, nrgLimit, msg);
        }
    }
}

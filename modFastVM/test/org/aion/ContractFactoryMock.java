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

import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.vm.IContractFactory;
import org.aion.vm.IPrecompiledContract;
import org.aion.vm.KernelInterfaceForFastVM;
import org.aion.vm.api.interfaces.TransactionContext;

public class ContractFactoryMock implements IContractFactory {
    public static final String CALL_ME =
            "9999999999999999999999999999999999999999999999999999999999999999";

    /** A mocked up version of getPrecompiledContract that only returns TestPrecompiledContract. */
    @Override
    public IPrecompiledContract getPrecompiledContract(
            TransactionContext context, KernelInterfaceForFastVM track) {

        switch (context.getDestinationAddress().toString()) {
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
        public FastVmTransactionResult execute(byte[] input, long nrgLimit) {
            youCalledMe = true;
            byte[] msg = new byte[head.getBytes().length + input.length];
            System.arraycopy(head.getBytes(), 0, msg, 0, head.getBytes().length);
            System.arraycopy(input, 0, msg, head.getBytes().length, input.length);
            return new FastVmTransactionResult(FastVmResultCode.SUCCESS, nrgLimit, msg);
        }
    }
}

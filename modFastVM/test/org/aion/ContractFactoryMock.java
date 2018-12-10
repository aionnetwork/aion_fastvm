package org.aion;

import org.aion.vm.api.ResultCode;
import org.aion.vm.api.TransactionResult;
import org.aion.base.db.IRepositoryCache;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.vm.IContractFactory;
import org.aion.vm.IPrecompiledContract;
import org.aion.base.vm.IDataWord;
import org.aion.vm.api.interfaces.TransactionContext;

public class ContractFactoryMock implements IContractFactory {
    public static final String CALL_ME =
            "9999999999999999999999999999999999999999999999999999999999999999";

    /** A mocked up version of getPrecompiledContract that only returns TestPrecompiledContract. */
    @Override
    public IPrecompiledContract getPrecompiledContract(
            TransactionContext context,
            IRepositoryCache<AccountState, IDataWord, IBlockStoreBase<?, ?>> track) {

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
        public TransactionResult execute(byte[] input, long nrgLimit) {
            youCalledMe = true;
            byte[] msg = new byte[head.getBytes().length + input.length];
            System.arraycopy(head.getBytes(), 0, msg, 0, head.getBytes().length);
            System.arraycopy(input, 0, msg, head.getBytes().length, input.length);
            return new TransactionResult(ResultCode.SUCCESS, nrgLimit, msg);
        }
    }
}

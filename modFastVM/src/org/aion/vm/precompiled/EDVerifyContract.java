package org.aion.vm.precompiled;

import org.aion.crypto.ed25519.ECKeyEd25519;
import org.aion.vm.ExecutionResult;
import org.aion.vm.PrecompiledContracts;

public class EDVerifyContract extends PrecompiledContracts.PrecompiledContract {
    private final static long COST = 21000L;

    @Override
    public ExecutionResult execute(byte[] input, long nrg) {
        if (COST > nrg) {
            return new ExecutionResult(ExecutionResult.Code.OUT_OF_NRG, 0);
        }
        byte[] msg = new byte[64];
        byte[] sig = new byte[64];
        byte[] pubKey = new byte[32];

        System.arraycopy(input, 0, msg, 0, 64);
        System.arraycopy(input, 64, sig, 0, 64);
        System.arraycopy(input, 128, pubKey, 0, 32);
        try {
            boolean verify = ECKeyEd25519.verify(msg, sig, pubKey);
            byte[] result = new byte[1];
            result[0] = verify ? (byte)1 : (byte)0;
            return new ExecutionResult(ExecutionResult.Code.SUCCESS, nrg - COST, result);
        } catch (Throwable e) {
            return new ExecutionResult(ExecutionResult.Code.INTERNAL_ERROR, 0);
        }
    }
}

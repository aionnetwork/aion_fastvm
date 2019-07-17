package org.aion.precompiled;

import java.util.HashMap;
import java.util.Map;
import org.aion.fastvm.ExecutionContext;
import org.aion.fastvm.FastVmResultCode;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.types.AionAddress;

/**
 * The {@link org.aion.StateImplForTesting} class uses this class to find and call our mocked up
 * precompiled contracts.
 */
public final class PrecompiledFactoryForTesting {
    private static final Map<AionAddress, IPrecompiled> CONTRACTS = new HashMap<>();

    /**
     * Registers the specified precompiled contract to be associated with the given address.
     *
     * @param address The address of the contract.
     * @param contract The contract.
     */
    public static void registerPrecompiledContract(AionAddress address, IPrecompiled contract) {
        if (address == null) {
            throw new NullPointerException("Cannot register a precompiled contract with a null address!");
        }
        if (contract == null) {
            throw new NullPointerException("Cannot register a null precompiled contract!");
        }
        if (CONTRACTS.containsKey(address)) {
            throw new IllegalArgumentException("Cannot overwrite an existing precompiled contract entry!");
        }
        CONTRACTS.put(address, contract);
    }

    /**
     * Returns {@code true} only if this address is a precompiled contract.
     */
    public static boolean isPrecompiledContract(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot check if a null address is precompiled!");
        }
        return CONTRACTS.containsKey(address);
    }

    /**
     * Runs a precompiled contract whose address is given by the destination address in the given
     * context, using the data in the context to run the code. Returns the result.
     *
     * If the contract does not exist then a SUCCESS result is returned that uses all of the caller's
     * energy.
     *
     * @param context The context.
     * @return the result.
     */
    public static FastVmTransactionResult runPrecompiledContract(ExecutionContext context) {
        if (CONTRACTS.isEmpty()) {
            throw new IllegalStateException("No precompiled contracts have been registered!");
        }

        IPrecompiled contract = CONTRACTS.get(context.address);
        if (contract == null) {
            return new FastVmTransactionResult(FastVmResultCode.SUCCESS, context.getTransactionEnergy());
        } else {
            return contract.run();
        }
    }

    /**
     * Clears all contracts.
     */
    public static void clearContracts() {
        CONTRACTS.clear();
    }
}

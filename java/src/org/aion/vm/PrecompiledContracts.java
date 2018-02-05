package org.aion.vm;

import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.core.AccountState;
import org.aion.db.IBlockStoreBase;
import org.aion.vm.precompiled.TotalCurrencyContract;
import org.aion.vm.types.DataWord;

/**
 * A collection of precompiled contracts for FastVM.
 *
 * @author yulong, yao
 */
public class PrecompiledContracts {

    // total currency address definition
    public static final Address totalCurrencyAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000100");
    // TODO: move these to a configurable location (BlockConstants?)
    public static final Address totalCurrencyOwnerAddress = Address.wrap("0000000000000000000000000000000000000000000000000000000000000100");

    /**
     * Returns a precompiled contract by address. For a reader with only knowledge regarding
     * this area of the code, define the track as a temporary state on top of an existing state.
     * The intent is for the caller of this contract to be able to rollback any changes on
     * error execution. This is not enforced at compile time.
     *
     * @param address address of the desired precompiled contract (non-null)
     * @param track   temporary state on top of world state (non-null)
     * @return the desired precompiled contract or {@code null} if none exists
     */
    public static PrecompiledContract getPrecompiledContract(
            Address address, IRepositoryCache track, ExecutionContext context) {
        if (totalCurrencyAddress.equals(address)) {
            return new TotalCurrencyContract(track, address, totalCurrencyOwnerAddress);
        }
        return null;
    }

    /**
     * The abstract base class for pre-compiled contracts.
     */
    public static abstract class PrecompiledContract {
        /**
         * Returns the execution result given the specified input and nrg limit.
         *
         * @param input
         * @param nrg
         * @return
         */
        public abstract ExecutionResult execute(byte[] input, long nrg);
    }

    /**
     * Class of precompiled contracts that are capable of modifying state, the key
     * difference is that these contracts should be instance based, with an immutable
     * reference to a particular state.
     */
    public static abstract class StatefulPrecompiledContract extends PrecompiledContract {
        protected final IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track;

        public StatefulPrecompiledContract(IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> track) {
            this.track = track;
        }
    }
}

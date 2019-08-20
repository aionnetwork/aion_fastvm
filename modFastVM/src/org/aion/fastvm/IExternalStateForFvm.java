package org.aion.fastvm;

import java.math.BigInteger;
import org.aion.types.AionAddress;

/**
 * An interface that allows the caller to supply the fvm with access to state updates and state
 * queries.
 */
public interface IExternalStateForFvm {

    /**
     * Commits any state changes in this world state to its parent world state.
     */
    void commit();

    /**
     * Resets any state changes that have been made since this object was created. This is equivalent
     * to creating this object anew again from the same initial conditions.
     */
    void rollback();

    /**
     * Returns a new world state that is a direct child of this world state.
     *
     * @return a child world state.
     */
    IExternalStateForFvm newChildExternalState();

    /**
     * Returns {@code true} only if the specified address is the address of a precompiled contract.
     *
     * @param address The address to check.
     * @return whether the address is a precompiled contract.
     */
    boolean isPrecompiledContract(AionAddress address);

    /**
     * Executes an internal precompiled contract call. The provided context is the context for the
     * internal transaction.
     *
     * @param context The context of the internal transaction.
     * @return the execution result.
     */
    FastVmTransactionResult runInternalPrecompiledContractCall(ExecutionContext context);

    /**
     * Adds the specified key-value pair to the storage space of the given address, overwriting the
     * value of the pairing with this new value if the key already exists.
     *
     * @param address The address.
     * @param key The key.
     * @param value The value.
     */
    void addStorageValue(AionAddress address, FvmDataWord key, FvmDataWord value);

    /**
     * Removes any key-value pairing whose key is the given key in the storage space of the given
     * address.
     *
     * @param address The address.
     * @param key The key.
     */
    void removeStorage(AionAddress address, FvmDataWord key);

    /**
     * Returns the value of the key-value pairing in the storage space of the given address if one
     * exists, and {@code null} otherwise.
     *
     * @param address The address.
     * @param key The key.
     * @return the value.
     */
    FvmDataWord getStorageValue(AionAddress address, FvmDataWord key);

    /**
     * Returns {@code true} only if the destination address is safe for the FVM to execute.
     * Otherwise {@code false}.
     *
     * A destination address is considered safe if it is an FVM contract, a precompiled contract,
     * or a regular account.
     *
     * @param destination The destination.
     * @return whether the destination is safe.
     */
    boolean destinationAddressIsSafeForFvm(AionAddress destination);

    /**
     * Returns the code associated with the given address, or {@code null} if no code exists.
     *
     * @param address The account address.
     * @return the code.
     */
    byte[] getCode(AionAddress address);

    /**
     * Saves the specified code to the specified address.
     *
     * @param address The account address.
     * @param code The code.
     */
    void putCode(AionAddress address, byte[] code);

    /**
     * Returns {@code true} only if the specified account has state associated with it.
     * Otherwise {@code false}.
     *
     * @param address The account address.
     * @return whether the account has state or not.
     */
    boolean hasAccountState(AionAddress address);

    /**
     * Creates the specified account address.
     *
     * @param address The address to create.
     */
    void createAccount(AionAddress address);

    /**
     * Sets the vm type to FVM for the specified address.
     *
     * @param address The account address.
     */
    void setVmType(AionAddress address);

    /**
     * Returns the balance of the specified address.
     *
     * @param address The address.
     * @return the balance.
     */
    BigInteger getBalance(AionAddress address);

    /**
     * Adds the specified amount to the balance of the given address.
     *
     * @param address The address.
     * @param amount The amount.
     */
    void addBalance(AionAddress address, BigInteger amount);

    /**
     * Returns the nonce of the specified address.
     *
     * @param address The address.
     * @return the nonce.
     */
    BigInteger getNonce(AionAddress address);

    /**
     * Increments the nonce of the given address by one.
     *
     * @param address The address.
     */
    void incrementNonce(AionAddress address);

    /**
     * Returns {@code true} if the given energy limit is valid for CREATE contracts. Otherwise
     * {@code false}.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the limit is valid or not.
     */
    boolean isValidEnergyLimitForCreate(long energyLimit);

    /**
     * Returns {@code true} if the given energy limit is valid for non-CREATE contracts. Otherwise
     * {@code false}.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the limit is valid or not.
     */
    boolean isValidEnergyLimitForNonCreate(long energyLimit);

    /**
     * Returns {@code true} only if the nonce of the address is equal to the given nonce or not.
     *
     * @param address The address.
     * @param nonce The nonce to check.
     * @return whether the nonce is equal or not.
     */
    boolean accountNonceEquals(AionAddress address, BigInteger nonce);

    /**
     * Returns {@code true} only if the balance of the address is greater than or equal to the
     * balance or not.
     *
     * @param address The address.
     * @param balance The balance to check.
     * @return whether the balance is equal or not.
     */
    boolean accountBalanceIsAtLeast(AionAddress address, BigInteger balance);

    /**
     * Deducts the given energyCost from the address.
     *
     * @param address The address.
     * @param energyCost The energy cost to deduct.
     */
    void deductEnergyCost(AionAddress address, BigInteger energyCost);

    /**
     * Returns {@code true} only if the 0.4.0 fork is enabled. Otherwise {@code false}.
     *
     * @return whether the fork is enabled or not.
     */
    boolean isFork040enabled();

    /**
     * Returns {@code true} only if this is a local call. Otherwise {@code false}.
     *
     * @return whether this is a local call or not.
     */
    boolean isLocalCall();

    /**
     * Returns {@code true} only if the sender nonce should be incremented. Otherwise {@code false}.
     *
     * @return whether the sender nonce should be incremented.
     */
    boolean allowNonceIncrement();

    /**
     * Returns the address of the miner that is mining the current block.
     *
     * @return the miner address.
     */
    AionAddress getMinerAddress();

    /**
     * Returns the block number of the current block.
     *
     * @return the current block number.
     */
    long getBlockNumber();

    /**
     * Returns the timestamp of the current block.
     *
     * @return the current block timestamp.
     */
    long getBlockTimestamp();

    /**
     * Returns the energy limit of the current block.
     *
     * @return the current block energy limit.
     */
    long getBlockEnergyLimit();

    /**
     * Returns the difficulty of the current block.
     *
     * @return the current block difficulty.
     */
    FvmDataWord getBlockDifficulty();

    /**
     * Returns the block hash of the block whose block number is the specified number, or
     * {@code null} if no such block exists.
     *
     * @param blockNumber The block number.
     * @return the block hash.
     */
    byte[] getBlockHashByNumber(long blockNumber);
}

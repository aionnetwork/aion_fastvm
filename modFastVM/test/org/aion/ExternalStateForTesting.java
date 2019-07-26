package org.aion;

import java.math.BigInteger;
import org.aion.repository.AccountStateForTesting.VmType;
import org.aion.fastvm.ExecutionContext;
import org.aion.fastvm.FastVmTransactionResult;
import org.aion.fastvm.FvmDataWord;
import org.aion.fastvm.IExternalStateForFvm;
import org.aion.precompiled.PrecompiledFactoryForTesting;
import org.aion.repository.BlockchainForTesting;
import org.aion.repository.EnergyLimitRuleForTesting;
import org.aion.repository.RepositoryForTesting;
import org.aion.types.AionAddress;

public final class ExternalStateForTesting implements IExternalStateForFvm {
    private final RepositoryForTesting repository;
    private final BlockchainForTesting blockchain;
    private final AionAddress miner;
    private final boolean isLocalCall;
    private final boolean allowNonceIncrement;
    private final boolean isFork040enabled;
    private final long blockNumber;
    private final long blockTimestamp;
    private final long blockEnergyLimit;
    private final FvmDataWord blockDifficulty;

    public ExternalStateForTesting(RepositoryForTesting repository, BlockchainForTesting blockchain, AionAddress miner, FvmDataWord blockDifficulty, boolean isLocalCall, boolean allowNonceIncrement, boolean isFork040enabled, long blockNumber, long blockTimestamp, long blockEnergyLimit) {
        this.repository = repository;
        this.blockchain = blockchain;
        this.miner = miner;
        this.blockDifficulty = blockDifficulty;
        this.isLocalCall = isLocalCall;
        this.allowNonceIncrement = allowNonceIncrement;
        this.isFork040enabled = isFork040enabled;
        this.blockNumber = blockNumber;
        this.blockTimestamp = blockTimestamp;
        this.blockEnergyLimit = blockEnergyLimit;
    }

    /**
     * Commits the changes in this world state to its parent world state.
     */
    @Override
    public void commit() {
        this.repository.commit();
    }

    /**
     * Rolls back the state of this FvmState to its initial state, when this object was first
     * constructed.
     */
    @Override
    public void rollback() {
        this.repository.rollback();
    }

    /**
     * Returns a new world state that is a direct child of this world state.
     *
     * <p>Any changes made in the child world state will not alter this world state unless the
     * {@code commit()} method is called in the child, in which case all state changes will be
     * pushed to this world state.
     *
     * <p>The child world state will be passed the same block number, isLocalCall and
     * allowNonceIncrement values as this world state.
     *
     * @return a child world state.
     */
    @Override
    public ExternalStateForTesting newChildExternalState() {
        return new ExternalStateForTesting(this.repository.newChildRepository(), this.blockchain, this.miner, this.blockDifficulty, this.isLocalCall, this.allowNonceIncrement, this.isFork040enabled, this.blockNumber, this.blockTimestamp, this.blockEnergyLimit);
    }

    /**
     * Returns {@code true} only if the specified address is the address of a precompiled contract.
     *
     * @param address The address to check.
     * @return whether the address is a precompiled contract.
     */
    @Override
    public boolean isPrecompiledContract(AionAddress address) {
        return PrecompiledFactoryForTesting.isPrecompiledContract(address);
    }

    /**
     * Executes an internal precompiled contract call and returns the result.
     *
     * @param context The context of the internal transaction.
     * @return the execution result.
     */
    @Override
    public FastVmTransactionResult runInternalPrecompiledContractCall(ExecutionContext context) {
        return PrecompiledFactoryForTesting.runPrecompiledContract(context);
    }

    /**
     * Adds the provided key-value pairing to the world state, associating it only with the given
     * address.
     *
     * <p>If the given address already has a key-value pairing whose key is the same as the given
     * key, then the given value will overwrite whatever value is currently paired with that key.
     *
     * @param address The account address.
     * @param key The key.
     * @param value The value.
     */
    @Override
    public void addStorageValue(AionAddress address, FvmDataWord key, FvmDataWord value) {
        this.repository.addToStorage(address, key, value);
    }

    /**
     * Removes the provided key from the storage associated with the given address, if that key
     * exists.
     *
     * @param address The account address.
     * @param key The key.
     */
    @Override
    public void removeStorage(AionAddress address, FvmDataWord key) {
        this.repository.removeFromStorage(address, key);
    }

    /**
     * Returns the value in the key-value pairing associated with the given address and key, or
     * {@code null} if no such pairing exists.
     *
     * @param address The account address.
     * @param key The key.
     * @return the value.
     */
    @Override
    public FvmDataWord getStorageValue(AionAddress address, FvmDataWord key) {
        FvmDataWord value = this.repository.getStorageValue(address, key);
        return (value == null) ? FvmDataWord.fromBytes(new byte[FvmDataWord.SIZE]) : value;
    }

    /**
     * Returns {@code true} only if the specified destination address is a safe address for the FVM.
     *
     * A safe address for the FVM is an FVM contract, a precompiled contract or a regular account.
     *
     * @param destination The destination.
     * @return whether or not the address is safe.
     */
    @Override
    public boolean destinationAddressIsSafeForFvm(AionAddress destination) {
        return this.repository.getVmType(destination) != VmType.AVM;
    }

    /**
     * Returns the code associated with the specified address, or {@code null} if no code exists.
     *
     * @param address The account address.
     * @return the contract code.
     */
    @Override
    public byte[] getCode(AionAddress address) {
        return this.repository.getCode(address);
    }

    /**
     * Saves the specified code with the specified account.
     *
     * @param address The account address.
     * @param code The code.
     */
    @Override
    public void putCode(AionAddress address, byte[] code) {
        this.repository.saveCode(address, code);
    }

    /**
     * Returns {@code true} only if the specified address has state associated with it.
     *
     * @param address The account address.
     * @return whether the address has state.
     */
    @Override
    public boolean hasAccountState(AionAddress address) {
        return this.repository.hasAccountState(address);
    }

    /**
     * Creates a new account whose address is the specified address.
     *
     * @param address The address to create.
     */
    @Override
    public void createAccount(AionAddress address) {
        this.repository.createAccount(address);
    }

    /**
     * Sets the vm type of the specified address to FVM.
     *
     * @param address The account address.
     */
    @Override
    public void setVmType(AionAddress address) {
        this.repository.setVmType(address, VmType.FVM);
    }

    /**
     * Returns the balance of the specified address.
     *
     * @param address The address.
     * @return the balance.
     */
    @Override
    public BigInteger getBalance(AionAddress address) {
        return this.repository.getBalance(address);
    }

    /**
     * Adds the specified amount to the address. If amount is negative it is possible that the result
     * could be a negative balance for the account. It is the responsibility of the caller to ensure
     * that this operation is safe.
     *
     * @param address The address.
     * @param amount The amount.
     */
    @Override
    public void addBalance(AionAddress address, BigInteger amount) {
        this.repository.addBalance(address, amount);
    }

    /**
     * Returns the nonce of the specified address.
     *
     * @param address The address.
     * @return the account nonce.
     */
    @Override
    public BigInteger getNonce(AionAddress address) {
        return this.repository.getNonce(address);
    }

    /**
     * Increments the account nonce by one if this is a local call and nonce incrementation is
     * allowed. Otherwise this method does nothing.
     *
     * @param address The address.
     */
    @Override
    public void incrementNonce(AionAddress address) {
        if (!this.isLocalCall && this.allowNonceIncrement) {
            this.repository.incrementNonce(address);
        }
    }

    /**
     * Returns {@code true} only if the specified energyLimit is a valid limit for a contract
     * create transaction.
     *
     * This method always returns {@code true} if this is a local call. We skip energy checks for
     * local calls.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the energy limit is valid.
     */
    @Override
    public boolean isValidEnergyLimitForCreate(long energyLimit) {
        return (this.isLocalCall) ? true : EnergyLimitRuleForTesting.isValidEnergyLimitForCreate(energyLimit);
    }

    /**
     * Returns {@code true} only if the specified energyLimit is a valid limit for a contract
     * non-create transaction.
     *
     * This method always returns {@code true} if this is a local call. We skip energy checks for
     * local calls.
     *
     * @param energyLimit The energy limit to check.
     * @return whether the energy limit is valid.
     */
    @Override
    public boolean isValidEnergyLimitForNonCreate(long energyLimit) {
        return (this.isLocalCall) ? true : EnergyLimitRuleForTesting.isValidEnergyLimitForNonCreate(energyLimit);
    }

    /**
     * Returns {@code true} only if the specified nonce is equal to the nonce of the specified
     * account.
     *
     * This method always returns {@code true} if this is a local call. We skip nonce checks for
     * local calls.
     *
     * @param address The address.
     * @param nonce The nonce to check.
     * @return whether the nonce is equal.
     */
    @Override
    public boolean accountNonceEquals(AionAddress address, BigInteger nonce) {
        return (this.isLocalCall) ? true : getNonce(address).equals(nonce);
    }

    /**
     * Returns {@code true} only if the balance of the specified address is greater than or equal to
     * the specified balance.
     *
     * This method always returns {@code true} if this is a local call. We skip balance checks for
     * local calls.
     *
     * @param address The address.
     * @param balance The balance to check.
     * @return
     */
    @Override
    public boolean accountBalanceIsAtLeast(AionAddress address, BigInteger balance) {
        return (this.isLocalCall) ? true : getBalance(address).compareTo(balance) >= 0;
    }

    /**
     * Deducts the specified energyCost from the specified account.
     *
     * This method can result in a negative balance for the specified account and it is the
     * responsibility of the caller to ensure that it is appropriate to perform this operation.
     *
     * This method always returns {@code true} if this is a local call. We do not charge energy costs
     * for local calls.
     *
     * @param address The address.
     * @param energyCost The energy cost to deduct.
     */
    @Override
    public void deductEnergyCost(AionAddress address, BigInteger energyCost) {
        if (!this.isLocalCall) {
            this.repository.addBalance(address, energyCost.negate());
        }
    }

    /**
     * Returns {@code true} only if the 0.4.0 fork is enabled.
     *
     * @return whether the fork is enabled.
     */
    @Override
    public boolean isFork040enabled() {
        return this.isFork040enabled;
    }

    /**
     * Returns {@code true} only if this is a local call.
     *
     * @return whether this is a local call.
     */
    @Override
    public boolean isLocalCall() {
        return this.isLocalCall;
    }

    /**
     * Returns {@code true} only if the sender nonce is allowed to be incremented.
     *
     * @return whether the sender nonce can be incremented.
     */
    @Override
    public boolean allowNonceIncrement() {
        return this.allowNonceIncrement;
    }

    /**
     * Returns the address of the miner that is mining the current block.
     *
     * @return the miner address.
     */
    @Override
    public AionAddress getMinerAddress() {
        return this.miner;
    }

    /**
     * Returns the number of the current block.
     *
     * @return the current block number.
     */
    @Override
    public long getBlockNumber() {
        return this.blockNumber;
    }

    /**
     * Returns the timestamp of the current block.
     *
     * @return the current block timestamp.
     */
    @Override
    public long getBlockTimestamp() {
        return this.blockTimestamp;
    }

    /**
     * Returns the block energy limit of the current block.
     *
     * @return the current block energy limit.
     */
    @Override
    public long getBlockEnergyLimit() {
        return this.blockEnergyLimit;
    }

    /**
     * Returns the difficulty of the current block.
     *
     * @return the current block difficulty.
     */
    @Override
    public long getBlockDifficulty() {
        return this.blockDifficulty.toLong();
    }

    /**
     * Returns the hash of the block whose block number is the specified number, or {@code null} if
     * no such block exists.
     *
     * @param blockNumber The block number.
     * @return the block hash.
     */
    @Override
    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.blockchain.getBlockHashByNumber(blockNumber);
    }

    public void setNonce(AionAddress address, BigInteger nonce) {
        this.repository.setNonce(address, nonce);
    }

    public VmType getVmType(AionAddress address) {
        return this.repository.getVmType(address);
    }
}

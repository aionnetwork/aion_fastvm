package org.aion.repository;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.aion.repository.AccountStateForTesting.VmType;
import org.aion.fastvm.FvmDataWord;
import org.aion.types.AionAddress;

public final class RepositoryForTesting {
    private final RepositoryForTesting parent;
    private final Map<AionAddress, Map<FvmDataWord, FvmDataWord>> storage;
    private final Map<AionAddress, Set<FvmDataWord>> deletedStorage;
    private final Map<AionAddress, AccountStateForTesting> accountStates;

    private RepositoryForTesting(RepositoryForTesting parent) {
        this.parent = parent;
        this.storage = new HashMap<>();
        this.deletedStorage = new HashMap<>();
        this.accountStates = new HashMap<>();
    }

    public static RepositoryForTesting newRepository() {
        return new RepositoryForTesting(null);
    }

    public RepositoryForTesting newChildRepository() {
        return new RepositoryForTesting(this);
    }

    public void commit() {
        if (this.parent == null) {
            throw new IllegalStateException("Cannot commit: this repository has no parent repository!");
        }

        // Delete all of the storage that this repository has deleted.
        for (Entry<AionAddress, Set<FvmDataWord>> deletedStorageEntry : this.deletedStorage.entrySet()) {
            if (this.parent.storage.containsKey(deletedStorageEntry.getKey())) {
                for (FvmDataWord deletedStorage : deletedStorageEntry.getValue()) {
                    this.parent.storage.get(deletedStorageEntry.getKey()).remove(deletedStorage);
                }
            }

            if (!this.parent.deletedStorage.containsKey(deletedStorageEntry.getKey())) {
                this.parent.deletedStorage.put(deletedStorageEntry.getKey(), new HashSet<>());
            }
            for (FvmDataWord deletedKey : deletedStorageEntry.getValue()) {
                this.parent.deletedStorage.get(deletedStorageEntry.getKey()).add(deletedKey);
            }
        }

        // Add all of the storage from this repository that has been added.
        for (Entry<AionAddress, Map<FvmDataWord, FvmDataWord>> addedStorageEntry : this.storage.entrySet()) {
            for (Entry<FvmDataWord, FvmDataWord> keyValueEntry : addedStorageEntry.getValue().entrySet()) {
                if (!this.parent.storage.containsKey(addedStorageEntry.getKey())) {
                    this.parent.storage.put(addedStorageEntry.getKey(), new HashMap<>());
                }
                this.parent.storage.get(addedStorageEntry.getKey()).put(keyValueEntry.getKey(), keyValueEntry.getValue());
            }
        }

        // Add all of the account states from this repository that have been added.
        for (Entry<AionAddress, AccountStateForTesting> accountStatesEntry : this.accountStates.entrySet()) {
            this.parent.accountStates.put(accountStatesEntry.getKey(), accountStatesEntry.getValue());
        }

        this.deletedStorage.clear();
        this.storage.clear();
        this.accountStates.clear();
    }

    public void rollback() {
        this.storage.clear();
        this.deletedStorage.clear();
        this.accountStates.clear();
    }

    /**
     * Adds the key-value pair to the storage for the specified account. If this account already has
     * the specified key, then its value will be overwritten.
     */
    public void addToStorage(AionAddress address, FvmDataWord key, FvmDataWord value) {
        if (address == null) {
            throw new NullPointerException("Cannot add to null address!");
        }
        if (key == null) {
            throw new NullPointerException("Cannot add null key!");
        }
        if (value == null) {
            throw new NullPointerException("Cannot add null value!");
        }

        if (!this.storage.containsKey(address)) {
            // Try to grab storage entry from parent if we have one.
            this.storage.put(address, new HashMap<>(getKeyValueStoreFromParent(address)));
        }

        this.storage.get(address).put(key, value);
    }

    /**
     * Removes the key-value pair from the storage for the specified account if it exists.
     */
    public void removeFromStorage(AionAddress address, FvmDataWord key) {
        if (address == null) {
            throw new NullPointerException("Cannot remove from null address!");
        }
        if (key == null) {
            throw new NullPointerException("Cannot remove null key!");
        }

        // If we have already deleted this key then we have nothing else to do.
        if (this.deletedStorage.containsKey(address)) {
            if (this.deletedStorage.get(address).contains(key)) {
                return;
            }
        }

        if (this.storage.containsKey(address)) {
            if (this.storage.get(address).containsKey(key)) {
                this.storage.get(address).remove(key);
            }
        }

        // Record this key as deleted.
        if (!this.deletedStorage.containsKey(address)) {
            this.deletedStorage.put(address, new HashSet<>());
        }
        this.deletedStorage.get(address).add(key);
    }

    /**
     * Returns the value corresponding to the key for the specified account if such a value exists,
     * or {@code null} if it does not exist.
     */
    public FvmDataWord getStorageValue(AionAddress address, FvmDataWord key) {
        if (address == null) {
            throw new NullPointerException("Cannot get value from null address!");
        }
        if (key == null) {
            throw new NullPointerException("Cannot get value from null key!");
        }

        // If this key is already marked as deleted, then we're done.
        if (this.deletedStorage.containsKey(address)) {
            if (this.deletedStorage.get(address).contains(key)) {
                return null;
            }
        }

        if (this.storage.containsKey(address)) {
            if (this.storage.get(address).containsKey(key)) {
                return this.storage.get(address).get(key);
            } else {
                // Query the parent for the value if we have a parent.
                return (this.parent == null) ? null : this.parent.getStorageValue(address, key);
            }
        } else {
            // Query the parent for the value if we have a parent.
            return (this.parent == null) ? null : this.parent.getStorageValue(address, key);
        }
    }

    /**
     * Returns the code associated with address.
     */
    public byte[] getCode(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot get code from null address!");
        }

        if (this.accountStates.containsKey(address)) {
            return this.accountStates.get(address).code;
        } else {
            // Query the parent for the value if we have a parent.
            return (this.parent == null) ? null : this.parent.getCode(address);
        }
    }

    /**
     * Saves the code to the account.
     */
    public void saveCode(AionAddress address, byte[] code) {
        if (address == null) {
            throw new NullPointerException("Cannot save code to null address!");
        }
        if (code == null) {
            throw new NullPointerException("Cannot save null code!");
        }

        if (!this.accountStates.containsKey(address)) {
            this.accountStates.put(address, getAccountStateFromParent(address));
        }

        AccountStateForTesting accountState = this.accountStates.get(address);
        AccountStateForTesting newAccountState = AccountStateForTesting.newState(accountState.balance, accountState.nonce, code, accountState.type);

        this.accountStates.put(address, newAccountState);
    }

    /**
     * Returns true if the address has state.
     */
    public boolean hasAccountState(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot check account state of null address!");
        }

        return hasAccount(address) || hasStorage(address);
    }

    /**
     * Creates state for the account.
     */
    public void createAccount(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot create null address!");
        }

        if (this.accountStates.containsKey(address)) {
            throw new IllegalStateException("Account already has state!");
        }

        this.accountStates.put(address, AccountStateForTesting.emptyState());
    }

    /**
     * Sets the vm type for the address.
     */
    public void setVmType(AionAddress address, VmType type) {
        if (address == null) {
            throw new NullPointerException("Cannot set vm type of null address!");
        }
        if (type == null) {
            throw new NullPointerException("Cannot set null vm type!");
        }

        if (!this.accountStates.containsKey(address)) {
            this.accountStates.put(address, getAccountStateFromParent(address));
        }

        AccountStateForTesting accountState = this.accountStates.get(address);
        AccountStateForTesting newAccountState = AccountStateForTesting.newState(accountState.balance, accountState.nonce, accountState.code, type);

        this.accountStates.put(address, newAccountState);
    }

    /**
     * Returns the balance of the account.
     */
    public BigInteger getBalance(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot get balance of null address!");
        }

        if (this.accountStates.containsKey(address)) {
            return this.accountStates.get(address).balance;
        } else {
            // Query the parent for the balance if we have a parent.
            return (this.parent == null) ? BigInteger.ZERO : this.parent.getBalance(address);
        }
    }

    /**
     * Adds the amount to the account.
     */
    public void addBalance(AionAddress address, BigInteger amount) {
        if (address == null) {
            throw new NullPointerException("Cannot add balance to null address!");
        }
        if (amount == null) {
            throw new NullPointerException("Cannot add null amount!");
        }

        if (!this.accountStates.containsKey(address)) {
            this.accountStates.put(address, getAccountStateFromParent(address));
        }

        AccountStateForTesting accountState = this.accountStates.get(address);
        AccountStateForTesting newAccountState = AccountStateForTesting.newState(accountState.balance.add(amount), accountState.nonce, accountState.code, accountState.type);

        this.accountStates.put(address, newAccountState);
    }

    /**
     * Returns the nonce of the specified account.
     */
    public BigInteger getNonce(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot get nonce of null address!");
        }

        if (this.accountStates.containsKey(address)) {
            return this.accountStates.get(address).nonce;
        } else {
            // Query the parent for the balance if we have a parent.
            return (this.parent == null) ? BigInteger.ZERO : this.parent.getNonce(address);
        }
    }

    /**
     * Sets the nonce of the specified address to nonce.
     */
    public void setNonce(AionAddress address, BigInteger nonce) {
        if (address == null) {
            throw new NullPointerException("Cannot set nonce of null address!");
        }
        if (nonce == null) {
            throw new NullPointerException("Cannot set null nonce!");
        }

        if (!this.accountStates.containsKey(address)) {
            this.accountStates.put(address, getAccountStateFromParent(address));
        }

        AccountStateForTesting accountState = this.accountStates.get(address);
        AccountStateForTesting newAccountState = AccountStateForTesting.newState(accountState.balance, nonce, accountState.code, accountState.type);

        this.accountStates.put(address, newAccountState);
    }

    /**
     * Increments the nonce of the account.
     */
    public void incrementNonce(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot increment nonce of null address!");
        }

        if (!this.accountStates.containsKey(address)) {
            this.accountStates.put(address, getAccountStateFromParent(address));
        }

        AccountStateForTesting accountState = this.accountStates.get(address);
        AccountStateForTesting newAccountState = AccountStateForTesting.newState(accountState.balance, accountState.nonce.add(BigInteger.ONE), accountState.code, accountState.type);

        this.accountStates.put(address, newAccountState);
    }

    /**
     * Returns the vm type of the specified address.
     */
    public VmType getVmType(AionAddress address) {
        if (address == null) {
            throw new NullPointerException("Cannot get vm type of null address!");
        }

        if (this.accountStates.containsKey(address)) {
            return this.accountStates.get(address).type;
        } else {
            // Query the parent for the vm type if we have a parent.
            return (this.parent == null) ? VmType.NONE : this.parent.getVmType(address);
        }
    }

    public boolean hasStorageKeys(AionAddress address) {
        if (this.storage.containsKey(address)) {
            return !this.storage.get(address).isEmpty();
        } else {
            return (this.parent == null) ? false : this.parent.hasStorageKeys(address);
        }
    }

    private boolean hasStorage(AionAddress address) {
        if (this.storage.containsKey(address)) {
            return true;
        } else {
            return (this.parent == null) ? false : this.parent.hasStorage(address);
        }
    }

    private boolean hasAccount(AionAddress address) {
        if (this.accountStates.containsKey(address)) {
            return true;
        } else {
            return (this.parent == null) ? false : this.parent.hasAccountState(address);
        }
    }

    private Map<FvmDataWord, FvmDataWord> getKeyValueStoreFromParent(AionAddress address) {
        if (this.parent != null) {
            if (this.storage.containsKey(address)) {
                return this.storage.get(address);
            } else {
                return this.parent.getKeyValueStoreFromParent(address);
            }
        } else {
            return (this.storage.containsKey(address)) ? this.storage.get(address) : new HashMap<>();
        }
    }

    private AccountStateForTesting getAccountStateFromParent(AionAddress address) {
        if (this.parent != null) {
            if (this.accountStates.containsKey(address)) {
                return this.accountStates.get(address);
            } else {
                return this.parent.getAccountStateFromParent(address);
            }
        } else {
            return (this.accountStates.containsKey(address)) ? this.accountStates.get(address) : AccountStateForTesting.emptyState();
        }
    }
}

package org.aion.vm;

import java.math.BigInteger;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.aion.types.ByteArrayWrapper;
import org.aion.interfaces.db.ContractDetails;
import org.aion.interfaces.db.Repository;
import org.aion.interfaces.db.RepositoryCache;
import org.aion.types.ByteArrayWrapper;
import org.aion.util.bytes.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.types.Address;

public class DummyRepository implements RepositoryCache<AccountState, IBlockStoreBase<?, ?>> {
    private DummyRepository parent;
    Map<Address, AccountState> accounts = new HashMap<>();
    Map<Address, byte[]> contracts = new HashMap<>();
    Map<Address, Map<String, byte[]>> storage = new HashMap<>();

    public DummyRepository() {}

    public DummyRepository(DummyRepository parent) {
        // Note: only references are copied
        accounts.putAll(parent.accounts);
        contracts.putAll(parent.contracts);
        storage.putAll(parent.storage);
        this.parent = parent;
    }

    public void addContract(Address address, byte[] code) {
        contracts.put(address, code);
    }

    @Override
    public AccountState createAccount(Address addr) {
        AccountState as = new AccountState();
        accounts.put(addr, as);
        return as;
    }

    @Override
    public boolean hasAccountState(Address addr) {
        return accounts.containsKey(addr);
    }

    @Override
    public AccountState getAccountState(Address addr) {
        if (!hasAccountState(addr)) {
            createAccount(addr);
        }
        return accounts.get(addr);
    }

    @Override
    public void deleteAccount(Address addr) {
        accounts.remove(addr);
    }

    @Override
    public BigInteger incrementNonce(Address addr) {
        // an exception will be thrown if account does not exist
        AccountState as = getAccountState(addr);
        as.incrementNonce();

        return as.getNonce();
    }

    @Override
    public BigInteger setNonce(Address address, BigInteger nonce) {
        AccountState as = getAccountState(address);
        as.setNonce(nonce);
        return nonce;
    }

    @Override
    public BigInteger getNonce(Address addr) {
        // an exception will be thrown if account does not exist
        return getAccountState(addr).getNonce();
    }

    @Override
    public ContractDetails getContractDetails(Address addr) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasContractDetails(Address addr) {
        return contracts.containsKey(addr);
    }

    @Override
    public void saveCode(Address addr, byte[] code) {
        contracts.put(addr, code);
    }

    @Override
    public byte[] getCode(Address addr) {
        byte[] code = contracts.get(addr);
        return code == null ? ByteUtil.EMPTY_BYTE_ARRAY : code;
    }

    @Override
    public Map<ByteArrayWrapper, ByteArrayWrapper> getStorage(
            Address address, Collection<ByteArrayWrapper> keys) {
        throw new RuntimeException("Not supported");
    }

    public int getStorageSize(Address address) {
        throw new RuntimeException("Not supported");
    }

    public Set<ByteArrayWrapper> getStorageKeys(Address address) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void addStorageRow(Address addr, ByteArrayWrapper key, ByteArrayWrapper value) {
        Map<String, byte[]> map = storage.computeIfAbsent(addr, k -> new HashMap<>());

        map.put(key.toString(), value.getData());
    }

    @Override
    public void removeStorageRow(Address addr, ByteArrayWrapper key) {
        Map<String, byte[]> map = storage.computeIfAbsent(addr, k -> new HashMap<>());
        map.remove(key.toString());
    }

    @Override
    public ByteArrayWrapper getStorageValue(Address addr, ByteArrayWrapper key) {
        Map<String, byte[]> map = storage.get(addr);
        if (map != null && map.containsKey(key.toString())) {
            return new ByteArrayWrapper(map.get(key.toString()));
        } else {
            return null;
        }
    }

    @Override
    public List<byte[]> getPoolTx() {
        return null;
    }

    @Override
    public List<byte[]> getCacheTx() {
        return null;
    }

    @Override
    public BigInteger getBalance(Address addr) {
        return getAccountState(addr).getBalance();
    }

    @Override
    public BigInteger addBalance(Address addr, BigInteger value) {
        return getAccountState(addr).addToBalance(value);
    }

    @Override
    public RepositoryCache<AccountState, IBlockStoreBase<?, ?>> startTracking() {
        return new DummyRepository(this);
    }

    @Override
    public void flush() {
        this.parent.accounts = accounts;
        this.parent.contracts = contracts;
        this.parent.storage = storage;
    }

    @Override
    public void flushTo(Repository repo, boolean clearStorageAfterFlush) {
        DummyRepository repoAsDummy = (DummyRepository) repo;
        repoAsDummy.accounts = accounts;
        repoAsDummy.contracts = contracts;
        repoAsDummy.storage = storage;
    }

    @Override
    public void rollback() {}

    @Override
    public void syncToRoot(byte[] root) {}

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {}

    @Override
    public boolean isValidRoot(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isIndexed(byte[] hash, long level) {
        return false;
    }

    @Override
    public void updateBatch(
            Map<Address, AccountState> accountStates,
            Map<Address, ContractDetails> contractDetailes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadAccountState(
            Address addr,
            Map<Address, AccountState> cacheAccounts,
            Map<Address, ContractDetails> cacheDetails) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Repository<AccountState, IBlockStoreBase<?, ?>> getSnapshotTo(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isSnapshot() {
        return false;
    }

    @Override
    public IBlockStoreBase<?, ?> getBlockStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addTxBatch(Map<byte[], byte[]> pendingTx, boolean isPool) {}

    @Override
    public void removeTxBatch(Set<byte[]> pendingTx, boolean isPool) {}

    @Override
    public void compact() {
        throw new UnsupportedOperationException(
                "The tracking cache cannot be compacted. \'Compact\' should be called on the tracked repository.");
    }
}

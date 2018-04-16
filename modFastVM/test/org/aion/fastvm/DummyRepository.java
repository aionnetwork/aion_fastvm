/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.fastvm;

import org.aion.base.db.IContractDetails;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;

import java.math.BigInteger;
import java.util.*;

public class DummyRepository implements IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> {

    private Map<Address, AccountState> accounts = new HashMap<>();

    private Map<Address, byte[]> contracts = new HashMap<>();

    private Map<Address, Map<String, byte[]>> storage = new HashMap<>();

    public DummyRepository() {
    }

    public DummyRepository(DummyRepository parent) {
        // Note: only references are copied
        accounts.putAll(parent.accounts);
        contracts.putAll(parent.contracts);
        storage.putAll(parent.storage);
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
        throw new RuntimeException("Not supported");
    }

    @Override
    public BigInteger getNonce(Address addr) {
        // an exception will be thrown if account does not exist
        return getAccountState(addr).getNonce();
    }

    @Override
    public IContractDetails<DataWord> getContractDetails(Address addr) {
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
    public Map<DataWord, DataWord> getStorage(Address address, Collection<DataWord> keys) {
        throw new RuntimeException("Not supported");
    }

    @Override
    public void addStorageRow(Address addr, DataWord key, DataWord value) {
        Map<String, byte[]> map = storage.get(addr);
        if (map == null) {
            map = new HashMap<>();
            storage.put(addr, map);
        }

        map.put(key.toString(), value.getData());
    }

    @Override
    public DataWord getStorageValue(Address addr, DataWord key) {
        Map<String, byte[]> map = storage.get(addr);
        if (map != null && map.containsKey(key.toString())) {
            return new DataWord(map.get(key.toString()));
        } else {
            return DataWord.ZERO;
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

    //    @Override
    //    public void setBalance(Address addr, BigInteger value) {
    //        getAccountState(addr).setBalance(value);
    //    }
    //
    //    @Override
    //    public Set<Address> getAccountsKeys() {
    //        Set<Address> set = new HashSet<>();
    //        for (Address k : accounts.keySet()) {
    //            set.add(k);
    //        }
    //        return set;
    //    }

    @Override
    public IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> startTracking() {
        return new DummyRepository(this);
    }

    @Override
    public void flush() {

    }

    @Override
    public void rollback() {

    }

    @Override
    public void syncToRoot(byte[] root) {

    }

    @Override
    public boolean isClosed() {
        return false;
    }

    @Override
    public void close() {

    }

    @Override
    public boolean isValidRoot(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateBatch(Map<Address, AccountState> accountStates,
                            Map<Address, IContractDetails<DataWord>> contractDetailes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getRoot() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void loadAccountState(Address addr, Map<Address, AccountState> cacheAccounts,
                                 Map<Address, IContractDetails<DataWord>> cacheDetails) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IRepository<AccountState, DataWord, IBlockStoreBase<?, ?>> getSnapshotTo(byte[] root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IBlockStoreBase<?, ?> getBlockStore() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addTxBatch(Map<byte[], byte[]> pendingTx, boolean isPool) {

    }

    @Override
    public void removeTxBatch(Set<byte[]> pendingTx, boolean isPool) {

    }

    @Override
    public void compact() {
        throw new UnsupportedOperationException(
                "The tracking cache cannot be compacted. \'Compact\' should be called on the tracked repository.");
    }
}

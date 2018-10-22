package org.aion.vm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import org.aion.base.type.Address;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.ExecutionHelper.Call;
import org.aion.zero.types.AionInternalTx;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// NOTE: Calls have no restrictions on them, can be null or anything, is this wanted?
// Also, seems no one uses Call, is it even necessary?

public class ExecutionHelperUnitTest {
    private ExecutionHelper helper;

    @Before
    public void setup() {
        helper = new ExecutionHelper();
    }

    @After
    public void tearDown() {
        helper = null;
    }

    @Test
    public void testNewExecutionHelper() {
        assertTrue(helper.getDeleteAccounts().isEmpty());
        assertTrue(helper.getLogs().isEmpty());
        assertTrue(helper.getCalls().isEmpty());
        assertTrue(helper.getInternalTransactions().isEmpty());
    }

    @Test
    public void testAddDeleteAccount() {
        Address addr = getNewAddress();
        helper.addDeleteAccount(addr);
        assertEquals(1, helper.getDeleteAccounts().size());
        assertEquals(addr, helper.getDeleteAccounts().get(0));
    }

    @Test
    public void testAddDeleteAccountDuplicate() {
        Address addr1 = getNewAddress();
        Address addr2 = getNewAddress();
        helper.addDeleteAccount(addr1);
        helper.addDeleteAccount(addr2);
        helper.addDeleteAccount(addr2);
        assertEquals(2, helper.getDeleteAccounts().size());
        Address addr = helper.getDeleteAccounts().get(0);
        if (addr.equals(addr1)) {
            assertEquals(addr2, helper.getDeleteAccounts().get(1));
        } else if (addr.equals(addr2)) {
            assertEquals(addr1, helper.getDeleteAccounts().get(1));
        } else {
            fail("Delete accounts not added properly.");
        }
    }

    @Test
    public void testAddDeleteAccountsCollectionContainsNulls() {
        int numAddrs = 15;
        Collection<Address> addresses = getNewAddresses(numAddrs, 12);
        helper.addDeleteAccounts(addresses);
        assertEquals(numAddrs, helper.getDeleteAccounts().size());
        for (Address addr : helper.getDeleteAccounts()) {
            assertTrue(addresses.contains(addr));
        }
    }

    @Test
    public void testAddDeleteAccountsCollectionContainsDuplicates() {
        int numAddrs = 31;
        Collection<Address> addresses = getNewAddresses(numAddrs, 3);
        Collection<Address> duplicates = new ArrayList<>();
        Iterator<Address> addrIt = addresses.iterator();
        for (int i = 0; i < addresses.size() / 2; i++) {
            duplicates.add(addrIt.next());
        }
        duplicates.addAll(addresses);
        helper.addDeleteAccounts(duplicates);
        assertEquals(numAddrs, helper.getDeleteAccounts().size());
        for (Address addr : helper.getDeleteAccounts()) {
            assertTrue(addresses.contains(addr));
        }
    }

    @Test
    public void testAddDeleteAccountsWithDuplicates() {
        int numAddrs1 = 23;
        int numAddrs2 = 45;
        Collection<Address> addresses = getNewAddresses(numAddrs1, 3);
        Collection<Address> merged = new ArrayList<>(addresses);
        merged.addAll(getNewAddresses(numAddrs2, 6));
        helper.addDeleteAccounts(addresses);
        helper.addDeleteAccounts(merged);
        assertEquals(numAddrs1 + numAddrs2, helper.getDeleteAccounts().size());
        for (Address addr : helper.getDeleteAccounts()) {
            assertTrue(merged.contains(addr));
        }
    }

    @Test
    public void testAddLogBulk() {
        Collection<Log> logs = getNewLogs(29);
        for (Log log : logs) {
            helper.addLog(log);
        }
        assertEquals(logs, helper.getLogs());
    }

    @Test
    public void testAddLogBulkSomeDuplicates() {
        Collection<Log> logs = getNewLogs(22);
        for (Log log : logs) {
            helper.addLog(log);
            helper.addLog(log);
        }
        assertEquals(logs.size() * 2, helper.getLogs().size());
        for (Log log : helper.getLogs()) {
            assertTrue(logs.contains(log));
        }
    }

    @Test
    public void testAddLogsBulk() {
        Collection<Log> logs = getNewLogs(38);
        helper.addLogs(logs);
        assertEquals(logs, helper.getLogs());
    }

    @Test
    public void testAddLogsSomeNull() {
        int numLogs = 33;
        Collection<Log> logs = getNewLogs(numLogs);
        for (int i = 0; i < 8; i++) {
            logs.add(null);
        }
        helper.addLogs(logs);
        assertEquals(numLogs, helper.getLogs().size());
        for (Log log : logs) {
            assertTrue(logs.contains(log));
        }
    }

    @Test
    public void testAddLogsSomeDuplicates() {
        Collection<Log> logs = getNewLogs(29);
        helper.addLogs(logs);
        helper.addLogs(logs);
        assertEquals(logs.size() * 2, helper.getLogs().size());
        for (Log log : logs) {
            assertTrue(logs.contains(log));
        }
    }

    @Test
    public void testAddCallNullFields() {
        helper.addCall(null, null, null);
        List<Call> calls = helper.getCalls();
        assertEquals(1, calls.size());
        Call call = calls.get(0);
        assertNull(call.getData());
        assertNull(call.getDestination());
        assertNull(call.getValue());
    }

    @Test
    public void testAddTxBulk() {
        Collection<AionInternalTx> txs = getNewInternalTxs(22);
        for (AionInternalTx tx : txs) {
            helper.addInternalTransaction(tx);
        }
        assertEquals(txs, helper.getInternalTransactions());
    }

    @Test
    public void testAddTxBulkSomeDuplicates() {
        Collection<AionInternalTx> txs = getNewInternalTxs(17);
        for (AionInternalTx tx : txs) {
            helper.addInternalTransaction(tx);
            helper.addInternalTransaction(tx);
        }
        assertEquals(txs.size() * 2, helper.getInternalTransactions().size());
    }

    @Test
    public void testAddTxs() {
        Collection<AionInternalTx> txs = getNewInternalTxs(26);
        helper.addInternalTransactions(txs);
        assertEquals(txs.size(), helper.getInternalTransactions().size());
    }

    @Test
    public void testAddTxsSomeNull() {
        Collection<AionInternalTx> txs = getNewInternalTxs(26);
        Collection<AionInternalTx> txsWithNulls = new ArrayList<>(txs);
        for (int i = 0; i < 19; i++) {
            txsWithNulls.add(null);
        }
        helper.addInternalTransactions(txsWithNulls);
        assertEquals(txs.size(), helper.getInternalTransactions().size());
    }

    @Test
    public void testAddTxsSomeDuplicates() {
        Collection<AionInternalTx> txs = getNewInternalTxs(26);
        helper.addInternalTransactions(txs);
        helper.addInternalTransactions(txs);
        assertEquals(txs.size() * 2, helper.getInternalTransactions().size());
    }

    @Test
    public void testRejectEmptyTxListThrowsNoExceptions() {
        helper.rejectInternalTransactions();
    }

    @Test
    public void testRejectNonEmptyTxList() {
        helper.addInternalTransactions(getNewInternalTxs(33));
        for (AionInternalTx tx : helper.getInternalTransactions()) {
            assertFalse(tx.isRejected());
        }
        helper.rejectInternalTransactions();
        for (AionInternalTx tx : helper.getInternalTransactions()) {
            assertTrue(tx.isRejected());
        }
    }

    @Test
    public void testMergeWhenSuccessTrue() {
        int otherTx = 21, helperTx = 15, otherAddrs = 17, helperAddrs = 23, otherLogs = 13;
        int helperLogs = 11, numCalls = 12;
        ExecutionHelper other = getNewHelper(otherTx, otherAddrs, otherLogs, 9);
        helper = getNewHelper(helperTx, helperAddrs, helperLogs, numCalls);
        helper.merge(other, true);

        // Txs, addrs, logs all merge but no calls get transferred.
        int numTxs = otherTx + helperTx;
        int numAddrs = otherAddrs + helperAddrs;
        int numLogs = otherLogs + helperLogs;
        assertEquals(numTxs, helper.getInternalTransactions().size());
        assertEquals(numAddrs, helper.getDeleteAccounts().size());
        assertEquals(numLogs, helper.getLogs().size());
        assertEquals(numCalls, helper.getCalls().size());
    }

    @Test
    public void testMergeWhenSuccessFalse() {
        int otherTx = 32, helperTx = 42, helperAddrs = 33, helperLogs = 27, numCalls = 21;
        ExecutionHelper other = getNewHelper(otherTx, 22, 17, 9);
        helper = getNewHelper(helperTx, helperAddrs, helperLogs, numCalls);
        helper.merge(other, false);

        // Txs are the only thing transferred.
        int numTxs = otherTx + helperTx;
        assertEquals(numTxs, helper.getInternalTransactions().size());
        assertEquals(helperAddrs, helper.getDeleteAccounts().size());
        assertEquals(helperLogs, helper.getLogs().size());
        assertEquals(numCalls, helper.getCalls().size());
    }

    /**
     * Returns a collection of num newly created addresses, each consisting of random bytes and of
     * numNull null values. If numNull > num then num nulls are added.
     *
     * @param num The number of addresses in the returned collection.
     * @param numNull The number of nulls to add to the collection.
     * @return the collection of newly created addresses and numNull nulls.
     */
    private Collection<Address> getNewAddresses(int num, int numNull) {
        Collection<Address> collection = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            collection.add(getNewAddress());
            if (i < numNull) {
                collection.add(null);
            }
        }
        return collection;
    }

    /**
     * Returns a newly created address consisting of random bytes.
     *
     * @return a newly created address consisting of random bytes.
     */
    private Address getNewAddress() {
        return new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
    }

    /**
     * Returns a collection of num new randomly generated logs.
     *
     * @param num The number of logs to produce.
     * @return the collection of new logs.
     */
    private Collection<Log> getNewLogs(int num) {
        Collection<Log> logs = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            logs.add(getNewLog());
        }
        return logs;
    }

    /**
     * Returns a newly created log consisting of a random number of topics of random bytes of random
     * size as well as a randomly sized random byte array of data.
     *
     * @return a new log.
     */
    private Log getNewLog() {
        int numTopics = RandomUtils.nextInt(0, 50);
        int topicSize = RandomUtils.nextInt(0, 100);
        int dataSize = RandomUtils.nextInt(0, 100);
        return new Log(
                getNewAddress(),
                generateTopics(numTopics, topicSize),
                RandomUtils.nextBytes(dataSize));
    }

    /**
     * Returns a list of num topics each of topicSize random bytes.
     *
     * @param num The number of topics to return.
     * @param topicSize The size of each topic.
     * @return the list of topics.
     */
    private List<byte[]> generateTopics(int num, int topicSize) {
        List<byte[]> topics = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            topics.add(RandomUtils.nextBytes(topicSize));
        }
        return topics;
    }

    /**
     * Returns a collection of num randomly generated new internal transactions.
     *
     * @param num The number of internal transactions in the collection.
     * @return the collection of internal transactions.
     */
    private Collection<AionInternalTx> getNewInternalTxs(int num) {
        Collection<AionInternalTx> internals = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            internals.add(getNewInternalTx());
        }
        return internals;
    }

    /**
     * Returns a new internal transaction whose fields are randomly generated.
     *
     * @return a new internal transaction.
     */
    private AionInternalTx getNewInternalTx() {
        Address sender = getNewAddress();
        Address recipient = getNewAddress();
        String note = "";
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] parentHash = RandomUtils.nextBytes(arraySizes);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        byte[] value = RandomUtils.nextBytes(arraySizes);
        byte[] data = RandomUtils.nextBytes(arraySizes);
        int deep = RandomUtils.nextInt(0, 1000);
        int index = RandomUtils.nextInt(0, 1000);
        return new AionInternalTx(
                parentHash, deep, index, nonce, sender, recipient, value, data, note);
    }

    /**
     * Returns a new ExecutionHelper that has numTxs internal transactions, numAddrs deleted
     * addresses, numLogs logs and numCalls Calls. Each of these objects are randomly generated. All
     * Call objects added are null.
     *
     * @param numTxs The number of internal transactions.
     * @param numAddrs The number of addresses.
     * @param numLogs The number of logs.
     * @param numCalls The number of calls.
     * @return the new ExecutionHelper.
     */
    private ExecutionHelper getNewHelper(int numTxs, int numAddrs, int numLogs, int numCalls) {
        ExecutionHelper helper = new ExecutionHelper();
        helper.addInternalTransactions(getNewInternalTxs(numTxs));
        helper.addDeleteAccounts(getNewAddresses(numAddrs, 0));
        helper.addLogs(getNewLogs(numLogs));
        for (int i = 0; i < numCalls; i++) {
            helper.addCall(null, null, null);
        }
        return helper;
    }
}

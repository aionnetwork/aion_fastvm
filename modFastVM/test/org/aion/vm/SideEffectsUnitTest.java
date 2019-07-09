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
import org.aion.types.AionAddress;
import org.aion.fastvm.SideEffects;
import org.aion.fastvm.SideEffects.Call;
import org.aion.types.Log;
import org.aion.zero.types.AionInternalTx;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

// NOTE: Calls have no restrictions on them, can be null or anything, is this wanted?
// Also, seems no one uses Call, is it even necessary?

public class SideEffectsUnitTest {
    private SideEffects sideEffects;

    @Before
    public void setup() {
        sideEffects = new SideEffects();
    }

    @After
    public void tearDown() {
        sideEffects = null;
    }

    @Test
    public void testNewExecutionHelper() {
        assertTrue(sideEffects.getAddressesToBeDeleted().isEmpty());
        assertTrue(sideEffects.getExecutionLogs().isEmpty());
        assertTrue(sideEffects.getCalls().isEmpty());
        assertTrue(sideEffects.getInternalTransactions().isEmpty());
    }

    @Test
    public void testAddDeleteAccount() {
        AionAddress addr = getNewAddress();
        sideEffects.addToDeletedAddresses(addr);
        assertEquals(1, sideEffects.getAddressesToBeDeleted().size());
        assertEquals(addr, sideEffects.getAddressesToBeDeleted().get(0));
    }

    @Test
    public void testAddDeleteAccountDuplicate() {
        AionAddress addr1 = getNewAddress();
        AionAddress addr2 = getNewAddress();
        sideEffects.addToDeletedAddresses(addr1);
        sideEffects.addToDeletedAddresses(addr2);
        sideEffects.addToDeletedAddresses(addr2);
        assertEquals(2, sideEffects.getAddressesToBeDeleted().size());
        AionAddress addr = sideEffects.getAddressesToBeDeleted().get(0);
        if (addr.equals(addr1)) {
            assertEquals(addr2, sideEffects.getAddressesToBeDeleted().get(1));
        } else if (addr.equals(addr2)) {
            assertEquals(addr1, sideEffects.getAddressesToBeDeleted().get(1));
        } else {
            fail("Delete accounts not added properly.");
        }
    }

    @Test
    public void testAddDeleteAccountsCollectionContainsNulls() {
        int numAddrs = 15;
        Collection<AionAddress> addresses = getNewAddresses(numAddrs, 12);
        sideEffects.addAllToDeletedAddresses(addresses);
        assertEquals(numAddrs, sideEffects.getAddressesToBeDeleted().size());
        for (AionAddress addr : sideEffects.getAddressesToBeDeleted()) {
            assertTrue(addresses.contains(addr));
        }
    }

    @Test
    public void testAddDeleteAccountsCollectionContainsDuplicates() {
        int numAddrs = 31;
        Collection<AionAddress> addresses = getNewAddresses(numAddrs, 3);
        Collection<AionAddress> duplicates = new ArrayList<>();
        Iterator<AionAddress> addrIt = addresses.iterator();
        for (int i = 0; i < addresses.size() / 2; i++) {
            duplicates.add(addrIt.next());
        }
        duplicates.addAll(addresses);
        sideEffects.addAllToDeletedAddresses(duplicates);
        assertEquals(numAddrs, sideEffects.getAddressesToBeDeleted().size());
        for (AionAddress addr : sideEffects.getAddressesToBeDeleted()) {
            assertTrue(addresses.contains(addr));
        }
    }

    @Test
    public void testAddDeleteAccountsWithDuplicates() {
        int numAddrs1 = 23;
        int numAddrs2 = 45;
        Collection<AionAddress> addresses = getNewAddresses(numAddrs1, 3);
        Collection<AionAddress> merged = new ArrayList<>(addresses);
        merged.addAll(getNewAddresses(numAddrs2, 6));
        sideEffects.addAllToDeletedAddresses(addresses);
        sideEffects.addAllToDeletedAddresses(merged);
        assertEquals(numAddrs1 + numAddrs2, sideEffects.getAddressesToBeDeleted().size());
        for (AionAddress addr : sideEffects.getAddressesToBeDeleted()) {
            assertTrue(merged.contains(addr));
        }
    }

    @Test
    public void testAddLogBulk() {
        Collection<Log> logs = getNewLogs(29);
        for (Log log : logs) {
            sideEffects.addLog(log);
        }
        assertEquals(logs, sideEffects.getExecutionLogs());
    }

    @Test
    public void testAddLogBulkSomeDuplicates() {
        Collection<Log> logs = getNewLogs(22);
        for (Log log : logs) {
            sideEffects.addLog(log);
            sideEffects.addLog(log);
        }
        assertEquals(logs.size() * 2, sideEffects.getExecutionLogs().size());
        for (Log log : sideEffects.getExecutionLogs()) {
            assertTrue(logs.contains(log));
        }
    }

    @Test
    public void testAddLogsBulk() {
        Collection<Log> logs = getNewLogs(38);
        sideEffects.addLogs(logs);
        assertEquals(logs, sideEffects.getExecutionLogs());
    }

    @Test
    public void testAddLogsSomeNull() {
        int numLogs = 33;
        Collection<Log> logs = getNewLogs(numLogs);
        for (int i = 0; i < 8; i++) {
            logs.add(null);
        }
        sideEffects.addLogs(logs);
        assertEquals(numLogs, sideEffects.getExecutionLogs().size());
        for (Log log : logs) {
            assertTrue(logs.contains(log));
        }
    }

    @Test
    public void testAddLogsSomeDuplicates() {
        Collection<Log> logs = getNewLogs(29);
        sideEffects.addLogs(logs);
        sideEffects.addLogs(logs);
        assertEquals(logs.size() * 2, sideEffects.getExecutionLogs().size());
        for (Log log : logs) {
            assertTrue(logs.contains(log));
        }
    }

    @Test
    public void testAddCallNullFields() {
        sideEffects.addCall(null, null, null);
        List<Call> calls = sideEffects.getCalls();
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
            sideEffects.addInternalTransaction(tx);
        }
        assertEquals(txs, sideEffects.getInternalTransactions());
    }

    @Test
    public void testAddTxBulkSomeDuplicates() {
        Collection<AionInternalTx> txs = getNewInternalTxs(17);
        for (AionInternalTx tx : txs) {
            sideEffects.addInternalTransaction(tx);
            sideEffects.addInternalTransaction(tx);
        }
        assertEquals(txs.size() * 2, sideEffects.getInternalTransactions().size());
    }

    @Test
    public void testAddTxs() {
        List<AionInternalTx> txs = getNewInternalTxs(26);
        sideEffects.addInternalTransactions(txs);
        assertEquals(txs.size(), sideEffects.getInternalTransactions().size());
    }

    @Test
    public void testAddTxsSomeNull() {
        List<AionInternalTx> txs = getNewInternalTxs(26);
        List<AionInternalTx> txsWithNulls = new ArrayList<>(txs);
        for (int i = 0; i < 19; i++) {
            txsWithNulls.add(null);
        }
        sideEffects.addInternalTransactions(txsWithNulls);
        assertEquals(txs.size(), sideEffects.getInternalTransactions().size());
    }

    @Test
    public void testAddTxsSomeDuplicates() {
        List<AionInternalTx> txs = getNewInternalTxs(26);
        sideEffects.addInternalTransactions(txs);
        sideEffects.addInternalTransactions(txs);
        assertEquals(txs.size() * 2, sideEffects.getInternalTransactions().size());
    }

    @Test
    public void testRejectEmptyTxListThrowsNoExceptions() {
        sideEffects.markAllInternalTransactionsAsRejected();
    }

    @Test
    public void testRejectNonEmptyTxList() {
        sideEffects.addInternalTransactions(getNewInternalTxs(33));
        for (AionInternalTx tx : sideEffects.getInternalTransactions()) {
            assertFalse(tx.isRejected());
        }
        sideEffects.markAllInternalTransactionsAsRejected();
        for (AionInternalTx tx : sideEffects.getInternalTransactions()) {
            assertTrue(tx.isRejected());
        }
    }

    @Test
    public void testMergeSideEffects() {
        int otherTx = 21, helperTx = 15, otherAddrs = 17, helperAddrs = 23, otherLogs = 13;
        int helperLogs = 11, numCalls = 12;
        SideEffects other = getNewHelper(otherTx, otherAddrs, otherLogs, 9);
        sideEffects = getNewHelper(helperTx, helperAddrs, helperLogs, numCalls);
        sideEffects.merge(other);

        // Txs, addrs, logs all merge but no calls get transferred.
        int numTxs = otherTx + helperTx;
        int numAddrs = otherAddrs + helperAddrs;
        int numLogs = otherLogs + helperLogs;
        assertEquals(numTxs, sideEffects.getInternalTransactions().size());
        assertEquals(numAddrs, sideEffects.getAddressesToBeDeleted().size());
        assertEquals(numLogs, sideEffects.getExecutionLogs().size());
        assertEquals(numCalls, sideEffects.getCalls().size());
    }

    @Test
    public void testAddInternalTransactionsOfOtherSideEffects() {
        int otherTx = 32, helperTx = 42, helperAddrs = 33, helperLogs = 27, numCalls = 21;
        SideEffects other = getNewHelper(otherTx, 22, 17, 9);
        sideEffects = getNewHelper(helperTx, helperAddrs, helperLogs, numCalls);
        sideEffects.addInternalTransactions(other.getInternalTransactions());

        // Txs are the only thing transferred.
        int numTxs = otherTx + helperTx;
        assertEquals(numTxs, sideEffects.getInternalTransactions().size());
        assertEquals(helperAddrs, sideEffects.getAddressesToBeDeleted().size());
        assertEquals(helperLogs, sideEffects.getExecutionLogs().size());
        assertEquals(numCalls, sideEffects.getCalls().size());
    }

    /**
     * Returns a collection of num newly created addresses, each consisting of random bytes and of
     * numNull null values. If numNull > num then num nulls are added.
     *
     * @param num The number of addresses in the returned collection.
     * @param numNull The number of nulls to add to the collection.
     * @return the collection of newly created addresses and numNull nulls.
     */
    private Collection<AionAddress> getNewAddresses(int num, int numNull) {
        Collection<AionAddress> collection = new ArrayList<>();
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
    private AionAddress getNewAddress() {
        return new AionAddress(RandomUtils.nextBytes(AionAddress.LENGTH));
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
        return Log.topicsAndData(
                getNewAddress().toByteArray(),
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
    private List<AionInternalTx> getNewInternalTxs(int num) {
        List<AionInternalTx> internals = new ArrayList<>();
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
        AionAddress sender = getNewAddress();
        AionAddress recipient = getNewAddress();
        String note = "";
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        byte[] value = RandomUtils.nextBytes(arraySizes);
        byte[] data = RandomUtils.nextBytes(arraySizes);
        return new AionInternalTx(nonce, sender, recipient, value, data, note);
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
    private SideEffects getNewHelper(int numTxs, int numAddrs, int numLogs, int numCalls) {
        SideEffects helper = new SideEffects();
        helper.addInternalTransactions(getNewInternalTxs(numTxs));
        helper.addAllToDeletedAddresses(getNewAddresses(numAddrs, 0));
        helper.addLogs(getNewLogs(numLogs));
        for (int i = 0; i < numCalls; i++) {
            helper.addCall(null, null, null);
        }
        return helper;
    }
}

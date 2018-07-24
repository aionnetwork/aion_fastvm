/*
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 * Contributors:
 *     Aion foundation.
 */
package org.aion.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.db.IRepository;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.type.ITxReceipt;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKeyFac;
import org.aion.fastvm.TestUtils;
import org.aion.fastvm.TestVMProvider;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.solidity.Compiler.Options;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;
import org.slf4j.Logger;

public class TransactionExecutorTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private static final IRepository REPO = new DummyRepository();

    @Test
    public void testBuildReceiptEnergyUsedDataAllZeroes() {
        int size = RandomUtils.nextInt(0, 1000);
        checkBuildReceiptEnergyUsed(size, size, 0);
    }

    @Test
    public void testBuildReceiptEnergyUsedDataNoZeroes() {
        int size = RandomUtils.nextInt(0, 1000);
        checkBuildReceiptEnergyUsed(size, 0, size);
    }

    @Test
    public void testBuildReceiptEnergyDataSizeZero() {
        checkBuildReceiptEnergyUsed(0, 0, 0);
    }

    @Test
    public void testBuildReceiptEnergyRandomData() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        checkBuildReceiptEnergyUsed(size, numZeroes, size - numZeroes);
    }

    @Test
    public void testBuildReceiptIsValidAndIsSuccessful() {
        // error is null or empty string <=> isValid is true
        // isValid == isSuccessful
        //    ^redundant, though technically isValid tests null but isSuccessful never sees null..
        AionTxReceipt receipt = produceReceipt(0, 0, false);
        receipt.setError(null);
        assertTrue(receipt.isValid());
        assertTrue(receipt.isSuccessful());
        receipt.setError("");
        assertTrue(receipt.isValid());
        assertTrue(receipt.isSuccessful());
        receipt.setError(" ");
        assertFalse(receipt.isValid());
        assertFalse(receipt.isSuccessful());
    }

    @Test
    public void testBuildReceiptGetTransaction() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        byte[] data = produceData(size, numZeroes);
        AionTransaction tx = getNewAionTransaction(data, numZeroes);
        tx.sign(ECKeyFac.inst().create());
        TransactionExecutor executor = getNewExecutor(tx, true, 3, numZeroes);
        AionTxReceipt receipt = (AionTxReceipt) executor.
            buildReceipt(new AionTxReceipt(), tx, getNewLogs(8));
        assertEquals(tx, receipt.getTransaction());
    }

    @Test
    public void testBuildReceiptBloomFilter() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        byte[] data = produceData(size, numZeroes);
        List<Log> logs = getNewLogs(RandomUtils.nextInt(0, 50));
        AionTransaction tx = getNewAionTransaction(data, numZeroes);
        TransactionExecutor executor = getNewExecutor(tx, false, 8, numZeroes);
        AionTxReceipt receipt = (AionTxReceipt) executor.buildReceipt(new AionTxReceipt(), tx, logs);
        assertEquals(logs.size(), receipt.getLogInfoList().size());
        assertEquals(getOrOfBlooms(logs), receipt.getBloomFilter());
    }

    @Test
    public void testBuildReceiptExecutionResult() {
        //TODO: this is ExecutionResult output; need to understand calls first.
    }

    @Test
    public void testBuildReceiptGetError() {
        //TODO: this is ExecutionResult.getResultCode == SUCCESS; need to understand calls first.
    }



    // =================
    // old tests below
    // =================

    @Test
    public void testCallTransaction() throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(
            ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = DataWord.ZERO.getData();
        Address from = Address
            .wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111"));
        Address to = Address
            .wrap(Hex.decode("2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWord.ZERO.getData();
        byte[] data = Hex.decode("c0004213");
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = TestUtils.createDummyBlock();

        DummyRepository repo = new DummyRepository();
        repo.addBalance(from, BigInteger.valueOf(100_000).multiply(tx.nrgPrice().value()));
        repo.addContract(to, Hex.decode(contract));

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(Hex.decode("00000000000000000000000000000000"),
            receipt.getExecutionResult());
    }

    @Test
    public void testCreateTransaction() throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(
            ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin;
        System.out.println(deployer);

        byte[] txNonce = DataWord.ZERO.getData();
        Address from = Address
            .wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111"));
        Address to = Address.EMPTY_ADDRESS();
        byte[] value = DataWord.ZERO.getData();
        byte[] data = Hex.decode(deployer);
        long nrg = 500_000L;
        long nrgPrice = 1;
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = TestUtils.createDummyBlock();

        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo = new DummyRepository();
        repo.addBalance(from, BigInteger.valueOf(500_000L).multiply(tx.nrgPrice().value()));

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(Hex.decode(deployer.substring(deployer.indexOf("60506040", 1))),
            receipt.getExecutionResult());
    }

    @Test
    public void testPerformance() throws IOException {
        Compiler.Result r = Compiler.getInstance().compile(
            ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = DataWord.ZERO.getData();
        Address from = Address
            .wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111"));
        Address to = Address
            .wrap(Hex.decode("2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWord.ZERO.getData();
        byte[] data = Hex.decode("c0004213");
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);
        tx.sign(ECKeyFac.inst().create());

        AionBlock block = TestUtils.createDummyBlock();

        DummyRepository repo = new DummyRepository();
        repo.addBalance(from, BigInteger.valueOf(100_000).multiply(tx.nrgPrice().value()));
        repo.addContract(to, Hex.decode(contract));

        long t1 = System.nanoTime();
        long repeat = 1000;
        for (int i = 0; i < repeat; i++) {
            TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
            exec.setExecutorProvider(new TestVMProvider());
            exec.execute();
        }
        long t2 = System.nanoTime();
        System.out.println((t2 - t1) / repeat);
    }

    @Test
    public void testBasicTransactionCost() {
        byte[] txNonce = DataWord.ZERO.getData();
        Address from = Address
            .wrap(Hex.decode("1111111111111111111111111111111111111111111111111111111111111111"));
        Address to = Address
            .wrap(Hex.decode("2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWord.ONE.getData();
        byte[] data = new byte[0];
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = TestUtils.createDummyBlock();

        DummyRepository repo = new DummyRepository();
        repo.addBalance(from, BigInteger.valueOf(1_000_000_000L));

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertEquals(tx.transactionCost(block.getNumber()), receipt.getEnergyUsed());
    }

    /**
     * Returns a new TransactionExecutor whose constructor params are randomly generated except for
     * isLocalCall.
     *
     * @param tx The transaction.
     * @param isLocalCall True if a local call.
     * @param blockNrg The block energy limit.
     * @param numZeroes The number of zeroes in the data in tx.
     * @return a new TransactionExecutor.
     */
    private TransactionExecutor getNewExecutor(AionTransaction tx, boolean isLocalCall, long blockNrg,
        int numZeroes) {

        IAionBlock block = getNewIAionBlock(blockNrg, tx.getData(), numZeroes);
        long nrgLeft = tx.transactionCost(block.getNumber());
        return new TransactionExecutor(tx, block, REPO, isLocalCall, nrgLeft, LOGGER_VM);
    }

    /**
     * Returns a new IAionBlock whose fields are randomized.
     *
     * @param energyLimit The energy limit.
     * @param data The data.
     * @param numZeroes The number of zero bytes in data.
     * @return a new IAionBlock.
     */
    private IAionBlock getNewIAionBlock(long energyLimit, byte[] data, int numZeroes) {
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] parentHash = RandomUtils.nextBytes(arraySizes);
        Address coinbase = getNewAddress();
        byte[] logsBloom = RandomUtils.nextBytes(arraySizes);
        byte[] difficulty = RandomUtils.nextBytes(arraySizes);
        long number = RandomUtils.nextLong(0, 10_000);
        long timestamp = RandomUtils.nextLong(0, 10_000);
        byte[] extraData = RandomUtils.nextBytes(arraySizes);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        byte[] receiptsRoot = RandomUtils.nextBytes(arraySizes);
        byte[] transactionsRoot = RandomUtils.nextBytes(arraySizes);
        byte[] stateRoot = RandomUtils.nextBytes(arraySizes);
        List<AionTransaction> transactionList = getNewAionTransactions(3, data, numZeroes);
        byte[] solutions = RandomUtils.nextBytes(arraySizes);
        long energyConsumed = RandomUtils.nextLong(0, 10_000);
        return new AionBlock(parentHash, coinbase, logsBloom, difficulty, number, timestamp,
            extraData, nonce, receiptsRoot, transactionsRoot, stateRoot, transactionList,
            solutions, energyConsumed, energyLimit);
    }

    /**
     * Returns a list of num new AionTransactions whose fields are randomized.
     *
     * @param num The number of transactions in the list.
     * @param data The data to include in each of the transactions.
     * @param numZeroes The number of zero bytes in data.
     * @return the list of transactions.
     */
    private List<AionTransaction> getNewAionTransactions(int num, byte[] data, int numZeroes) {
        List<AionTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            transactions.add(getNewAionTransaction(data, numZeroes));
        }
        return transactions;
    }

    /**
     * Returns a new address consisting of random bytes.
     *
     * @return a new random address.
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
    private List<Log> getNewLogs(int num) {
        List<Log> logs = new ArrayList<>();
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
        return new Log(getNewAddress(), generateTopics(numTopics, topicSize), RandomUtils.nextBytes(dataSize));
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
     * Returns a byte array of length size with numZeroes zero bytes.
     *
     * @param size The array size.
     * @param numZeroes The number of zeroes in the array.
     * @return the specified byte array.
     */
    private byte[] produceData(int size, int numZeroes) {
        byte[] data = new byte[size];
        for (int i = 0; i < (size - numZeroes); i++) {
            data[i] = 0x1;
        }
        return data;
    }

    /**
     * Checks the energy used field of a transaction receipt when build receipt is called. This
     * checks both contract creation and non-contract-creation logic. This method causes the calling
     * test to fail if there is an error.
     *
     * @param dataSize The data length.
     * @param numZeroes The number of zeroe-bytes in the data.
     * @param numNonZeroes The number of non-zero bytes in the data.
     */
    private void checkBuildReceiptEnergyUsed(int dataSize, int numZeroes, int numNonZeroes) {
        assertEquals(dataSize, numNonZeroes + numZeroes);

        // First check when we are not creating a contract.
        long energyUsed = computeTxCost(false, numZeroes, numNonZeroes);
        AionTxReceipt builtReceipt = produceReceipt(dataSize, numZeroes, false);
        assertEquals(energyUsed, builtReceipt.getEnergyUsed());

        // Second check when we are creating a contract.
        energyUsed = computeTxCost(true, numZeroes, numNonZeroes);
        builtReceipt = produceReceipt(dataSize, numZeroes, true);
        assertEquals(energyUsed, builtReceipt.getEnergyUsed());
    }

    /**
     * Returns a receipt for a transaction that is a contract creation if isContractCreation is
     * true and whose data is length dataSize and that data consists of numZeroes zero bytes.
     *
     * @param dataSize The transaction data length.
     * @param numZeroes The number of zero bytes in the data.
     * @param isContractCreation True only if transaction is for contract creation.
     * @return a new transaction receipt.
     */
    private AionTxReceipt produceReceipt(int dataSize, int numZeroes, boolean isContractCreation) {
        int numLogs = RandomUtils.nextInt(0, 50);
        return produceReceipt(dataSize, numZeroes, isContractCreation, getNewLogs(numLogs));
    }

    /**
     * Returns a receipt for a transaction that is a contract creation if isContractCreation is
     * true and whose data is length dataSize and that data consists of numZeroes zero bytes.
     *
     * @param dataSize The transaction data length.
     * @param numZeroes The number of zero bytes in the data.
     * @param isContractCreation True only if transaction is for contract creation.
     * @param logs The logs to add to the receipt.
     * @return a new transaction receipt.
     */
    private AionTxReceipt produceReceipt(int dataSize, int numZeroes, boolean isContractCreation,
        List<Log> logs) {

        byte[] data = produceData(dataSize, numZeroes);
        AionTransaction tx = (isContractCreation) ?
            getNewAionTransactionContractCreation(data, numZeroes) :
            getNewAionTransaction(data, numZeroes);
        TransactionExecutor executor = getNewExecutor(tx,true, 0, numZeroes);
        ITxReceipt receipt = new AionTxReceipt();
        return (AionTxReceipt) executor.buildReceipt(receipt, tx, logs);
    }

    /**
     * Returns a new AionTransaction, most of whose fields are randomized. This transaction is not
     * for contract creation.
     *
     * @param data The transaction data.
     * @param numZeroes The number of zero bytes in data.
     * @return a new AionTransaction.
     */
    private AionTransaction getNewAionTransaction(byte[] data, int numZeroes) {
        long energyUsed = computeTxCost(false, numZeroes, data.length - numZeroes);
        long diff = RandomUtils.nextLong(0, 100_000);
        long nrg = RandomUtils.nextLong(energyUsed, energyUsed + diff);
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        Address from = getNewAddress();
        Address to = getNewAddress();
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        long nrgPrice = RandomUtils.nextLong(0, 10_000);
        return new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice);
    }

    /**
     * Returns a new AionTransaction for contract creation logic. That is, its 'to' address is null.
     *
     * @param data The transaction data.
     * @param numZeroes The number of zero bytes in data.
     * @return a new AionTransaction for contract creation.
     */
    private AionTransaction getNewAionTransactionContractCreation(byte[] data, int numZeroes) {
        long energyUsed = computeTxCost(true, numZeroes, data.length - numZeroes);
        long diff = RandomUtils.nextLong(0, 100_000);
        long nrg = RandomUtils.nextLong(energyUsed, energyUsed + diff);
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        Address from = getNewAddress();
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        long nrgPrice = RandomUtils.nextLong(0, 10_000);
        return new AionTransaction(nonce, from, null, value, data, nrg, nrgPrice);
    }

    /**
     * Computes the transaction cost for processing a transaction whose data has numZeroes zero bytes
     * and numNonZeroes non-zero bytes.
     *
     * If transaction is a contract creation then the fee is:
     *   createFee + nrgTrans + (numZeroes * zeroDataNrg) + (numNonZeroes * nonzeroDataNrg)
     *
     * otherwise the fee is the same as above minus createFee.
     *
     * @param isContractCreation True if the transaction creates a new contract.
     * @param numZeroes The number of zero bytes in the transaction data.
     * @param numNonZeroes The umber of non-zero bytes in the transaction data.
     * @return the transaction cost.
     */
    private long computeTxCost(boolean isContractCreation, long numZeroes, long numNonZeroes) {
        return (isContractCreation ? Constants.NRG_TX_CREATE : 0)
            + Constants.NRG_TRANSACTION
            + (numZeroes * Constants.NRG_TX_DATA_ZERO)
            + (numNonZeroes * Constants.NRG_TX_DATA_NONZERO);
    }

    /**
     * Returns the logical-OR of all of the bloom filters contained in each log in logs as a bloom
     * filter itself.
     *
     * @param logs The logs.
     * @return a bloom filter that is the OR of all the filters in logs.
     */
    private Bloom getOrOfBlooms(List<Log> logs) {
        Bloom bloom = new Bloom();
        for (Log log : logs) {
            bloom.or(log.getBloom());
        }
        return bloom;
    }

}

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
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.type.IExecutionResult;
import org.aion.base.type.ITxReceipt;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.base.util.ByteUtil;
import org.aion.crypto.ECKeyFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.Bloom;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionInternalTx;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.aion.zero.types.IAionBlock;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;

/**
 * Tests the TransactionExecutor class.
 */
public class TransactionExecutorUnitTest {

    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private DummyRepository repo;

    @Before
    public void setup() {
        repo = new DummyRepository();
    }

    @After
    public void tearDown() {
        repo = null;
    }

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
        TransactionExecutor executor = getNewExecutor(tx, true, 3);
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
        TransactionExecutor executor = getNewExecutor(tx, false, 8);
        AionTxReceipt receipt = (AionTxReceipt) executor
            .buildReceipt(new AionTxReceipt(), tx, logs);
        assertEquals(logs.size(), receipt.getLogInfoList().size());
        assertEquals(getOrOfBlooms(logs), receipt.getBloomFilter());
    }

    @Test
    public void testBuildReceiptExecutionResult() {
        TransactionExecutor executor = getNewExecutor(mockTx(), false, 10);
        byte[] output = RandomUtils.nextBytes(RandomUtils.nextInt(0, 1000));
        executor.setExecutionResult(new ExecutionResult(ResultCode.SUCCESS, 0, output));
        AionTxReceipt receipt = (AionTxReceipt) executor
            .buildReceipt(new AionTxReceipt(), mockTx(), new ArrayList());
        assertArrayEquals(output, receipt.getExecutionResult());
    }

    @Test
    public void testBuildReceiptGetErrorWhenResultIsSuccess() {
        TransactionExecutor executor = getNewExecutor(mockTx(), false, 10);
        byte[] output = RandomUtils.nextBytes(RandomUtils.nextInt(0, 1000));
        executor.setExecutionResult(new ExecutionResult(ResultCode.SUCCESS, 0, output));
        AionTxReceipt receipt = (AionTxReceipt) executor
            .buildReceipt(new AionTxReceipt(), mockTx(), new ArrayList());
        assertEquals("", receipt.getError());
    }

    @Test
    public void testBuildReceiptGetErrorWhenResultNotSuccess() {
        for (int i = -1; i < 12; i++) {
            if (!ResultCode.fromInt(i).equals(ResultCode.SUCCESS)) {
                ResultCode code = ResultCode.fromInt(i);
                TransactionExecutor executor = getNewExecutor(mockTx(), false, 10);
                byte[] output = RandomUtils.nextBytes(RandomUtils.nextInt(0, 1000));
                executor.setExecutionResult(new ExecutionResult(code, 0, output));
                AionTxReceipt receipt = (AionTxReceipt) executor
                    .buildReceipt(new AionTxReceipt(), mockTx(), new ArrayList());
                assertEquals(code.name(), receipt.getError());
            }
        }
    }

    @Test
    public void testUpdateRepoIsLocalCall() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        byte[] data = produceData(size, numZeroes);
        AionTransaction tx = getNewAionTransaction(data, numZeroes);
        runUpdateRepo(tx, getNewAddress(), true, false);

        // When call is local there should be no state change.
        assertTrue(repo.accounts.isEmpty());
        assertTrue(repo.contracts.isEmpty());
        assertTrue(repo.storage.isEmpty());
    }

    @Test
    public void testUpdateRepoSummaryIsRejected() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        byte[] data = produceData(size, numZeroes);
        AionTransaction tx = getNewAionTransaction(data, numZeroes);
        runUpdateRepo(tx, getNewAddress(), false, true);

        // When summary is rejected there should be no state change.
        assertTrue(repo.accounts.isEmpty());
        assertTrue(repo.contracts.isEmpty());
        assertTrue(repo.storage.isEmpty());
    }

    @Test
    public void testUpdateRepoCoinbaseBalanceNotContractCreationTx() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        byte[] data = produceData(size, numZeroes);
        Address coinbase = getNewAddress();
        AionTransaction tx = getNewAionTransaction(data, numZeroes);
        runUpdateRepo(tx, coinbase, false, false);
        BigInteger coinbaseFee = computeCoinbaseFee(false, numZeroes,
            size - numZeroes, tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));
    }

    @Test
    public void testUpdateRepoCoinbaseBalanceContractCreationTx() {
        int size = RandomUtils.nextInt(0, 1000);
        int numZeroes = RandomUtils.nextInt(0, size);
        byte[] data = produceData(size, numZeroes);
        Address coinbase = getNewAddress();
        AionTransaction tx = getNewAionTransactionContractCreation(data, numZeroes);
        runUpdateRepo(tx, coinbase, false, false);
        BigInteger coinbaseFee = computeCoinbaseFee(true, numZeroes,
            size - numZeroes, tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));
    }

    @Test
    public void testUpdateRepoCoinbaseTxHasZeroLengthData() {
        // First test contract creation tx.
        byte[] data = produceData(0, 0);
        Address coinbase = getNewAddress();
        AionTransaction tx = getNewAionTransactionContractCreation(data, 0);
        runUpdateRepo(tx, coinbase, false, false);
        BigInteger coinbaseFee = computeCoinbaseFee(true, 0,
            0, tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));

        // Second test regular tx.
        coinbase = getNewAddress();
        tx = getNewAionTransaction(data, 0);
        runUpdateRepo(tx, coinbase, false, false);
        coinbaseFee = computeCoinbaseFee(false, 0, 0,
            tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));
    }

    @Test
    public void testUpdateRepoCoinbaseDataAllZeroes() {
        // First test contract creation tx.
        int size = RandomUtils.nextInt(0, 1000);
        byte[] data = produceData(size, size);
        Address coinbase = getNewAddress();
        AionTransaction tx = getNewAionTransactionContractCreation(data, size);
        runUpdateRepo(tx, coinbase, false, false);
        BigInteger coinbaseFee = computeCoinbaseFee(true, size, 0,
            tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));

        // Second test regular tx.
        coinbase = getNewAddress();
        tx = getNewAionTransaction(data, size);
        runUpdateRepo(tx, coinbase, false, false);
        coinbaseFee = computeCoinbaseFee(false, size, 0, tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));
    }

    @Test
    public void testUpdateRepoCoinbaseDataNoZeroes() {
        // First test contract creation tx.
        int size = RandomUtils.nextInt(0, 1000);
        byte[] data = produceData(size, 0);
        Address coinbase = getNewAddress();
        AionTransaction tx = getNewAionTransactionContractCreation(data, 0);
        runUpdateRepo(tx, coinbase, false, false);
        BigInteger coinbaseFee = computeCoinbaseFee(true, 0, size,
            tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));

        // Second test regular tx.
        coinbase = getNewAddress();
        tx = getNewAionTransaction(data, 0);
        runUpdateRepo(tx, coinbase, false, false);
        coinbaseFee = computeCoinbaseFee(false, 0, size, tx.getNrgPrice());
        assertEquals(coinbaseFee, repo.getBalance(coinbase));
    }

    @Test
    public void testUpdateRepoCoinbaseZeroNrgPrice() {
        // First test contract creation tx.
        int size = RandomUtils.nextInt(0, 1000);
        byte[] data = produceData(size, 0);
        Address coinbase = getNewAddress();
        AionTransaction tx = getNewAionTransactionContractCreation(data, 0);
        runUpdateRepo(tx, coinbase, false, false);
        assertEquals(BigInteger.ZERO, repo.getBalance(coinbase));

        // Second test regular tx.
        coinbase = getNewAddress();
        tx = getNewAionTransaction(data, 0);
        runUpdateRepo(tx, coinbase, false, false);
        assertEquals(BigInteger.ZERO, repo.getBalance(coinbase));
    }

    @Test
    public void testUpdateRepoNrgConsumptionContractCreationTx() {
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(getNewAddress());
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        executor
            .updateRepo(produceSummary(executor, tx), tx, block.getCoinbase(), new ArrayList<>());
        assertEquals(tx.getNrgConsume(), computeEnergyConsumption(tx));
    }

    @Test
    public void testUpdateRepoEnergyPriceRefund() {
        for (ResultCode code : ResultCode.values()) {
            Address sender = getNewAddress();
            AionTransaction tx = mockTx(sender, BigInteger.TEN.toByteArray(),
                RandomUtils.nextLong(0, 10_000));
            AionBlock block = mockBlock(getNewAddress());
            TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
                LOGGER_VM);
            executor.setExecutionResult(new ExecutionResult(code, 0));

            AionTxExecSummary summary = produceSummary(executor, tx);
            executor.updateRepo(summary, tx, block.getCoinbase(), new ArrayList<>());

            // Refund occurs only when ResultCode is SUCCESS or REVERT.
            if (code.equals(ResultCode.SUCCESS) || code.equals(ResultCode.REVERT)) {
                assertEquals(computeRefund(tx, summary), repo.getBalance(sender));
            } else {
                assertEquals(BigInteger.ZERO, repo.getBalance(sender));
            }
        }
    }

    @Test
    public void testUpdateRepoDeletedAccounts() {
        for (ResultCode code : ResultCode.values()) {
            List<Address> accounts = addAccountsToRepo(RandomUtils.nextInt(5, 50));
            Address sender = getNewAddress();
            AionTransaction tx = mockTx(sender, BigInteger.TEN.toByteArray(),
                RandomUtils.nextLong(0, 10_000));
            AionBlock block = mockBlock(getNewAddress());
            TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
                LOGGER_VM);
            executor.setExecutionResult(new ExecutionResult(code, 0));

            AionTxExecSummary summary = produceSummary(executor, tx);
            executor.updateRepo(summary, tx, block.getCoinbase(), accounts);

            // Account deletion occurs only when ResultCode is SUCCESS.
            if (code.equals(ResultCode.SUCCESS)) {
                for (Address acc : repo.accounts.keySet()) {
                    assertFalse(accounts.contains(acc));
                }
            } else {
                Set<Address> repoAccounts = repo.accounts.keySet();
                for (Address acc : accounts) {
                    assertTrue(repoAccounts.contains(acc));
                }
            }
            repo.accounts.clear();
        }
    }

    @Test
    public void testGetNrgLeft() {
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(getNewAddress());
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true, LOGGER_VM);
        assertEquals(tx.nrgLimit() - tx.transactionCost(0), executor.getNrgLeft());
    }

    @Test
    public void testConstructorExecutionContextForContractCreation() {
        boolean isContractCreation = true, valueIsNull = false, dataIsNull = false;
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorExecutionContextForRegTx() {
        boolean isContractCreation = false, valueIsNull = false, dataIsNull = false;
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorExecutionContextWithNullValue() {
        boolean isContractCreation = true, valueIsNull = true, dataIsNull = false;
        // isContractCreation == true
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);

        // isContractCreation == false
        isContractCreation = false;
        tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        executor = new TransactionExecutor(tx, block, repo, true, block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorExecutionContextWithNullData() {
        boolean isContractCreation = true, valueIsNull = false, dataIsNull = true;
        // isContractCreation == true
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);

        // isContractCreation == false
        isContractCreation = false;
        tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        executor = new TransactionExecutor(tx, block, repo, true, block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorExecutionContextLargeDifficulty() {
        boolean isContractCreation = true, valueIsNull = false, dataIsNull = false;
        // isContractCreation == true
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES * 5);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);

        // isContractCreation == false
        isContractCreation = false;
        tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        executor = new TransactionExecutor(tx, block, repo, true, block.getNrgLimit(), LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorUsesBlockNrgLimit() {
        // We are using a different constructor here. This one implicitly grabs the block's energy limit.

        boolean isContractCreation = true, valueIsNull = false, dataIsNull = false;
        // isContractCreation == true
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true, LOGGER_VM);
        checkExecutionContext(executor, tx, block);

        // isContractCreation == false
        isContractCreation = false;
        tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        executor = new TransactionExecutor(tx, block, repo, true, LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorNonLocalUsesBlockNrgLimit() {
        // This is also a different constructor. This one grabs the block's energy limit also and
        // additionally it sets the isLocalCall variable false.

        boolean isContractCreation = true, valueIsNull = false, dataIsNull = false;
        // isContractCreation == true
        AionTransaction tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        checkExecutionContext(executor, tx, block);

        // isContractCreation == false
        isContractCreation = false;
        tx = mockTx(isContractCreation, valueIsNull, dataIsNull);
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        checkExecutionContext(executor, tx, block);
    }

    @Test
    public void testConstructorExecutionResult() {
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(DataWord.BYTES);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            block.getNrgLimit(), LOGGER_VM);
        checkExecutionResult(executor, tx);

        // test second constructor.
        executor = new TransactionExecutor(tx, block, repo, true, LOGGER_VM);
        checkExecutionResult(executor, tx);

        // test third constructor.
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        checkExecutionResult(executor, tx);
    }

    @Test
    public void testPrepareWhenIsLocalCall() {
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(getNewAddress());
        long nrgLimit = block.getNrgLimit();
        long expectedNrg = tx.nrgLimit() - tx.transactionCost(0);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, true,
            nrgLimit, LOGGER_VM);

        assertTrue(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.SUCCESS.toInt(), expectedNrg);

        // Test other constructor. (Can't test third one since it sets localCall false.
        executor = new TransactionExecutor(tx, block, repo, true, LOGGER_VM);
        assertTrue(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.SUCCESS.toInt(), expectedNrg);
    }

    @Test
    public void testPrepareIsContractCreateWithInvalidNrgLowerBound() {
        doPrepareInvalidNrgLimitTest(true, true);
    }

    @Test
    public void testPrepareIsContractCreateWithInvalidNrgUpperBound() {
        doPrepareInvalidNrgLimitTest(true, false);
    }

    @Test
    public void testPrepareRegularTxInvalidNrgLowerBound() {
        doPrepareInvalidNrgLimitTest(false, true);
    }

    @Test
    public void testPrepareRegularTxInvalidNrgUpperBound() {
        doPrepareInvalidNrgLimitTest(false, false);
    }

    @Test
    public void testPrepareNrgLimitAboveRemainingNrgContractCreation() {
        doPrepareNrgLimitAboveRemainingNrg(true);
    }

    @Test
    public void testPrepareNrgLimitAboveBlockRemainingNrg() {
        doPrepareNrgLimitAboveRemainingNrg(false);
    }

    @Test
    public void testPrepareNegativeContextNrgLimit() {
        doPrepareContextNrgLimitIsNegative(false);
    }

    @Test
    public void testPrepareNegativeContextNrgLimitIsContractCreation() {
        doPrepareContextNrgLimitIsNegative(true);
    }

    @Test
    public void testPrepareTxNonceNotEqualToSenderAccountNonce() {
        long nrgLimit = produceValidNrgLimit(false);
        BigInteger nonce = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        Address sender = addAccountsToRepo(1).get(0);
        repo.setNonce(sender, nonce);

        AionTransaction tx = mockTx(sender, nonce.add(BigInteger.ONE).toByteArray(), 0);
        AionBlock block = mockBlock(getNewAddress());
        when(tx.getNrg()).thenReturn(nrgLimit);
        when(block.getNrgLimit()).thenReturn(nrgLimit);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NONCE.toInt(), 0);

        executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NONCE.toInt(), 0);

        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NONCE.toInt(), 0);
    }

    @Test
    public void testPrepareInsufficientBalance() {
        long nrgPrice = RandomUtils.nextLong(0, 100_000);
        byte[] value = RandomUtils.nextBytes(8);
        value[0] &= 0x7F;   // Creates a positive value.
        doPrepareInsufficientBalance(false, nrgPrice, value);
    }

    @Test
    public void testPrepareInsufficientBalanceContractCreation() {
        long nrgPrice = RandomUtils.nextLong(0, 100_000);
        byte[] value = RandomUtils.nextBytes(8);
        value[0] &= 0x7F;   // Creates a positive value.
        doPrepareInsufficientBalance(true, nrgPrice, value);
    }

    @Test
    public void testPrepareInsufficientBalanceUseNegativeTxValue() {
        long nrgPrice = RandomUtils.nextLong(0, 100_000);
        byte[] value = RandomUtils.nextBytes(8);
        value[0] |= 0x80;   // Creates a negative value.
        doPrepareInsufficientBalance(false, nrgPrice, value);
    }

    @Test
    public void testPrepareInsufficientBalanceUseNegativeTxValueContractCreation() {
        long nrgPrice = RandomUtils.nextLong(0, 100_000);
        byte[] value = RandomUtils.nextBytes(8);
        value[0] |= 0x80;   // Creates a negative value.
        doPrepareInsufficientBalance(true, nrgPrice, value);
    }

    @Test
    public void testPrepareIsGoodNoSkipNonce() {
        doPrepareIsGood(false, false, true);
    }

    @Test
    public void testPrepareIsGoodNoSkipNonceContractCreation() {
        doPrepareIsGood(true, false, true);
    }

    @Test
    public void testPrepareIsGoodSkipNonce() {

        doPrepareIsGood(false, true, true);
    }

    @Test
    public void testPrepareIsGoodSkipNonceContractCreation() {

        doPrepareIsGood(true, true, true);
    }

    @Test
    public void testPrepareIsGoodBalanceIsEqualToTxCost() {

        doPrepareIsGood(false, false, true);
    }

    @Test
    public void testPrepareIsGoodBalanceIsEqualToTxCostContractCreation() {
        doPrepareIsGood(true, false, true);
    }

    @Test
    public void testPrepareIsGoodBalanceGreaterThanTxCost() {
        doPrepareIsGood(false, false, false);
    }

    @Test
    public void testPrepareIsGoodBalanceGreaterThanTxCostContractCreation() {
        doPrepareIsGood(true, false, false);
    }

    @Test
    public void testCreateWhenContractAlreadyExists() {
        IRepositoryCache cache = mock(IRepositoryCache.class);
        when(cache.hasAccountState(Mockito.any(Address.class))).thenReturn(true);
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(getNewAddress());

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        executor.repoTrack = cache;
        executor.create();
        checkExecutionResults(executor.getResult(), ResultCode.CONTRACT_ALREADY_EXISTS.toInt(), 0);

        // Test second constructor.
        executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        executor.repoTrack = cache;
        executor.create();
        checkExecutionResults(executor.getResult(), ResultCode.CONTRACT_ALREADY_EXISTS.toInt(), 0);

        // Test third constructor.
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        executor.repoTrack = cache;
        executor.create();
        checkExecutionResults(executor.getResult(), ResultCode.CONTRACT_ALREADY_EXISTS.toInt(), 0);
    }

    @Test
    public void testCreateNullTxData() {
        doCreateAndCheck(null, new ExecutionResult(ResultCode.SUCCESS, 0), true);
    }

    @Test
    public void testCreateEmptyTxData() {
        doCreateAndCheck(new byte[0], new ExecutionResult(ResultCode.SUCCESS, 0), true);
    }

    @Test
    public void testCreateRunIsSuccessful() {
        byte[] output = RandomUtils.nextBytes(RandomUtils.nextInt(50, 150));
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0, output);
        doCreateAndCheck(RandomUtils.nextBytes(RandomUtils.nextInt(1, 1000)), result, true);
    }

    @Test
    public void testCreateRunUnsuccessful() {
        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS)) {
                byte[] output = RandomUtils.nextBytes(RandomUtils.nextInt(50, 150));
                ExecutionResult result = new ExecutionResult(code, 0, output);
                doCreateAndCheck(RandomUtils.nextBytes(RandomUtils.nextInt(1, 1000)), result, true);
            }
        }
    }

    @Test
    public void testCreateNegativeValue() {
        doCreateAndCheck(RandomUtils.nextBytes(8),
            new ExecutionResult(ResultCode.SUCCESS, 0), false);
    }

    @Test
    public void testCallIsPrecompiledContract() {
        for (ResultCode code : ResultCode.values()) {
            ExecutionResult result = new ExecutionResult(code, 0, RandomUtils.nextBytes(16));
            doCallAndCheck(result, true, null, true);
        }
    }

    @Test
    public void testCallNotPrecompiledContractCodeIsNull() {
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0,
            RandomUtils.nextBytes(16));
        doCallAndCheck(result, false, null, true);
    }

    @Test
    public void testCallNotPrecompiledContractCodeIsEmpty() {
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0,
            RandomUtils.nextBytes(16));
        doCallAndCheck(result, false, new byte[0], true);
    }

    @Test
    public void testCallNotPrecompiledContractCodeIsNonEmpty() {
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0,
            RandomUtils.nextBytes(16));
        doCallAndCheck(result, false, RandomUtils.nextBytes(16), true);
    }

    @Test
    public void testCallNegativeValue() {
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0,
            RandomUtils.nextBytes(16));
        doCallAndCheck(result, false, RandomUtils.nextBytes(16), false);
    }

    @Test
    public void testFinishWithSeptForkIsTrueIsLocalIsSuccess() {
        Address coinbase = getNewAddress();
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0,
            RandomUtils.nextBytes(10));
        ExecutionHelper helper = makeHelper();
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(coinbase);

        doFinishAndCheck(tx, block, helper, result, coinbase, true);
    }

    @Test
    public void testFinishWithSeptForkIsTrueIsLocalIsRevert() {
        Address coinbase = getNewAddress();
        ExecutionResult result = new ExecutionResult(ResultCode.REVERT, 0,
            RandomUtils.nextBytes(10));
        ExecutionHelper helper = makeHelper();
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(coinbase);

        doFinishAndCheck(tx, block, helper, result, coinbase, true);
    }

    @Test
    public void testFinishWithSeptForkIsTrueIsLocalIsNotSuccessNotRevert() {
        Address coinbase = getNewAddress();
        ExecutionHelper helper = makeHelper();
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(coinbase);

        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS) && !code.equals(ResultCode.REVERT)) {
                ExecutionResult result = new ExecutionResult(code, 0,
                    RandomUtils.nextBytes(10));
                doFinishAndCheck(tx, block, helper, result, coinbase, true);
            }
        }
    }

    @Test
    public void testFinishWithSeptForkIsTrueNotLocalIsSuccess() {
        Address coinbase = getNewAddress();
        ExecutionResult result = new ExecutionResult(ResultCode.SUCCESS, 0,
            RandomUtils.nextBytes(10));
        ExecutionHelper helper = makeHelper();
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(coinbase);

        doFinishAndCheck(tx, block, helper, result, coinbase, false);
    }

    @Test
    public void testFinishWithSeptForkIsTrueNotLocalIsRevert() {
        Address coinbase = getNewAddress();
        ExecutionResult result = new ExecutionResult(ResultCode.REVERT, 0,
            RandomUtils.nextBytes(10));
        ExecutionHelper helper = makeHelper();
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(coinbase);

        doFinishAndCheck(tx, block, helper, result, coinbase, false);
    }

    @Test
    public void testFinishWithSeptForkIsTrueNotLocalNotSuccessNotRevert() {
        Address coinbase = getNewAddress();
        ExecutionHelper helper = makeHelper();
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(coinbase);

        for (ResultCode code : ResultCode.values()) {
            if (!code.equals(ResultCode.SUCCESS) && !code.equals(ResultCode.REVERT)) {
                ExecutionResult result = new ExecutionResult(code, 0,
                    RandomUtils.nextBytes(10));
                doFinishAndCheck(tx, block, helper, result, coinbase, false);
            }
        }
    }

    // <------------------------------------------HELPERS------------------------------------------>


    /**
     * Returns a new TransactionExecutor whose constructor params are randomly generated except for
     * isLocalCall. This executor executes tx and the block containing the tx has energy limit
     * blockNrg .
     *
     * @param tx The transaction.
     * @param isLocalCall True if a local call.
     * @param blockNrg The block energy limit.
     * @return a new TransactionExecutor.
     */
    private TransactionExecutor getNewExecutor(AionTransaction tx, boolean isLocalCall,
        long blockNrg) {

        return getNewExecutor(tx, isLocalCall, blockNrg, getNewAddress());
    }

    /**
     * Returns a new TransactionExecutor whose constructor params are radomly generated except for
     * isLocalCall. This executor executes tx and the tx data contains numZeroes zeroes and the
     * block containing the tx has energy limit blockNrg and a coinbase coinbase.
     *
     * @param tx The transaction.
     * @param isLocalCall True if a local call.
     * @param blockNrg The block energy limit.
     * @param coinbase The coinbase.
     * @return a new TransactionExecutor.
     */
    private TransactionExecutor getNewExecutor(AionTransaction tx, boolean isLocalCall,
        long blockNrg,
        Address coinbase) {

        IAionBlock block = getNewAionBlock(blockNrg, tx.getData(), coinbase);
        long nrgLeft = tx.transactionCost(block.getNumber());
        return new TransactionExecutor(tx, block, repo, isLocalCall, nrgLeft, LOGGER_VM);
    }

    /**
     * Returns a new AionBlock whose fields are randomized except for the ones provided by the input
     * parameters
     *
     * @param energyLimit The energy limit.
     * @param data The data.
     * @param coinbase The block's coinbase account.
     * @return a new AionBlock.
     */
    private AionBlock getNewAionBlock(long energyLimit, byte[] data, Address coinbase) {
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] parentHash = RandomUtils.nextBytes(arraySizes);
        byte[] logsBloom = RandomUtils.nextBytes(arraySizes);
        byte[] difficulty = RandomUtils.nextBytes(arraySizes);
        long number = RandomUtils.nextLong(0, 10_000);
        long timestamp = RandomUtils.nextLong(0, 10_000);
        byte[] extraData = RandomUtils.nextBytes(arraySizes);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        byte[] receiptsRoot = RandomUtils.nextBytes(arraySizes);
        byte[] transactionsRoot = RandomUtils.nextBytes(arraySizes);
        byte[] stateRoot = RandomUtils.nextBytes(arraySizes);
        List<AionTransaction> transactionList = getNewAionTransactions(3, data);
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
     * @return the list of transactions.
     */
    private List<AionTransaction> getNewAionTransactions(int num, byte[] data) {
        List<AionTransaction> transactions = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            transactions.add(getNewAionTransaction(data, RandomUtils.nextLong(1, 10_000)));
        }
        return transactions;
    }

    /**
     * Returns a list of num new random addresses.
     *
     * @param num The number of addresses to create.
     * @return the list of addresses.
     */
    private List<Address> getNewAddresses(int num) {
        List<Address> addresses = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            addresses.add(getNewAddress());
        }
        return addresses;
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
        return new Log(getNewAddress(), generateTopics(numTopics, topicSize),
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
     * Returns a receipt for a transaction that is a contract creation if isContractCreation is true
     * and whose data is length dataSize and that data consists of numZeroes zero bytes.
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
     * Returns a receipt for a transaction that is a contract creation if isContractCreation is true
     * and whose data is length dataSize and that data consists of numZeroes zero bytes.
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
        long nrgPrice = RandomUtils.nextLong(1, 10_000);
        AionTransaction tx = (isContractCreation) ?
            getNewAionTransactionContractCreation(data, nrgPrice) :
            getNewAionTransaction(data, nrgPrice);
        TransactionExecutor executor = getNewExecutor(tx, true, 0);
        ITxReceipt receipt = new AionTxReceipt();
        return (AionTxReceipt) executor.buildReceipt(receipt, tx, logs);
    }

    /**
     * Returns a new AionTransaction, most of whose fields are randomized. This transaction is not
     * for contract creation.
     *
     * @param data The transaction data.
     * @param nrgPrice The price per unit of energy.
     * @return a new AionTransaction.
     */
    private AionTransaction getNewAionTransaction(byte[] data, long nrgPrice) {
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        Address from = getNewAddress();
        Address to = getNewAddress();
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        return new AionTransaction(nonce, from, to, value, data, 10000000L, nrgPrice);
    }

    /**
     * Returns a new AionTransaction for contract creation logic. That is, its 'to' address is
     * null.
     *
     * @param data The transaction data.
     * @param nrgPrice The price per unit of energy.
     * @return a new AionTransaction for contract creation.
     */
    private AionTransaction getNewAionTransactionContractCreation(byte[] data, long nrgPrice) {
        int arraySizes = RandomUtils.nextInt(0, 50);
        byte[] nonce = RandomUtils.nextBytes(arraySizes);
        Address from = getNewAddress();
        byte[] value = RandomUtils.nextBytes(DataWord.BYTES);
        return new AionTransaction(nonce, from, null, value, data, 10000000L, nrgPrice);
    }

    /**
     * Computes the transaction cost for processing a transaction whose data has numZeroes zero
     * bytes and numNonZeroes non-zero bytes.
     *
     * If transaction is a contract creation then the fee is: createFee + nrgTrans + (numZeroes *
     * zeroDataNrg) + (numNonZeroes * nonzeroDataNrg)
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

    /**
     * Returns the fee that the coinbase receives for the newly mined block. This quantity is equal
     * to the amount of energy used by the transaction multiplied by the energy price.
     *
     * @param isContractCreation True if the transaction is for contract creation.
     * @param numZeroes The number of zero bytes in the transaction data.
     * @param numNonZeroes The number of non-zero bytes in the transaction data.
     * @param nrgPrice The energy price.
     * @return the coinbase's fee.
     */
    private BigInteger computeCoinbaseFee(boolean isContractCreation, int numZeroes,
        int numNonZeroes, long nrgPrice) {
        return BigInteger
            .valueOf(computeTxCost(isContractCreation, numZeroes, numNonZeroes) * nrgPrice);
    }

    /**
     * Runs the updateRepo method of a TransactionExecutor that has been constructed according to
     * the specified parameters.
     *
     * @param tx The transaction.
     * @param coinbase The block coinbase.
     * @param isLocalCall True if call is local.
     * @param markRejected True if tx summary is to be marked as rejected.
     */
    private void runUpdateRepo(AionTransaction tx, Address coinbase, boolean isLocalCall,
        boolean markRejected) {

        byte[] result = RandomUtils.nextBytes(RandomUtils.nextInt(0, 50));
        List<Log> logs = getNewLogs(RandomUtils.nextInt(0, 20));
        TransactionExecutor executor = getNewExecutor(tx, isLocalCall, 21_000, coinbase);
        AionTxReceipt receipt = (AionTxReceipt) executor
            .buildReceipt(new AionTxReceipt(), tx, logs);
        AionTxExecSummary.Builder summaryBuilder = new AionTxExecSummary.Builder(receipt)
            .result(result);
        if (markRejected) {
            summaryBuilder.markAsRejected();
        }
        executor.updateRepo(summaryBuilder.build(), tx, coinbase,
            getNewAddresses(RandomUtils.nextInt(0, 10)));
    }

    /**
     * Returns the amount of energy consumed by the transaction tx.
     *
     * @param tx The transaction.
     * @return the amount of energy consumed.
     */
    private long computeEnergyConsumption(AionTransaction tx) {
        return tx.getNrg() - tx.nrgLimit() + tx.transactionCost(0);
    }

    /**
     * Produces a transaction summary using executor's build receipt from tx.
     *
     * @param executor The executor to build the receipt with.
     * @param tx The transaction which informs the receipt.
     * @return the transaction summary.
     */
    private AionTxExecSummary produceSummary(TransactionExecutor executor, AionTransaction tx) {
        return new AionTxExecSummary.
            Builder((AionTxReceipt) executor.buildReceipt(new AionTxReceipt(), tx, new ArrayList()))
            .
                result(RandomUtils.nextBytes(RandomUtils.nextInt(0, 100))).
                build();
    }

    /**
     * Produces a mocked AionBlock whose difficulty consists of difficultyLength random bytes.
     *
     * @param difficultyLength The difficulty byte array length.
     * @return a mocked AionBlock.
     */
    private AionBlock mockBlock(int difficultyLength) {
        AionBlock block = mockBlock(getNewAddress());
        when(block.getDifficulty()).thenReturn(RandomUtils.nextBytes(difficultyLength));
        return block;
    }

    /**
     * Produces a mocked AionBlock whose getCoinbase method returns coinbase.
     *
     * @param coinbase The block's coinbase.
     * @return a mocked AionBlock.
     */
    private AionBlock mockBlock(Address coinbase) {
        AionBlock block = mock(AionBlock.class);
        when(block.getDifficulty()).thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 100)));
        when(block.getCoinbase()).thenReturn(coinbase);
        when(block.getNumber()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(block.getTimestamp()).thenReturn(RandomUtils.nextLong(0, 10_000));
        when(block.getNrgLimit()).thenReturn(RandomUtils.nextLong(0, 10_000));
        return block;
    }

    /**
     * Produces a mocked AionTransaction with the following real methods: setNrgConsume
     * getNrgConsume
     *
     * @return a mocked AionTransaction.
     */
    private AionTransaction mockTx() {
        return mockTx(getNewAddress(), BigInteger.TEN.toByteArray(),
            RandomUtils.nextLong(0, 10_000));
    }

    /**
     * Produces a mocked AionTransaction with the following real methods: setNrgConsume
     * getNrgConsume
     *
     * @param isContractCreation True only if transaction is for contract creation.
     * @param valueIsNull If true then tx.getValue() will return null.
     * @param dataIsNull If true then tx.getData() will return null.
     * @return a mocked AionTransaction.
     */
    private AionTransaction mockTx(boolean isContractCreation, boolean valueIsNull,
        boolean dataIsNull) {
        AionTransaction tx = mockTx(getNewAddress(), BigInteger.TEN.toByteArray(),
            RandomUtils.nextLong(0, 10_000));
        when(tx.isContractCreation()).thenReturn(isContractCreation);
        if (valueIsNull) {
            when(tx.getValue()).thenReturn(null);
        }
        if (dataIsNull) {
            when(tx.getData()).thenReturn(null);
        }
        return tx;
    }

    /**
     * Produces a mocked AionTransaction with the following real methods: setNrgConsume
     * getNrgConsume
     *
     * @param sender The sender of the transaction.
     * @param nonce The sender's nonce.
     * @param nrgPrice The energy price.
     * @return a mocked AionTransaction.
     */
    private AionTransaction mockTx(Address sender, byte[] nonce, long nrgPrice) {
        long txCost = RandomUtils.nextLong(2, 10_000);
        long nrgLimit = RandomUtils.nextLong(txCost, txCost + RandomUtils.nextLong(2, 10_000));
        long nrg = RandomUtils.nextLong(txCost, txCost + RandomUtils.nextLong(2, 10_000));
        AionTransaction tx = mock(AionTransaction.class);
        when(tx.getHash()).thenReturn(RandomUtils.nextBytes(32));
        when(tx.getData()).thenReturn(RandomUtils.nextBytes(RandomUtils.nextInt(0, 100)));
        when(tx.getNrg()).thenReturn(nrg);
        when(tx.nrgPrice()).thenReturn(new DataWord(RandomUtils.nextInt(0, 100)));
        when(tx.getNrgPrice()).thenReturn(nrgPrice);
        when(tx.nrgLimit()).thenReturn(nrgLimit);
        when(tx.getTo()).thenReturn(getNewAddress());
        when(tx.getContractAddress()).thenReturn(getNewAddress());
        when(tx.getFrom()).thenReturn(sender);
        when(tx.getValue())
            .thenReturn(BigInteger.valueOf(RandomUtils.nextInt(0, 100)).toByteArray());
        when(tx.getNonce()).thenReturn(nonce);
        when(tx.transactionCost(Mockito.any(Long.class))).thenReturn(txCost);
        doCallRealMethod().when(tx).setNrgConsume(Mockito.any(Long.class));
        when(tx.getNrgConsume()).thenCallRealMethod();
        return tx;
    }

    /**
     * Returns the refund that the transaction sender is entitled to (if indeed entitled to a
     * refund) if the sender sends the transaction tx, from which summary is derived.
     *
     * @param tx The transaction.
     * @param summary The transaction summary.
     * @return the sender's refund.
     */
    private BigInteger computeRefund(AionTransaction tx, AionTxExecSummary summary) {
        return BigInteger
            .valueOf((tx.getNrg() - summary.getReceipt().getEnergyUsed()) * tx.getNrgPrice());
    }

    /**
     * Adds numAccounts to the repository and returns them in a list.
     *
     * @param numAccounts The number of accounts to add.
     * @return the list of newly added accounts.
     */
    private List<Address> addAccountsToRepo(int numAccounts) {
        List<Address> accounts = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            Address acc = getNewAddress();
            repo.createAccount(acc);
            accounts.add(acc);
        }
        return accounts;
    }

    /**
     * Checks the fields of the internal ExecutionContext object that executor holds under the
     * assumption that executor was built from tx and block.
     *
     * @param tx The transaction.
     * @param block The block.
     */
    private void checkExecutionContext(TransactionExecutor executor, AionTransaction tx,
        AionBlock block) {
        ExecutionContext ctx = executor.getContext();
        Address recipient;
        int kind;
        byte[] data;
        if (tx.isContractCreation()) {
            recipient = tx.getContractAddress();
            kind = ExecutionContext.CREATE;
            data = ByteUtil.EMPTY_BYTE_ARRAY;
        } else {
            recipient = tx.getTo();
            kind = ExecutionContext.CALL;
            data = (tx.getData() == null) ? ByteUtil.EMPTY_BYTE_ARRAY : tx.getData();
        }

        byte[] value = (tx.getValue() == null) ? ByteUtil.EMPTY_BYTE_ARRAY : tx.getValue();
        byte[] tempDiff = block.getDifficulty();
        byte[] diff = (tempDiff.length > DataWord.BYTES) ?
            Arrays.copyOfRange(tempDiff, tempDiff.length - DataWord.BYTES, tempDiff.length) :
            tempDiff;

        assertArrayEquals(ctx.transactionHash(), tx.getHash());
        assertEquals(ctx.address(), recipient);
        assertEquals(ctx.origin(), tx.getFrom());
        assertEquals(ctx.sender(), tx.getFrom());
        assertEquals(ctx.nrgPrice(), tx.nrgPrice());
        assertEquals(ctx.nrgLimit(), tx.nrgLimit() - tx.transactionCost(0));
        assertEquals(ctx.callValue(), new DataWord(value));
        assertArrayEquals(ctx.callData(), data);
        assertEquals(ctx.depth(), 0);
        assertEquals(ctx.kind(), kind);
        assertEquals(ctx.flags(), 0);
        assertEquals(ctx.blockCoinbase(), block.getCoinbase());
        assertEquals(ctx.blockNumber(), block.getNumber());
        assertEquals(ctx.blockTimestamp(), block.getTimestamp());
        assertEquals(ctx.blockNrgLimit(), block.getNrgLimit());
        assertEquals(ctx.blockDifficulty(), new DataWord(diff));
    }

    /**
     * Checks that the internal IExecutionResult object that executor holds and the executor was
     * constructed using the transaction tx. If these checks fail then the calling test fails.
     *
     * @param tx The transaction that the TransactionExecutor was built off.
     */
    private void checkExecutionResult(TransactionExecutor executor, AionTransaction tx) {
        IExecutionResult result = executor.getResult();
        assertEquals(result.getCode(), ResultCode.SUCCESS.toInt());
        assertEquals(result.getNrgLeft(), tx.nrgLimit() - tx.transactionCost(0));
        assertArrayEquals(result.getOutput(), ByteUtil.EMPTY_BYTE_ARRAY);
    }

    /**
     * Checks that result has the expected code and expected energy limit. If this fails then the
     * calling test fails.
     *
     * @param result The result to check.
     * @param expectedCode The expected result code.
     * @param expectedNrgLeft The expected energy left.
     */
    private void checkExecutionResults(IExecutionResult result, int expectedCode,
        long expectedNrgLeft) {
        assertEquals(expectedCode, result.getCode());
        assertEquals(expectedNrgLeft, result.getNrgLeft());
    }

    /**
     * Returns an energy limit quantity that is invalid. The quantity is invalid for contract
     * creation energy limits if isContractCreation is true, otherwise it is invalid for regular
     * transactions. In addition to this, if isTooLow is true then the quantity will be below the
     * acceptable lower bound, otherwise it will be above the acceptable upper bound.
     *
     * @param isContractCreation True only if the energy limit is for contract creation.
     * @param isTooLow True if the energy limit is to be below lower bound, else above upper bound.
     * @return the invalid energy limit.
     */
    private long produceInvalidNrgLimit(boolean isContractCreation, boolean isTooLow) {
        if (isContractCreation) {
            return (isTooLow) ? Constants.NRG_TX_CREATE - 1 : Constants.NRG_TX_CREATE_MAX + 1;
        } else {
            return (isTooLow) ? Constants.NRG_TRANSACTION - 1 : Constants.NRG_TRANSACTION_MAX + 1;
        }
    }

    /**
     * Calls the prepare method of a TransactionExecutor with an invalid energy limit.
     *
     * @param isContractCreation True if transaction in executor is a contract creation.
     * @param isLowerBoundTest True if the lower bound is tested, false if the upper bound is
     * tested.
     */
    private void doPrepareInvalidNrgLimitTest(boolean isContractCreation,
        boolean isLowerBoundTest) {
        AionTransaction tx = mockTx(isContractCreation, false, false);
        AionBlock block = mockBlock(getNewAddress());
        long nrgLimit = block.getNrgLimit();
        long invalidLimit = produceInvalidNrgLimit(isContractCreation, isLowerBoundTest);

        when(tx.getNrg()).thenReturn(invalidLimit);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            nrgLimit, LOGGER_VM);

        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(),
            invalidLimit);

        // Test second constructor.
        executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(),
            invalidLimit);

        // Test third constructor.
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(),
            invalidLimit);
    }

    /**
     * Returns an energy limit that is valid for contract creation if isContractCreation is true,
     * otherwise a valid energy limit for a regular transaction.
     *
     * @param isContractCreation True if transaction is a contract creator.
     * @return a valid energy limit.
     */
    private long produceValidNrgLimit(boolean isContractCreation) {
        return (isContractCreation) ? Constants.NRG_TX_CREATE : Constants.NRG_TRANSACTION;
    }

    /**
     * Runs the prepare method of a TransactionExecutor with the transaction energy limit larger
     * than the block's remaining energy.
     *
     * @param isContractCreation True if the transaction is for contract creation.
     */
    private void doPrepareNrgLimitAboveRemainingNrg(boolean isContractCreation) {
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(getNewAddress());
        when(block.getNrgLimit()).thenReturn(produceValidNrgLimit(isContractCreation));
        long nrgLimit = block.getNrgLimit() + 1;
        when(tx.getNrg()).thenReturn(nrgLimit);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(), 0);

        // Test second constructor.
        executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(), 0);

        // Test third constructor.
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(), 0);
    }

    /**
     * Runs the prepare method of a TransactionExecutor with the context energy limit negative.
     *
     * @param isContractCreation True if the transaction is for contract creation.
     */
    private void doPrepareContextNrgLimitIsNegative(boolean isContractCreation) {
        AionTransaction tx = mockTx();
        AionBlock block = mockBlock(getNewAddress());
        when(tx.getNrg()).thenReturn(produceValidNrgLimit(isContractCreation));

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        assertFalse(executor.prepare(tx, -1));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(), 0);

        executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        assertFalse(executor.prepare(tx, -1));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(), 0);

        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        assertFalse(executor.prepare(tx, -1));
        checkExecutionResults(executor.getResult(), ResultCode.INVALID_NRG_LIMIT.toInt(), 0);
    }

    /**
     * Runs the prepare method of a TransactionExecutor.
     *
     * @param isContractCreation Transaction is for contract creation.
     * @param skipNonceCheck True if the nonce check is to be skipped.
     * @param balanceIsEqual True if account's balance is equal to the execution cost, otherwise it
     * is larger.
     */
    private void doPrepareIsGood(boolean isContractCreation, boolean skipNonceCheck,
        boolean balanceIsEqual) {
        byte[] value = RandomUtils.nextBytes(8);
        value[0] &= 0x7F;   // ensure value is positive.
        long nrgLimit = produceValidNrgLimit(isContractCreation);
        BigInteger nonce = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        Address sender = addAccountsToRepo(1).get(0);

        AionTransaction tx = mockTx(sender, nonce.toByteArray(), 1);
        AionBlock block = mockBlock(getNewAddress());
        when(tx.getNrg()).thenReturn(nrgLimit);
        when(block.getNrgLimit()).thenReturn(nrgLimit);
        when(tx.getValue()).thenReturn(value);
        BigInteger executionCost = computeTxExecutionCost(tx);
        repo.setNonce(sender, nonce);
        repo.addBalance(sender,
            (balanceIsEqual) ? executionCost : executionCost.add(BigInteger.ONE));

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        if (skipNonceCheck) {
            executor.setBypassNonce();
        }

        long expectedNrg = tx.nrgLimit() - tx.transactionCost(0);
        assertTrue(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.SUCCESS.toInt(), expectedNrg);
    }

    /**
     * Runs the prepare method of a TransactionExecutor.
     *
     * @param isContractCreation Transaction is for contract creation.
     * @param txNrgPrice The transaction energy price.
     * @param txValue The transaction value.
     */
    private void doPrepareInsufficientBalance(boolean isContractCreation, long txNrgPrice,
        byte[] txValue) {
        long nrgLimit = produceValidNrgLimit(isContractCreation);
        BigInteger nonce = BigInteger.valueOf(RandomUtils.nextLong(0, 10_000));
        Address sender = addAccountsToRepo(1).get(0);
        AionTransaction tx = mockTx(sender, nonce.toByteArray(), txNrgPrice);
        AionBlock block = mockBlock(getNewAddress());
        when(tx.getNrg()).thenReturn(nrgLimit);
        when(tx.getValue()).thenReturn(txValue);
        when(block.getNrgLimit()).thenReturn(nrgLimit);

        BigInteger executionCost = computeTxExecutionCost(tx);
        repo.addBalance(sender, executionCost.subtract(BigInteger.ONE));
        repo.setNonce(sender, nonce);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INSUFFICIENT_BALANCE.toInt(), 0);

        // Test second constructor.
        executor = new TransactionExecutor(tx, block, repo, false, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INSUFFICIENT_BALANCE.toInt(), 0);

        // Test third constructor.
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        assertFalse(executor.prepare(tx, 0));
        checkExecutionResults(executor.getResult(), ResultCode.INSUFFICIENT_BALANCE.toInt(), 0);
    }

    /**
     * Returns the cost of executing the transaction tx. This cost is equal to: (PN) + V
     *
     * where P is the energy price, N is the energy limit and V is the transaction value.
     *
     * @param tx The transaction.
     * @return the cost of executing the transaction.
     */
    private BigInteger computeTxExecutionCost(AionTransaction tx) {
        BigInteger price = BigInteger.valueOf(Math.abs(tx.getNrgPrice()));
        BigInteger nrg = BigInteger.valueOf(Math.abs(tx.getNrg()));
        BigInteger value = new BigInteger(1, tx.getValue());

        return ((price).multiply(nrg)).add(value);
    }

    /**
     * Runs TransactionExecutor's create method on a transaction whose transaction data is data and
     * checks the repository for the correct state changes.
     *
     * @param data The transaction data.
     * @param vmResult The mocked execution result of the fastVM's run method.
     */
    private void doCreateAndCheck(byte[] data, ExecutionResult vmResult, boolean valueIsPositive) {
        byte[] val = RandomUtils.nextBytes(8);
        if (valueIsPositive) {
            val[0] &= 0x7F;
        } else {
            val[0] |= 0x80;
        }

        BigInteger txValue = new BigInteger(val);
        Address contractAddr = getNewAddress();
        Address sender = addAccountsToRepo(1).get(0);
        AionTransaction tx = mockTx();
        when(tx.getContractAddress()).thenReturn(contractAddr);
        when(tx.getFrom()).thenReturn(sender);
        when(tx.getData()).thenReturn(data);
        when(tx.getValue()).thenReturn(val);
        AionBlock block = mockBlock(getNewAddress());
        VirtualMachine vm = mock(VirtualMachine.class);
        when(vm.run(
            Mockito.any(byte[].class), Mockito.any(ExecutionContext.class),
            Mockito.any(IRepositoryCache.class))).
            thenReturn(vmResult);
        ExecutorProvider provider = mock(ExecutorProvider.class);
        when(provider.getVM()).thenReturn(vm);
        repo.addBalance(sender, txValue.abs());

        long expectedNrg = tx.nrgLimit() - tx.transactionCost(0);
        vmResult.setNrgLeft(expectedNrg);
        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        executor.setExecutorProvider(provider);
        executor.create();

        checkExecutionResults(executor.getResult(), vmResult.code.toInt(), expectedNrg);
        assertEquals(txValue.abs().subtract(new BigInteger(1, val)),
            executor.repoTrack.getBalance(sender));
        assertEquals(new BigInteger(1, val), executor.repoTrack.getBalance(contractAddr));
        if (vmResult.code.equals(ResultCode.SUCCESS)) {
            assertArrayEquals(vmResult.output, executor.repoTrack.getCode(contractAddr));
        } else {
            assertArrayEquals(new byte[0], executor.repoTrack.getCode(contractAddr));
        }
    }

    /**
     * Runs TransactionExecutor's call method and checks state afterwards.
     *
     * @param result The result for the mocked fastVM to return.
     * @param isPrecompiled True implies the transaction will be mocked as a precompiled contract.
     * @param code The code to execute in the recipient address using the fastVM.
     * @param valIsPositive True implies the transaction value will be positive. Otherwise
     * negative.
     */
    private void doCallAndCheck(ExecutionResult result, boolean isPrecompiled, byte[] code,
        boolean valIsPositive) {
        byte[] val = RandomUtils.nextBytes(8);
        if (valIsPositive) {
            val[0] &= 0x7F;
        } else {
            val[0] |= 0x80;
        }
        BigInteger txValue = new BigInteger(1, val);
        List<Address> accts = addAccountsToRepo(2);
        Address sender = accts.get(0);
        Address recipient = accts.get(1);
        AionTransaction tx = mockTx();
        when(tx.getFrom()).thenReturn(sender);
        when(tx.getTo()).thenReturn(recipient);
        when(tx.getValue()).thenReturn(val);
        AionBlock block = mockBlock(getNewAddress());
        long expectedNrg = tx.nrgLimit() - tx.transactionCost(0);
        result.setNrgLeft(expectedNrg);
        repo.addBalance(sender, txValue);
        repo.saveCode(recipient, code);

        VirtualMachine vm = mock(VirtualMachine.class);
        when(vm.run(
            Mockito.any(byte[].class), Mockito.any(ExecutionContext.class),
            Mockito.any(IRepositoryCache.class))).
            thenReturn(result);
        IPrecompiledContract pc = mock(IPrecompiledContract.class);
        when(pc.execute(Mockito.any(byte[].class), Mockito.any(Long.class))).thenReturn(result);
        ExecutorProvider provider = mock(ExecutorProvider.class);
        when(provider.getPrecompiledContract(
            Mockito.any(ExecutionContext.class), Mockito.any(IRepositoryCache.class))).
            thenReturn((isPrecompiled) ? pc : null);
        when(provider.getVM()).thenReturn(vm);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, false,
            block.getNrgLimit(), LOGGER_VM);
        executor.setExecutorProvider(provider);
        executor.call();

        if ((code != null) && (code.length > 0)) {
            assertEquals(result.getCode(), executor.getResult().getCode());
            assertEquals(result.getNrgLeft(), executor.getResult().getNrgLeft());
            assertArrayEquals(result.getOutput(), executor.getResult().getOutput());
        }

        assertEquals(BigInteger.ZERO, executor.repoTrack.getBalance(sender));
        assertEquals(txValue, executor.repoTrack.getBalance(recipient));
    }

    private ExecutionHelper makeHelper() {
        ExecutionHelper helper = new ExecutionHelper();
        helper.addInternalTransactions(newInternalTxs(RandomUtils.nextInt(5, 15)));
        helper.addDeleteAccounts(addAccountsToRepo(RandomUtils.nextInt(5, 15)));
        helper.addLogs(newLogs(RandomUtils.nextInt(5, 15)));
        return helper;
    }

    private Collection<Log> newLogs(int num) {
        Collection<Log> logs = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            logs.add(newLog());
        }
        return logs;
    }

    private Log newLog() {
        return new Log(getNewAddress(), newTopics(RandomUtils.nextInt(2, 8)),
            RandomUtils.nextBytes(10));
    }

    private List<byte[]> newTopics(int num) {
        List<byte[]> topics = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            topics.add(RandomUtils.nextBytes(10));
        }
        return topics;
    }

    private Collection<AionInternalTx> newInternalTxs(int num) {
        Collection<AionInternalTx> txs = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            txs.add(newInternalTx());
        }
        return txs;
    }

    private AionInternalTx newInternalTx() {
        String note = "";
        byte[] parentHash = RandomUtils.nextBytes(32);
        byte[] nonce = RandomUtils.nextBytes(10);
        byte[] value = RandomUtils.nextBytes(10);
        byte[] data = RandomUtils.nextBytes(10);
        int deep = 0, index = 0;
        return new AionInternalTx(parentHash, deep, index, nonce, getNewAddress(), getNewAddress(),
            value, data, note);
    }

    /**
     * Checks that summary and helper both contain identical logs. If they do not then this method
     * causes the calling test to fail.
     *
     * @param summary A transaction summary.
     * @param helper An execution helper.
     */
    private void checkLogs(AionTxExecSummary summary, ExecutionHelper helper) {
        List<Log> summaryLogs = summary.getLogs();
        List<Log> helperLogs = helper.getLogs();
        List<Address> summaryAddrs = new ArrayList<>();
        List<Address> helperAddrs = new ArrayList<>();
        List<ByteArrayWrapper> summaryData = new ArrayList<>();
        List<ByteArrayWrapper> helperData = new ArrayList<>();
        List<ByteArrayWrapper> summaryTopics = new ArrayList<>();
        List<ByteArrayWrapper> helperTopics = new ArrayList<>();

        for (Log log : summaryLogs) {
            summaryAddrs.add(log.getAddress());
            summaryData.add(new ByteArrayWrapper(log.getData()));
            summaryTopics.addAll(wrapTopics(log.getTopics()));
        }
        for (Log log : helperLogs) {
            helperAddrs.add(log.getAddress());
            helperData.add(new ByteArrayWrapper(log.getData()));
            helperTopics.addAll(wrapTopics(log.getTopics()));
        }

        assertEquals(helperAddrs, summaryAddrs);
        assertEquals(helperData, summaryData);
        assertEquals(helperTopics, summaryTopics);
    }

    private List<ByteArrayWrapper> wrapTopics(List<byte[]> topics) {
        List<ByteArrayWrapper> wrappedTopics = new ArrayList<>();
        for (byte[] topic : topics) {
            wrappedTopics.add(new ByteArrayWrapper(topic));
        }
        return wrappedTopics;
    }

    private BigInteger computeSummaryFee(AionTxReceipt receipt, AionTransaction tx) {
        return BigInteger.valueOf(receipt.getEnergyUsed())
            .multiply(BigInteger.valueOf(tx.getNrgPrice()));
    }

    /**
     * Checks that summary is in the expected state after a TransactionExecutor's call to finish.
     *
     * @param summary The summary of the finish method.
     * @param helper The transaction helper.
     * @param receipt The transaction receipt.
     * @param tx The transaction.
     * @param result The transaction result.
     * @param isFailed If the summary should be failed.
     * @param isRejected If the summary should be rejected.
     */
    private void checkSummary(AionTxExecSummary summary, ExecutionHelper helper,
        AionTxReceipt receipt,
        AionTransaction tx, ExecutionResult result, boolean isFailed, boolean isRejected) {

        assertEquals(isFailed, summary.isFailed());
        assertEquals(isRejected, summary.isRejected());
        assertEquals(new BigInteger(receipt.getTransaction().getValue()), summary.getValue());
        if (result.getResultCode().equals(ResultCode.SUCCESS)) {
            assertEquals(helper.getDeleteAccounts(), summary.getDeletedAccounts());
            checkLogs(summary, helper);
        } else {
            assertTrue(summary.getDeletedAccounts().isEmpty());
            assertTrue(summary.getLogs().isEmpty());
        }
        assertEquals(helper.getInternalTransactions(), summary.getInternalTransactions());
        assertArrayEquals(result.getOutput(), summary.getResult());
        assertArrayEquals(tx.getHash(), summary.getTransactionHash());
        assertEquals(computeRefund(tx, summary), summary.getRefund());
        assertTrue(summary.getTouchedStorage().isEmpty());
        assertEquals(BigInteger.valueOf(receipt.getEnergyUsed()), summary.getNrgUsed());
        assertEquals(computeSummaryFee(receipt, tx), summary.getFee());
    }

    private boolean determineIfFailed(ExecutionResult result) {
        switch (result.getResultCode()) {
            case CONTRACT_ALREADY_EXISTS:
            case FAILURE:
            case OUT_OF_NRG:
            case BAD_INSTRUCTION:
            case BAD_JUMP_DESTINATION:
            case STACK_OVERFLOW:
            case STACK_UNDERFLOW:
            case REVERT:
            case INTERNAL_ERROR:
            case INVALID_NONCE:
            case INVALID_NRG_LIMIT:
            case INSUFFICIENT_BALANCE:
                return true;
            default:
                return false;
        }
    }

    private boolean determineIfRejected(ExecutionResult result) {
        switch (result.getResultCode()) {
            case INVALID_NONCE:
            case INVALID_NRG_LIMIT:
            case INSUFFICIENT_BALANCE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Runs TransactionExecutor's finish method and checks its results.
     */
    private void doFinishAndCheck(AionTransaction tx, AionBlock block, ExecutionHelper helper,
        ExecutionResult result, Address coinbase, boolean isLocalCall) {

        boolean isFailed = determineIfFailed(result);
        boolean isRejected = determineIfRejected(result);

        TransactionExecutor executor = new TransactionExecutor(tx, block, repo, isLocalCall,
            block.getNrgLimit(), LOGGER_VM);

        // This essentially makes executor's helper the same as helper
        executor.getContext().helper().merge(helper, true);
        executor.exeResult = result;
        AionTxReceipt receipt = executor.getReceipt(helper.getLogs());

        AionTxExecSummary summary = executor.finish();
        checkSummary(summary, helper, receipt, tx, result, isFailed, isRejected);
        checkRepoStateAfterFinish(coinbase, result, helper, tx, summary, isLocalCall,
            summary.isRejected());

        // Try second constructor.
        executor = new TransactionExecutor(tx, block, repo, isLocalCall, LOGGER_VM);
        executor.getContext().helper().merge(helper, true);
        executor.exeResult = result;
        receipt = executor.getReceipt(helper.getLogs());
        summary = executor.finish();
        checkSummary(summary, helper, receipt, tx, result, isFailed, isRejected);
        checkRepoStateAfterFinish(coinbase, result, helper, tx, summary, isLocalCall,
            summary.isRejected());

        // Try third constructor.
        executor = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        executor.getContext().helper().merge(helper, true);
        executor.exeResult = result;
        receipt = executor.getReceipt(helper.getLogs());
        summary = executor.finish();
        checkSummary(summary, helper, receipt, tx, result, isFailed, isRejected);
        checkRepoStateAfterFinish(coinbase, result, helper, tx, summary, false,
            summary.isRejected());
    }

    /**
     * Checks the expected state of the repoistory after a TransactionExecutor's finish method has
     * run. If these checks fail then the calling test fails.
     *
     * @param coinbase The block coinbase.
     * @param result The execution result.
     * @param helper The transaction helper.
     * @param tx The transaction.
     * @param summary The finish method's summary.
     */
    private void checkRepoStateAfterFinish(Address coinbase, ExecutionResult result,
        ExecutionHelper helper, AionTransaction tx, AionTxExecSummary summary, boolean isLocalCall,
        boolean isRejected) {

        if (isLocalCall || isRejected) {
            assertEquals(BigInteger.ZERO, repo.getBalance(tx.getFrom()));
            // nrg consume??
            assertEquals(BigInteger.ZERO, repo.getBalance(coinbase));
            for (Address address : helper.getDeleteAccounts()) {
                assertTrue(repo.accounts.containsKey(address));
            }
            return;
        }

        if (result.getResultCode().equals(ResultCode.SUCCESS) ||
            result.getResultCode().equals(ResultCode.REVERT)) {

            assertEquals(summary.getRefund(), repo.getBalance(tx.getFrom()));
            repo.addBalance(tx.getFrom(), summary.getRefund().negate());
        } else {
            assertEquals(BigInteger.ZERO, repo.getBalance(tx.getFrom()));
        }

        assertEquals(tx.getNrg() - result.getNrgLeft(), tx.getNrgConsume());
        assertEquals(summary.getFee(), repo.getBalance(coinbase));
        repo.addBalance(coinbase, summary.getFee().negate());

        if (result.getResultCode().equals(ResultCode.SUCCESS)) {
            for (Address address : helper.getDeleteAccounts()) {
                assertFalse(repo.accounts.containsKey(address));
            }
        }
    }

}

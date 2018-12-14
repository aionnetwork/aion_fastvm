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
package org.aion.fastvm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKey;
import org.aion.crypto.ECKeyFac;
import org.aion.crypto.ECKeyFac.ECKeyType;
import org.aion.crypto.SignatureFac;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.zero.impl.db.AionRepositoryImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.aion.zero.types.AionTxReceipt;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;

public class Benchmark {

    private static AionBlock block = TestUtils.createDummyBlock();
    private static AionRepositoryImpl db = AionRepositoryImpl.inst();
    private static IRepositoryCache<AccountState, IBlockStoreBase<?, ?>> repo = db.startTracking();

    private static ECKey key;
    private static AionAddress owner;
    private static AionAddress contract;

    private static List<byte[]> recipients = new ArrayList<>();

    private static long timePrepare;
    private static long timeSignTransactions;
    private static long timeValidateTransactions;
    private static long timeExecuteTransactions;
    private static long timeFlush;

    private static Logger LOGGER = AionLoggerFactory.getLogger(LogEnum.VM.name());

    private static void prepare() throws IOException {
        long t1 = System.currentTimeMillis();

        // create owner account
        ECKeyFac.setType(ECKeyType.ED25519);
        key = ECKeyFac.inst().create();
        owner = AionAddress.wrap(key.getAddress());
        repo.createAccount(owner);
        repo.addBalance(owner, BigInteger.valueOf(1_000_000_000L));

        // create transaction
        byte[] deployer =
                ContractUtils.getContractDeployer("BenchmarkERC20.sol", "FixedSupplyToken");
        byte[] nonce = DataWord.ZERO.getData();
        AionAddress from = owner;
        AionAddress to = null;
        byte[] value = DataWord.ZERO.getData();
        long nrg = 1_000_000L;
        long nrgPrice = 1L;
        AionTransaction tx = new AionTransaction(nonce, from, to, value, deployer, nrg, nrgPrice);

        // save contract address
        contract = tx.getContractAddress();

        // deploy contract
        TransactionExecutor exec = new TransactionExecutor(tx, block, new KernelInterfaceForFastVM(repo, true, false), LOGGER);
        AionTxExecSummary summary = exec.execute();
        assertFalse(summary.isFailed());

        long t2 = System.currentTimeMillis();
        timePrepare = t2 - t1;
    }

    private static List<AionTransaction> signTransactions(int num) {
        long t1 = System.currentTimeMillis();
        List<AionTransaction> list = new ArrayList<>();

        long ownerNonce = repo.getNonce(owner).longValue();

        for (int i = 0; i < num; i++) {
            byte[] recipient = RandomUtils.nextBytes(20);
            recipients.add(recipient);

            // transfer token to random people
            byte[] nonce = new DataWord(ownerNonce + i).getData();
            AionAddress from = owner;
            AionAddress to = contract;
            byte[] value = DataWord.ZERO.getData();
            byte[] data =
                    ByteUtil.merge(
                            Hex.decode("fbb001d6" + "000000000000000000000000"),
                            recipient,
                            DataWord.ONE.getData());
            long nrg = 1_000_000L;
            long nrgPrice = 1L;
            AionTransaction tx = new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice);

            tx.sign(key);
            list.add(tx);
        }

        long t2 = System.currentTimeMillis();
        timeSignTransactions = t2 - t1;

        return list;
    }

    private static List<AionTxReceipt> validateTransactions(List<AionTransaction> txs) {
        long t1 = System.currentTimeMillis();
        List<AionTxReceipt> list = new ArrayList<>();

        for (AionTransaction tx : txs) {
            boolean valid =
                    tx.getTransactionHash() != null
                            && tx.getTransactionHash().length == 32 //
                            && tx.getValue() != null
                            && tx.getValue().length == 16 //
                            && tx.getData() != null //
                            && tx.getSenderAddress() != null //
                            && tx.getDestinationAddress() == null //
                            && tx.getNonce() != null
                            && tx.getNonce().length == 16 //
                            && tx.getEnergyLimit() > 0 //
                            && tx.getEnergyPrice() > 0 //
                            && SignatureFac.verify(
                                    tx.getRawHash(),
                                    tx.getSignature()); // TODO: verify signature here
            assertTrue(valid);
        }

        long t2 = System.currentTimeMillis();
        timeValidateTransactions = t2 - t1;

        return list;
    }

    private static List<AionTxReceipt> executeTransactions(List<AionTransaction> txs) {
        long t1 = System.currentTimeMillis();
        List<AionTxReceipt> list = new ArrayList<>();

        for (AionTransaction tx : txs) {
            TransactionExecutor exec = new TransactionExecutor(tx, block, new KernelInterfaceForFastVM(repo, true, false), LOGGER);
            AionTxExecSummary summary = exec.execute();
            assertFalse(summary.isFailed());

            list.add(summary.getReceipt());
        }

        long t2 = System.currentTimeMillis();
        timeExecuteTransactions = t2 - t1;

        return list;
    }

    private static void flush() {
        long t1 = System.currentTimeMillis();

        repo.flush();
        db.flush();

        long t2 = System.currentTimeMillis();
        timeFlush = t2 - t1;
    }

    private static void verifyState(int num) {
        long ownerNonce = repo.getNonce(owner).longValue();

        for (int i = 0; i < recipients.size(); i++) {
            byte[] nonce = new DataWord(ownerNonce + i).getData();
            AionAddress from = owner;
            AionAddress to = contract;
            byte[] value = DataWord.ZERO.getData();
            byte[] data =
                    ByteUtil.merge(
                            Hex.decode("70a08231" + "000000000000000000000000"), recipients.get(i));
            long nrg = 1_000_000L;
            long nrgPrice = 1L;
            AionTransaction tx = new AionTransaction(nonce, from, to, value, data, nrg, nrgPrice);

            TransactionExecutor exec = new TransactionExecutor(tx, block, new KernelInterfaceForFastVM(repo, true, false), LOGGER);
            AionTxExecSummary summary = exec.execute();
            assertFalse(summary.isFailed());

            assertEquals(1, new DataWord(summary.getReceipt().getTransactionOutput()).longValue());
        }
    }

    public static void main(String args[]) throws IOException {
        int n = 10000;
        prepare();
        List<AionTransaction> list = signTransactions(n);
        validateTransactions(list);
        executeTransactions(list);
        flush();
        verifyState(n);

        System.out.println("==========================================");
        System.out.println("Benchmark (ERC20 transfer): " + n + " txs");
        System.out.println("==========================================");
        System.out.println("prepare               : " + timePrepare + " ms");
        System.out.println("sign_transactions     : " + timeSignTransactions + " ms");
        System.out.println("validate_transactions : " + timeValidateTransactions + " ms");
        System.out.println("execute_transactions  : " + timeExecuteTransactions + " ms");
        System.out.println("flush                 : " + timeFlush + " ms");
    }
}

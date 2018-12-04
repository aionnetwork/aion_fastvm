package org.aion.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKeyFac;
import org.aion.fastvm.TestUtils;
import org.aion.fastvm.TestVMProvider;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.core.AccountState;
import org.aion.mcf.db.IBlockStoreBase;
import org.aion.mcf.vm.types.DataWord;
import org.aion.solidity.CompilationResult;
import org.aion.solidity.Compiler;
import org.aion.solidity.Compiler.Options;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * These were the tests previous in TransactionExecutorUnitTest but that class is now strictly unit
 * testing. These tests are more on the integration test level. Once there's a place to fit these
 * they will be moved.
 */
public class OldTxExecutorTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    @Test
    public void testCallTransaction() throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = DataWord.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to =
                Address.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
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

        assertArrayEquals(
                Hex.decode("00000000000000000000000000000000"), receipt.getTransactionOutput());
    }

    @Test
    public void testCreateTransaction() throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin;
        System.out.println(deployer);

        byte[] txNonce = DataWord.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to = Address.EMPTY_ADDRESS();
        byte[] value = DataWord.ZERO.getData();
        byte[] data = Hex.decode(deployer);
        long nrg = 500_000L;
        long nrgPrice = 1;
        AionTransaction tx = new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);

        AionBlock block = TestUtils.createDummyBlock();

        IRepositoryCache<AccountState, DataWord, IBlockStoreBase<?, ?>> repo =
                new DummyRepository();
        repo.addBalance(from, BigInteger.valueOf(500_000L).multiply(tx.nrgPrice().value()));

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(
                Hex.decode(deployer.substring(deployer.indexOf("60506040", 1))),
                receipt.getTransactionOutput());
    }

    @Test
    public void testPerformance() throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(
                                ContractUtils.readContract("Ticker.sol"), Options.ABI, Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("Ticker").bin; // deployer
        String contract = deployer.substring(deployer.indexOf("60506040", 1)); // contract

        byte[] txNonce = DataWord.ZERO.getData();
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to =
                Address.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
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
        Address from =
                Address.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        Address to =
                Address.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
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
}

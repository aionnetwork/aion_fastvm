package org.aion.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKey;
import org.aion.fastvm.TestVMProvider;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.db.ContractDetailsCacheImpl;
import org.aion.mcf.vm.types.DataWord;
import org.aion.precompiled.ContractFactory.TestPrecompiledContract;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.zero.db.AionContractDetailsImpl;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.types.AionInternalTx;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.apache.commons.lang3.RandomUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

public class CallcodeDelegateTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private Address deployer;
    private BigInteger deployerBalance;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
            .withValidatorConfiguration("simple")
            .withDefaultAccounts()
            .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        deployer = new Address(deployerKey.getAddress());
        deployerBalance = Builder.DEFAULT_BALANCE;
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
        deployer = null;
        deployerBalance = null;
    }

    @Test
    public void testRepository() {
        Address address = new Address(RandomUtils.nextBytes(Address.ADDRESS_LEN));
        AionContractDetailsImpl a = new AionContractDetailsImpl();
        a.setAddress(address);
        ContractDetailsCacheImpl b = new ContractDetailsCacheImpl(a);
        b.setAddress(address);
        b.get(DataWord.ZERO);
        b.origContract.get(DataWord.ZERO);
        assertTrue(b.origContract == a);
    }

    @Test
    public void testCallcodeStorage() throws IOException {
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        BigInteger n = new BigInteger("7638523");
        Address D = deployContract(repo, "D", "D.sol", BigInteger.ZERO);
        Address E = deployContract(repo, "E", "D.sol", BigInteger.ZERO);
        System.err.println("Deployer is: " + deployer);
        System.err.println("Contract D: " + D);
        System.err.println("Contract E: " + E);
        System.err.println("The n value is: " + n);

        // Deployer calls contract D which performs CALLCODE to call contract E. We expect that the
        // storage in contract D is modified by the code that is called in contract E.
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = BigInteger.TWO;
        byte[] input = ByteUtil.merge(Hex.decode("5cce9fc2"), E.toBytes()); // use CALLCODE on E.
        input = ByteUtil.merge(input, new DataWord(n).getData()); // pass in 'n' also.

        AionTransaction tx = new AionTransaction(nonce.toByteArray(), D,
            BigInteger.ZERO.toByteArray(), input, nrg, nrgPrice);
        tx.sign(deployerKey);
        System.err.println("\nExpect FROM is deployer and TO is D.");
        System.err.println("Transaction FROM is deployer: " + tx.getFrom());
        System.err.println("Transaction TO is D: " + tx.getTo());
        assertEquals(deployer, tx.getFrom());
        assertEquals(D, tx.getTo());

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - summary.getNrgUsed().longValue(), result.getNrgLeft());
        nonce = nonce.add(BigInteger.ONE);

        // When we call into contract D we should find its storage is modified so that 'n' is set.
/*
        input = Hex.decode("3e955225");
        tx = new AionTransaction(nonce.toByteArray(), D, BigInteger.ZERO.toByteArray(), input, nrg,
            nrgPrice);
        tx.sign(deployerKey);
        System.err.println("\nQuerying contract D's storage.");
        System.err.println("Transaction FROM is deployer: " + tx.getFrom());
        System.err.println("Transaction TO is D: " + tx.getTo());
        assertEquals(deployer, tx.getFrom());
        assertEquals(D, tx.getTo());

        context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        BigInteger inStore = new BigInteger(exec.execute().getResult());
        System.err.println("Found in D's storage for n: " + inStore);
//        assertEquals(n, inStore);
        nonce = nonce.add(BigInteger.ONE);

        // When we call into contract E we should find its storage is unmodified.
        tx = new AionTransaction(nonce.toByteArray(), E, BigInteger.ZERO.toByteArray(), input, nrg,
            nrgPrice);
        tx.sign(deployerKey);
        System.err.println("\nQuerying contract E's storage.");
        System.err.println("Transaction FROM is deployer: " + tx.getFrom());
        System.err.println("Transaction TO is E: " + tx.getTo());
        assertEquals(deployer, tx.getFrom());
        assertEquals(E, tx.getTo());

        context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        inStore = new BigInteger(exec.execute().getResult());
        System.err.println("Found in E's storage for n: " + inStore);
//        assertEquals(BigInteger.ZERO, inStore);
*/
    }

    @Test
    public void testCallcodeActors() {
        //TODO
    }

    @Test
    public void testCallcodeValueTransfer() {
        //TODO
    }

    // test callcode to callcode

    //<-------------------------------------------------------------------------------------------->

    /**
     * Deploys the contract named contractName in the file named contractFilename with value value.
     */
    private Address deployContract(IRepositoryCache repo, String contractName,
        String contractFilename, BigInteger value) throws IOException {

        //TODO: value.toByteArray or put inside DataWord first?
        byte[] deployCode = ContractUtils.getContractDeployer(contractFilename, contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger nonce = repo.getNonce(deployer);
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        Address contract = deployContract(repo, tx, contractName, contractFilename, value, nrg,
            nrgPrice, nonce);
        deployerBalance = repo.getBalance(deployer);
        return contract;
    }

    /**
     * Deploys a contract named contractName in a file named contractFilename and checks the state
     * of the deployed contract and the contract deployer.
     *
     * Returns the address of the newly deployed contract.
     */
    private Address deployContract(IRepositoryCache repo, AionTransaction tx, String contractName,
        String contractFilename, BigInteger value, long nrg, long nrgPrice, BigInteger expectedNonce)
        throws IOException {

        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());
        assertEquals(deployerBalance, repo.getBalance(deployer));
        assertEquals(expectedNonce, repo.getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);

        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contractFilename, contract, value);
        checkStateOfDeployer(repo, deployerBalance, summary.getNrgUsed().longValue(), nrgPrice,
            value, expectedNonce);
        return contract;
    }

    /**
     * Checks that the newly deployed contract in file contractFilename and named contractName is
     * deployed at address contractAddr with the expected body code and a zero nonce and balance
     * equal to whatever value was transferred to it when deployed.
     */
    private void checkStateOfNewContract(IRepositoryCache repo, String contractName,
        String contractFilename, Address contractAddr, BigInteger valueTransferred) throws IOException {

        byte[] expectedBodyCode = ContractUtils.getContractBody(contractFilename, contractName);
        assertArrayEquals(expectedBodyCode, repo.getCode(contractAddr));
        assertEquals(valueTransferred, repo.getBalance(contractAddr));
        assertEquals(BigInteger.ZERO, repo.getNonce(contractAddr));
    }

    /**
     * Checks the state of the deployer after a successful contract deployment. In this case we
     * expect the deployer's nonce to have incremented by one and their new balance to be equal to
     * the prior balance minus the tx cost and the value transferred.
     */
    private void checkStateOfDeployer(IRepositoryCache repo, BigInteger priorBalance, long nrgUsed,
        long nrgPrice, BigInteger value, BigInteger priorNonce) {

        assertEquals(priorNonce.add(BigInteger.ONE), repo.getNonce(deployer));
        BigInteger txCost = BigInteger.valueOf(nrgUsed * nrgPrice);
        assertEquals(priorBalance.subtract(txCost).subtract(value), repo.getBalance(deployer));
    }

}

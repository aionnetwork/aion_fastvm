package org.aion.vm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import org.aion.base.db.IRepositoryCache;
import org.aion.base.type.Address;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.crypto.ECKey;
import org.aion.fastvm.TestVMProvider;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.vm.types.DataWord;
import org.aion.vm.AbstractExecutionResult.ResultCode;
import org.aion.zero.impl.BlockContext;
import org.aion.zero.impl.StandaloneBlockchain;
import org.aion.zero.impl.StandaloneBlockchain.Builder;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxExecSummary;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;

/**
 * Tests the opcall CREATE for deploying new smart contracts as well as CALL to call a deployed
 * contract.
 */
public class ContractCreationTest {
    private static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());
    private StandaloneBlockchain blockchain;
    private ECKey deployerKey;
    private Address deployer;

    @Before
    public void setup() {
        StandaloneBlockchain.Bundle bundle = (new StandaloneBlockchain.Builder())
            .withValidatorConfiguration("simple")
            .withDefaultAccounts()
            .build();
        blockchain = bundle.bc;
        deployerKey = bundle.privateKeys.get(0);
        deployer = new Address(deployerKey.getAddress());
    }

    @After
    public void tearDown() {
        blockchain = null;
        deployerKey = null;
        deployer = null;
    }

    @Test
    public void testEmptyContract() throws IOException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, result, value);
        checkStateOfDeployer(repo, summary, nrgPrice, value);
    }

    @Test
    public void testContractDeployCodeIsEmpty() {
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            new byte[0], nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());

        Address contract = tx.getContractAddress();
        assertArrayEquals(new byte[0], result.getOutput());
        assertArrayEquals(new byte[0], repo.getCode(contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertEquals(BigInteger.ZERO, repo.getNonce(contract));

        assertEquals(BigInteger.ONE, repo.getNonce(deployer));
        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testContractDeployCodeIsNonsensical() {
        byte[] deployCode = new byte[1];
        deployCode[0] = 0x1;
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ZERO;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.OUT_OF_NRG, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertEquals(nrg, tx.getNrgConsume());

        Address contract = tx.getContractAddress();
        assertArrayEquals(new byte[0], result.getOutput());
        assertArrayEquals(new byte[0], repo.getCode(contract));
        assertEquals(BigInteger.ZERO, repo.getBalance(contract));
        assertEquals(BigInteger.ZERO, repo.getNonce(contract));

        assertEquals(BigInteger.ONE, repo.getNonce(deployer));
        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost), repo.getBalance(deployer));
    }

    @Test
    public void testTransferValueToNonPayableConstructor() throws IOException {
        String contractName = "EmptyContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;  // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.REVERT, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());   // all energy is not used up.

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, result, BigInteger.ZERO);
        checkStateOfDeployer(repo, summary, nrgPrice, BigInteger.ZERO);
    }

    @Test
    public void testTransferValueToPayableConstructor() throws IOException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TWO.pow(10);  // attempt to transfer value to new contract.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());   // all energy is not used up.

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, result, value);
        checkStateOfDeployer(repo, summary, nrgPrice, value);
    }

    @Test
    public void testTransferValueToPayableConstructorInsufficientFunds() throws IOException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = Builder.DEFAULT_BALANCE.add(BigInteger.ONE); // send too much value.
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.INSUFFICIENT_BALANCE, result.getResultCode());
        assertEquals(0, tx.getNrgConsume());
        assertEquals(0, result.getNrgLeft());

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, result, BigInteger.ZERO);
        checkStateOfDeployerOnBadDeploy(repo);
    }

    @Ignore
    public void testTransferValueToPayableConstructorVeryLargeAmount() throws IOException {
        String contractName = "PayableConstructor";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.TEN.pow(130); // requires more than 16 bytes!
        BigInteger nonce = BigInteger.ZERO;

        IRepositoryCache repo = blockchain.getRepository().startTracking();
        repo.addBalance(deployer, value);

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(BigInteger.ZERO, repo.getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);

        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        exec.execute();
    }

    @Test
    public void testConstructorIsCalledOnCodeDeployment() throws IOException {
        //TODO: how to call 'constructor'
        String contractName = "MultiFeatureContract";
        byte[] deployCode = ContractUtils.getContractDeployer("MultiFeatureContract.sol", "MultiFeatureContract");
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, result, value);
        checkStateOfDeployer(repo, summary, nrgPrice, value);

        // Now call the contract and check that the constructor message was set.
        String getMsgFunctionHash = "ce6d41de";
        nonce = nonce.add(BigInteger.ONE);
        tx = new AionTransaction(nonce.toByteArray(), contract, BigInteger.ZERO.toByteArray(),
            Hex.decode(getMsgFunctionHash), nrg, nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreation());

        context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        summary = exec.execute();
        result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());

        String expectedMsg = "Im alive!";
        assertEquals(expectedMsg, new String(extractOutput(result.getOutput())));
    }

    @Test
    public void testCallFunction() throws IOException {
        String contractName = "MultiFeatureContract";
        byte[] deployCode = getDeployCode(contractName);
        long nrg = 1_000_000;
        long nrgPrice = 1;
        BigInteger value = BigInteger.ONE;
        BigInteger nonce = BigInteger.ZERO;

        // to == null  signals that this is contract creation.
        AionTransaction tx = new AionTransaction(nonce.toByteArray(), null, value.toByteArray(),
            deployCode, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertTrue(tx.isContractCreation());

        assertEquals(Builder.DEFAULT_BALANCE, blockchain.getRepository().getBalance(deployer));
        assertEquals(BigInteger.ZERO, blockchain.getRepository().getNonce(deployer));

        BlockContext context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        IRepositoryCache repo = blockchain.getRepository().startTracking();
        TransactionExecutor exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        AionTxExecSummary summary = exec.execute();
        ExecutionResult result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());

        Address contract = tx.getContractAddress();
        checkStateOfNewContract(repo, contractName, contract, result, value);
        checkStateOfDeployer(repo, summary, nrgPrice, value);

        //             ---------- This command will perform addition. ----------
        int num = 53475374;
        byte[] input = ByteUtil.merge(Hex.decode("f601704f"), new DataWord(num).getData());
        input = ByteUtil.merge(input, new DataWord(1).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx = new AionTransaction(nonce.toByteArray(), contract, BigInteger.ZERO.toByteArray(),
            input, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreation());

        context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        summary = exec.execute();
        result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        int expectedResult = 1111 + (num & 0xFF);
        assertEquals(expectedResult, new DataWord(result.getOutput()).intValue());

        //             --------- This command will perform subtraction. ----------
        input = ByteUtil.merge(Hex.decode("f601704f"), new DataWord(num).getData());
        input = ByteUtil.merge(input, new DataWord(0).getData());
        nonce = nonce.add(BigInteger.ONE);
        tx = new AionTransaction(nonce.toByteArray(), contract, BigInteger.ZERO.toByteArray(),
            input, nrg, nrgPrice);
        tx.sign(deployerKey);
        assertFalse(tx.isContractCreation());

        context = blockchain.createNewBlockContext(blockchain.getBestBlock(),
            Collections.singletonList(tx), false);
        exec = new TransactionExecutor(tx, context.block, repo, LOGGER_VM);
        exec.setExecutorProvider(new TestVMProvider());
        summary = exec.execute();
        result = (ExecutionResult) exec.getResult();
        assertEquals(ResultCode.SUCCESS, result.getResultCode());
        assertEquals(nrg - tx.getNrgConsume(), result.getNrgLeft());
        assertNotEquals(nrg, tx.getNrgConsume());

        // Since input takes in uint8 we only want the last byte of num. Output size is well-defined
        // at 128 bits, or 16 bytes.
        expectedResult = 1111 - (num & 0xFF);
        assertEquals(expectedResult, new DataWord(result.getOutput()).intValue());
    }

    @Test
    public void testOverWithdrawFromContract() {
        //TODO
    }

    @Test
    public void testWithdrawFromContract() {
        //TODO
    }


    //<------------------------------------------HELPERS------------------------------------------->

    /**
     * Returns the deployment code to create the contract whose name is contractName and whose file
     * name is contractName.sol.
     */
    private byte[] getDeployCode(String contractName) throws IOException {
        String fileName = contractName + ".sol";
        return ContractUtils.getContractDeployer(fileName, contractName);
    }

    /**
     * Returns the code body of the contract whose name is contractName and whose file name is
     * contractName.sol.
     */
    private byte[] getBodyCode(String contractName) throws IOException {
        String filename = contractName + ".sol";
        return ContractUtils.getContractBody(filename, contractName);
    }

    /**
     * Checks that the newly deployed contract at address contractAddr is in the expected state
     * after the contract whose name is contractName is deployed to it.
     */
    private void checkStateOfNewContract(IRepositoryCache repo, String contractName,
        Address contractAddr, ExecutionResult result, BigInteger value) throws IOException {

        byte[] body = getBodyCode(contractName);
        if (result.getResultCode().equals(ResultCode.SUCCESS)) {
            assertArrayEquals(body, result.getOutput());
            assertArrayEquals(body, repo.getCode(contractAddr));
        } else {
            assertArrayEquals(new byte[0], result.getOutput());
            assertArrayEquals(new byte[0], repo.getCode(contractAddr));
        }
        assertEquals(value, repo.getBalance(contractAddr));
        assertEquals(BigInteger.ZERO, repo.getNonce(contractAddr));
    }

    /**
     * Checks the state of the deployer after a successful contract deployment. In this case we
     * expect the deployer's nonce to have incremented to one and their new balance to be equal to:
     *     D - UP - V
     *
     * D is default starting amount
     * U is energy used
     * P is energy price
     * V is value transferred
     */
    private void checkStateOfDeployer(IRepositoryCache repo, AionTxExecSummary summary, long nrgPrice,
        BigInteger value) {

        assertEquals(BigInteger.ONE, repo.getNonce(deployer));
        BigInteger txCost = summary.getNrgUsed().multiply(BigInteger.valueOf(nrgPrice));
        assertEquals(Builder.DEFAULT_BALANCE.subtract(txCost).subtract(value), repo.getBalance(deployer));
    }

    /**
     * Checks the state of the deployer after a failed attempt to deploy a contract. In this case
     * we expect the deployer's nonce to still be zero and their balance still default and unchanged.
     */
    private void checkStateOfDeployerOnBadDeploy(IRepositoryCache repo) {

        assertEquals(BigInteger.ZERO, repo.getNonce(deployer));
        assertEquals(Builder.DEFAULT_BALANCE, repo.getBalance(deployer));
    }

    private byte[] extractOutput(byte[] rawOutput) {
        int headerLen = new DataWord(Arrays.copyOfRange(rawOutput, 0, DataWord.BYTES)).intValue();
        int outputLen = new DataWord(
            Arrays.copyOfRange(rawOutput, (DataWord.BYTES * 2) - headerLen, DataWord.BYTES * 2)).
            intValue();
        byte[] output = new byte[outputLen];
        System.arraycopy(rawOutput, DataWord.BYTES * 2, output, 0, outputLen);
        return output;
    }

}

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
package org.aion.solidity;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;
import org.aion.contract.ContractUtils;
import org.aion.fastvm.TestUtils;
import org.aion.log.AionLoggerFactory;
import org.aion.log.LogEnum;
import org.aion.mcf.vm.types.DataWord;
import org.aion.solidity.Compiler.Options;
import org.aion.solidity.SolidityType.AddressType;
import org.aion.solidity.SolidityType.BoolType;
import org.aion.solidity.SolidityType.Bytes32Type;
import org.aion.solidity.SolidityType.BytesType;
import org.aion.solidity.SolidityType.DynamicArrayType;
import org.aion.solidity.SolidityType.IntType;
import org.aion.solidity.SolidityType.StaticArrayType;
import org.aion.solidity.SolidityType.StringType;
import org.aion.vm.DummyRepository;
import org.aion.fastvm.TransactionExecutor;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.aion.zero.types.AionTxReceipt;
import org.junit.Test;
import org.slf4j.Logger;

public class SolidityTypeTest {
    static final Logger LOGGER_VM = AionLoggerFactory.getLogger(LogEnum.VM.toString());

    private AionTransaction createTransaction(byte[] callData) {
        byte[] txNonce = DataWord.ZERO.getData();
        AionAddress from =
                AionAddress.wrap(
                        Hex.decode(
                                "1111111111111111111111111111111111111111111111111111111111111111"));
        AionAddress to =
                AionAddress.wrap(
                        Hex.decode(
                                "2222222222222222222222222222222222222222222222222222222222222222"));
        byte[] value = DataWord.ZERO.getData();
        byte[] data = callData;
        long nrg = new DataWord(100000L).longValue();
        long nrgPrice = DataWord.ONE.longValue();
        return new AionTransaction(txNonce, from, to, value, data, nrg, nrgPrice);
    }

    private DummyRepository createRepository(AionTransaction tx) throws IOException {
        Compiler.Result r =
                Compiler.getInstance()
                        .compile(ContractUtils.readContract("SolidityType.sol"), Options.BIN);
        CompilationResult cr = CompilationResult.parse(r.output);
        String deployer = cr.contracts.get("SolidityType").bin;
        String contract = deployer.substring(deployer.indexOf("60506040", 1));

        DummyRepository repo = new DummyRepository();
        repo.addContract(tx.getDestinationAddress(), Hex.decode(contract));
        repo.addBalance(tx.getSenderAddress(), tx.nrgPrice().value().multiply(BigInteger.valueOf(500_000L)));

        return repo;
    }

    @Test
    public void testBool() throws IOException {
        byte[] params = new BoolType().encode(true);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("e8dde232"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertEquals(Boolean.TRUE, new BoolType().decode(receipt.getTransactionOutput()));
    }

    @Test
    public void testInt() throws IOException {
        BigInteger bi = new BigInteger(1, Hex.decode("ffffffffffffffffffffffff"));
        byte[] params = new IntType("int96").encode(bi);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("6761755c"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(
                Hex.decode("00000000ffffffffffffffffffffffff"), receipt.getTransactionOutput());
        assertEquals(bi, new IntType("int96").decode(receipt.getTransactionOutput()));
    }

    @Test
    public void testAddress() throws IOException {
        byte[] x = Hex.decode("1122334455667788112233445566778811223344556677881122334455667788");
        byte[] params = new AddressType().encode(x);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("42f45790"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertArrayEquals(x, (byte[]) new AddressType().decode(receipt.getTransactionOutput()));
    }

    @Test
    public void testFixedBytes1() throws IOException {
        byte[] x = Hex.decode("1122334455");
        SolidityType type = new Bytes32Type("bytes5");
        byte[] params = type.encode(x);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("faa068d1"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertArrayEquals(x, (byte[]) type.decode(receipt.getTransactionOutput()));
    }

    @Test
    public void testFixedBytes2() throws IOException {
        byte[] x = Hex.decode("1122334455667788112233445566778811223344");
        SolidityType type = new Bytes32Type("bytes20");
        byte[] params = type.encode(x);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("877b277f"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertArrayEquals(x, (byte[]) type.decode(receipt.getTransactionOutput()));
    }

    @Test
    public void testString1() throws IOException {
        String x = "hello, world!";
        SolidityType type = new StringType();
        byte[] params =
                ByteUtil.merge(Hex.decode("00000000000000000000000000000010"), type.encode(x));
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("61cb5a01"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertEquals(x, type.decode(receipt.getTransactionOutput(), 16));
    }

    @Test
    public void testString2() throws IOException {
        String x = "hello, world!hello, world!hello, world!hello, world!hello, world!hello, world!";
        SolidityType type = new StringType();
        byte[] params =
                ByteUtil.merge(Hex.decode("00000000000000000000000000000010"), type.encode(x));
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("61cb5a01"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertEquals(x, type.decode(receipt.getTransactionOutput(), 16));
    }

    @Test
    public void testBytes1() throws IOException {
        byte[] x = Hex.decode("1122334455667788");
        SolidityType type = new BytesType();
        byte[] params =
                ByteUtil.merge(Hex.decode("00000000000000000000000000000010"), type.encode(x));
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("61cb5a01"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertArrayEquals(x, (byte[]) type.decode(receipt.getTransactionOutput(), 16));
    }

    @Test
    public void testBytes2() throws IOException {
        byte[] x =
                Hex.decode(
                        "11223344556677881122334455667788112233445566778811223344556677881122334455667788");
        SolidityType type = new BytesType();
        byte[] params =
                ByteUtil.merge(Hex.decode("00000000000000000000000000000010"), type.encode(x));
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("61cb5a01"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());
        assertArrayEquals(x, (byte[]) type.decode(receipt.getTransactionOutput(), 16));
    }

    @Test
    public void testStaticArray1() throws IOException {
        List<BigInteger> x = new ArrayList<>();
        x.add(BigInteger.valueOf(1L));
        x.add(BigInteger.valueOf(2L));
        x.add(BigInteger.valueOf(3L));

        SolidityType type = new StaticArrayType("uint16[3]");
        byte[] params = type.encode(x);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("97e934e2"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());

        Object[] decoded = (Object[]) type.decode(receipt.getTransactionOutput());
        for (Object d : decoded) {
            System.out.println(d);
        }
    }

    @Test
    public void testStaticArray2() throws IOException {
        List<byte[]> x = new ArrayList<>();
        x.add(Hex.decode("1122334455667788112233445566778811223344"));
        x.add(Hex.decode("2122334455667788112233445566778811223344"));
        x.add(Hex.decode("3122334455667788112233445566778811223344"));

        SolidityType type = new StaticArrayType("bytes20[3]");
        byte[] params = type.encode(x);
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("e4bef5c9"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());

        Object[] decoded = (Object[]) type.decode(receipt.getTransactionOutput());
        for (Object d : decoded) {
            System.out.println(Hex.toHexString((byte[]) d));
        }
    }

    @Test
    public void testDynamicArray1() throws IOException {
        List<BigInteger> x = new ArrayList<>();
        x.add(BigInteger.valueOf(1L));
        x.add(BigInteger.valueOf(2L));
        x.add(BigInteger.valueOf(3L));

        SolidityType type = new DynamicArrayType("uint16[]");
        byte[] params =
                ByteUtil.merge(Hex.decode("00000000000000000000000000000010"), type.encode(x));
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("8c0c5523"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());

        Object[] decoded = (Object[]) type.decode(receipt.getTransactionOutput(), 16);
        for (Object d : decoded) {
            System.out.println(d);
        }
    }

    @Test
    public void testDynamicArray2() throws IOException {
        List<byte[]> x = new ArrayList<>();
        x.add(Hex.decode("1122334455667788112233445566778811223344"));
        x.add(Hex.decode("2122334455667788112233445566778811223344"));
        x.add(Hex.decode("3122334455667788112233445566778811223344"));

        SolidityType type = new DynamicArrayType("bytes20[]");
        byte[] params =
                ByteUtil.merge(Hex.decode("00000000000000000000000000000010"), type.encode(x));
        System.out.println(Hex.toHexString(params));

        AionTransaction tx = createTransaction(ByteUtil.merge(Hex.decode("97c3b2db"), params));
        AionBlock block = TestUtils.createDummyBlock();
        DummyRepository repo = createRepository(tx);

        TransactionExecutor exec = new TransactionExecutor(tx, block, repo, LOGGER_VM);
        AionTxReceipt receipt = exec.execute().getReceipt();
        System.out.println(receipt);

        assertArrayEquals(params, receipt.getTransactionOutput());

        Object[] decoded = (Object[]) type.decode(receipt.getTransactionOutput(), 16);
        for (Object d : decoded) {
            System.out.println(d);
        }
    }
}

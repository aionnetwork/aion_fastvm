package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.Address;
import org.aion.util.bytes.ByteUtil;
import org.aion.interfaces.vm.DataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.type.PrecompiledContract;
import org.aion.vm.api.interfaces.TransactionContext;
import org.aion.vm.api.interfaces.TransactionResult;
import org.aion.zero.types.AionInternalTx;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;

/**
 * This class handles all callbacks from the JIT side. It is not thread-safe and should be
 * synchronized for parallel execution.
 *
 * <p>All methods are static for better JNI performance.
 *
 * @author yulong
 */
public class Callback {

    private static LinkedList<Pair<TransactionContext, KernelInterfaceForFastVM>> stack =
            new LinkedList<>();

    /**
     * Pushes a pair of context and repository into the callback stack.
     *
     * @param pair
     */
    public static void push(Pair<TransactionContext, KernelInterfaceForFastVM> pair) {
        stack.push(pair);
    }

    /** Pops the last <context, repository> pair */
    public static void pop() {
        stack.pop();
    }

    /**
     * Returns the current context.
     *
     * @return
     */
    public static TransactionContext context() {
        return stack.peek().getLeft();
    }

    /**
     * Returns the current repository.
     *
     * @return
     */
    public static KernelInterfaceForFastVM kernelRepo() {
        return stack.peek().getRight();
    }

    /**
     * Returns the hash of the given block.
     *
     * @param number
     * @return
     */
    public static byte[] getBlockHash(long number) {
        byte[] hash = kernelRepo().getBlockHashByNumber(number);
        return hash == null ? new byte[32] : hash;
    }

    /**
     * Returns the code of a contract.
     *
     * @param address
     * @return
     */
    public static byte[] getCode(byte[] address) {
        byte[] code = kernelRepo().getCode(Address.wrap(address));
        return code == null ? new byte[0] : code;
    }

    /**
     * Returns the balance of an account.
     *
     * @param address
     * @return
     */
    public static byte[] getBalance(byte[] address) {
        BigInteger balance = kernelRepo().getBalance(Address.wrap(address));
        return balance == null ? DataWordImpl.ZERO.getData() : new DataWordImpl(balance).getData();
    }

    /**
     * Returns whether an account exists.
     *
     * @param address
     * @return
     */
    public static boolean exists(byte[] address) {
        return kernelRepo().hasAccountState(Address.wrap(address));
    }

    /**
     * Returns the value that is mapped to the given key.
     *
     * @param address
     * @param key
     * @return
     */
    public static byte[] getStorage(byte[] address, byte[] key) {
        // System.err.println("GET_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + (value == null ?
        // "":Hex.toHexString(value.getData())));

        return kernelRepo().getStorage(Address.wrap(address), key);
    }

    /**
     * Sets the value that is mapped to the given key.
     *
     * @param address
     * @param key
     * @param value
     */
    public static void putStorage(byte[] address, byte[] key, byte[] value) {

        // System.err.println("PUT_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + Hex.toHexString(value));

        if (value == null || value.length == 0 || isZero(value)) {
            kernelRepo().removeStorage(Address.wrap(address), key);
        } else {
            kernelRepo().putStorage(Address.wrap(address), key, value);
        }
    }

    private static boolean isZero(byte[] value) {
        int length = value.length;
        for (int i = 0; i < length; i++) {
            if (value[length - 1 - i] != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Processes SELFDESTRUCT opcode.
     *
     * @param owner
     * @param beneficiary
     */
    public static void selfDestruct(byte[] owner, byte[] beneficiary) {
        BigInteger balance = kernelRepo().getBalance(Address.wrap(owner));

        // add internal transaction
        AionInternalTx internalTx =
                newInternalTx(
                        Address.wrap(owner),
                        Address.wrap(beneficiary),
                        kernelRepo().getNonce(Address.wrap(owner)),
                        new DataWordImpl(balance),
                        ByteUtil.EMPTY_BYTE_ARRAY,
                        "selfdestruct");
        context().getSideEffects().addInternalTransaction(internalTx);

        // transfer
        kernelRepo().adjustBalance(Address.wrap(owner), balance.negate());
        if (!Arrays.equals(owner, beneficiary)) {
            kernelRepo().adjustBalance(Address.wrap(beneficiary), balance);
        }

        context().getSideEffects().addToDeletedAddresses(Address.wrap(owner));
    }

    /**
     * Processes LOG opcode.
     *
     * @param address
     * @param topics
     * @param data
     */
    public static void log(byte[] address, byte[] topics, byte[] data) {
        List<byte[]> list = new ArrayList<>();

        for (int i = 0; i < topics.length; i += 32) {
            byte[] t = Arrays.copyOfRange(topics, i, i + 32);
            list.add(t);
        }

        context().getSideEffects().addLog(new Log(Address.wrap(address), list, data));
    }

    /**
     * This method only exists so that FastVM and ContractFactory can be mocked for testing. This
     * method was formerly called call and now the call method simply invokes this method with new
     * instances of the fast vm and contract factory.
     */
    static byte[] performCall(byte[] message, FastVM vm, ContractFactory factory) {
        ExecutionContext ctx = parseMessage(message);

        // check call stack depth
        if (ctx.getTransactionStackDepth() >= Constants.MAX_CALL_DEPTH) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0).toBytes();
        }

        // check value
        BigInteger endowment = ctx.getTransferValue();
        BigInteger callersBalance = kernelRepo().getBalance(ctx.getSenderAddress());
        if (callersBalance.compareTo(endowment) < 0) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0).toBytes();
        }

        // call sub-routine
        TransactionResult result;
        if (ctx.getTransactionKind() == ExecutionContext.CREATE) {
            result = doCreate(ctx, vm);
        } else {
            result = doCall(ctx, vm, factory);
        }

        // merge the effects
        if (result.getResultCode().toInt() == FastVmResultCode.SUCCESS.toInt()) {
            context().getSideEffects().merge(ctx.getSideEffects());
        } else {
            context()
                    .getSideEffects()
                    .addInternalTransactions(ctx.getSideEffects().getInternalTransactions());
        }

        return result.toBytes();
    }

    /**
     * Process CALL/CALLCODE/DELEGATECALL/CREATE opcode.
     *
     * @param message
     * @return
     */
    public static byte[] call(byte[] message) {
        return performCall(message, new FastVM(), new ContractFactory());
    }

    /**
     * The method handles the CALL/CALLCODE/DELEGATECALL opcode.
     *
     * @param ctx
     * @return
     */
    private static TransactionResult doCall(
            TransactionContext ctx, FastVM jit, ContractFactory factory) {
        Address codeAddress = ctx.getDestinationAddress();
        if (ctx.getTransactionKind() == ExecutionContext.CALLCODE
                || ctx.getTransactionKind() == ExecutionContext.DELEGATECALL) {
            ctx.setDestinationAddress(context().getDestinationAddress());
        }

        // Check that the destination address is safe to call from this VM.
        if (!kernelRepo().destinationAddressIsSafeForThisVM(codeAddress)) {
            return new FastVmTransactionResult(
                    FastVmResultCode.INCOMPATIBLE_CONTRACT_CALL, ctx.getTransactionEnergy());
        }

        KernelInterfaceForFastVM track = kernelRepo().makeChildKernelInterface();
        TransactionResult result =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, ctx.getTransactionEnergy());

        // add internal transaction
        AionInternalTx internalTx =
                newInternalTx(
                        ctx.getSenderAddress(),
                        ctx.getDestinationAddress(),
                        track.getNonce(ctx.getSenderAddress()),
                        new DataWordImpl(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        "call");
        context().getSideEffects().addInternalTransaction(internalTx);
        ctx.setTransactionHash(internalTx.getTransactionHash());

        // transfer balance
        if (ctx.getTransactionKind() != ExecutionContext.DELEGATECALL
                && ctx.getTransactionKind() != ExecutionContext.CALLCODE) {
            BigInteger transferAmount = ctx.getTransferValue();
            track.adjustBalance(ctx.getSenderAddress(), transferAmount.negate());
            track.adjustBalance(ctx.getDestinationAddress(), transferAmount);
        }

        PrecompiledContract pc = factory.getPrecompiledContract(ctx, track);
        if (pc != null) {
            result = pc.execute(ctx.getTransactionData(), ctx.getTransactionEnergy());
        } else {
            // get the code
            byte[] code =
                    track.hasAccountState(codeAddress)
                            ? track.getCode(codeAddress)
                            : ByteUtil.EMPTY_BYTE_ARRAY;

            // execute transaction
            if (ArrayUtils.isNotEmpty(code)) {
                result = jit.run(code, ctx, track);
            }
        }

        // post execution
        if (result.getResultCode().toInt() != FastVmResultCode.SUCCESS.toInt()) {
            internalTx.markAsRejected();
            ctx.getSideEffects().markAllInternalTransactionsAsRejected(); // reject all

            track.rollback();
        } else {
            track.commit();
        }

        return result;
    }

    /**
     * This method handles the CREATE opcode.
     *
     * @param ctx execution context
     * @return
     */
    private static FastVmTransactionResult doCreate(ExecutionContext ctx, FastVM jit) {
        KernelInterfaceForFastVM track = kernelRepo().makeChildKernelInterface();
        FastVmTransactionResult result =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, ctx.getTransactionEnergy());

        // compute new address
        byte[] nonce = track.getNonce(ctx.getSenderAddress()).toByteArray();
        Address newAddress =
                Address.wrap(HashUtil.calcNewAddr(ctx.getSenderAddress().toBytes(), nonce));
        ctx.setDestinationAddress(newAddress);

        // add internal transaction
        // TODO: should the `to` address be null?
        AionInternalTx internalTx =
                newInternalTx(
                        ctx.getSenderAddress(),
                        ctx.getDestinationAddress(),
                        track.getNonce(ctx.getSenderAddress()),
                        new DataWordImpl(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        "create");
        context().getSideEffects().addInternalTransaction(internalTx);
        ctx.setTransactionHash(internalTx.getTransactionHash());

        // in case of hashing collisions
        boolean alreadyExsits = track.hasAccountState(newAddress);
        BigInteger oldBalance = track.getBalance(newAddress);
        track.createAccount(newAddress);
        track.incrementNonce(newAddress); // EIP-161
        track.adjustBalance(newAddress, oldBalance);

        // transfer balance
        BigInteger transferAmount = ctx.getTransferValue();
        track.adjustBalance(ctx.getSenderAddress(), transferAmount.negate());
        track.adjustBalance(newAddress, transferAmount);

        // update nonce
        track.incrementNonce(ctx.getSenderAddress());

        // add internal transaction
        internalTx =
                newInternalTx(
                        ctx.getSenderAddress(),
                        null,
                        track.getNonce(ctx.getSenderAddress()),
                        new DataWordImpl(ctx.getTransferValue()),
                        ctx.getTransactionData(),
                        "create");
        ctx.getSideEffects().addInternalTransaction(internalTx);

        // execute transaction
        if (alreadyExsits) {
            result.setResultCodeAndEnergyRemaining(FastVmResultCode.FAILURE, 0);
        } else {
            if (ArrayUtils.isNotEmpty(ctx.getTransactionData())) {
                result = jit.run(ctx.getTransactionData(), ctx, track);
            }
        }

        // post execution
        if (result.getResultCode().toInt() != FastVmResultCode.SUCCESS.toInt()) {
            internalTx.markAsRejected();
            ctx.getSideEffects().markAllInternalTransactionsAsRejected(); // reject all

            track.rollback();
        } else {
            // charge the codedeposit
            if (result.getEnergyRemaining() < Constants.NRG_CODE_DEPOSIT) {
                result.setResultCodeAndEnergyRemaining(FastVmResultCode.FAILURE, 0);
                return result;
            }
            byte[] code = result.getReturnData();
            track.putCode(newAddress, code == null ? new byte[0] : code);

            result.setReturnData(newAddress.toBytes());

            track.commit();
        }

        return result;
    }

    /**
     * Parses the execution context from encoded message.
     *
     * @param message
     * @return
     */
    protected static ExecutionContext parseMessage(byte[] message) {
        TransactionContext prev = context();

        ByteBuffer buffer = ByteBuffer.wrap(message);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] txHash = prev.getTransactionHash();

        byte[] address = new byte[Address.SIZE];
        buffer.get(address);
        Address origin = prev.getOriginAddress();
        byte[] caller = new byte[Address.SIZE];
        buffer.get(caller);

        DataWord nrgPrice = new DataWordImpl(prev.getTransactionEnergyPrice());
        long nrgLimit = buffer.getLong();
        byte[] buf = new byte[16];
        buffer.get(buf);
        DataWordImpl callValue = new DataWordImpl(buf);
        byte[] callData = new byte[buffer.getInt()];
        buffer.get(callData);

        int depth = buffer.getInt();
        int kind = buffer.getInt();
        int flags = buffer.getInt();

        Address blockCoinbase = prev.getMinerAddress();
        long blockNumber = prev.getBlockNumber();
        long blockTimestamp = prev.getBlockTimestamp();
        long blockNrgLimit = prev.getBlockEnergyLimit();
        DataWord blockDifficulty = new DataWordImpl(prev.getBlockDifficulty());

        // TODO: properly construct a transaction first
        return new ExecutionContext(
                null,
                txHash,
                Address.wrap(address),
                origin,
                Address.wrap(caller),
                nrgPrice,
                nrgLimit,
                callValue,
                callData,
                depth,
                kind,
                flags,
                blockCoinbase,
                blockNumber,
                blockTimestamp,
                blockNrgLimit,
                blockDifficulty);
    }

    /** Creates a new internal transaction. */
    private static AionInternalTx newInternalTx(
            Address from, Address to, BigInteger nonce, DataWord value, byte[] data, String note) {
        byte[] parentHash = context().getTransactionHash();
        int depth = context().getTransactionStackDepth();
        int index = context().getSideEffects().getInternalTransactions().size();

        return new AionInternalTx(
                parentHash,
                depth,
                index,
                new DataWordImpl(nonce).getData(),
                from,
                to,
                value.getData(),
                data,
                note);
    }
}

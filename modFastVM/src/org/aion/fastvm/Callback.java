package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.aion.types.AionAddress;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.InternalTransaction.RejectedStatus;
import org.aion.types.Log;
import org.aion.util.bytes.ByteUtil;
import org.aion.mcf.vm.DataWord;
import org.aion.crypto.HashUtil;
import org.aion.base.Constants;
import org.aion.types.InternalTransaction;
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

    private static LinkedList<Pair<ExecutionContext, IExternalStateForFvm>> stack =
            new LinkedList<>();

    public static boolean stackIsEmpty() {
        return stack.isEmpty();
    }

    /** Pushes a pair of context and repository into the callback stack. */
    public static void push(Pair<ExecutionContext, IExternalStateForFvm> pair) {
        stack.push(pair);
    }

    /** Pops the last <context, repository> pair */
    public static void pop() {
        stack.pop();
    }

    /** Returns the current context. */
    public static ExecutionContext context() {
        return stack.peek().getLeft();
    }

    /** Returns the current repository. */
    public static IExternalStateForFvm externalState() {
        return stack.peek().getRight();
    }

    /** Returns the hash of the given block. */
    public static byte[] getBlockHash(long number) {
        byte[] hash = externalState().getBlockHashByNumber(number);
        return hash == null ? new byte[32] : hash;
    }

    /** Returns the code of a contract. */
    public static byte[] getCode(byte[] address) {
        byte[] code = externalState().getCode(new AionAddress(address));
        return code == null ? new byte[0] : code;
    }

    /** Returns the balance of an account. */
    public static byte[] getBalance(byte[] address) {
        BigInteger balance = externalState().getBalance(new AionAddress(address));
        return balance == null ? DataWordImpl.ZERO.getData() : new DataWordImpl(balance).getData();
    }

    /** Returns whether an account exists. */
    public static boolean exists(byte[] address) {
        return externalState().hasAccountState(new AionAddress(address));
    }

    /** Returns the value that is mapped to the given key. */
    public static byte[] getStorage(byte[] address, byte[] key) {
        // System.err.println("GET_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + (value == null ?
        // "":Hex.toHexString(value.getData())));

        return externalState().getStorageValue(new AionAddress(address), key);
    }

    /** Sets the value that is mapped to the given key. */
    public static void putStorage(byte[] address, byte[] key, byte[] value) {

        // System.err.println("PUT_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + Hex.toHexString(value));

        if (value == null || value.length == 0 || isZero(value)) {
            externalState().removeStorage(new AionAddress(address), key);
        } else {
            externalState().addStorageValue(new AionAddress(address), key, value);
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
     */
    public static void selfDestruct(byte[] sender, byte[] destination) {
        BigInteger balance = externalState().getBalance(new AionAddress(sender));

        // add internal transaction
        InternalTransaction internalTx =
                InternalTransaction.contractCallTransaction(
                        RejectedStatus.NOT_REJECTED,
                        new AionAddress(sender),
                        new AionAddress(destination),
                        externalState().getNonce(new AionAddress(sender)),
                        balance,
                        ByteUtil.EMPTY_BYTE_ARRAY,
                        0L,
                        1L);
        context().getSideEffects().addInternalTransaction(internalTx);

        // transfer
        externalState().addBalance(new AionAddress(sender), balance.negate());
        if (!Arrays.equals(sender, destination)) {
            externalState().addBalance(new AionAddress(destination), balance);
        }

        context().getSideEffects().addToDeletedAddresses(new AionAddress(sender));
    }

    /** Processes LOG opcode. */
    public static void log(byte[] address, byte[] topics, byte[] data) {
        List<byte[]> list = new ArrayList<>();

        for (int i = 0; i < topics.length; i += 32) {
            byte[] t = Arrays.copyOfRange(topics, i, i + 32);
            list.add(t);
        }

        context().getSideEffects().addLog(Log.topicsAndData(address, list, data));
    }

    /**
     * This method only exists so that FastVM and ContractFactory can be mocked for testing. This
     * method was formerly called call and now the call method simply invokes this method with new
     * instances of the fast vm and contract factory.
     */
    static byte[] performCall(byte[] message, FastVM vm) {
        ExecutionContext ctx = parseMessage(message);

        // check call stack depth
        if (ctx.getTransactionStackDepth() >= Constants.MAX_CALL_DEPTH) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0).toBytes();
        }

        // check value
        BigInteger endowment = ctx.getTransferValue();
        BigInteger callersBalance = externalState().getBalance(ctx.getSenderAddress());
        if (callersBalance.compareTo(endowment) < 0) {
            return new FastVmTransactionResult(FastVmResultCode.FAILURE, 0).toBytes();
        }

        // call sub-routine
        FastVmTransactionResult result;
        if (ctx.getTransactionKind() == ExecutionContext.CREATE) {
            result = doCreate(ctx, vm);
        } else {
            result = doCall(ctx, vm);
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

    /** Process CALL/CALLCODE/DELEGATECALL/CREATE opcode. */
    public static byte[] call(byte[] message) {
        return performCall(message, new FastVM());
    }

    /** The method handles the CALL/CALLCODE/DELEGATECALL opcode. */
    private static FastVmTransactionResult doCall(ExecutionContext ctx, FastVM jit) {
        AionAddress codeAddress = ctx.getDestinationAddress();
        if (ctx.getTransactionKind() == ExecutionContext.CALLCODE
                || ctx.getTransactionKind() == ExecutionContext.DELEGATECALL) {
            ctx.setDestinationAddress(context().getDestinationAddress());
        }

        // Check that the destination address is safe to call from this VM.
        if (!externalState().destinationAddressIsSafeForFvm(codeAddress)) {
            return new FastVmTransactionResult(
                    FastVmResultCode.INCOMPATIBLE_CONTRACT_CALL, ctx.getTransactionEnergy());
        }

        IExternalStateForFvm childState = externalState().newChildExternalState();
        FastVmTransactionResult result =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, ctx.getTransactionEnergy());

        // add internal transaction
        InternalTransaction internalTx =
                InternalTransaction.contractCallTransaction(
                        RejectedStatus.NOT_REJECTED,
                        ctx.getSenderAddress(),
                        ctx.getDestinationAddress(),
                        childState.getNonce(ctx.getSenderAddress()),
                        ctx.getTransferValue(),
                        ctx.getTransactionData(),
                        0L,
                        1L);
        context().getSideEffects().addInternalTransaction(internalTx);

        // transfer balance
        if (ctx.getTransactionKind() != ExecutionContext.DELEGATECALL
                && ctx.getTransactionKind() != ExecutionContext.CALLCODE) {
            BigInteger transferAmount = ctx.getTransferValue();
            childState.addBalance(ctx.getSenderAddress(), transferAmount.negate());
            childState.addBalance(ctx.getDestinationAddress(), transferAmount);
        }

        if (childState.isPrecompiledContract(ctx.getDestinationAddress())) {

            result = childState.runInternalPrecompiledContractCall(ctx);

        } else {
            // get the code
            byte[] code =
                    childState.hasAccountState(codeAddress)
                            ? childState.getCode(codeAddress)
                            : ByteUtil.EMPTY_BYTE_ARRAY;

            // execute transaction
            if (ArrayUtils.isNotEmpty(code)) {
                result = jit.run(code, ctx, childState);
            }
        }

        // post execution
        if (result.getResultCode().toInt() != FastVmResultCode.SUCCESS.toInt()) {
            context().getSideEffects().markMostRecentInternalTransactionAsRejected();
            ctx.getSideEffects().markAllInternalTransactionsAsRejected(); // reject all

            childState.rollback();
        } else {
            childState.commit();
        }

        return result;
    }

    /**
     * This method handles the CREATE opcode.
     *
     * @param ctx execution context
     */
    private static FastVmTransactionResult doCreate(ExecutionContext ctx, FastVM jit) {
        IExternalStateForFvm childState = externalState().newChildExternalState();
        FastVmTransactionResult result =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, ctx.getTransactionEnergy());

        // compute new address
        byte[] nonce = childState.getNonce(ctx.getSenderAddress()).toByteArray();
        AionAddress newAddress =
                new AionAddress(HashUtil.calcNewAddr(ctx.getSenderAddress().toByteArray(), nonce));
        ctx.setDestinationAddress(newAddress);

        // add internal transaction
        InternalTransaction internalTx =
                InternalTransaction.contractCreateTransaction(
                        RejectedStatus.NOT_REJECTED,
                        ctx.getSenderAddress(),
                        childState.getNonce(ctx.getSenderAddress()),
                        ctx.getTransferValue(),
                        ctx.getTransactionData(),
                        0L,
                        1L);
        context().getSideEffects().addInternalTransaction(internalTx);

        // in case of hashing collisions
        boolean alreadyExsits = childState.hasAccountState(newAddress);

        if (childState.isFork040enabled()) {
            byte[] code = childState.getCode(newAddress);
            if (code == null || code.length == 0) {
                alreadyExsits = false;
            }
        }

        BigInteger oldBalance = childState.getBalance(newAddress);
        childState.createAccount(newAddress);
        childState.incrementNonce(newAddress); // EIP-161
        childState.addBalance(newAddress, oldBalance);

        // transfer balance
        BigInteger transferAmount = ctx.getTransferValue();
        childState.addBalance(ctx.getSenderAddress(), transferAmount.negate());
        childState.addBalance(newAddress, transferAmount);

        // update nonce
        childState.incrementNonce(ctx.getSenderAddress());

        // add internal transaction
        internalTx =
                InternalTransaction.contractCreateTransaction(
                        RejectedStatus.NOT_REJECTED,
                        ctx.getSenderAddress(),
                        childState.getNonce(ctx.getSenderAddress()),
                        ctx.getTransferValue(),
                        ctx.getTransactionData(),
                        0L,
                        1L);
        ctx.getSideEffects().addInternalTransaction(internalTx);

        // execute transaction
        if (alreadyExsits) {
            result.setResultCodeAndEnergyRemaining(FastVmResultCode.FAILURE, 0);
        } else {
            if (ArrayUtils.isNotEmpty(ctx.getTransactionData())) {
                result = jit.run(ctx.getTransactionData(), ctx, childState);
            }
        }

        // post execution
        if (result.getResultCode().toInt() != FastVmResultCode.SUCCESS.toInt()) {
            context().getSideEffects().markMostRecentInternalTransactionAsRejected();
            ctx.getSideEffects().markAllInternalTransactionsAsRejected(); // reject all

            childState.rollback();
        } else {
            // charge the codedeposit
            if (result.getEnergyRemaining() < Constants.NRG_CODE_DEPOSIT) {
                result.setResultCodeAndEnergyRemaining(FastVmResultCode.FAILURE, 0);
                context().getSideEffects().markMostRecentInternalTransactionAsRejected();
                ctx.getSideEffects().markAllInternalTransactionsAsRejected(); // reject all

                childState.rollback();
                return result;
            }
            byte[] code = result.getReturnData();
            childState.putCode(newAddress, code == null ? new byte[0] : code);

            result.setReturnData(newAddress.toByteArray());

            childState.commit();
        }

        return result;
    }

    /** Parses the execution context from encoded message. */
    protected static ExecutionContext parseMessage(byte[] message) {
        ExecutionContext prev = context();

        ByteBuffer buffer = ByteBuffer.wrap(message);
        buffer.order(ByteOrder.BIG_ENDIAN);

        byte[] txHash = prev.getTransactionHash();

        byte[] address = new byte[AionAddress.LENGTH];
        buffer.get(address);
        AionAddress origin = prev.getOriginAddress();
        byte[] caller = new byte[AionAddress.LENGTH];
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

        AionAddress blockCoinbase = prev.getMinerAddress();
        long blockNumber = prev.getBlockNumber();
        long blockTimestamp = prev.getBlockTimestamp();
        long blockNrgLimit = prev.getBlockEnergyLimit();
        DataWord blockDifficulty = new DataWordImpl(prev.getBlockDifficulty());

        // TODO: properly construct a transaction first
        return new ExecutionContext(
                null,
                txHash,
                new AionAddress(address),
                origin,
                new AionAddress(caller),
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
}

package org.aion.fastvm;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.aion.precompiled.PrecompiledResultCode;
import org.aion.precompiled.PrecompiledTransactionResult;
import org.aion.precompiled.type.PrecompiledTransactionContext;
import org.aion.types.AionAddress;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.util.bytes.ByteUtil;
import org.aion.interfaces.vm.DataWord;
import org.aion.crypto.HashUtil;
import org.aion.mcf.vm.Constants;
import org.aion.mcf.vm.types.KernelInterfaceForFastVM;
import org.aion.mcf.vm.types.Log;
import org.aion.precompiled.ContractFactory;
import org.aion.precompiled.type.PrecompiledContract;
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

    private static LinkedList<Pair<ExecutionContext, KernelInterfaceForFastVM>> stack =
            new LinkedList<>();

    /** Pushes a pair of context and repository into the callback stack. */
    public static void push(Pair<ExecutionContext, KernelInterfaceForFastVM> pair) {
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
    public static KernelInterfaceForFastVM kernelRepo() {
        return stack.peek().getRight();
    }

    /** Returns the hash of the given block. */
    public static byte[] getBlockHash(long number) {
        byte[] hash = kernelRepo().getBlockHashByNumber(number);
        return hash == null ? new byte[32] : hash;
    }

    /** Returns the code of a contract. */
    public static byte[] getCode(byte[] address) {
        byte[] code = kernelRepo().getCode(new AionAddress(address));
        return code == null ? new byte[0] : code;
    }

    /** Returns the balance of an account. */
    public static byte[] getBalance(byte[] address) {
        BigInteger balance = kernelRepo().getBalance(new AionAddress(address));
        return balance == null ? DataWordImpl.ZERO.getData() : new DataWordImpl(balance).getData();
    }

    /** Returns whether an account exists. */
    public static boolean exists(byte[] address) {
        return kernelRepo().hasAccountState(new AionAddress(address));
    }

    /** Returns the value that is mapped to the given key. */
    public static byte[] getStorage(byte[] address, byte[] key) {
        // System.err.println("GET_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + (value == null ?
        // "":Hex.toHexString(value.getData())));

        return kernelRepo().getStorage(new AionAddress(address), key);
    }

    /** Sets the value that is mapped to the given key. */
    public static void putStorage(byte[] address, byte[] key, byte[] value) {

        // System.err.println("PUT_STORAGE: address = " + Hex.toHexString(address) + ", key = " +
        // Hex.toHexString(key) + ", value = " + Hex.toHexString(value));

        if (value == null || value.length == 0 || isZero(value)) {
            kernelRepo().removeStorage(new AionAddress(address), key);
        } else {
            kernelRepo().putStorage(new AionAddress(address), key, value);
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

    /** Processes SELFDESTRUCT opcode. */
    public static void selfDestruct(byte[] owner, byte[] beneficiary) {
        BigInteger balance = kernelRepo().getBalance(new AionAddress(owner));

        // add internal transaction
        AionInternalTx internalTx =
                newInternalTx(
                        new AionAddress(owner),
                        new AionAddress(beneficiary),
                        kernelRepo().getNonce(new AionAddress(owner)),
                        new DataWordImpl(balance),
                        ByteUtil.EMPTY_BYTE_ARRAY,
                        "selfdestruct");
        context().getSideEffects().addInternalTransaction(internalTx);

        // transfer
        kernelRepo().adjustBalance(new AionAddress(owner), balance.negate());
        if (!Arrays.equals(owner, beneficiary)) {
            kernelRepo().adjustBalance(new AionAddress(beneficiary), balance);
        }

        context().getSideEffects().addToDeletedAddresses(new AionAddress(owner));
    }

    /** Processes LOG opcode. */
    public static void log(byte[] address, byte[] topics, byte[] data) {
        List<byte[]> list = new ArrayList<>();

        for (int i = 0; i < topics.length; i += 32) {
            byte[] t = Arrays.copyOfRange(topics, i, i + 32);
            list.add(t);
        }

        context().getSideEffects().addLog(new Log(new AionAddress(address), list, data));
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
        FastVmTransactionResult result;
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

    /** Process CALL/CALLCODE/DELEGATECALL/CREATE opcode. */
    public static byte[] call(byte[] message) {
        return performCall(message, new FastVM(), new ContractFactory());
    }

    /** The method handles the CALL/CALLCODE/DELEGATECALL opcode. */
    private static FastVmTransactionResult doCall(
            ExecutionContext ctx, FastVM jit, ContractFactory factory) {
        AionAddress codeAddress = ctx.getDestinationAddress();
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
        FastVmTransactionResult result =
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

        PrecompiledContract pc =
                factory.getPrecompiledContract(toPrecompiledTransactionContext(ctx), track);
        if (pc != null) {
            result =
                    precompiledToFvmResult(
                            pc.execute(ctx.getTransactionData(), ctx.getTransactionEnergy()));
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
     */
    private static FastVmTransactionResult doCreate(ExecutionContext ctx, FastVM jit) {
        KernelInterfaceForFastVM track = kernelRepo().makeChildKernelInterface();
        FastVmTransactionResult result =
                new FastVmTransactionResult(FastVmResultCode.SUCCESS, ctx.getTransactionEnergy());

        // compute new address
        byte[] nonce = track.getNonce(ctx.getSenderAddress()).toByteArray();
        AionAddress newAddress =
                new AionAddress(HashUtil.calcNewAddr(ctx.getSenderAddress().toByteArray(), nonce));
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

        if (track.isFork040Enable()) {
            byte[] code = track.getCode(newAddress);
            if (code == null || code.length == 0) {
                alreadyExsits = false;
            }
        }

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
                internalTx.markAsRejected();
                ctx.getSideEffects().markAllInternalTransactionsAsRejected(); // reject all

                track.rollback();
                return result;
            }
            byte[] code = result.getReturnData();
            track.putCode(newAddress, code == null ? new byte[0] : code);

            result.setReturnData(newAddress.toByteArray());

            track.commit();
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

    /** Creates a new internal transaction. */
    private static AionInternalTx newInternalTx(
            AionAddress from,
            AionAddress to,
            BigInteger nonce,
            DataWord value,
            byte[] data,
            String note) {
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

    private static FastVmTransactionResult precompiledToFvmResult(
            PrecompiledTransactionResult precompiledResult) {
        FastVmTransactionResult fvmResult = new FastVmTransactionResult();

        fvmResult.addLogs(precompiledResult.getLogs());
        fvmResult.addInternalTransactions(precompiledResult.getInternalTransactions());
        fvmResult.addDeletedAddresses(precompiledResult.getDeletedAddresses());

        fvmResult.setEnergyRemaining(precompiledResult.getEnergyRemaining());
        fvmResult.setResultCode(precompiledToFvmResultCode(precompiledResult.getResultCode()));
        fvmResult.setReturnData(precompiledResult.getReturnData());
        fvmResult.setKernelInterface(precompiledResult.getKernelInterface());

        return fvmResult;
    }

    private static FastVmResultCode precompiledToFvmResultCode(
            PrecompiledResultCode precompiledResultCode) {
        switch (precompiledResultCode) {
            case BAD_JUMP_DESTINATION:
                return FastVmResultCode.BAD_JUMP_DESTINATION;
            case VM_INTERNAL_ERROR:
                return FastVmResultCode.VM_INTERNAL_ERROR;
            case STATIC_MODE_ERROR:
                return FastVmResultCode.STATIC_MODE_ERROR;
            case INVALID_NRG_LIMIT:
                return FastVmResultCode.INVALID_NRG_LIMIT;
            case STACK_UNDERFLOW:
                return FastVmResultCode.STACK_UNDERFLOW;
            case BAD_INSTRUCTION:
                return FastVmResultCode.BAD_INSTRUCTION;
            case STACK_OVERFLOW:
                return FastVmResultCode.STACK_OVERFLOW;
            case INVALID_NONCE:
                return FastVmResultCode.INVALID_NONCE;
            case VM_REJECTED:
                return FastVmResultCode.VM_REJECTED;
            case OUT_OF_NRG:
                return FastVmResultCode.OUT_OF_NRG;
            case SUCCESS:
                return FastVmResultCode.SUCCESS;
            case FAILURE:
                return FastVmResultCode.FAILURE;
            case REVERT:
                return FastVmResultCode.REVERT;
            case ABORT:
                return FastVmResultCode.ABORT;
            case INSUFFICIENT_BALANCE:
                return FastVmResultCode.INSUFFICIENT_BALANCE;
            case INCOMPATIBLE_CONTRACT_CALL:
                return FastVmResultCode.INCOMPATIBLE_CONTRACT_CALL;
            default:
                throw new IllegalStateException("Unknown code: " + precompiledResultCode);
        }
    }

    private static PrecompiledTransactionContext toPrecompiledTransactionContext(
            ExecutionContext context) {
        return new PrecompiledTransactionContext(
                context.getDestinationAddress(),
                context.getOriginAddress(),
                context.getSenderAddress(),
                context.getSideEffects().getExecutionLogs(),
                context.getSideEffects().getInternalTransactions(),
                context.getSideEffects().getAddressesToBeDeleted(),
                context.getHashOfOriginTransaction(),
                context.getTransactionHash(),
                context.getBlockNumber(),
                context.getTransactionEnergy(),
                context.getTransactionStackDepth());
    }
}

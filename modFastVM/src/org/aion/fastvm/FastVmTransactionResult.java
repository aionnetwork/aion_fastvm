package org.aion.fastvm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.mcf.types.InternalTransactionInterface;
import org.aion.mcf.types.KernelInterface;
import org.aion.mcf.types.ResultCode;

public final class FastVmTransactionResult {

    private KernelInterface kernel;
    private FastVmResultCode code;
    private byte[] output;
    private long energyRemaining;
    private List<Log> logs;
    private List<InternalTransactionInterface> internalTransactions;
    private List<AionAddress> deletedAddresses;

    /**
     * Constructs a new {@code TransactionResult} with no side-effects, with zero energy remaining,
     * with an empty byte array as its output and {@link FastVmResultCode#SUCCESS} as its result
     * code.
     */
    public FastVmTransactionResult() {
        this.kernel = null;
        this.logs = new ArrayList<>();
        this.internalTransactions = new ArrayList<>();
        this.deletedAddresses = new ArrayList<>();
        this.code = FastVmResultCode.SUCCESS;
        this.output = new byte[0];
        this.energyRemaining = 0;
    }

    /**
     * Constructs a new {@code TransactionResult} with no side-effects and with the specified result
     * code and remaining energy.
     *
     * @param code The transaction result code.
     * @param energyRemaining The energy remaining after executing the transaction.
     */
    public FastVmTransactionResult(FastVmResultCode code, long energyRemaining) {
        this.kernel = null;
        this.logs = new ArrayList<>();
        this.internalTransactions = new ArrayList<>();
        this.deletedAddresses = new ArrayList<>();
        this.code = code;
        this.energyRemaining = energyRemaining;
        this.output = new byte[0];
    }

    /**
     * Constructs a new {@code TransactionResult} with no side-effects and with the specified result
     * code, remaining energy and output.
     *
     * @param code The transaction result code.
     * @param energyRemaining The energy remaining after executing the transaction.
     * @param output The output of executing the transaction.
     */
    public FastVmTransactionResult(FastVmResultCode code, long energyRemaining, byte[] output) {
        this.kernel = null;
        this.logs = new ArrayList<>();
        this.internalTransactions = new ArrayList<>();
        this.deletedAddresses = new ArrayList<>();
        this.code = code;
        this.output = (output == null) ? new byte[0] : output;
        this.energyRemaining = energyRemaining;
    }

    /**
     * Returns a <i>partial</i> byte array representation of this {@code TransactionResult}.
     *
     * <p>The representation is partial because it only represents the {@link FastVmResultCode}, the
     * amount of energy remaining, and the output.
     *
     * <p>In particular, the {@link KernelInterface} is not included in this representation, meaning
     * these components of this object will be lost when the byte array representation is
     * transformed back into a {@code TransactionResult} via the {@code fromBytes()} method.
     *
     * @return A partial byte array representation of this object.
     */
    public byte[] toBytes() {
        ByteBuffer buffer =
                ByteBuffer.allocate(
                        Integer.BYTES + Long.BYTES + Integer.BYTES + this.output.length);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(this.code.toInt());
        buffer.putLong(this.energyRemaining);
        buffer.putInt(this.output.length);
        buffer.put(this.output);
        return buffer.array();
    }

    // TODO: document exception / maybe catch it and throw something more informative

    /**
     * Returns a {@code TransactionResult} object from a partial byte array representation obtained
     * via the {@code toBytes()} method.
     *
     * <p>The returned object will be constructed from the partial representation, which, because it
     * is partial, will have no {@link KernelInterface}.
     *
     * @param bytes A partial byte array representation of a {@code TransactionResult}.
     * @return The {@code TransactionResult} object obtained from the byte array representation.
     */
    public static FastVmTransactionResult fromBytes(byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        buffer.order(ByteOrder.BIG_ENDIAN);

        FastVmResultCode code = FastVmResultCode.fromInt(buffer.getInt());
        long energyRemaining = buffer.getLong();

        int outputLength = buffer.getInt();
        byte[] output = new byte[outputLength];
        buffer.get(output);

        return new FastVmTransactionResult(code, energyRemaining, output);
    }

    public void setResultCodeAndEnergyRemaining(FastVmResultCode code, long energyRemaining) {
        this.code = code;
        this.energyRemaining = energyRemaining;
    }

    public void setResultCode(ResultCode code) {
        if (code == null) {
            throw new NullPointerException("Cannot set null result code.");
        }
        if (!(code instanceof FastVmResultCode)) {
            throw new IllegalArgumentException(
                    "Type of code must be FastVmResultCode for FastVmTransactionResult.");
        }
        this.code = (FastVmResultCode) code;
    }

    public void setKernelInterface(KernelInterface kernel) {
        this.kernel = kernel;
    }

    public void setReturnData(byte[] output) {
        this.output = (output == null) ? new byte[0] : output;
    }

    public void setEnergyRemaining(long energyRemaining) {
        this.energyRemaining = energyRemaining;
    }

    public FastVmResultCode getResultCode() {
        return this.code;
    }

    public byte[] getReturnData() {
        return this.output;
    }

    public long getEnergyRemaining() {
        return this.energyRemaining;
    }

    public KernelInterface getKernelInterface() {
        return this.kernel;
    }

    public void addLogs(List<Log> logs) {
        this.logs.addAll(logs);
    }

    public void addInternalTransactions(List<InternalTransactionInterface> internalTransactions) {
        this.internalTransactions.addAll(internalTransactions);
    }

    public void addDeletedAddresses(List<AionAddress> deletedAddresses) {
        this.deletedAddresses.addAll(deletedAddresses);
    }

    public List<Log> getLogs() {
        return this.logs;
    }

    public List<InternalTransactionInterface> getInternalTransactions() {
        return this.internalTransactions;
    }

    public List<AionAddress> getDeletedAddresses() {
        return this.deletedAddresses;
    }

    @Override
    public String toString() {
        return "TransactionResult { code = "
                + this.code
                + ", energy remaining = "
                + this.energyRemaining
                + "}";
        //            + ", output = " + ByteUtil.toHexString(this.output) + " }";
    }

    public String toStringWithSideEffects() {
        return "TransactionResult { code = "
                + this.code
                + ", energy remaining = "
                + this.energyRemaining
                + "}";
        //            + ", output = " + ByteUtil.toHexString(this.output) + " }";
    }
}

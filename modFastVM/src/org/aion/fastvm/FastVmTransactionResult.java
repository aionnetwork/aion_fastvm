package org.aion.fastvm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class FastVmTransactionResult {

    private FastVmResultCode code;
    private byte[] output;
    private long energyRemaining;

    /**
     * Constructs a new {@code TransactionResult} with zero energy remaining, with an empty byte
     * array as its output and {@link FastVmResultCode#SUCCESS} as its result code.
     */
    public FastVmTransactionResult() {
        this.code = FastVmResultCode.SUCCESS;
        this.output = new byte[0];
        this.energyRemaining = 0;
    }

    /**
     * Constructs a new {@code TransactionResult} with the specified result code and remaining energy.
     *
     * @param code The transaction result code.
     * @param energyRemaining The energy remaining after executing the transaction.
     */
    public FastVmTransactionResult(FastVmResultCode code, long energyRemaining) {
        this.code = code;
        this.energyRemaining = energyRemaining;
        this.output = new byte[0];
    }

    /**
     * Constructs a new {@code TransactionResult} with the specified result code, remaining energy and output.
     *
     * @param code The transaction result code.
     * @param energyRemaining The energy remaining after executing the transaction.
     * @param output The output of executing the transaction.
     */
    public FastVmTransactionResult(FastVmResultCode code, long energyRemaining, byte[] output) {
        this.code = code;
        this.output = (output == null) ? new byte[0] : output;
        this.energyRemaining = energyRemaining;
    }

    /**
     * Returns a byte array representation of this {@code TransactionResult}.
     *
     * @return A byte array representation of this object.
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
     * Returns a {@code TransactionResult} object from a byte array representation obtained
     * via the {@code toBytes()} method.
     *
     * @param bytes A byte array representation of a {@code TransactionResult}.
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

    public void setResultCode(FastVmResultCode code) {
        if (code == null) {
            throw new NullPointerException("Cannot set null result code.");
        }
        this.code = code;
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

    @Override
    public String toString() {
        return "TransactionResult { code = "
                + this.code
                + ", energy remaining = "
                + this.energyRemaining
                + "}";
    }
}

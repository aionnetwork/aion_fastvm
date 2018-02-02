package org.aion.vm;

import org.aion.base.util.ByteUtil;
import org.spongycastle.util.encoders.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A ExecutionResult is the result of a VM execution. It contains the VM status
 * code, nrg usage, output, etc.
 *
 * @author yulong
 */
public class ExecutionResult {

    public enum Code {
        SUCCESS(0),

        FAILURE(1),

        OUT_OF_NRG(2),

        BAD_INSTRUCTION(3),

        BAD_JUMP_DESTINATION(4),

        STACK_OVERFLOW(5),

        STACK_UNDERFLOW(6),

        REVERT(7),

        INVALID_NONCE(8),

        INVALID_NRG_LIMIT(9),

        INSUFFICIENT_BALANCE(10),

        CONTRACT_ALREADY_EXISTS(11),

        INTERNAL_ERROR(-1);

        private int code;

        Code(int code) {
            this.code = code;
        }

        public static Code fromInt(int code) {
            // TODO: optimize
            for (Code c : Code.values()) {
                if (c.code == code) {
                    return c;
                }
            }
            return null;
        }

        public int toInt() {
            return code;
        }
    }

    private Code code;
    private long nrgLeft;
    private byte[] output;

    /**
     * Create an execution result.
     *
     * @param code
     * @param nrgLeft
     * @param output
     */
    public ExecutionResult(Code code, long nrgLeft, byte[] output) {
        this.code = code;
        this.nrgLeft = nrgLeft;
        this.output = output;
    }

    /**
     * Creates an execution result.
     */
    public ExecutionResult(Code code, long nrgLeft) {
        this(code, nrgLeft, ByteUtil.EMPTY_BYTE_ARRAY);
    }

    /**
     * Parse execution result from byte array.
     *
     * @param result
     * @return
     */
    public static ExecutionResult parse(byte[] result) {
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.order(ByteOrder.BIG_ENDIAN);

        Code code = Code.fromInt(buffer.getInt());
        long nrgLeft = buffer.getLong();
        byte[] output = new byte[buffer.getInt()];
        buffer.get(output);

        return new ExecutionResult(code, nrgLeft, output);
    }

    /**
     * Encode execution resul tinto byte array.
     *
     * @return
     */
    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 8 + 4 + (output == null ? 0 : output.length));
        buffer.order(ByteOrder.BIG_ENDIAN);

        buffer.putInt(code.toInt());
        buffer.putLong(nrgLeft);
        buffer.putInt(output == null ? 0 : output.length);
        if (output != null) {
            buffer.put(output);
        }

        return buffer.array();
    }

    /**
     * Returns the code.
     *
     * @return
     */
    public Code getCode() {
        return code;
    }

    /**
     * Sets the code.
     *
     * @param code
     */
    public void setCode(Code code) {
        this.code = code;
    }

    /**
     * Returns nrg left.
     *
     * @return
     */
    public long getNrgLeft() {
        return nrgLeft;
    }

    /**
     * Sets nrg left.
     *
     * @param nrgLeft
     */
    public void setNrgLeft(long nrgLeft) {
        this.nrgLeft = nrgLeft;
    }

    /**
     * Sets code and energy left.
     *
     * @param code
     * @param nrgLeft
     */
    public void setCodeAndNrgLeft(Code code, long nrgLeft) {
        this.code = code;
        this.nrgLeft = nrgLeft;
    }

    /**
     * Returns the output.
     *
     * @return
     */
    public byte[] getOutput() {
        return output;
    }

    /**
     * Sets the output.
     *
     * @param output
     */
    public void setOutput(byte[] output) {
        this.output = output;
    }

    @Override
    public String toString() {
        return "[code = " + code + ", nrgLeft = " + nrgLeft + ", output = " + Hex.toHexString(output) + "]";
    }
}

/*******************************************************************************
 *
 * Copyright (c) 2017 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
package org.aion.vm;

import org.aion.base.util.ByteUtil;
import org.aion.base.util.Hex;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.aion.precompiled.ContractExecutionResult;

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

    /**
     * Converts the ContractExecutionResult result into an equivalent ExecutionResult.
     *
     * @param result The result to convert.
     * @return the converted result.
     */
    public static ExecutionResult fromContractResult(ContractExecutionResult result) {
        long nrg = result.getNrgLeft();
        byte[] output = result.getOutput();
        Code code;

        // TODO: find more elegant solution.
        ContractExecutionResult.ResultCode contCode = result.getCode();
        switch (contCode) {
            case SUCCESS:
                code = Code.SUCCESS;
                break;
            case OUT_OF_NRG:
                code = Code.OUT_OF_NRG;
                break;
            case INVALID_NRG_LIMIT:
                code = Code.INVALID_NRG_LIMIT;
                break;
            case INSUFFICIENT_BALANCE:
                code = Code.INSUFFICIENT_BALANCE;
                break;
            case REVERT:
                code = Code.REVERT;
                break;
            case FAILURE:
                code = Code.FAILURE;
                break;
            case INVALID_NONCE:
                code = Code.INVALID_NONCE;
                break;
            case INTERNAL_ERROR:
                code = Code.INTERNAL_ERROR;
                break;
            default:
                code = Code.INTERNAL_ERROR;
        }

        if ((output == null) || (output.length == 0)) {
            return new ExecutionResult(code, nrg);
        } else {
            return new ExecutionResult(code, nrg, output);
        }
    }

    @Override
    public String toString() {
        return "[code = " + code + ", nrgLeft = " + nrgLeft + ", output = " + Hex.toHexString(output) + "]";
    }
}

package org.aion.fastvm;

import static org.aion.fastvm.Instruction.Tier.BASE;
import static org.aion.fastvm.Instruction.Tier.EXT_CODE;
import static org.aion.fastvm.Instruction.Tier.HIGH;
import static org.aion.fastvm.Instruction.Tier.LOW;
import static org.aion.fastvm.Instruction.Tier.MID;
import static org.aion.fastvm.Instruction.Tier.SPECIAL;
import static org.aion.fastvm.Instruction.Tier.VERY_LOW;
import static org.aion.fastvm.Instruction.Tier.ZERO;

/**
 * Instruction set for the Aion fast virtual machine.
 *
 * @author yulong
 */
public enum Instruction {

    /*
     * 0s: Stop and Arithmetic Operations
     */

    STOP(0x00, 0, 0, ZERO),

    ADD(0x01, 2, 1, VERY_LOW),

    MUL(0x02, 2, 1, LOW),

    SUB(0x03, 2, 1, VERY_LOW),

    DIV(0x04, 2, 1, LOW),

    SDIV(0x05, 2, 1, LOW),

    MOD(0x06, 2, 1, LOW),

    SMOD(0x07, 2, 1, LOW),

    ADDMOD(0x08, 3, 1, MID),

    MULMOD(0x09, 3, 1, MID),

    EXP(0x0a, 2, 1, SPECIAL),

    SIGNEXTEND(0x0b, 2, 1, LOW),

    /*
     * 10s: Comparison & Bitwise Logic Operations
     */

    LT(0X10, 2, 1, VERY_LOW),

    GT(0X11, 2, 1, VERY_LOW),

    SLT(0X12, 2, 1, VERY_LOW),

    SGT(0X13, 2, 1, VERY_LOW),

    EQ(0X14, 2, 1, VERY_LOW),

    ISZERO(0x15, 1, 1, VERY_LOW),

    AND(0x16, 2, 1, VERY_LOW),

    OR(0x17, 2, 1, VERY_LOW),

    XOR(0x18, 2, 1, VERY_LOW),

    NOT(0x19, 1, 1, VERY_LOW),

    BYTE(0x1a, 2, 1, VERY_LOW),

    /*
     * 20s: SHA3
     */

    SHA3(0x20, 2, 2, SPECIAL),

    /*
     * 30s: Environmental Information
     */

    ADDRESS(0x30, 0, 2, BASE),

    BALANCE(0x31, 2, 1, EXT_CODE),

    ORIGIN(0x32, 0, 2, BASE),

    CALLER(0x33, 0, 2, BASE),

    CALLVALUE(0x34, 0, 1, BASE),

    CALLDATALOAD(0x35, 1, 1, VERY_LOW),

    CALLDATASIZE(0x36, 0, 1, BASE),

    CALLDATACOPY(0x37, 3, 0, VERY_LOW),

    CODESIZE(0x38, 0, 1, BASE),

    CODECOPY(0x39, 3, 0, VERY_LOW),

    RETURNDATASIZE(0x3d, 0, 1, BASE),

    RETURNDATACOPY(0x3e, 3, 0, VERY_LOW),

    GASPRICE(0x3a, 0, 1, BASE),

    EXTCODESIZE(0x3b, 1, 1, EXT_CODE),

    EXTCODECOPY(0x3c, 4, 0, EXT_CODE),

    /*
     * 40s: Block Information
     */

    BLOCKHASH(0x40, 1, 2, EXT_CODE),

    COINBASE(0x41, 0, 2, BASE),

    TIMESTAMP(0x42, 0, 1, BASE),

    NUMBER(0x43, 0, 1, BASE),

    DIFFICULTY(0x44, 0, 1, BASE),

    GASLIMIT(0x45, 0, 1, BASE),

    /*
     * 50s: Stack, Memory, Storage and Flow Operations
     */

    POP(0x50, 1, 0, BASE),

    MLOAD(0x51, 1, 1, VERY_LOW),

    MSTORE(0x52, 2, 0, VERY_LOW),

    MSTORE8(0x53, 2, 0, VERY_LOW),

    SLOAD(0x54, 1, 1, SPECIAL),

    SSTORE(0x55, 2, 0, SPECIAL),

    JUMP(0x56, 1, 0, MID),

    JUMPI(0x57, 2, 0, HIGH),

    PC(0x58, 0, 1, BASE),

    MSIZE(0x59, 0, 1, BASE),

    GAS(0x5a, 0, 1, BASE),

    JUMPDEST(0x5b, 0, 0, SPECIAL),

    /*
     * 60s & 70s: Push Operations
     */

    PUSH1(0x60, 0, 1, VERY_LOW),

    PUSH2(0x61, 0, 1, VERY_LOW),

    PUSH3(0x62, 0, 1, VERY_LOW),

    PUSH4(0x63, 0, 1, VERY_LOW),

    PUSH5(0x64, 0, 1, VERY_LOW),

    PUSH6(0x65, 0, 1, VERY_LOW),

    PUSH7(0x66, 0, 1, VERY_LOW),

    PUSH8(0x67, 0, 1, VERY_LOW),

    PUSH9(0x68, 0, 1, VERY_LOW),

    PUSH10(0x69, 0, 1, VERY_LOW),

    PUSH11(0x6a, 0, 1, VERY_LOW),

    PUSH12(0x6b, 0, 1, VERY_LOW),

    PUSH13(0x6c, 0, 1, VERY_LOW),

    PUSH14(0x6d, 0, 1, VERY_LOW),

    PUSH15(0x6e, 0, 1, VERY_LOW),

    PUSH16(0x6f, 0, 1, VERY_LOW),

    PUSH17(0x70, 0, 2, VERY_LOW),

    PUSH18(0x71, 0, 2, VERY_LOW),

    PUSH19(0x72, 0, 2, VERY_LOW),

    PUSH20(0x73, 0, 2, VERY_LOW),

    PUSH21(0x74, 0, 2, VERY_LOW),

    PUSH22(0x75, 0, 2, VERY_LOW),

    PUSH23(0x76, 0, 2, VERY_LOW),

    PUSH24(0x77, 0, 2, VERY_LOW),

    PUSH25(0x78, 0, 2, VERY_LOW),

    PUSH26(0x79, 0, 2, VERY_LOW),

    PUSH27(0x7a, 0, 2, VERY_LOW),

    PUSH28(0x7b, 0, 2, VERY_LOW),

    PUSH29(0x7c, 0, 2, VERY_LOW),

    PUSH30(0x7d, 0, 2, VERY_LOW),

    PUSH31(0x7e, 0, 2, VERY_LOW),

    PUSH32(0x7f, 0, 2, VERY_LOW),

    /*
     * 80s: Duplication Operations
     */

    DUP1(0x80, 1, 2, VERY_LOW),

    DUP2(0x81, 2, 3, VERY_LOW),

    DUP3(0x82, 3, 4, VERY_LOW),

    DUP4(0x83, 4, 5, VERY_LOW),

    DUP5(0x84, 5, 6, VERY_LOW),

    DUP6(0x85, 6, 7, VERY_LOW),

    DUP7(0x86, 7, 8, VERY_LOW),

    DUP8(0x87, 8, 9, VERY_LOW),

    DUP9(0x88, 9, 10, VERY_LOW),

    DUP10(0x89, 10, 11, VERY_LOW),

    DUP11(0x8a, 11, 12, VERY_LOW),

    DUP12(0x8b, 12, 13, VERY_LOW),

    DUP13(0x8c, 13, 14, VERY_LOW),

    DUP14(0x8d, 14, 15, VERY_LOW),

    DUP15(0x8e, 15, 16, VERY_LOW),

    DUP16(0x8f, 16, 17, VERY_LOW),

    /*
     * 90s: Exchange Operations
     */

    SWAP1(0x90, 2, 2, VERY_LOW),

    SWAP2(0x91, 3, 3, VERY_LOW),

    SWAP3(0x92, 4, 4, VERY_LOW),

    SWAP4(0x93, 5, 5, VERY_LOW),

    SWAP5(0x94, 6, 6, VERY_LOW),

    SWAP6(0x95, 7, 7, VERY_LOW),

    SWAP7(0x96, 8, 8, VERY_LOW),

    SWAP8(0x97, 9, 9, VERY_LOW),

    SWAP9(0x98, 10, 10, VERY_LOW),

    SWAP10(0x99, 11, 11, VERY_LOW),

    SWAP11(0x9a, 12, 12, VERY_LOW),

    SWAP12(0x9b, 13, 13, VERY_LOW),

    SWAP13(0x9c, 14, 14, VERY_LOW),

    SWAP14(0x9d, 15, 15, VERY_LOW),

    SWAP15(0x9e, 16, 16, VERY_LOW),

    SWAP16(0x9f, 17, 17, VERY_LOW),

    /*
     * a0s: Logging Operations
     */

    LOG0(0xa0, 2, 0, SPECIAL),

    LOG1(0xa1, 4, 0, SPECIAL),

    LOG2(0xa2, 6, 0, SPECIAL),

    LOG3(0xa3, 8, 0, SPECIAL),

    LOG4(0xa4, 10, 0, SPECIAL),

    /*
     * f0s: System operations
     */

    CREATE(0xf0, 3, 1, SPECIAL),

    CALL(0xf1, 7, 1, SPECIAL),

    CALLCODE(0xf2, 7, 1, SPECIAL),

    RETURN(0xf3, 2, 0, ZERO),

    DELEGATECALL(0xf4, 6, 1, SPECIAL),

    STATICCALL(0xfa, 6, 1, SPECIAL),

    REVERT(0xfd, 2, 0, ZERO),

    SELFDESTRUCT(0xff, 1, 0, ZERO);

    private final byte code;
    private final int req;
    private final int ret;
    private final Tier tier;

    private static final Instruction[] map = new Instruction[256];

    static {
        for (Instruction i : Instruction.values()) {
            map[i.code & 0xFF] = i;
        }
    }

    Instruction(int op, int req, int ret, Tier tier) {
        this.code = (byte) op;
        this.req = req;
        this.ret = ret;
        this.tier = tier;
    }

    /**
     * Returns the code in byte.
     *
     * @return
     */
    public byte code() {
        return code;
    }

    /**
     * Returns the required # of stack items.
     *
     * @return
     */
    public int req() {
        return req;
    }

    /**
     * Returns the returned # of stack items.
     *
     * @return
     */
    public int ret() {
        return ret;
    }

    /**
     * Returns the energy cost tier
     *
     * @return
     */
    public Tier tier() {
        return this.tier;
    }

    /**
     * Converts from code to instruction.
     *
     * @param code
     * @return
     */
    public static Instruction of(int code) {
        return map[code & 0xFF];
    }

    /**
     * Energy cost tier.
     *
     * @author yulong
     */
    public enum Tier {
        ZERO(0),

        BASE(2),

        VERY_LOW(3),

        LOW(5),

        MID(8),

        HIGH(10),

        EXT_CODE(20),

        SPECIAL(1);

        private final int cost;

        Tier(int cost) {
            this.cost = cost;
        }

        public int cost() {
            return cost;
        }
    }
}

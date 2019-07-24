package org.aion.fastvm;

/**
 * Some constants that are used by the Fvm.
 */
public final class FvmConstants {

    public static final int ENERGY_CODE_DEPOSIT = 1_000;

    public static final int CREATE_TRANSACTION_FEE = 200_000;

    public static final int TRANSACTION_BASE_FEE = 21_000;

    public static final int ZERO_BYTE_FEE = 4;

    public static final int NONZERO_BYTE_FEE = 64;

    /** Call stack depth limit. Based on EIP-150, the theoretical limit is ~340. */
    public static final int MAX_CALL_DEPTH = 128;
}

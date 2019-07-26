package org.aion.repository;

import java.math.BigInteger;
import java.util.Arrays;

public final class AccountStateForTesting {
    public enum VmType { FVM, PRECOMPILED, AVM, NONE }

    public final BigInteger balance;
    public final BigInteger nonce;
    public final byte[] code;
    public final VmType type;

    private AccountStateForTesting(BigInteger balance, BigInteger nonce, byte[] code, VmType type) {
        this.balance = balance;
        this.nonce = nonce;
        this.code = (code == null) ? null : Arrays.copyOf(code, code.length);
        this.type = type;
    }

    public static AccountStateForTesting emptyState() {
        return new AccountStateForTesting(BigInteger.ZERO, BigInteger.ZERO, null, VmType.NONE);
    }

    public static AccountStateForTesting newState(BigInteger balance, BigInteger nonce, byte[] code, VmType type) {
        return new AccountStateForTesting(balance, nonce, code, type);
    }
}

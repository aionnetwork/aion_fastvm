package org.aion.fastvm;

public enum TransactionKind {
    CALL(0),
    DELEGATE_CALL(1),
    CALLCODE(2),
    CREATE(3);

    public final int intValue;

    TransactionKind(int intValue) {
        this.intValue = intValue;
    }

    public static TransactionKind fromInt(int intValue) {
        switch (intValue) {
            case 0: return CALL;
            case 1: return DELEGATE_CALL;
            case 2: return CALLCODE;
            case 3: return CREATE;
            default: throw new IllegalArgumentException("No transaction kind for the int value: " + intValue);
        }
    }
}

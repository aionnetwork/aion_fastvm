package org.aion.repository;

public final class EnergyLimitRuleForTesting {
    public static final long MAX_CREATE_ENERGY_LIMIT = 5_000_000L;
    public static final long MIN_CREATE_ENERGY_LIMIT = 21_000L;
    public static final long MAX_NON_CREATE_ENERGY_LIMIT = 2_000_000L;
    public static final long MIN_NON_CREATE_ENERGY_LIMIT = 21_000L;

    public static boolean isValidEnergyLimitForCreate(long limit, byte[] data) {
        return limit >= MIN_CREATE_ENERGY_LIMIT && limit <= MAX_CREATE_ENERGY_LIMIT;
    }

    public static boolean isValidEnergyLimitForNonCreate(long limit, byte[] data) {
        return limit >= MIN_NON_CREATE_ENERGY_LIMIT && limit <= MAX_NON_CREATE_ENERGY_LIMIT;
    }
}

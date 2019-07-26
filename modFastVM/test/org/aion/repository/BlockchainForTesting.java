package org.aion.repository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class BlockchainForTesting {
    private final Map<Long, byte[]> blockHashes = new HashMap<>();

    public void registerBlockHash(long blockNumber, byte[] hash) {
        if (hash == null) {
            throw new NullPointerException("Cannot register null block hash!");
        }
        if (this.blockHashes.containsKey(blockNumber)) {
            throw new IllegalArgumentException("Cannot overwrite an existing block hash entry!");
        }
        this.blockHashes.put(blockNumber, Arrays.copyOf(hash, hash.length));
    }

    public byte[] getBlockHashByNumber(long blockNumber) {
        return this.blockHashes.get(blockNumber);
    }
}

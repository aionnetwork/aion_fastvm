package org.aion.fastvm;

import java.util.Collections;
import java.util.List;

import org.aion.types.AionAddress;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.zero.impl.types.AionBlock;
import org.aion.zero.types.AionTransaction;
import org.apache.commons.lang3.RandomUtils;

public class TestUtils {

    public static AionBlock createDummyBlock() {
        byte[] parentHash = new byte[32];
        byte[] coinbase = RandomUtils.nextBytes(AionAddress.LENGTH);
        byte[] logsBloom = new byte[0];
        byte[] difficulty = new DataWordImpl(0x1000000L).getData();
        long number = 1;
        long timestamp = System.currentTimeMillis() / 1000;
        byte[] extraData = new byte[0];
        byte[] nonce = new byte[32];
        byte[] receiptsRoot = new byte[32];
        byte[] transactionsRoot = new byte[32];
        byte[] stateRoot = new byte[32];
        List<AionTransaction> transactionsList = Collections.emptyList();
        byte[] solutions = new byte[0];

        // TODO: set a dummy limit of 5000000 for now
        return new AionBlock(
                parentHash,
                new AionAddress(coinbase),
                logsBloom,
                difficulty,
                number,
                timestamp,
                extraData,
                nonce,
                receiptsRoot,
                transactionsRoot,
                stateRoot,
                transactionsList,
                solutions,
                0,
                5000000);
    }
}

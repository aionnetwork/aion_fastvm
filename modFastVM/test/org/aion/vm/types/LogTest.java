package org.aion.vm.types;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aion.base.type.AionAddress;
import org.aion.mcf.vm.types.DataWord;
import org.aion.mcf.vm.types.Log;
import org.junit.Test;

public class LogTest {

    @Test
    public void testEncode() {
        AionAddress address = AionAddress.ZERO_ADDRESS();
        List<byte[]> topics = new ArrayList<byte[]>();
        topics.add(DataWord.ZERO.getData());
        byte[] data = new byte[200];

        Log info = new Log(address, topics, data);
        Log info2 = new Log(info.getEncoded());

        assertTrue(address.equals(info2.getLogSourceAddress()));
        assertFalse(info2.getLogTopics().isEmpty());
        assertTrue(Arrays.equals(data, info2.getLogData()));
    }
}

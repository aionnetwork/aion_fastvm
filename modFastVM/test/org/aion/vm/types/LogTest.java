package org.aion.vm.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;

import org.aion.mcf.vm.types.LogUtility;
import org.aion.mcf.vm.types.DataWordImpl;
import org.aion.types.AionAddress;
import org.aion.types.Log;
import org.aion.util.types.AddressUtils;
import org.junit.Test;

public class LogTest {

    @Test
    public void testEncode() {
        AionAddress address = AddressUtils.ZERO_ADDRESS;
        List<byte[]> topics = new ArrayList<byte[]>();
        topics.add(DataWordImpl.ZERO.getData());
        byte[] data = new byte[200];

        Log info = Log.topicsAndData(address.toByteArray(), topics, data);
        Log info2 = LogUtility.decodeLog(LogUtility.encodeLog(info));

        assertArrayEquals(address.toByteArray(), info2.copyOfAddress());
        assertFalse(info2.copyOfTopics().isEmpty());
        assertArrayEquals(data, info2.copyOfData());
    }
}

package org.aion.vm.types;

import static junit.framework.TestCase.assertEquals;

import org.aion.mcf.vm.types.DataWord;
import org.junit.Test;

public class DataWordTest {

    @Test
    public void testIntToDataWord() {
        DataWord d1 = new DataWord(Integer.MIN_VALUE);
        DataWord d2 = new DataWord(1);
        DataWord d3 = new DataWord(Integer.MAX_VALUE);

        assertEquals(d1.intValue(), Integer.MIN_VALUE);
        assertEquals(d2.intValue(), 1);
        assertEquals(d3.intValue(), Integer.MAX_VALUE);
    }

    @Test
    public void testLongToDataWord() {
        DataWord d1 = new DataWord(Long.MIN_VALUE);
        DataWord d2 = new DataWord(1);
        DataWord d3 = new DataWord(Long.MAX_VALUE);

        assertEquals(d1.longValue(), Long.MIN_VALUE);
        assertEquals(d2.longValue(), 1);
        assertEquals(d3.longValue(), Long.MAX_VALUE);
    }
}

package org.aion.vm.types;

import static junit.framework.TestCase.assertEquals;

import org.aion.mcf.vm.types.DataWordImpl;
import org.junit.Test;

public class DataWordTest {

    @Test
    public void testIntToDataWord() {
        DataWordImpl d1 = new DataWordImpl(Integer.MIN_VALUE);
        DataWordImpl d2 = new DataWordImpl(1);
        DataWordImpl d3 = new DataWordImpl(Integer.MAX_VALUE);

        assertEquals(d1.intValue(), Integer.MIN_VALUE);
        assertEquals(d2.intValue(), 1);
        assertEquals(d3.intValue(), Integer.MAX_VALUE);
    }

    @Test
    public void testLongToDataWord() {
        DataWordImpl d1 = new DataWordImpl(Long.MIN_VALUE);
        DataWordImpl d2 = new DataWordImpl(1);
        DataWordImpl d3 = new DataWordImpl(Long.MAX_VALUE);

        assertEquals(d1.longValue(), Long.MIN_VALUE);
        assertEquals(d2.longValue(), 1);
        assertEquals(d3.longValue(), Long.MAX_VALUE);
    }
}

package org.aion.solidity;

import java.util.List;

public final class Fallback extends Entry {

    public Fallback(boolean payable, List<Param> inputs, List<Param> outputs) {
        super(null, null, payable, "", inputs, outputs, Type.fallback);
    }

    public List<?> decode(byte[] encoded) {
        return Param.decodeList(inputs, encoded);
    }
}

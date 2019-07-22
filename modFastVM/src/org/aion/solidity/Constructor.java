package org.aion.solidity;

import java.util.List;

public final class Constructor extends Entry {

    public Constructor(boolean payable, List<Param> inputs, List<Param> outputs) {
        super(null, null, payable, "", inputs, outputs, Type.constructor);
    }

    public List<?> decode(byte[] encoded) {
        return Param.decodeList(inputs, encoded);
    }
}

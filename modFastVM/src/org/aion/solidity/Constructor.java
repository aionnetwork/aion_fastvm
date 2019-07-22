package org.aion.solidity;

import java.util.List;
import org.aion.fastvm.IExternalCapabilities;

public final class Constructor extends Entry {

    public Constructor(boolean payable, List<Param> inputs, List<Param> outputs, IExternalCapabilities capabilities) {
        super(null, null, payable, "", inputs, outputs, Type.constructor, capabilities);
    }

    public List<?> decode(byte[] encoded) {
        return Param.decodeList(inputs, encoded);
    }
}

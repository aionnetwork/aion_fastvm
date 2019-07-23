package org.aion.solidity;

import static org.aion.solidity.SolidityType.IntType.encodeInt;
import static org.apache.commons.lang3.ArrayUtils.subarray;

import java.util.List;
import org.aion.fastvm.IExternalCapabilities;
import org.aion.util.ByteUtil;

public final class Function extends Entry {
    private static final int ENCODED_SIGN_LENGTH = 4;

    public Function(
        boolean constant,
        boolean payable,
        String name,
        List<Param> inputs,
        List<Param> outputs,
        IExternalCapabilities capabilities) {
        super(null, constant, payable, name, inputs, outputs, Type.function, capabilities);    }

    public byte[] encode(Object... args) {
        return ByteUtil.merge(encodeSignature(), encodeArguments(args));
    }

    private byte[] encodeArguments(Object... args) {
        if (args.length > inputs.size())
            throw new RuntimeException(
                "Too many arguments: " + args.length + " > " + inputs.size());

        int staticSize = 0;
        int dynamicCnt = 0;
        // calculating static size and number of dynamic params
        for (int i = 0; i < args.length; i++) {
            SolidityType type = inputs.get(i).type;
            if (type.isDynamicType()) {
                dynamicCnt++;
            }
            staticSize += type.getFixedSize();
        }

        byte[][] bb = new byte[args.length + dynamicCnt][];
        for (int curDynamicPtr = staticSize, curDynamicCnt = 0, i = 0; i < args.length; i++) {
            SolidityType type = inputs.get(i).type;
            if (type.isDynamicType()) {
                byte[] dynBB = type.encode(args[i]);
                bb[i] = encodeInt(curDynamicPtr);
                bb[args.length + curDynamicCnt] = dynBB;
                curDynamicCnt++;
                curDynamicPtr += dynBB.length;
            } else {
                bb[i] = type.encode(args[i]);
            }
        }

        return ByteUtil.merge(bb);
    }

    public List<?> decode(byte[] encoded) {
        return Param.decodeList(inputs, subarray(encoded, ENCODED_SIGN_LENGTH, encoded.length));
    }

    @Override
    public byte[] encodeSignature() {
        return extractSignature(super.encodeSignature());
    }

    public static byte[] extractSignature(byte[] data) {
        return subarray(data, 0, ENCODED_SIGN_LENGTH);
    }
}

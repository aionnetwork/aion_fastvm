package org.aion.solidity;

import static java.lang.String.format;
import static org.apache.commons.collections4.ListUtils.select;
import static org.apache.commons.lang3.ArrayUtils.subarray;
import static org.apache.commons.lang3.StringUtils.join;

import java.util.ArrayList;
import java.util.List;
import org.aion.fastvm.IExternalCapabilities;
import org.aion.util.bytes.ByteUtil;
import org.apache.commons.collections4.Predicate;

public final class Event extends Entry {

    public Event(boolean anonymous, String name, List<Param> inputs, List<Param> outputs, IExternalCapabilities capabilities) {
        super(anonymous, null, null, name, inputs, outputs, Type.event, capabilities);
    }

    public List<?> decode(byte[] data, byte[][] topics) {
        List<Object> result = new ArrayList<>(inputs.size());

        byte[][] argTopics = anonymous ? topics : subarray(topics, 1, topics.length);
        List<?> indexed = Param.decodeList(filteredInputs(true), ByteUtil.merge(argTopics));
        List<?> notIndexed = Param.decodeList(filteredInputs(false), data);

        for (Param input : inputs) {
            result.add(input.indexed ? indexed.remove(0) : notIndexed.remove(0));
        }

        return result;
    }

    private List<Param> filteredInputs(final boolean indexed) {
        return select(
            inputs,
            new Predicate<Param>() {
                @Override
                public boolean evaluate(Param param) {
                    return param.indexed == indexed;
                }
            });
    }

    @Override
    public String toString() {
        return format("event %s(%s);", name, join(inputs, ", "));
    }
}

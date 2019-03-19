package org.aion.solidity;

import static java.lang.String.format;
import static org.aion.crypto.HashUtil.h256;
import static org.aion.solidity.SolidityType.IntType.decodeInt;
import static org.aion.solidity.SolidityType.IntType.encodeInt;
import static org.apache.commons.collections4.ListUtils.select;
import static org.apache.commons.lang3.ArrayUtils.subarray;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.StringUtils.stripEnd;

import java.util.ArrayList;
import java.util.List;
import org.aion.util.bytes.ByteUtil;
import org.apache.commons.collections4.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Abi {

    private List<Entry> entries = new ArrayList<>();

    public static Abi fromJSON(String json) {
        Abi abi = new Abi();

        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            abi.entries.add(Entry.fromJSON(obj));
        }
        return abi;
    }

    public String toJSON() {
        JSONArray arr = new JSONArray();
        for (Entry entry : entries) {
            arr.put(entry.toJSON());
        }
        return arr.toString();
    }

    @SuppressWarnings("unchecked")
    private <T extends Entry> T find(
            Class<T> resultClass, final Entry.Type type, final Predicate<T> searchPredicate) {
        for (Entry entry : entries) {
            if (entry.type == type && searchPredicate.evaluate((T) entry)) {
                return (T) entry;
            }
        }
        return null;
    }

    public Function findFunction(Predicate<Function> searchPredicate) {
        return find(Function.class, Entry.Type.function, searchPredicate);
    }

    public Event findEvent(Predicate<Event> searchPredicate) {
        return find(Event.class, Entry.Type.event, searchPredicate);
    }

    public Abi.Constructor findConstructor() {
        return find(
                Constructor.class,
                Entry.Type.constructor,
                new Predicate<Constructor>() {
                    @Override
                    public boolean evaluate(Constructor object) {
                        return true;
                    }
                });
    }

    public Entry[] getEntries() {
        Entry[] _entries = new Entry[this.entries.size()];
        entries.toArray(_entries);
        return _entries;
    }

    @Override
    public String toString() {
        return toJSON();
    }

    public abstract static class Entry {

        public enum Type {
            constructor,
            fallback,
            function,
            event
        }

        public static class Param {
            public Boolean indexed;
            public String name;
            public SolidityType type;

            public static Param fromJSON(JSONObject obj) {
                Param p = new Param();
                p.indexed = obj.has("indexed") ? obj.getBoolean("indexed") : null;
                p.name = obj.has("name") ? obj.getString("name") : null;
                p.type = obj.has("type") ? SolidityType.getType(obj.getString("type")) : null;
                return p;
            }

            public JSONObject toJSON() {
                JSONObject o = new JSONObject();
                o.put("indexed", indexed);
                o.put("name", name);
                o.put("type", type.toString());
                return o;
            }

            public static List<?> decodeList(List<Param> params, byte[] encoded) {
                List<Object> result = new ArrayList<>(params.size());

                int offset = 0;
                for (Param param : params) {
                    Object decoded =
                            param.type.isDynamicType()
                                    ? param.type.decode(
                                            encoded, decodeInt(encoded, offset).intValue())
                                    : param.type.decode(encoded, offset);
                    result.add(decoded);

                    offset += param.type.getFixedSize();
                }

                return result;
            }

            @Override
            public String toString() {
                return format(
                        "%s%s%s",
                        type.getCanonicalName(),
                        (indexed != null && indexed) ? " indexed " : " ",
                        name);
            }
        }

        public final Boolean anonymous;
        public final Boolean constant;
        public final Boolean payable;
        public final String name;
        public final List<Param> inputs;
        public final List<Param> outputs;
        public final Type type;

        public Entry(
                Boolean anonymous,
                Boolean constant,
                Boolean payable,
                String name,
                List<Param> inputs,
                List<Param> outputs,
                Type type) {
            this.anonymous = anonymous;
            this.constant = constant;
            this.payable = payable;
            this.name = name;
            this.inputs = inputs;
            this.outputs = outputs;
            this.type = type;
        }

        @SuppressWarnings("unchecked")
        public static <T extends Entry> T fromJSON(JSONObject obj) {
            Boolean anonymous = obj.has("anonymous") ? obj.getBoolean("anonymous") : null;
            Boolean constant = obj.has("constant") ? obj.getBoolean("constant") : null;
            Boolean payable = obj.has("payable") ? obj.getBoolean("payable") : null;
            String name = obj.has("name") ? obj.getString("name") : null;
            List<Param> inputs = new ArrayList<>();
            if (obj.has("inputs")) {
                JSONArray arr = obj.getJSONArray("inputs");
                for (int i = 0; i < arr.length(); i++) {
                    inputs.add(Param.fromJSON(arr.getJSONObject(i)));
                }
            }
            List<Param> outputs = new ArrayList<>();
            if (obj.has("outputs")) {
                JSONArray arr = obj.getJSONArray("outputs");
                for (int i = 0; i < arr.length(); i++) {
                    outputs.add(Param.fromJSON(arr.getJSONObject(i)));
                }
            }

            switch (obj.getString("type")) {
                case "constructor":
                    return (T) new Constructor(payable, inputs, outputs);
                case "fallback":
                    return (T) new Fallback(payable, inputs, outputs);
                case "function":
                    return (T) new Function(constant, payable, name, inputs, outputs);
                case "event":
                    return (T) new Event(anonymous, name, inputs, outputs);
                default:
                    throw new RuntimeException(
                            "Unrecognized ABI entry type: " + obj.getString("type"));
            }
        }

        public JSONObject toJSON() {
            JSONObject obj = new JSONObject();
            obj.put("anonymous", anonymous);
            obj.put("payable", payable);
            obj.put("constant", constant);
            obj.put("name", name);
            JSONArray arr = new JSONArray();
            for (Param p : inputs) {
                arr.put(p.toJSON());
            }
            obj.put("inputs", arr);
            arr = new JSONArray();
            for (Param p : outputs) {
                arr.put(p.toJSON());
            }
            obj.put("outputs", arr);
            obj.put("type", type.toString());
            return obj;
        }

        public String formatSignature() {
            StringBuilder paramsTypes = new StringBuilder();
            for (Entry.Param param : inputs) {
                paramsTypes.append(param.type.getCanonicalName()).append(",");
            }

            return format("%s(%s)", name, stripEnd(paramsTypes.toString(), ","));
        }

        public byte[] fingerprintSignature() {
            return h256(formatSignature().getBytes());
        }

        public byte[] encodeSignature() {
            return fingerprintSignature();
        }

        public static Entry create(
                boolean anonymous,
                boolean constant,
                boolean payable,
                String name,
                List<Param> inputs,
                List<Param> outputs,
                Type type) {
            Entry result = null;
            switch (type) {
                case constructor:
                    result = new Constructor(payable, inputs, outputs);
                    break;
                case fallback:
                    result = new Fallback(payable, inputs, outputs);
                    break;
                case function:
                    result = new Function(constant, payable, name, inputs, outputs);
                    break;
                case event:
                    result = new Event(anonymous, name, inputs, outputs);
                    break;
            }

            return result;
        }
    }

    public static class Constructor extends Entry {

        public Constructor(boolean payable, List<Param> inputs, List<Param> outputs) {
            super(null, null, payable, "", inputs, outputs, Type.constructor);
        }

        public List<?> decode(byte[] encoded) {
            return Param.decodeList(inputs, encoded);
        }
    }

    public static class Fallback extends Entry {

        public Fallback(boolean payable, List<Param> inputs, List<Param> outputs) {
            super(null, null, payable, "", inputs, outputs, Type.fallback);
        }

        public List<?> decode(byte[] encoded) {
            return Param.decodeList(inputs, encoded);
        }
    }

    public static class Function extends Entry {

        private static final int ENCODED_SIGN_LENGTH = 4;

        public Function(
                boolean constant,
                boolean payable,
                String name,
                List<Param> inputs,
                List<Param> outputs) {
            super(null, constant, payable, name, inputs, outputs, Type.function);
        }

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

        public byte[] encodeSignatureLong() {
            String signature = formatSignature();
            return h256(signature.getBytes());
        }

        public List<?> decode(byte[] encoded) {
            return Param.decodeList(inputs, subarray(encoded, ENCODED_SIGN_LENGTH, encoded.length));
        }

        public List<?> decodeResult(byte[] encoded) {
            return Param.decodeList(outputs, encoded);
        }

        @Override
        public byte[] encodeSignature() {
            return extractSignature(super.encodeSignature());
        }

        public static byte[] extractSignature(byte[] data) {
            return subarray(data, 0, ENCODED_SIGN_LENGTH);
        }
    }

    public static class Event extends Entry {

        public Event(boolean anonymous, String name, List<Param> inputs, List<Param> outputs) {
            super(anonymous, null, null, name, inputs, outputs, Type.event);
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
}

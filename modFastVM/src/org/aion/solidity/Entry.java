package org.aion.solidity;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.stripEnd;

import java.util.ArrayList;
import java.util.List;
import org.aion.fastvm.IExternalCapabilities;
import org.json.JSONArray;
import org.json.JSONObject;

public class Entry {
    public enum Type {
        constructor,
        fallback,
        function,
        event
    }

    public final Boolean anonymous;
    public final Boolean constant;
    public final Boolean payable;
    public final String name;
    public final List<Param> inputs;
    public final List<Param> outputs;
    public final Type type;
    private final IExternalCapabilities externalCapabilities;

    public Entry(
        Boolean anonymous,
        Boolean constant,
        Boolean payable,
        String name,
        List<Param> inputs,
        List<Param> outputs,
        Type type,
        IExternalCapabilities capabilities) {
        this.anonymous = anonymous;
        this.constant = constant;
        this.payable = payable;
        this.name = name;
        this.inputs = inputs;
        this.outputs = outputs;
        this.type = type;
        this.externalCapabilities = capabilities;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entry> T fromJSON(JSONObject obj, IExternalCapabilities capabilities) {
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
                return (T) new Constructor(payable, inputs, outputs, capabilities);
            case "fallback":
                return (T) new Fallback(payable, inputs, outputs, capabilities);
            case "function":
                return (T) new Function(constant, payable, name, inputs, outputs, capabilities);
            case "event":
                return (T) new Event(anonymous, name, inputs, outputs, capabilities);
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
        for (Param param : inputs) {
            paramsTypes.append(param.type.getCanonicalName()).append(",");
        }

        return format("%s(%s)", name, stripEnd(paramsTypes.toString(), ","));
    }

    public byte[] fingerprintSignature() {
        return this.externalCapabilities.hash256(formatSignature().getBytes());
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
        Type type,
        IExternalCapabilities capabilities) {
        Entry result = null;
        switch (type) {
            case constructor:
                result = new Constructor(payable, inputs, outputs, capabilities);
                break;
            case fallback:
                result = new Fallback(payable, inputs, outputs, capabilities);
                break;
            case function:
                result = new Function(constant, payable, name, inputs, outputs, capabilities);
                break;
            case event:
                result = new Event(anonymous, name, inputs, outputs, capabilities);
                break;
        }

        return result;
    }
}

package org.aion.solidity;

import static java.lang.String.format;
import static org.aion.solidity.SolidityType.IntType.decodeInt;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class Param {
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

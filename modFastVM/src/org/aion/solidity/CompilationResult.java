package org.aion.solidity;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


public class CompilationResult {

    public Map<String, Contract> contracts = new HashMap<>();

    public String version;

    /**
     * Parses the compilation results from JSON string.
     *
     * @param json
     * @return
     * @throws IOException
     */
    public static CompilationResult parse(String json) throws IOException {
        if (json == null || json.isEmpty()) {
            CompilationResult empty = new CompilationResult();
            empty.contracts = Collections.emptyMap();
            empty.version = "";
            return empty;
        } else {
            CompilationResult result = new CompilationResult();
            JSONObject obj = new JSONObject(json);
            result.version = obj.has("version") ? obj.getString("version") : null;
            if (obj.has("contracts")) {
                JSONObject c = obj.getJSONObject("contracts");
                for (String k : c.keySet()) {
                    result.contracts.put(k.replace("<stdin>:", ""), Contract.fromJSON(c.getJSONObject(k)));
                }
            }
            return result;
        }
    }

    /**
     * Represents a compiled contract.
     */
    public static class Contract {
        public String abi;
        public String bin;
        public String interface0;
        public String metadata;

        public static Contract fromJSON(JSONObject obj) {
            Contract meta = new Contract();
            meta.abi = obj.has("abi") ? obj.getString("abi") : null;
            meta.bin = obj.has("bin") ? obj.getString("bin") : null;
            meta.interface0 = obj.has("interface") ? obj.getString("interface") : null;
            meta.metadata = obj.has("metadata") ? obj.getString("metadata") : null;

            return meta;
        }
    }
}

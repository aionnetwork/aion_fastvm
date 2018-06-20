/*******************************************************************************
 *
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>
 *
 * Contributors:
 *     Aion foundation.
 ******************************************************************************/
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
     */
    public static CompilationResult parse(String json) {
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

package org.aion.solidity;

import java.util.ArrayList;
import java.util.List;
import org.aion.fastvm.IExternalCapabilities;
import org.apache.commons.collections4.Predicate;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Abi {

    private List<Entry> entries = new ArrayList<>();

    public static Abi fromJSON(String json, IExternalCapabilities capabilities) {
        Abi abi = new Abi();

        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);
            abi.entries.add(Entry.fromJSON(obj, capabilities));
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

    public Entry[] getEntries() {
        Entry[] _entries = new Entry[this.entries.size()];
        entries.toArray(_entries);
        return _entries;
    }

    @Override
    public String toString() {
        return toJSON();
    }
}

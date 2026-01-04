package com.talosprotocol.talos.canonical;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;

/**
 * Canonical JSON serialization.
 */
public class CanonicalJson {
    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * Marshal object to canonical JSON (sorted keys, no whitespace).
     */
    public static byte[] marshal(Object v) throws IOException {
        // First convert to generic map structure to ensure we treat everything as data
        // and ordering is based on keys, not field order (though field order doesn't
        // matter for Map)
        Object generic = mapper.convertValue(v, Object.class);
        return mapper.writeValueAsBytes(generic);
    }
}

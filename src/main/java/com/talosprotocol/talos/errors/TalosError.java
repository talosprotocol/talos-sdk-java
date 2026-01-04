package com.talosprotocol.talos.errors;

import java.util.Collections;
import java.util.Map;

/**
 * Canonical error type for Talos SDK.
 */
public class TalosError extends RuntimeException {
    private final TalosErrorCode code;
    private final Map<String, Object> details;
    private final String requestId;

    public TalosError(TalosErrorCode code, String message) {
        this(code, message, null, null, null);
    }

    public TalosError(TalosErrorCode code, String message, Throwable cause) {
        this(code, message, null, null, cause);
    }

    public TalosError(TalosErrorCode code, String message, Map<String, Object> details, String requestId,
            Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details == null ? Collections.emptyMap() : Collections.unmodifiableMap(details);
        this.requestId = requestId;
    }

    public TalosErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getRequestId() {
        return requestId;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s", code, getMessage());
    }
}

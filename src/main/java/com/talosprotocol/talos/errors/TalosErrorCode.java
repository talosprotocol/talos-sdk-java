package com.talosprotocol.talos.errors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard Talos error codes.
 */
public enum TalosErrorCode {
    // Authorization
    TALOS_DENIED,
    TALOS_INVALID_CAPABILITY,

    // Protocol
    TALOS_PROTOCOL_MISMATCH,
    TALOS_FRAME_INVALID,

    // Crypto
    TALOS_CRYPTO_ERROR,
    TALOS_INVALID_INPUT,

    // Transport
    TALOS_TRANSPORT_TIMEOUT,
    TALOS_TRANSPORT_ERROR;
}

package com.talosprotocol.talos.crypto;

import com.talosprotocol.talos.errors.TalosError;
import com.talosprotocol.talos.errors.TalosErrorCode;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.SecureRandom;

/**
 * Cryptographic operations using Bouncy Castle.
 */
public class Crypto {
    private static final SecureRandom random = new SecureRandom();

    public static byte[] sign(Ed25519PrivateKeyParameters privateKey, byte[] message) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privateKey);
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }

    public static boolean verify(Ed25519PublicKeyParameters publicKey, byte[] message, byte[] signature) {
        if (signature.length != 64)
            return false;
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, publicKey);
        verifier.update(message, 0, message.length);
        return verifier.verifySignature(signature);
    }

    public static Ed25519PrivateKeyParameters generateKey() {
        return new Ed25519PrivateKeyParameters(random);
    }

    public static Ed25519PrivateKeyParameters fromSeed(byte[] seed) {
        if (seed.length != Ed25519PrivateKeyParameters.KEY_SIZE) {
            throw new TalosError(TalosErrorCode.TALOS_INVALID_INPUT, "Invalid seed length");
        }
        return new Ed25519PrivateKeyParameters(seed, 0);
    }
}

package com.talosprotocol.talos.crypto;

import java.security.SecureRandom;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.params.HKDFParameters;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.X25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import com.talosprotocol.talos.errors.TalosError;
import com.talosprotocol.talos.errors.TalosErrorCode;

/**
 * Cryptographic operations using Bouncy Castle.
 */
public class Crypto {
    private static final SecureRandom random = new SecureRandom();

    public static Ed25519PrivateKeyParameters generateKey() {
        return new Ed25519PrivateKeyParameters(random);
    }

    public static Ed25519PrivateKeyParameters fromSeed(byte[] seed) {
        if (seed.length != Ed25519PrivateKeyParameters.KEY_SIZE) {
            throw new TalosError(TalosErrorCode.TALOS_INVALID_INPUT, "Invalid seed length");
        }
        return new Ed25519PrivateKeyParameters(seed, 0);
    }

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

    // --- X25519 ---
    public static class KeyPairX25519 {
        public final byte[] privateKey;
        public final byte[] publicKey;

        public KeyPairX25519(byte[] privateKey, byte[] publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }
    }

    public static KeyPairX25519 x25519Generate() {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(random);
        X25519PublicKeyParameters pub = priv.generatePublicKey();
        return new KeyPairX25519(priv.getEncoded(), pub.getEncoded());
    }

    public static byte[] x25519Dh(byte[] privateKey, byte[] publicKey) {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(privateKey, 0);
        X25519PublicKeyParameters pub = new X25519PublicKeyParameters(publicKey, 0);
        byte[] secret = new byte[32];
        priv.generateSecret(pub, secret, 0);
        return secret;
    }
    
    public static byte[] x25519GetPublic(byte[] privateKey) {
        X25519PrivateKeyParameters priv = new X25519PrivateKeyParameters(privateKey, 0);
        return priv.generatePublicKey().getEncoded();
    }

    // --- HKDF ---
    public static byte[] hkdfDerive(byte[] ikm, byte[] salt, byte[] info, int length) {
        HKDFBytesGenerator hkdf = new HKDFBytesGenerator(new SHA256Digest());
        hkdf.init(new HKDFParameters(ikm, salt, info));
        byte[] okm = new byte[length];
        hkdf.generateBytes(okm, 0, length);
        return okm;
    }

    // --- HMAC-SHA256 ---
    public static byte[] hmacSha256(byte[] key, byte[] data) {
        HMac hmac = new HMac(new SHA256Digest());
        hmac.init(new KeyParameter(key));
        hmac.update(data, 0, data.length);
        byte[] output = new byte[hmac.getMacSize()];
        hmac.doFinal(output, 0);
        return output;
    }

    // --- AEAD (ChaCha20-Poly1305) ---
    public static byte[] encryptWithNonce(byte[] key, byte[] nonce, byte[] plaintext, byte[] ad) {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(true, new ParametersWithIV(new KeyParameter(key), nonce));
        if (ad != null && ad.length > 0) {
            cipher.processAADBytes(ad, 0, ad.length);
        }

        byte[] output = new byte[plaintext.length + 16];
        int len = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);
        try {
            cipher.doFinal(output, len);
        } catch (Exception e) {
            throw new TalosError(TalosErrorCode.TALOS_CRYPTO_ERROR, "Encryption failed", e);
        }
        return output;
    }

    public static byte[] decryptWithNonce(byte[] key, byte[] nonce, byte[] ciphertext, byte[] ad) {
        ChaCha20Poly1305 cipher = new ChaCha20Poly1305();
        cipher.init(false, new ParametersWithIV(new KeyParameter(key), nonce));
        if (ad != null && ad.length > 0) {
            cipher.processAADBytes(ad, 0, ad.length);
        }

        byte[] output = new byte[ciphertext.length - 16];
        int len = cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);
        try {
            cipher.doFinal(output, len);
        } catch (Exception e) {
            throw new TalosError(TalosErrorCode.TALOS_CRYPTO_ERROR, "Decryption failed: " + e.getMessage(), e);
        }
        return output;
    }
}

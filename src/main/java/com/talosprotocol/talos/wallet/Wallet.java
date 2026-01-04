package com.talosprotocol.talos.wallet;

import java.math.BigInteger;
import java.security.MessageDigest;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;

import com.talosprotocol.talos.crypto.Crypto;
import com.talosprotocol.talos.errors.TalosError;
import com.talosprotocol.talos.errors.TalosErrorCode;

/**
 * Talos identity wallet.
 */
public class Wallet {
    private final Ed25519PrivateKeyParameters privateKey;
    private final Ed25519PublicKeyParameters publicKey;
    private final String name;

    private static final String BASE58_ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private Wallet(Ed25519PrivateKeyParameters privateKey, String name) {
        this.privateKey = privateKey;
        this.publicKey = privateKey.generatePublicKey();
        this.name = name;
    }

    public static Wallet generate(String name) {
        return new Wallet(Crypto.generateKey(), name);
    }

    public static Wallet fromSeed(byte[] seed, String name) {
        return new Wallet(Crypto.fromSeed(seed), name);
    }

    public byte[] getPublicKey() {
        return publicKey.getEncoded();
    }

    public String getName() {
        return name;
    }

    public String toDid() {
        byte[] prefix = new byte[] { (byte) 0xED, 0x01 };
        byte[] pub = getPublicKey();
        byte[] input = new byte[prefix.length + pub.length];
        System.arraycopy(prefix, 0, input, 0, prefix.length);
        System.arraycopy(pub, 0, input, prefix.length, pub.length);

        return "did:key:z" + encodeBase58(input);
    }

    public String address() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(getPublicKey());
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new TalosError(TalosErrorCode.TALOS_CRYPTO_ERROR, "SHA-256 not available", e);
        }
    }

    public byte[] sign(byte[] message) {
        return Crypto.sign(privateKey, message);
    }

    public static boolean verify(byte[] message, byte[] signature, byte[] publicKey) {
        if (publicKey.length != 32)
            return false;
        try {
            Ed25519PublicKeyParameters pk = new Ed25519PublicKeyParameters(publicKey, 0);
            return Crypto.verify(pk, message, signature);
        } catch (Exception e) {
            return false;
        }
    }

    private static String encodeBase58(byte[] input) {
        if (input.length == 0)
            return "";

        BigInteger bi = new BigInteger(1, input);
        StringBuilder s = new StringBuilder();
        BigInteger base = BigInteger.valueOf(58);
        while (bi.compareTo(BigInteger.ZERO) > 0) {
            BigInteger[] divmod = bi.divideAndRemainder(base);
            bi = divmod[0];
            s.insert(0, BASE58_ALPHABET.charAt(divmod[1].intValue()));
        }

        for (byte b : input) {
            if (b == 0)
                s.insert(0, BASE58_ALPHABET.charAt(0));
            else
                break;
        }

        return s.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

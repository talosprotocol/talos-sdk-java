package com.talosprotocol.talos.crypto;

import java.nio.charset.StandardCharsets;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class CryptoTest {

	@Test
	void testEd25519SignVerify() {
		Ed25519PrivateKeyParameters priv = Crypto.generateKey();
		byte[] msg = "hello".getBytes(StandardCharsets.UTF_8);
		byte[] sig = Crypto.sign(priv, msg);

		assertEquals(64, sig.length);

		Ed25519PublicKeyParameters pub = priv.generatePublicKey();
		assertTrue(Crypto.verify(pub, msg, sig));

		byte[] badMsg = "hallo".getBytes(StandardCharsets.UTF_8);
		assertFalse(Crypto.verify(pub, badMsg, sig));
	}

	@Test
	void testFromSeed() {
		byte[] seed = new byte[32]; // all zeros
		Ed25519PrivateKeyParameters priv = Crypto.fromSeed(seed);
		assertNotNull(priv);

		// Deterministic check
		Ed25519PrivateKeyParameters priv2 = Crypto.fromSeed(seed);
		assertArrayEquals(priv.getEncoded(), priv2.getEncoded());
	}

	@Test
	void testX25519() {
		Crypto.KeyPairX25519 kp1 = Crypto.x25519Generate();
		Crypto.KeyPairX25519 kp2 = Crypto.x25519Generate();

		byte[] secret1 = Crypto.x25519Dh(kp1.privateKey, kp2.publicKey);
		byte[] secret2 = Crypto.x25519Dh(kp2.privateKey, kp1.publicKey);

		assertArrayEquals(secret1, secret2);
		assertEquals(32, secret1.length);
	}

	@Test
	void testAead() {
		byte[] key = new byte[32];
		byte[] nonce = new byte[12];
		byte[] plain = "secret".getBytes(StandardCharsets.UTF_8);
		byte[] ad = "header".getBytes(StandardCharsets.UTF_8);

		byte[] cipher = Crypto.encryptWithNonce(key, nonce, plain, ad);
		byte[] decrypted = Crypto.decryptWithNonce(key, nonce, cipher, ad);

		assertArrayEquals(plain, decrypted);
	}

	@Test
	void testHmac() {
		byte[] key = new byte[32];
		byte[] data = "data".getBytes(StandardCharsets.UTF_8);
		byte[] mac = Crypto.hmacSha256(key, data);
		assertEquals(32, mac.length);
	}
}

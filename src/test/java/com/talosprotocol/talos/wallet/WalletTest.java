package com.talosprotocol.talos.wallet;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class WalletTest {

	@Test
	void testGenerate() {
		Wallet w = Wallet.generate("Alice");
		assertEquals("Alice", w.getName());
		assertEquals(32, w.getPublicKey().length);
	}

	@Test
	void testFromSeed() {
		byte[] seed = new byte[32];
		Wallet w = Wallet.fromSeed(seed, "Bob");
		assertEquals("Bob", w.getName());

		// Valid DID for 0 seed
		assertTrue(w.toDid().startsWith("did:key:z"));
	}

	@Test
	void testSignVerify() {
		Wallet w = Wallet.generate("Signer");
		byte[] msg = "test".getBytes(StandardCharsets.UTF_8);
		byte[] sig = w.sign(msg);

		assertEquals(64, sig.length);
		assertTrue(Wallet.verify(msg, sig, w.getPublicKey()));

		assertFalse(Wallet.verify(msg, new byte[64], w.getPublicKey()));
	}

	@Test
	void testAddress() {
		Wallet w = Wallet.generate("Addr");
		String addr = w.address();
		assertEquals(64, addr.length()); // hex encoded sha256
	}
}

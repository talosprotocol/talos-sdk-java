package com.talosprotocol.talos.ratchet;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.talosprotocol.talos.crypto.Crypto;
import com.talosprotocol.talos.crypto.Crypto.KeyPairX25519;

class RatchetSessionTest {

    @Test
    void testRatchetPingPong() {
        // Alice Init
        KeyPairX25519 aliceId = Crypto.x25519Generate();
        
        // Bob Init
        KeyPairX25519 bobId = Crypto.x25519Generate();
        KeyPairX25519 bobSpk = Crypto.x25519Generate();
        
        RatchetSession alice = new RatchetSession();
        // initializeAsInitiator(sk_id, pk_peer, prekey, signed_prekey, onetime, test_eph)
        alice.initializeAsInitiator(aliceId.privateKey, bobId.publicKey, null, bobSpk.publicKey, null, null);
        
        RatchetSession bob = new RatchetSession();
        
        // Bob setup: initializeAsResponder(sk_id, sk_spk, sk_onetime, pk_init, pk_eph)
        bob.initializeAsResponder(bobId.privateKey, bobSpk.privateKey, null, aliceId.publicKey, alice.getState().dh_public);
        
        // Alice sends
        String msg1 = "Hello Bob";
        byte[] pt1 = msg1.getBytes(StandardCharsets.UTF_8);
        String ct1 = alice.encrypt(pt1, null);
        
        // Bob receives
        byte[] decrypted1 = bob.decrypt(ct1);
        assertEquals(msg1, new String(decrypted1, StandardCharsets.UTF_8));
        
        // Bob replies
        String msg2 = "Hello Alice";
        byte[] pt2 = msg2.getBytes(StandardCharsets.UTF_8);
        String ct2 = bob.encrypt(pt2, null);
        
        // Alice receives
        byte[] decrypted2 = alice.decrypt(ct2);
        assertEquals(msg2, new String(decrypted2, StandardCharsets.UTF_8));
    }
    
    @Test
    void testOutOfOrder() {
        KeyPairX25519 aliceId = Crypto.x25519Generate();
        KeyPairX25519 bobId = Crypto.x25519Generate();
        KeyPairX25519 bobSpk = Crypto.x25519Generate();
        
        RatchetSession alice = new RatchetSession();
        alice.initializeAsInitiator(aliceId.privateKey, bobId.publicKey, null, bobSpk.publicKey, null, null);
        
        RatchetSession bob = new RatchetSession();
        bob.initializeAsResponder(bobId.privateKey, bobSpk.privateKey, null, aliceId.publicKey, alice.getState().dh_public);
        
        byte[] pt1 = "Msg1".getBytes();
        byte[] pt2 = "Msg2".getBytes();
        
        String ct1 = alice.encrypt(pt1, null);
        String ct2 = alice.encrypt(pt2, null);
        
        // Bob decrypts 2 first (skipping 1)
        byte[] dec2 = bob.decrypt(ct2);
        assertEquals("Msg2", new String(dec2));
        
        // Bob decrypts 1 (from skip storage)
        byte[] dec1 = bob.decrypt(ct1);
        assertEquals("Msg1", new String(dec1));
    }
}

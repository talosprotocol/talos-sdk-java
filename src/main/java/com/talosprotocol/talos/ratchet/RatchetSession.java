package com.talosprotocol.talos.ratchet;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talosprotocol.talos.crypto.Crypto;
import com.talosprotocol.talos.crypto.Crypto.KeyPairX25519;
import com.talosprotocol.talos.errors.TalosError;
import com.talosprotocol.talos.errors.TalosErrorCode;

public class RatchetSession {
	private static final byte[] EMPTY_SALT = null;
	private static final byte[] INFO_ROOT = "talos-double-ratchet-root".getBytes(StandardCharsets.UTF_8);
	private static final byte[] INFO_MESSAGE = "talos-double-ratchet-message".getBytes(StandardCharsets.UTF_8);
	private static final byte[] INFO_CHAIN = "talos-double-ratchet-chain".getBytes(StandardCharsets.UTF_8);
	private static final byte[] INFO_X3DH = "x3dh-init".getBytes(StandardCharsets.UTF_8);

	private static final int MAX_SKIP = 1000;

	private final RatchetState state = new RatchetState();
	private static final ObjectMapper mapper = new ObjectMapper();

	public byte[] testNextRatchetKey = null;

	public static class RatchetState {
		public byte[] dh_private;
		public byte[] dh_public;
		public byte[] dh_remote;
		public byte[] root_key;
		public byte[] chain_key_s;
		public byte[] chain_key_r;
		public int n_s = 0;
		public int n_r = 0;
		public int pn = 0;
		public Map<String, byte[]> skipped_message_keys = new HashMap<>();
	}

	public RatchetState getState() {
		return state;
	}

	public void initializeAsInitiator(byte[] sk_identity, byte[] pk_peer, byte[] prekey_public,
			byte[] signed_prekey_public, byte[] onetime_prekey_public, byte[] test_ephemeral_private) {

		if (test_ephemeral_private != null) {
			state.dh_private = test_ephemeral_private;
			state.dh_public = Crypto.x25519GetPublic(test_ephemeral_private);
		} else {
			KeyPairX25519 epk = Crypto.x25519Generate();
			state.dh_private = epk.privateKey;
			state.dh_public = epk.publicKey;
		}
		state.dh_remote = signed_prekey_public;

		byte[] dh_x3dh = Crypto.x25519Dh(state.dh_private, signed_prekey_public);
		state.root_key = Crypto.hkdfDerive(dh_x3dh, EMPTY_SALT, INFO_X3DH, 32);

		byte[] dh_shared = dh_x3dh;
		byte[] rk_ck = kdfRk(state.root_key, dh_shared);
		state.root_key = Arrays.copyOfRange(rk_ck, 0, 32);
		state.chain_key_s = Arrays.copyOfRange(rk_ck, 32, 64);

		state.pn = 0;
		state.n_s = 0;
	}

	public void initializeAsResponder(byte[] sk_identity, byte[] sk_signed_prekey, byte[] sk_onetime_prekey,
			byte[] pk_initiator, byte[] pk_ephemeral) {

		state.dh_private = sk_signed_prekey;
		state.dh_public = Crypto.x25519GetPublic(sk_signed_prekey);
		state.dh_remote = pk_ephemeral;

		byte[] dh_x3dh = Crypto.x25519Dh(state.dh_private, pk_ephemeral);
		state.root_key = Crypto.hkdfDerive(dh_x3dh, EMPTY_SALT, INFO_X3DH, 32);

		byte[] dh_shared = dh_x3dh;
		byte[] rk_ck = kdfRk(state.root_key, dh_shared);
		state.root_key = Arrays.copyOfRange(rk_ck, 0, 32);
		state.chain_key_r = Arrays.copyOfRange(rk_ck, 32, 64);

		state.chain_key_s = null;
		state.n_r = 0;
	}

	private void initializeSendingChain() {
		KeyPairX25519 newPair;
		if (testNextRatchetKey != null) {
			newPair = new KeyPairX25519(testNextRatchetKey, Crypto.x25519GetPublic(testNextRatchetKey));
			testNextRatchetKey = null;
		} else {
			newPair = Crypto.x25519Generate();
		}

		state.dh_private = newPair.privateKey;
		state.dh_public = newPair.publicKey;

		byte[] dh_shared = Crypto.x25519Dh(state.dh_private, state.dh_remote);
		byte[] rk_ck = kdfRk(state.root_key, dh_shared);
		state.root_key = Arrays.copyOfRange(rk_ck, 0, 32);
		state.chain_key_s = Arrays.copyOfRange(rk_ck, 32, 64);

		state.pn = state.n_s;
		state.n_s = 0;
	}

	public String encrypt(byte[] plaintext, byte[] explicitNonce) {
		if (state.chain_key_s == null) {
			initializeSendingChain();
		}

		byte[][] kdfResults = kdfCk(state.chain_key_s);
		byte[] mk = kdfResults[0];
		state.chain_key_s = kdfResults[1];

		Map<String, Object> header = new TreeMap<>();
		header.put("dh", b64u(state.dh_public));
		header.put("pn", state.pn);
		header.put("n", state.n_s);
		state.n_s++;

		byte[] headerBytes = canonicalize(header);

		byte[] nonce = explicitNonce;
		if (nonce == null) {
			nonce = new byte[12];
		}

		byte[] ciphertext = Crypto.encryptWithNonce(mk, nonce, plaintext, headerBytes);

		Map<String, Object> envelope = new TreeMap<>();
		envelope.put("header", header);
		envelope.put("nonce", b64u(nonce));
		envelope.put("ciphertext", b64u(ciphertext));

		byte[] wireBytes = canonicalize(envelope);
		return b64u(wireBytes);
	}

	public byte[] decrypt(String wireMessageB64) {
		byte[] wireBytes = d64u(wireMessageB64);
		JsonNode envelope;
		try {
			envelope = mapper.readTree(wireBytes);
		} catch (Exception e) {
			throw new TalosError(TalosErrorCode.TALOS_FRAME_INVALID, "Invalid wire message JSON");
		}

		JsonNode headerNode = envelope.get("header");
		byte[] dhRemote = d64u(headerNode.get("dh").asText());
		int pn = headerNode.get("pn").asInt();
		int n = headerNode.get("n").asInt();

		byte[] headerBytes = canonicalize(headerNode);
		byte[] nonce = d64u(envelope.get("nonce").asText());
		byte[] ciphertext = d64u(envelope.get("ciphertext").asText());

		byte[] plaintext = trySkippedMessageKeys(dhRemote, n, ciphertext, headerBytes, nonce);
		if (plaintext != null)
			return plaintext;

		if (!Arrays.equals(dhRemote, state.dh_remote)) {
			skipMessageKeys(pn);
			dhRatchet(dhRemote);
		}

		skipMessageKeys(n);

		byte[][] kdfResults = kdfCk(state.chain_key_r);
		byte[] mk = kdfResults[0];
		state.chain_key_r = kdfResults[1];
		state.n_r++;

		return Crypto.decryptWithNonce(mk, nonce, ciphertext, headerBytes);
	}

	private void dhRatchet(byte[] dh_remote) {
		state.pn = state.n_s;
		state.n_s = 0;
		state.n_r = 0;
		state.dh_remote = dh_remote;

		byte[] dh_shared = Crypto.x25519Dh(state.dh_private, state.dh_remote);
		byte[] rk_ck = kdfRk(state.root_key, dh_shared);
		state.root_key = Arrays.copyOfRange(rk_ck, 0, 32);
		state.chain_key_r = Arrays.copyOfRange(rk_ck, 32, 64);

		initializeSendingChain();
	}

	private byte[] kdfRk(byte[] rk, byte[] dh_out) {
		byte[] ikm = new byte[rk.length + dh_out.length];
		System.arraycopy(rk, 0, ikm, 0, rk.length);
		System.arraycopy(dh_out, 0, ikm, rk.length, dh_out.length);
		return Crypto.hkdfDerive(ikm, EMPTY_SALT, INFO_ROOT, 64);
	}

	private byte[][] kdfCk(byte[] ck) {
		byte[] mk = Crypto.hkdfDerive(ck, EMPTY_SALT, INFO_MESSAGE, 32);
		byte[] nextCk = Crypto.hkdfDerive(ck, EMPTY_SALT, INFO_CHAIN, 32);
		return new byte[][]{mk, nextCk};
	}

	private void skipMessageKeys(int until) {
		if (state.n_r + MAX_SKIP < until) {
			throw new TalosError(TalosErrorCode.TALOS_FRAME_INVALID, "Too many skipped messages");
		}
		while (state.n_r < until) {
			byte[][] kdfResults = kdfCk(state.chain_key_r);
			byte[] mk = kdfResults[0];
			state.chain_key_r = kdfResults[1];

			String key = b64u(state.dh_remote) + "|" + state.n_r;
			state.skipped_message_keys.put(key, mk);
			state.n_r++;
		}
	}

	private byte[] trySkippedMessageKeys(byte[] dhRemote, int n, byte[] ciphertext, byte[] aad, byte[] nonce) {
		String key = b64u(dhRemote) + "|" + n;
		if (state.skipped_message_keys.containsKey(key)) {
			byte[] mk = state.skipped_message_keys.remove(key);
			return Crypto.decryptWithNonce(mk, nonce, ciphertext, aad);
		}
		return null;
	}

	private byte[] canonicalize(Object obj) {
		try {
			return com.talosprotocol.talos.canonical.CanonicalJson.marshal(obj);
		} catch (Exception e) {
			throw new RuntimeException("Canonicalization failed", e);
		}
	}

	private String b64u(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}

	private byte[] d64u(String s) {
		return Base64.getUrlDecoder().decode(s);
	}
}

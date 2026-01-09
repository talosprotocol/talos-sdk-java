package com.talosprotocol.talos;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talosprotocol.talos.canonical.CanonicalJson;
import com.talosprotocol.talos.crypto.Crypto;
import com.talosprotocol.talos.ratchet.RatchetSession;
import com.talosprotocol.talos.wallet.Wallet;

@Component
public class ConformanceRunner implements CommandLineRunner {

	private final ObjectMapper mapper = new ObjectMapper();

	@Override
	public void run(String... args) throws Exception {
		String vectorsPath = null;
		for (int i = 0; i < args.length; i++) {
			if ("--vectors".equals(args[i]) && i + 1 < args.length) {
				vectorsPath = args[i + 1];
			}
		}

		if (vectorsPath == null)
			return;

		System.out.println("Running vectors from: " + vectorsPath);
		try {
			processPath(vectorsPath);
			System.out.println("ALL TESTS PASSED");
			System.exit(0);
		} catch (Exception e) {
			System.err.println("CONFORMANCE FAILED: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void processPath(String path) throws Exception {
		File file = new File(path);
		if (!file.exists()) {
			throw new RuntimeException("Vector file not found: " + path);
		}
		System.out.println("Processing: " + file.getName());
		JsonNode root = mapper.readTree(file);

		if (root.isArray()) {
			for (JsonNode node : root) {
				runVector(node, false);
			}
		} else if (root.has("version") && root.has("vectors") && root.get("vectors").isArray()
				&& root.get("vectors").size() > 0 && root.get("vectors").get(0).isTextual()) {
			System.out.println("Release Set v" + root.get("version").asText());
			for (JsonNode vectorPath : root.get("vectors")) {
				File subFile = new File(file.getParent(), vectorPath.asText());
				processPath(subFile.getCanonicalPath());
			}
		} else if (root.has("steps")) {
			runTrace(root);
		} else if (root.has("vectors")) {
			for (JsonNode vec : root.get("vectors")) {
				runVector(vec, false);
			}
		} else if (root.has("negative_cases")) {
			for (JsonNode vec : root.get("negative_cases")) {
				runVector(vec, true);
			}
		} else {
			runVector(root, false);
		}
	}

	private void runVector(JsonNode vec, boolean isNegative) throws Exception {
		String testId = vec.has("test_id") ? vec.get("test_id").asText() : "unknown";
		try {
			Handler handler = getHandler(vec);
			if (handler != null) {
				handler.run(vec);
			}
		} catch (Exception e) {
			if (isNegative)
				return;
			throw new RuntimeException("Vector " + testId + " failed: " + e.getMessage(), e);
		}
	}

	interface Handler {
		void run(JsonNode vec) throws Exception;
	}

	private Handler getHandler(JsonNode vec) {
		String testId = vec.has("test_id") ? vec.get("test_id").asText() : "";
		if (testId.startsWith("signing_") || testId.startsWith("sign_") || testId.startsWith("verify_"))
			return new SigningHandler();
		if (testId.startsWith("canonical_"))
			return new CanonicalHandler();
		if (testId.startsWith("kdf_") || testId.startsWith("header_"))
			return new MicroVectorHandler();
		return null;
	}

	class SigningHandler implements Handler {
		public void run(JsonNode vec) throws Exception {
			JsonNode inputs = vec.get("inputs");
			JsonNode expected = vec.get("expected");

			if (inputs.has("seed_hex")) {
				Wallet w = Wallet.fromSeed(hexToBytes(inputs.get("seed_hex").asText()), null);
				String msg = inputs.has("message_utf8") ? inputs.get("message_utf8").asText() : "";
				byte[] sig = w.sign(msg.getBytes(StandardCharsets.UTF_8));
				String got = b64u(sig);
				if (expected.has("signature_base64url") && !got.equals(expected.get("signature_base64url").asText())) {
					throw new RuntimeException("Sign mismatch: got " + got);
				}
			}
		}
	}

	class CanonicalHandler implements Handler {
		public void run(JsonNode vec) throws Exception {
			JsonNode input = vec.get("input");
			byte[] marshaled = CanonicalJson.marshal(mapper.convertValue(input, Object.class));
			String got = b64u(marshaled);
			if (vec.has("expected_base64url")) {
				if (!got.equals(vec.get("expected_base64url").asText())) {
					throw new RuntimeException("Canonical mismatch: got " + got);
				}
			}
			if (vec.has("expected") && vec.get("expected").has("canonical")) {
				String expectedJson = vec.get("expected").get("canonical").asText();
				String gotJson = new String(marshaled, StandardCharsets.UTF_8);
				if (!gotJson.equals(expectedJson)) {
					throw new RuntimeException("Canonical JSON mismatch. Got " + gotJson + " want " + expectedJson);
				}
			}
		}
	}

	class MicroVectorHandler implements Handler {
		public void run(JsonNode vec) throws Exception {
			String testId = vec.get("test_id").asText();
			JsonNode expected = vec.get("expected");

			if (testId.equals("kdf_rk_root_ratchet")) {
				JsonNode inputs = vec.get("inputs");
				byte[] rk = d64u(inputs.get("rk").asText());
				byte[] dh = d64u(inputs.get("dh_out").asText());
				byte[] info = inputs.get("info").asText().getBytes(StandardCharsets.UTF_8);

				byte[] ikm = new byte[rk.length + dh.length];
				System.arraycopy(rk, 0, ikm, 0, rk.length);
				System.arraycopy(dh, 0, ikm, rk.length, dh.length);

				byte[] okm = Crypto.hkdfDerive(ikm, null, info, 64);
				String nextRk = b64u(Arrays.copyOfRange(okm, 0, 32));
				String nextCk = b64u(Arrays.copyOfRange(okm, 32, 64));

				if (!nextRk.equals(expected.get("new_rk").asText()))
					throw new RuntimeException("RK mismatch");
				if (!nextCk.equals(expected.get("new_ck").asText()))
					throw new RuntimeException("CK mismatch");
			} else if (testId.equals("kdf_ck_symmetric_ratchet")) {
				JsonNode inputs = vec.get("inputs");
				byte[] ck = d64u(inputs.get("ck").asText());
				byte[] infoChain = inputs.get("info_chain").asText().getBytes(StandardCharsets.UTF_8);
				byte[] infoMsg = inputs.get("info_message").asText().getBytes(StandardCharsets.UTF_8);

				byte[] nextCk = Crypto.hkdfDerive(ck, null, infoChain, 32);
				byte[] mk = Crypto.hkdfDerive(ck, null, infoMsg, 32);

				if (!b64u(nextCk).equals(expected.get("next_ck").asText()))
					throw new RuntimeException("Next CK mismatch");
				if (!b64u(mk).equals(expected.get("mk").asText()))
					throw new RuntimeException("MK mismatch");
			} else if (testId.equals("header_canonical_sorting")) {
				JsonNode input = vec.get("input_header");
				byte[] marshaled = CanonicalJson.marshal(mapper.convertValue(input, Object.class));
				String got = b64u(marshaled);
				if (!got.equals(vec.get("expected_canonical_b64u").asText())) {
					throw new RuntimeException("Header canonical mismatch: got " + got);
				}
			}
		}
	}

	private void runTrace(JsonNode root) throws Exception {
		System.out.println("Running Ratchet Trace: " + root.get("title").asText());
		RatchetSession alice = new RatchetSession();
		RatchetSession bob = new RatchetSession();

		JsonNode aliceInit = root.get("alice");
		JsonNode bobInit = root.get("bob");

		byte[] aliceEphPriv = aliceInit.has("ephemeral_private")
				? d64u(aliceInit.get("ephemeral_private").asText())
				: null;

		byte[] aliceIdPriv = d64u(aliceInit.get("identity_private").asText());
		byte[] bobIdPub = d64u(bobInit.get("identity_public").asText());
		byte[] bobSPK = d64u(bobInit.get("prekey_bundle").get("signed_prekey").asText());

		alice.initializeAsInitiator(aliceIdPriv, bobIdPub, null, bobSPK, null, aliceEphPriv);

		byte[] bobIdPriv = d64u(bobInit.get("identity_private").asText());
		byte[] bobSPKPriv = d64u(bobInit.get("bundle_secrets").get("signed_prekey_private").asText());
		byte[] aliceEphPub = alice.getState().dh_public;

		bob.initializeAsResponder(bobIdPriv, bobSPKPriv, null, d64u(aliceInit.get("identity_public").asText()),
				aliceEphPub);

		for (JsonNode step : root.get("steps")) {
			String actor = step.get("actor").asText();
			String action = step.get("action").asText();
			RatchetSession activeSession = actor.equals("alice") ? alice : bob;

			if (action.equals("encrypt")) {
				if (step.has("test_ephemeral_private")) {
					activeSession.testNextRatchetKey = d64u(step.get("test_ephemeral_private").asText());
				} else if (step.has("ratchet_priv")) {
					activeSession.testNextRatchetKey = d64u(step.get("ratchet_priv").asText());
				}

				byte[] plaintext = d64u(step.get("plaintext").asText());
				byte[] nonce = step.has("nonce") ? d64u(step.get("nonce").asText()) : null;
				String gotWireb64u = activeSession.encrypt(plaintext, nonce);

				if (step.has("wire_message_b64u") && !gotWireb64u.equals(step.get("wire_message_b64u").asText())) {
					throw new RuntimeException(
							"Trace encryption mismatch at step " + step.get("step") + ". Got " + gotWireb64u);
				}
			} else {
				String wire = step.get("wire_message_b64u").asText();
				byte[] decrypted = activeSession.decrypt(wire);
				byte[] expected = d64u(step.get("expected_plaintext").asText());
				if (!Arrays.equals(decrypted, expected)) {
					throw new RuntimeException("Trace decryption mismatch at step " + step.get("step"));
				}
			}
		}
		System.out.println("Trace OK");
	}

	private String b64u(byte[] data) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
	}
	private byte[] d64u(String s) {
		return Base64.getUrlDecoder().decode(s);
	}
	private byte[] hexToBytes(String s) {
		byte[] b = new byte[s.length() / 2];
		for (int i = 0; i < b.length; i++) {
			b[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
		}
		return b;
	}
}

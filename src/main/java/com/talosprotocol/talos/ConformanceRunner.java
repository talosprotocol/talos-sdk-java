package com.talosprotocol.talos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talosprotocol.talos.canonical.CanonicalJson;
import com.talosprotocol.talos.errors.TalosError;
import com.talosprotocol.talos.wallet.Wallet;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Iterator;

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

        if (vectorsPath == null) {
            // Not running in conformance mode or missing args
            return;
        }

        System.out.println("Running vectors from: " + vectorsPath);
        JsonNode root = mapper.readTree(new File(vectorsPath));

        int total = 0;
        int failures = 0;

        if (root.has("vectors")) {
            for (JsonNode vec : root.get("vectors")) {
                total++;
                try {
                    runVector(vec, false);
                } catch (Exception e) {
                    failures++;
                    System.out.printf("[FAIL] %s: %s%n", vec.get("test_id").asText(), e.getMessage());
                }
            }
        }

        if (root.has("negative_cases")) {
            for (JsonNode vec : root.get("negative_cases")) {
                total++;
                try {
                    runVector(vec, true);
                } catch (Exception e) {
                    failures++;
                    System.out.printf("[FAIL] %s: %s%n", vec.get("test_id").asText(), e.getMessage());
                }
            }
        }

        System.out.printf("Ran %d tests%n", total);
        if (failures > 0) {
            System.out.printf("FAILED (failures=%d)%n", failures);
            System.exit(1);
        } else {
            System.out.println("OK");
            System.exit(0);
        }
    }

    private void runVector(JsonNode vec, boolean isNegative) throws Exception {
        String testId = vec.get("test_id").asText();
        try {
            if (testId.startsWith("sign_") || testId.startsWith("invalid_")) {
                testSign(vec);
            } else if (testId.startsWith("verify_")) {
                testVerify(vec);
            }

            if (isNegative) {
                // If we are here, no exception was thrown, which is a failure for negative
                // tests
                // unless verify: false was expected result
                if (vec.has("expected") && vec.get("expected").has("verify")
                        && !vec.get("expected").get("verify").asBoolean()) {
                    return;
                }
                throw new RuntimeException("Expected error but operation succeeded");
            }

        } catch (Exception e) {
            if (isNegative) {
                if (vec.has("expected_error")) {
                    JsonNode expectedError = vec.get("expected_error");
                    if (expectedError.has("code")) {
                        if (e instanceof TalosError) {
                            String code = ((TalosError) e).getCode().toString();
                            if (!code.equals(expectedError.get("code").asText())) {
                                throw new RuntimeException("Error code mismatch: want "
                                        + expectedError.get("code").asText() + " got " + code);
                            }
                        }
                    }
                    if (expectedError.has("message_contains")) {
                        String msg = e.getMessage().toLowerCase();
                        String want = expectedError.get("message_contains").asText().toLowerCase();
                        if (!msg.contains(want)) {
                            throw new RuntimeException(
                                    "Error message mismatch: want part '" + want + "' got '" + msg + "'");
                        }
                    }
                    return; // Match
                }
                return; // Got error as expected
            }
            throw e; // Rethrow for positive case
        }
    }

    private void testSign(JsonNode vec) throws Exception {
        JsonNode inputs = vec.get("inputs");
        JsonNode expected = vec.get("expected");

        if (!inputs.has("seed_hex"))
            return;

        byte[] seed = hexToBytes(inputs.get("seed_hex").asText());
        Wallet w = Wallet.fromSeed(seed, null);

        if (expected != null && expected.has("did")) {
            String did = expected.get("did").asText();
            if (!w.toDid().equals(did)) {
                throw new RuntimeException("DID mismatch: want " + did + " got " + w.toDid());
            }
        }

        String msg = inputs.has("message_utf8") ? inputs.get("message_utf8").asText() : "";
        byte[] sig = w.sign(msg.getBytes(StandardCharsets.UTF_8));

        if (expected != null && expected.has("signature_base64url")) {
            // Java Base64 URL encoder might add padding? No, getUrlEncoder usually pads
            // vector expects sometimes no padding.
            // Let's decode expected to check bytes
            String expectedB64 = expected.get("signature_base64url").asText();
            byte[] expectedSig = Base64.getUrlDecoder().decode(expectedB64);

            // Or just re-encode our sig
            String myB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
            if (!myB64.equals(expectedB64)) {
                throw new RuntimeException("Signature mismatch: want " + expectedB64 + " got " + myB64);
            }
        }
    }

    private void testVerify(JsonNode vec) throws Exception {
        JsonNode inputs = vec.get("inputs");
        JsonNode expected = vec.get("expected");

        String msg = inputs.has("message_utf8") ? inputs.get("message_utf8").asText() : "";
        if (inputs.has("tampered_message")) {
            msg = inputs.get("tampered_message").asText();
        }

        byte[] pubKey = null;
        if (inputs.has("public_key_hex")) {
            pubKey = hexToBytes(inputs.get("public_key_hex").asText());
        } else if (inputs.has("wrong_public_key_hex")) {
            pubKey = hexToBytes(inputs.get("wrong_public_key_hex").asText());
        } else if (inputs.has("seed_hex")) {
            Wallet w = Wallet.fromSeed(hexToBytes(inputs.get("seed_hex").asText()), null);
            pubKey = w.getPublicKey();
        }

        byte[] sig = null;
        if (inputs.has("signature_base64url")) {
            sig = Base64.getUrlDecoder().decode(inputs.get("signature_base64url").asText());
        }

        boolean result = Wallet.verify(msg.getBytes(StandardCharsets.UTF_8), sig, pubKey);

        if (expected.has("verify")) {
            boolean want = expected.get("verify").asBoolean();
            if (result != want) {
                throw new RuntimeException("Verify mismatch: want " + want + " got " + result);
            }
        }
    }

    private static byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}

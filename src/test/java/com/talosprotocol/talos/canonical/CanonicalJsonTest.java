package com.talosprotocol.talos.canonical;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

	@Test
	public void testGolden() throws Exception {
		// Simple Map
		Map<String, Object> m1 = new HashMap<>();
		m1.put("b", 2);
		m1.put("a", 1);
		assertEquals("{\"a\":1,\"b\":2}", new String(CanonicalJson.marshal(m1), StandardCharsets.UTF_8));

		// Mixed types
		Map<String, Object> m2 = new HashMap<>();
		m2.put("id", 123);
		m2.put("active", true);
		m2.put("name", "test");
		assertEquals("{\"active\":true,\"id\":123,\"name\":\"test\"}",
				new String(CanonicalJson.marshal(m2), StandardCharsets.UTF_8));

		// Empty
		assertEquals("{}", new String(CanonicalJson.marshal(new HashMap<>()), StandardCharsets.UTF_8));
	}

	@Test
	public void testPermutation() throws Exception {
		// Map 1: Insert A then B
		Map<String, Object> m1 = new LinkedHashMap<>();
		m1.put("a", 1);
		m1.put("b", 2);

		// Map 2: Insert B then A
		Map<String, Object> m2 = new LinkedHashMap<>();
		m2.put("b", 2);
		m2.put("a", 1);

		String s1 = new String(CanonicalJson.marshal(m1), StandardCharsets.UTF_8);
		String s2 = new String(CanonicalJson.marshal(m2), StandardCharsets.UTF_8);

		assertEquals(s1, s2, "Permutation failed: insertion order affected output");
		assertEquals("{\"a\":1,\"b\":2}", s1, "Output not sorted");
	}

	@Test
	public void testNestedPermutation() throws Exception {
		// Nest 1: nested {z,y} ordering
		Map<String, Object> n1 = new HashMap<>();
		Map<String, Object> sub1 = new LinkedHashMap<>();
		sub1.put("z", 3);
		sub1.put("y", 2);
		n1.put("x", sub1);

		// Nest 2: Same reversed ordering
		Map<String, Object> n2 = new HashMap<>();
		Map<String, Object> sub2 = new LinkedHashMap<>();
		sub2.put("y", 2);
		sub2.put("z", 3);
		n2.put("x", sub2);

		String s1 = new String(CanonicalJson.marshal(n1), StandardCharsets.UTF_8);
		String s2 = new String(CanonicalJson.marshal(n2), StandardCharsets.UTF_8);

		assertEquals(s1, s2, "Nested permutation failed");
		assertEquals("{\"x\":{\"y\":2,\"z\":3}}", s1);
	}
}

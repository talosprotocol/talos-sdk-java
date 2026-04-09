package com.talosprotocol.talos.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class A2AJsonRpcClientTest {
	@Test
	void getsAgentCardAndReportsExtensions() throws Exception {
		List<CapturedRequest> requests = new ArrayList<>();
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				"sk-test",
				(method, url, headers, body) -> {
					requests.add(new CapturedRequest(method, url, headers, body));
					return new A2AJsonRpcClient.TransportResponse(
							200,
							"""
							{"supportedInterfaces":[{"transport":"https","url":"https://example.test/rpc"}],"capabilities":{"extensions":[{"uri":"https://talosprotocol.com/extensions/a2a/secure-channels/v1"},{"uri":"https://talosprotocol.com/extensions/a2a/attestation/v1"}]}}
							""");
				});

		Map<String, Object> card = client.getAgentCard();

		assertEquals(1, client.supportedInterfaces(card).size());
		assertTrue(client.supportsTalosSecureChannels(card));
		assertTrue(client.supportsTalosAttestation(card));
		assertFalse(client.supportsTalosCompatJsonrpc(card));
		assertEquals("GET", requests.get(0).method());
		assertEquals("https://example.test/.well-known/agent-card.json", requests.get(0).url());
		assertEquals("Bearer sk-test", requests.get(0).headers().get("Authorization"));
	}

	@Test
	void usesCanonicalJsonRpcMethods() throws Exception {
		List<CapturedRequest> requests = new ArrayList<>();
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				"sk-test",
				(method, url, headers, body) -> {
					requests.add(new CapturedRequest(method, url, headers, body));
					return new A2AJsonRpcClient.TransportResponse(
							200,
							"""
							{"jsonrpc":"2.0","id":"rpc-id","result":{"ok":true}}
							""");
				});

		client.getAuthenticatedExtendedAgentCard();
		client.sendMessage("hello", new A2AJsonRpcClient.MessageOptions());

		assertTrue(requests.get(0).body().contains("\"method\":\"GetExtendedAgentCard\""));
		assertTrue(requests.get(1).body().contains("\"method\":\"SendMessage\""));
	}

	@Test
	void parsesStreamingMethodsAndResults() throws Exception {
		List<CapturedRequest> requests = new ArrayList<>();
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				"sk-test",
				(method, url, headers, body) -> {
					requests.add(new CapturedRequest(method, url, headers, body));
					return new A2AJsonRpcClient.TransportResponse(
							200,
							"""
							data: {"jsonrpc":"2.0","id":"evt-1","result":{"index":1}}

							data: {"jsonrpc":"2.0","id":"evt-2","result":{"index":2}}
							""");
				});

		List<Map<String, Object>> events = client.sendStreamingMessage("hello", new A2AJsonRpcClient.MessageOptions());
		List<Map<String, Object>> taskEvents = client.subscribeToTask("task-1", new A2AJsonRpcClient.TaskOptions());

		assertEquals(2, events.size());
		assertEquals(1, ((Number) events.get(0).get("index")).intValue());
		assertEquals(2, ((Number) taskEvents.get(1).get("index")).intValue());
		assertEquals("text/event-stream", requests.get(0).headers().get("Accept"));
		assertTrue(requests.get(0).body().contains("\"method\":\"SendStreamingMessage\""));
		assertTrue(requests.get(1).body().contains("\"method\":\"SubscribeToTask\""));
	}

	@Test
	void invokesStreamingHandlersIncrementally() throws Exception {
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				"sk-test",
				(method, url, headers, body) -> new A2AJsonRpcClient.TransportResponse(
						200,
						"""
						data: {"jsonrpc":"2.0","id":"evt-1","result":{"index":1}}

						data: {"jsonrpc":"2.0","id":"evt-2","result":{"index":2}}
						"""));

		List<Integer> seen = new ArrayList<>();
		client.sendStreamingMessageEach("hello", new A2AJsonRpcClient.MessageOptions(),
				event -> seen.add(((Number) event.get("index")).intValue()));

		assertEquals(List.of(1, 2), seen);
	}

	@Test
	void exposesIteratorStyleStreamingEvents() throws Exception {
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				"sk-test",
				(method, url, headers, body) -> new A2AJsonRpcClient.TransportResponse(
						200,
						"""
						data: {"jsonrpc":"2.0","id":"evt-1","result":{"index":1}}

						data: {"jsonrpc":"2.0","id":"evt-2","result":{"index":2}}
						"""));

		List<Integer> seen = new ArrayList<>();
		for (Map<String, Object> event : client.sendStreamingMessageEvents("hello", new A2AJsonRpcClient.MessageOptions())) {
			seen.add(((Number) event.get("index")).intValue());
		}

		assertEquals(List.of(1, 2), seen);
	}

	@Test
	void surfacesJsonRpcErrors() {
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				null,
				(method, url, headers, body) -> new A2AJsonRpcClient.TransportResponse(
						200,
						"""
						{"jsonrpc":"2.0","id":"rpc-err","error":{"code":-32603,"message":"rpc failed","data":{"reason":"denied"}}}
						"""));

		A2AJsonRpcClient.A2AJsonRpcException error = assertThrows(
				A2AJsonRpcClient.A2AJsonRpcException.class,
				() -> client.rpc("GetTask", Map.of("id", "task-1")));

		assertEquals(-32603, error.getCode());
		assertEquals("rpc failed", error.getMessage());
		assertInstanceOf(Map.class, error.getData());
	}

	@Test
	void surfacesStreamJsonRpcErrors() {
		A2AJsonRpcClient client = new A2AJsonRpcClient(
				"https://example.test",
				null,
				(method, url, headers, body) -> new A2AJsonRpcClient.TransportResponse(
						200,
						"""
						data: {"jsonrpc":"2.0","id":"evt-err","error":{"code":-32000,"message":"stream failed","data":{"reason":"denied"}}}
						"""));

		A2AJsonRpcClient.A2AJsonRpcException error = assertThrows(
				A2AJsonRpcClient.A2AJsonRpcException.class,
				() -> client.subscribeToTask("task-1", new A2AJsonRpcClient.TaskOptions()));

		assertEquals(-32000, error.getCode());
		assertEquals("stream failed", error.getMessage());
	}

	private record CapturedRequest(String method, String url, Map<String, String> headers, String body) {
	}
}

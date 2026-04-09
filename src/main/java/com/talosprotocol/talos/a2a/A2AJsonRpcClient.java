package com.talosprotocol.talos.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Minimal standards-first A2A v1 JSON-RPC client for Java.
 */
public class A2AJsonRpcClient {
	public static final String TALOS_ATTESTATION_EXTENSION =
			"https://talosprotocol.com/extensions/a2a/attestation/v1";
	public static final String TALOS_SECURE_CHANNELS_EXTENSION =
			"https://talosprotocol.com/extensions/a2a/secure-channels/v1";
	public static final String TALOS_COMPAT_JSONRPC_EXTENSION =
			"https://talosprotocol.com/extensions/a2a/compat-jsonrpc/v0";

	private static final ObjectMapper mapper = new ObjectMapper();
	private static final TypeReference<Map<String, Object>> mapType = new TypeReference<>() {
	};

	private final String baseUrl;
	private final String apiToken;
	private final Transport transport;

	public A2AJsonRpcClient(String baseUrl) {
		this(baseUrl, null, new JavaNetTransport());
	}

	public A2AJsonRpcClient(String baseUrl, String apiToken) {
		this(baseUrl, apiToken, new JavaNetTransport());
	}

	A2AJsonRpcClient(String baseUrl, String apiToken, Transport transport) {
		this.baseUrl = trimTrailingSlash(baseUrl);
		this.apiToken = apiToken;
		this.transport = Objects.requireNonNull(transport, "transport");
	}

	public Map<String, Object> getAgentCard() throws IOException, InterruptedException {
		return requestObject("GET", "/.well-known/agent-card.json", null, false);
	}

	public Map<String, Object> getExtendedAgentCard() throws IOException, InterruptedException {
		return requestObject("GET", "/extendedAgentCard", null, false);
	}

	public Map<String, Object> getAuthenticatedExtendedAgentCard() throws IOException, InterruptedException {
		return rpc("GetExtendedAgentCard", null);
	}

	public List<Map<String, Object>> supportedInterfaces(Map<String, Object> card) {
		Object raw = card.get("supportedInterfaces");
		if (!(raw instanceof List<?> items)) {
			return List.of();
		}

		List<Map<String, Object>> result = new ArrayList<>();
		for (Object item : items) {
			if (item instanceof Map<?, ?> map) {
				result.add(castObjectMap(map));
			}
		}
		return result;
	}

	public List<String> extensionUris(Map<String, Object> card) {
		Object capabilitiesRaw = card.get("capabilities");
		if (!(capabilitiesRaw instanceof Map<?, ?> capabilities)) {
			return List.of();
		}
		Object extensionsRaw = capabilities.get("extensions");
		if (!(extensionsRaw instanceof List<?> extensions)) {
			return List.of();
		}

		List<String> uris = new ArrayList<>();
		for (Object item : extensions) {
			if (!(item instanceof Map<?, ?> extension)) {
				continue;
			}
			Object uri = extension.get("uri");
			if (uri instanceof String value) {
				uris.add(value);
			}
		}
		return uris;
	}

	public boolean supportsExtension(Map<String, Object> card, String uri) {
		return extensionUris(card).contains(uri);
	}

	public boolean supportsTalosSecureChannels(Map<String, Object> card) {
		return supportsExtension(card, TALOS_SECURE_CHANNELS_EXTENSION);
	}

	public boolean supportsTalosAttestation(Map<String, Object> card) {
		return supportsExtension(card, TALOS_ATTESTATION_EXTENSION);
	}

	public boolean supportsTalosCompatJsonrpc(Map<String, Object> card) {
		return supportsExtension(card, TALOS_COMPAT_JSONRPC_EXTENSION);
	}

	public Map<String, Object> sendMessage(String text, MessageOptions options)
			throws IOException, InterruptedException {
		Map<String, Object> params = new HashMap<>();
		params.put("message", message(text, options));
		if (options.configuration != null) {
			params.put("configuration", options.configuration);
		}
		return rpc("SendMessage", params);
	}

	public List<Map<String, Object>> sendStreamingMessage(String text, MessageOptions options)
			throws IOException, InterruptedException {
		Map<String, Object> params = new HashMap<>();
		params.put("message", message(text, options));
		if (options.configuration != null) {
			params.put("configuration", options.configuration);
		}
		return stream("SendStreamingMessage", params);
	}

	public Iterable<Map<String, Object>> sendStreamingMessageEvents(String text, MessageOptions options)
			throws IOException, InterruptedException {
		Map<String, Object> params = new HashMap<>();
		params.put("message", message(text, options));
		if (options.configuration != null) {
			params.put("configuration", options.configuration);
		}
		return streamEvents("SendStreamingMessage", params);
	}

	public void sendStreamingMessageEach(String text, MessageOptions options, StreamHandler handler)
			throws IOException, InterruptedException {
		Map<String, Object> params = new HashMap<>();
		params.put("message", message(text, options));
		if (options.configuration != null) {
			params.put("configuration", options.configuration);
		}
		streamEach("SendStreamingMessage", params, handler);
	}

	public Map<String, Object> getTask(String taskId, TaskOptions options) throws IOException, InterruptedException {
		return rpc("GetTask", taskParams(taskId, options));
	}

	public Map<String, Object> cancelTask(String taskId, TaskOptions options) throws IOException, InterruptedException {
		return rpc("CancelTask", taskParams(taskId, options));
	}

	public Map<String, Object> listTasks(ListTasksOptions options) throws IOException, InterruptedException {
		Map<String, Object> params = new HashMap<>();
		params.put("includeArtifacts", options.includeArtifacts);
		if (options.contextId != null) {
			params.put("contextId", options.contextId);
		}
		if (options.status != null) {
			params.put("status", options.status);
		}
		if (options.pageSize != null) {
			params.put("pageSize", options.pageSize);
		}
		if (options.pageToken != null) {
			params.put("pageToken", options.pageToken);
		}
		if (options.historyLength != null) {
			params.put("historyLength", options.historyLength);
		}
		return rpc("ListTasks", params);
	}

	public List<Map<String, Object>> subscribeToTask(String taskId, TaskOptions options)
			throws IOException, InterruptedException {
		return stream("SubscribeToTask", taskParams(taskId, options));
	}

	public Iterable<Map<String, Object>> subscribeToTaskEvents(String taskId, TaskOptions options)
			throws IOException, InterruptedException {
		return streamEvents("SubscribeToTask", taskParams(taskId, options));
	}

	public void subscribeToTaskEach(String taskId, TaskOptions options, StreamHandler handler)
			throws IOException, InterruptedException {
		streamEach("SubscribeToTask", taskParams(taskId, options), handler);
	}

	public Map<String, Object> setTaskPushNotificationConfig(String taskId, PushNotificationConfigOptions options)
			throws IOException, InterruptedException {
		Map<String, Object> params = new HashMap<>();
		params.put("taskId", taskId);
		params.put("id", options.configId != null ? options.configId : newId("push"));
		params.put("url", options.url);
		if (options.token != null) {
			params.put("token", options.token);
		}
		if (options.authentication != null) {
			params.put("authentication", options.authentication);
		}
		return rpc("CreateTaskPushNotificationConfig", params);
	}

	public Map<String, Object> getTaskPushNotificationConfig(String taskId, String configId)
			throws IOException, InterruptedException {
		return rpc("GetTaskPushNotificationConfig", Map.of("taskId", taskId, "id", configId));
	}

	public Map<String, Object> listTaskPushNotificationConfigs(String taskId)
			throws IOException, InterruptedException {
		return rpc("ListTaskPushNotificationConfigs", Map.of("taskId", taskId));
	}

	public Map<String, Object> deleteTaskPushNotificationConfig(String taskId, String configId)
			throws IOException, InterruptedException {
		return rpc("DeleteTaskPushNotificationConfig", Map.of("taskId", taskId, "id", configId));
	}

	public Map<String, Object> rpc(String method, Map<String, Object> params) throws IOException, InterruptedException {
		Map<String, Object> payload = new HashMap<>();
		payload.put("jsonrpc", "2.0");
		payload.put("id", newId("rpc"));
		payload.put("method", method);
		payload.put("params", params != null ? params : Map.of());
		return extractResult(requestObject("POST", "/rpc", payload, false));
	}

	public List<Map<String, Object>> stream(String method, Map<String, Object> params)
			throws IOException, InterruptedException {
		List<Map<String, Object>> results = new ArrayList<>();
		for (Map<String, Object> event : streamEvents(method, params)) {
			results.add(event);
		}
		return results;
	}

	public Iterable<Map<String, Object>> streamEvents(String method, Map<String, Object> params)
			throws IOException, InterruptedException {
		Map<String, Object> payload = new HashMap<>();
		payload.put("jsonrpc", "2.0");
		payload.put("id", newId("stream"));
		payload.put("method", method);
		payload.put("params", params != null ? params : Map.of());
		TransportResponse response = request("POST", "/rpc", payload, true);
		return new StreamResultIterable(response.body());
	}

	public void streamEach(String method, Map<String, Object> params, StreamHandler handler)
			throws IOException, InterruptedException {
		Objects.requireNonNull(handler, "handler");
		for (Map<String, Object> event : streamEvents(method, params)) {
			handler.onEvent(event);
		}
	}

	Map<String, Object> message(String text, MessageOptions options) {
		Map<String, Object> message = new HashMap<>();
		message.put("messageId", options.messageId != null ? options.messageId : newId("msg"));
		message.put("role", "user");
		message.put("parts", List.of(Map.of("text", text)));
		if (options.taskId != null) {
			message.put("taskId", options.taskId);
		}
		if (options.contextId != null) {
			message.put("contextId", options.contextId);
		}
		if (options.metadata != null) {
			message.put("metadata", options.metadata);
		}
		return message;
	}

	Map<String, Object> taskParams(String taskId, TaskOptions options) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", taskId);
		params.put("includeArtifacts", options.includeArtifacts);
		if (options.historyLength != null) {
			params.put("historyLength", options.historyLength);
		}
		return params;
	}

	Map<String, Object> extractResult(Map<String, Object> payload) {
		Object errorRaw = payload.get("error");
		if (errorRaw instanceof Map<?, ?> error) {
			throw new A2AJsonRpcException(
					numberValue(error.get("code"), -32603),
					stringValue(error.get("message")),
					error.containsKey("data") ? error.get("data") : null);
		}

		Object resultRaw = payload.get("result");
		if (!(resultRaw instanceof Map<?, ?> result)) {
			throw new IllegalStateException("Unexpected A2A response: " + payload);
		}
		return castObjectMap(result);
	}

	List<Map<String, Object>> extractStreamResults(String body) throws JsonProcessingException {
		List<Map<String, Object>> results = new ArrayList<>();
		processStreamResults(body, results::add);
		return results;
	}

	void processStreamResults(String body, StreamHandler handler) throws JsonProcessingException {
		for (Map<String, Object> event : new StreamResultIterable(body)) {
			handler.onEvent(event);
		}
	}

	Map<String, Object> requestObject(String method, String path, Map<String, Object> payload, boolean stream)
			throws IOException, InterruptedException {
		TransportResponse response = request(method, path, payload, stream);
		return mapper.readValue(response.body(), mapType);
	}

	TransportResponse request(String method, String path, Map<String, Object> payload, boolean stream)
			throws IOException, InterruptedException {
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");
		headers.put("Accept", stream ? "text/event-stream" : "application/json");
		if (apiToken != null && !apiToken.isBlank()) {
			headers.put("Authorization", "Bearer " + apiToken);
		}

		String body = payload == null ? null : mapper.writeValueAsString(payload);
		TransportResponse response = transport.execute(method, baseUrl + path, headers, body);
		if (response.statusCode() >= 400) {
			throw new A2AHttpException(response.statusCode(), parseErrorPayload(response.body()));
		}
		return response;
	}

	String newId(String prefix) {
		return prefix + "-" + UUID.randomUUID();
	}

	private static String trimTrailingSlash(String value) {
		if (value.endsWith("/")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	private static int numberValue(Object value, int fallback) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return fallback;
	}

	private static String stringValue(Object value) {
		return value instanceof String text ? text : "";
	}

	private static Object parseErrorPayload(String body) {
		try {
			return mapper.readValue(body, Object.class);
		} catch (Exception ignored) {
			return body;
		}
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> castObjectMap(Map<?, ?> value) {
		return (Map<String, Object>) value;
	}

	public static class MessageOptions {
		public String messageId;
		public String taskId;
		public String contextId;
		public Map<String, Object> configuration;
		public Map<String, Object> metadata;
	}

	public static class TaskOptions {
		public Integer historyLength;
		public boolean includeArtifacts;
	}

	public static class ListTasksOptions extends TaskOptions {
		public String contextId;
		public String status;
		public Integer pageSize;
		public String pageToken;
	}

	public static class PushNotificationConfigOptions {
		public String url;
		public String token;
		public Map<String, Object> authentication;
		public String configId;
	}

	interface Transport {
		TransportResponse execute(String method, String url, Map<String, String> headers, String body)
				throws IOException, InterruptedException;
	}

	@FunctionalInterface
	public interface StreamHandler {
		void onEvent(Map<String, Object> event);
	}

	private final class StreamResultIterable implements Iterable<Map<String, Object>> {
		private final String body;

		private StreamResultIterable(String body) {
			this.body = body;
		}

		@Override
		public Iterator<Map<String, Object>> iterator() {
			return new StreamResultIterator(body);
		}
	}

	private final class StreamResultIterator implements Iterator<Map<String, Object>> {
		private final String[] lines;
		private int index;
		private Map<String, Object> next;

		private StreamResultIterator(String body) {
			this.lines = body.split("\\R");
		}

		@Override
		public boolean hasNext() {
			if (next != null) {
				return true;
			}
			while (index < lines.length) {
				String trimmed = lines[index++].trim();
				if (!trimmed.startsWith("data: ")) {
					continue;
				}
				String rawPayload = trimmed.substring(6).trim();
				if (rawPayload.isEmpty()) {
					continue;
				}
				try {
					Map<String, Object> payload = mapper.readValue(rawPayload, mapType);
					next = extractResult(payload);
					return true;
				} catch (IOException error) {
					throw new IllegalStateException("Invalid SSE payload", error);
				}
			}
			return false;
		}

		@Override
		public Map<String, Object> next() {
			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			Map<String, Object> event = next;
			next = null;
			return event;
		}
	}

	record TransportResponse(int statusCode, String body) {
	}

	static class JavaNetTransport implements Transport {
		private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

		@Override
		public TransportResponse execute(String method, String url, Map<String, String> headers, String body)
				throws IOException, InterruptedException {
			HttpRequest.BodyPublisher publisher = body == null
					? HttpRequest.BodyPublishers.noBody()
					: HttpRequest.BodyPublishers.ofString(body);
			HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url)).method(method, publisher);
			for (Map.Entry<String, String> entry : headers.entrySet()) {
				builder.header(entry.getKey(), entry.getValue());
			}
			HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
			return new TransportResponse(response.statusCode(), response.body());
		}
	}

	public static class A2AHttpException extends RuntimeException {
		private final int statusCode;
		private final Object payload;

		public A2AHttpException(int statusCode, Object payload) {
			super("HTTP " + statusCode + ": " + payload);
			this.statusCode = statusCode;
			this.payload = payload;
		}

		public int getStatusCode() {
			return statusCode;
		}

		public Object getPayload() {
			return payload;
		}
	}

	public static class A2AJsonRpcException extends RuntimeException {
		private final int code;
		private final Object data;

		public A2AJsonRpcException(int code, String message, Object data) {
			super(message);
			this.code = code;
			this.data = data;
		}

		public int getCode() {
			return code;
		}

		public Object getData() {
			return data;
		}
	}
}

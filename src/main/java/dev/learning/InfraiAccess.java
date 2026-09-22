package dev.learning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfraiAccess {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json;
    private final String baseUrl;
    private final String key;

    public InfraiAccess(ObjectMapper json, @Value("${infrai.base-url}") String baseUrl,
                        @Value("${infrai.api-key}") String key) {
        this.json = json;
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.key = key;
    }

    public JsonNode sessions(String userId) {
        return call("GET", "/v1/auth/session/list_for_user/" + segment(userId));
    }

    public void revokeSession(String sessionId) {
        call("POST", "/v1/auth/session/revoke/" + segment(sessionId));
    }

    public void revokeCredential(String id) {
        call("DELETE", "/v1/account/keys/revoke/" + segment(id));
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode call(String method, String path) {
        for (int attempt = 0; attempt < 4; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                        .header("Authorization", "Bearer " + key)
                        .timeout(Duration.ofSeconds(15))
                        .method(method, HttpRequest.BodyPublishers.noBody()).build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode envelope = json.readTree(response.body());
                if (response.statusCode() == 429 && attempt < 3) {
                    String retryAfter = response.headers().firstValue("Retry-After").orElse("");
                    long seconds;
                    try { seconds = Long.parseLong(retryAfter); }
                    catch (NumberFormatException ignored) { seconds = 1L << attempt; }
                    Thread.sleep(Math.min(Math.max(seconds, 1), 30) * 1000);
                    continue;
                }
                if (!envelope.path("ok").asBoolean(false)) {
                    JsonNode error = envelope.path("error");
                    throw new InfraiRejection(response.statusCode(), error.path("code").asText("REQUEST_REJECTED"),
                            error.path("message").asText("Request rejected"));
                }
                if (response.statusCode() >= 500) throw new IllegalStateException("Upstream request failed");
                return envelope.path("data");
            } catch (IOException e) {
                throw new IllegalStateException("Could not read upstream response", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Request interrupted", e);
            }
        }
        throw new IllegalStateException("Retry limit reached");
    }

    public static final class InfraiRejection extends RuntimeException {
        private final int status;
        private final String code;
        public InfraiRejection(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
        public int status() { return status; }
        public String code() { return code; }
    }
}

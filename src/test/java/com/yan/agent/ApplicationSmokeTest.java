package com.yan.agent;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void shouldServeHomePageAndHealthEndpoint() throws IOException, InterruptedException {
        HttpResponse<String> homeResponse = get("/");
        HttpResponse<String> workspaceScriptResponse = get("/workspace.js");
        HttpResponse<String> healthResponse = get("/actuator/health");

        assertThat(homeResponse.statusCode()).isEqualTo(200);
        assertThat(homeResponse.body()).contains("知屿 · 个人知识库助手");
        assertThat(homeResponse.body()).contains("workspace.js");
        assertThat(workspaceScriptResponse.statusCode()).isEqualTo(200);
        assertThat(workspaceScriptResponse.body())
                .doesNotContain("EVALUATION_CASES")
                .contains("/api/v1/chat/sessions")
                .contains("/documents")
                .contains("/rag/ask")
                .contains("/evaluations/calibration");
        assertThat(healthResponse.statusCode()).isEqualTo(200);
        assertThat(healthResponse.body()).contains("UP");
    }

    @Test
    void shouldRejectBlankMessageBeforeCallingModel() throws IOException, InterruptedException {
        String token = createTestToken();
        HttpResponse<String> response = postJson(
                "/api/v1/chat",
                "{\"message\":\"   \"}",
                token);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("消息不能为空");
    }

    @Test
    void shouldExplainMissingApiKey() throws IOException, InterruptedException {
        String token = createTestToken();
        HttpResponse<String> response = postJson(
                "/api/v1/chat",
                "{\"message\":\"hello\"}",
                token);

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("DEEPSEEK_API_KEY");
    }

    @Test
    void shouldReturnStructuredUnauthorizedResponseWithRequestId()
            throws IOException, InterruptedException {
        HttpResponse<String> response = get("/api/v1/knowledge-bases");

        String requestId = response.headers()
                .firstValue("X-Request-Id")
                .orElseThrow();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body())
                .contains("请先登录或提供有效的访问令牌")
                .contains(requestId);
    }

    @Test
    void shouldRequireAuthenticationForModelEndpoints()
            throws IOException, InterruptedException {
        HttpResponse<String> chatResponse = postJson(
                "/api/v1/chat",
                "{\"message\":\"hello\"}");
        HttpResponse<String> streamResponse = postJson(
                "/api/v1/chat/stream",
                "{\"message\":\"hello\"}");

        assertThat(chatResponse.statusCode()).isEqualTo(401);
        assertThat(streamResponse.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String json)
            throws IOException, InterruptedException {
        return postJson(path, json, null);
    }

    private HttpResponse<String> postJson(
            String path,
            String json,
            String token)
            throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private String createTestToken() {
        Instant now = Instant.now();
        long userId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("zhiyu-agent")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .subject("smoke@example.com")
                .claim("userId", userId)
                .claim("displayName", "Smoke Test")
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }
}

package com.yan.agent;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationSmokeTest {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    void shouldServeHomePageAndHealthEndpoint() throws IOException, InterruptedException {
        HttpResponse<String> homeResponse = get("/");
        HttpResponse<String> workspaceScriptResponse = get("/workspace.js");
        HttpResponse<String> healthResponse = get("/actuator/health");

        assertThat(homeResponse.statusCode()).isEqualTo(200);
        assertThat(homeResponse.body()).contains("企业知识库智能 Agent");
        assertThat(homeResponse.body()).contains("workspace.js");
        assertThat(workspaceScriptResponse.statusCode()).isEqualTo(200);
        assertThat(workspaceScriptResponse.body())
                .contains("EVALUATION_CASES")
                .contains("/api/v1/chat/sessions")
                .contains("/documents")
                .contains("/rag/ask")
                .contains("/evaluations/retrieval");
        assertThat(healthResponse.statusCode()).isEqualTo(200);
        assertThat(healthResponse.body()).contains("UP");
    }

    @Test
    void shouldRejectBlankMessageBeforeCallingModel() throws IOException, InterruptedException {
        HttpResponse<String> response = postJson("/api/v1/chat", "{\"message\":\"   \"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("消息不能为空");
    }

    @Test
    void shouldExplainMissingApiKey() throws IOException, InterruptedException {
        HttpResponse<String> response = postJson("/api/v1/chat", "{\"message\":\"hello\"}");

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

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String json)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }
}

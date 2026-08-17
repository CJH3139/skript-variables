package com.skriptvariables.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPOutputStream;

public final class ApiClient {

    private static final String DEFAULT_SITE = "https://skript-variables.com";
    private static final int MAX_RETRIES = 2;

    private static volatile String site = DEFAULT_SITE;

    public static void setSite(String url) {
        if (url == null || url.isBlank()) return;
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (trimmed.endsWith("/api")) trimmed = trimmed.substring(0, trimmed.length() - 4);
        site = trimmed;
    }

    public static String siteUrl() { return site; }

    private static String base() { return site + "/api"; }

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private static volatile String apiKey = null;

    public static void setApiKey(String key) { apiKey = key; }
    public static boolean hasApiKey()        { return apiKey != null && !apiKey.isEmpty(); }

    public static String register() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(base() + "/register"))
            .timeout(Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
        HttpResponse<String> resp = sendWithRetry(req);
        if (resp.statusCode() != 200)
            throw new Exception("Registration failed HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        String key = extractField(resp.body(), "apiKey");
        if (key.isEmpty()) throw new Exception("Registration failed: no apiKey in response");
        return key;
    }

    public static String createSession(int totalVars) throws Exception {
        String body = "{\"total\":" + totalVars + "}";
        HttpResponse<String> resp = postWithRetry("/session", body);
        if (resp.statusCode() == 401) throw new AuthException();
        if (resp.statusCode() != 200) throw new Exception("createSession HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
        return extractField(resp.body(), "id");
    }

    public static void uploadChunk(String sessionId, int chunkIndex, String json) throws Exception {
        byte[] body = gzip(json);
        HttpRequest req = builder("/session/" + sessionId + "/chunk/" + chunkIndex, Duration.ofSeconds(30))
            .header("Content-Type", "application/octet-stream")
            .header("X-Gzip", "1")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
            .build();
        HttpResponse<String> resp = sendWithRetry(req);
        if (resp.statusCode() == 401) throw new AuthException();
        if (resp.statusCode() != 200) throw new Exception("uploadChunk " + chunkIndex + " HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
    }

    public static void uploadProfile(String sessionId, String json) throws Exception {
        HttpRequest req = builder("/session/" + sessionId + "/profile", Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        HttpResponse<String> resp = sendWithRetry(req);
        if (resp.statusCode() == 401) throw new AuthException();
        if (resp.statusCode() != 200)
            throw new Exception("uploadProfile HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
    }

    public static void markReady(String sessionId, int totalChunks) throws Exception {
        String body = "{\"chunks\":" + totalChunks + "}";
        HttpResponse<String> resp = postWithRetry("/session/" + sessionId + "/ready", body);
        if (resp.statusCode() == 401) throw new AuthException();
        if (resp.statusCode() != 200) throw new Exception("markReady HTTP " + resp.statusCode() + ": " + truncate(resp.body()));
    }

    public static String getDiff(String sessionId, String applyCode, boolean keep) throws Exception {
        HttpRequest req = builder("/session/" + sessionId + "/diff/" + applyCode + (keep ? "?keep=1" : ""), Duration.ofSeconds(15))
            .GET()
            .build();
        HttpResponse<String> resp = sendWithRetry(req);
        if (resp.statusCode() == 404) throw new NotFoundException("Apply code not found or expired.");
        if (resp.statusCode() == 401) throw new AuthException();
        if (resp.statusCode() != 200) throw new Exception(extractError(resp.body()));
        return resp.body();
    }

    private static HttpRequest.Builder builder(String path, Duration timeout) {
        HttpRequest.Builder b = HttpRequest.newBuilder()
            .uri(URI.create(base() + path))
            .timeout(timeout);
        if (apiKey != null && !apiKey.isEmpty())
            b.header("Authorization", "Bearer " + apiKey);
        return b;
    }

    private static HttpResponse<String> postWithRetry(String path, String json) throws Exception {
        HttpRequest req = builder(path, Duration.ofSeconds(15))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
        return sendWithRetry(req);
    }

    private static HttpResponse<String> sendWithRetry(HttpRequest req) throws Exception {
        IOException last = null;
        long start = System.nanoTime();
        int attempts = 0;

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            attempts++;
            try {
                return HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                last = e;
                boolean retryable = e instanceof java.net.http.HttpTimeoutException
                                 || e instanceof java.net.ConnectException;
                if (!retryable || attempt == MAX_RETRIES) break;
                Thread.sleep(1000L * (attempt + 1));
            }
        }

        throw new NetworkException(req.uri(), attempts, (System.nanoTime() - start) / 1_000_000L, last);
    }

    private static String extractField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        int end = json.indexOf('"', start);
        return end < 0 ? "" : json.substring(start, end);
    }

    private static String extractError(String json) {
        String v = extractField(json, "error");
        return v.isEmpty() ? "unexpected response from server" : v;
    }

    private static String truncate(String s) {
        if (s == null) return "(null)";
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= 120 ? clean : clean.substring(0, 120) + "…";
    }

    private static byte[] gzip(String json) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(json.length() / 4);
        try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(json.getBytes(StandardCharsets.UTF_8));
        }
        return bos.toByteArray();
    }

    public static class NotFoundException extends Exception {
        public NotFoundException(String msg) { super(msg); }
    }

    public static class AuthException extends Exception {
        public AuthException() { super("Invalid or expired API key"); }
    }

    public static class NetworkException extends Exception {
        public NetworkException(URI uri, int attempts, long elapsedMs, Throwable cause) {
            super(describe(uri, attempts, elapsedMs, cause), cause);
        }

        private static String describe(URI uri, int attempts, long elapsedMs, Throwable cause) {
            String reason;
            if (cause == null) {
                reason = "unknown error";
            } else if (cause instanceof java.net.UnknownHostException) {
                reason = "DNS lookup failed for " + uri.getHost() + " (host could not be resolved)";
            } else if (cause instanceof java.net.ConnectException) {
                reason = "connection refused or unreachable";
            } else if (cause instanceof java.net.http.HttpTimeoutException) {
                reason = "request timed out";
            } else if (cause instanceof javax.net.ssl.SSLException) {
                reason = "TLS/SSL failure (" + cause.getMessage() + ")";
            } else {
                reason = cause.getClass().getSimpleName()
                       + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
            }
            return reason + " [" + uri.getHost() + uri.getPath() + ", "
                 + attempts + " attempt(s), " + elapsedMs + "ms]";
        }
    }
}
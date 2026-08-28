package dev.prostovisuals.client.spatial;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Chromium DevTools Protocol source. Browser must expose a DevTools endpoint (usually --remote-debugging-port=9222). */
public final class BrowserTabCaptureSource implements CaptureSource {
    private final String targetId;
    private final String title;
    private final String webSocketUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final AtomicLong seq = new AtomicLong();
    private volatile WebSocket socket;
    private volatile CompletableFuture<String> pending;

    public BrowserTabCaptureSource(String targetId, String title, String webSocketUrl) {
        this.targetId = targetId;
        this.title = title;
        this.webSocketUrl = webSocketUrl;
    }

    public String tabId() { return targetId; }
    public String webSocketUrl() { return webSocketUrl; }

    @Override public String id() { return "tab:" + targetId; }
    @Override public String name() { return title; }

    @Override
    public BufferedImage capture() throws Exception {
        ensureSocket();
        long id = seq.incrementAndGet();
        CompletableFuture<String> response = new CompletableFuture<>();
        pending = response;
        String msg = "{\"id\":" + id + ",\"method\":\"Page.captureScreenshot\",\"params\":{\"format\":\"jpeg\",\"quality\":72,\"fromSurface\":true}}";
        socket.sendText(msg, true).join();
        String json = response.get(1500, TimeUnit.MILLISECONDS);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("result")) return null;
        JsonObject result = root.getAsJsonObject("result");
        if (!result.has("data")) return null;
        byte[] bytes = Base64.getDecoder().decode(result.get("data").getAsString().getBytes(StandardCharsets.ISO_8859_1));
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private synchronized void ensureSocket() {
        if (socket != null) return;
        socket = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(2))
                .buildAsync(URI.create(webSocketUrl), new WebSocket.Listener() {
                    private final StringBuilder buf = new StringBuilder();
                    @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            String message = buf.toString();
                            buf.setLength(0);
                            CompletableFuture<String> f = pending;
                            if (f != null && !f.isDone() && message.contains("\"id\"")) f.complete(message);
                        }
                        ws.request(1);
                        return null;
                    }
                    @Override public void onOpen(WebSocket ws) { ws.request(1); }
                    @Override public void onError(WebSocket ws, Throwable error) {
                        CompletableFuture<String> f = pending;
                        if (f != null && !f.isDone()) f.completeExceptionally(error);
                    }
                }).join();
    }

    @Override public synchronized void close() {
        if (socket != null) socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        socket = null;
    }
}

package dev.prostovisuals.client.spatial;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class CaptureSourceRegistry {
    private static volatile List<CaptureSource> cache = List.of();
    private static volatile long cacheTime;
    private static volatile CompletableFuture<List<CaptureSource>> inFlight;

    private CaptureSourceRegistry() {}

    /** Returns immediately; F10 never waits for native enumeration. */
    public static List<CaptureSource> cached() { return new ArrayList<>(cache); }
    public static long cacheAgeMs() { return cacheTime == 0 ? Long.MAX_VALUE : Math.max(0L, System.currentTimeMillis() - cacheTime); }

    public static synchronized CompletableFuture<List<CaptureSource>> refreshAsync() {
        CompletableFuture<List<CaptureSource>> current = inFlight;
        if (current != null && !current.isDone()) return current;

        CompletableFuture<List<CaptureSource>> future = new CompletableFuture<>();
        inFlight = future;
        Thread.startVirtualThread(() -> {
            try {
                List<CaptureSource> found = discoverNow();
                cache = List.copyOf(found);
                cacheTime = System.currentTimeMillis();
                future.complete(new ArrayList<>(found));
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static void warmup() {
        if (cache.isEmpty() || cacheAgeMs() > 5000L) refreshAsync();
    }

    public static List<CaptureSource> discover() { return discoverNow(); }

    private static List<CaptureSource> discoverNow() {
        LinkedHashMap<String, CaptureSource> unique = new LinkedHashMap<>();
        if (isWindows()) discoverWindows(unique);
        return new ArrayList<>(unique.values());
    }

    public static CaptureSource reopen(CaptureSource source) {
        if (source == null) return null;
        try {
            if (source instanceof WindowCaptureSource w) return new WindowCaptureSource(w.hwnd(), w.name(), w.bounds());
            if (source instanceof DesktopCaptureSource d) return new DesktopCaptureSource(d.id(), d.name(), d.bounds());
            if (source instanceof BrowserTabCaptureSource b) return new BrowserTabCaptureSource(b.tabId(), b.name(), b.webSocketUrl());
        } catch (Throwable ignored) {}
        return null;
    }

    private static void discoverWindows(Map<String, CaptureSource> out) {
        try {
            for (WindowNative.WindowInfo info : WindowNative.listCapturableWindows()) {
                WindowCaptureSource source = new WindowCaptureSource(info.hwnd(), info.displayName(), info.bounds());
                out.put(source.id(), source);
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isWindows() { return System.getProperty("os.name", "").toLowerCase().contains("win"); }
}

package dev.prostovisuals.client.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.prostovisuals.prostovisuals;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Keeps the HolyWorld coin -> money exchange rate cached client-side. */
public final class HolyWorldCoinRateManager {
    private static final String API_URL = "https://api.holyworld.me/v1/coins-trades";
    private static final long UPDATE_PERIOD_SECONDS = 30L;

    // Use the median of trades from the newest 30-second window. The endpoint
    // can contain single anomalous deals (e.g. 20,000 or 8,000,000 among
    // ~5.7-6.0m trades), and taking just one newest deal makes AH conversion
    // jump wildly. Median still tracks the latest market level but ignores
    // those one-off outliers.
    private static final long RATE_WINDOW_MS = 30_000L;

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final ThreadFactory THREAD_FACTORY = runnable -> {
        Thread thread = new Thread(runnable, "ProstoVisuals-HolyWorldCoinRate");
        thread.setDaemon(true);
        return thread;
    };

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(THREAD_FACTORY);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private static volatile double currentRate = Double.NaN;
    private static volatile long currentRateTimestamp = -1L;

    private HolyWorldCoinRateManager() {}

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) return;

        EXECUTOR.scheduleWithFixedDelay(
                HolyWorldCoinRateManager::updateSafely,
                0L,
                UPDATE_PERIOD_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public static boolean hasRate() {
        return Double.isFinite(currentRate) && currentRate > 0.0D;
    }

    public static double getCurrentRate() {
        return currentRate;
    }

    public static long getCurrentRateTimestamp() {
        return currentRateTimestamp;
    }

    public static double moneyToCoins(long moneyPrice) {
        double rate = currentRate;
        if (!Double.isFinite(rate) || rate <= 0.0D || moneyPrice < 0L) {
            return Double.NaN;
        }
        return moneyPrice / rate;
    }

    public static String formatCoins(long moneyPrice) {
        double coins = moneyToCoins(moneyPrice);
        if (!Double.isFinite(coins)) return null;

        // Keep hundredths even for tiny AH prices: 156¤ -> 0.00, while larger
        // values are still readable (e.g. 24,750,000¤ -> 4.31 at 5,735,000).
        return String.format(Locale.ROOT, "%.2f", coins);
    }

    private static void updateSafely() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL))
                    .GET()
                    .timeout(Duration.ofSeconds(7))
                    .header("Accept", "application/json")
                    .header("User-Agent", "ProstoVisuals/0.3")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                prostovisuals.LOGGER.warn("[prostovisuals] HolyWorld coin API HTTP {}", response.statusCode());
                return;
            }

            updateFromJson(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            prostovisuals.LOGGER.warn("[prostovisuals] HolyWorld coin rate refresh failed: {}", exception.toString());
        }
    }

    static void updateFromJson(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonArray()) return;

        JsonArray trades = root.getAsJsonArray();
        long newestTimestamp = Long.MIN_VALUE;

        // First pass: find the newest valid trade timestamp.
        for (JsonElement element : trades) {
            if (!element.isJsonObject()) continue;
            JsonObject trade = element.getAsJsonObject();
            if (!trade.has("rate") || !trade.has("datetime")) continue;
            try {
                double rate = trade.get("rate").getAsDouble();
                long timestamp = trade.get("datetime").getAsLong();
                if (Double.isFinite(rate) && rate > 0.0D && timestamp > newestTimestamp) {
                    newestTimestamp = timestamp;
                }
            } catch (RuntimeException ignored) {}
        }

        if (newestTimestamp == Long.MIN_VALUE) return;

        long cutoff = newestTimestamp - RATE_WINDOW_MS;
        List<Double> recentRates = new ArrayList<>();

        for (JsonElement element : trades) {
            if (!element.isJsonObject()) continue;
            JsonObject trade = element.getAsJsonObject();
            if (!trade.has("rate") || !trade.has("datetime")) continue;
            try {
                double rate = trade.get("rate").getAsDouble();
                long timestamp = trade.get("datetime").getAsLong();
                if (Double.isFinite(rate) && rate > 0.0D && timestamp >= cutoff) {
                    recentRates.add(rate);
                }
            } catch (RuntimeException ignored) {}
        }

        if (recentRates.isEmpty()) return;

        Collections.sort(recentRates);
        int size = recentRates.size();
        double median;
        if ((size & 1) == 1) {
            median = recentRates.get(size / 2);
        } else {
            median = (recentRates.get(size / 2 - 1) + recentRates.get(size / 2)) / 2.0D;
        }

        if (Double.isFinite(median) && median > 0.0D) {
            currentRate = median;
            currentRateTimestamp = newestTimestamp;
            prostovisuals.LOGGER.info(
                    "[prostovisuals] HolyWorld coin rate: {} ({} trades, newest={})",
                    Math.round(median), recentRates.size(), newestTimestamp
            );
        }
    }
}

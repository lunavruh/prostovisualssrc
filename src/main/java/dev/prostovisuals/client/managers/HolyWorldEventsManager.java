package dev.prostovisuals.client.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Read-only client for HolyWorld's public API.
 * The API is polled off the render thread so ClickGUI never blocks on HTTP.
 */
public final class HolyWorldEventsManager {
    public static final String SERVERS_URL = "https://api.holyworld.me/v1/servers";
    public static final String EVENTS_URL = "https://api.holyworld.me/v1/events";

    private static final HolyWorldEventsManager INSTANCE = new HolyWorldEventsManager();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "HolyWorld-API");
        t.setDaemon(true);
        return t;
    });

    private volatile Map<String, String> servers = Collections.emptyMap();
    private volatile List<EventInfo> events = List.of();
    private volatile long lastUpdate;
    private volatile boolean loading = true;
    private volatile String error = "";

    private HolyWorldEventsManager() {
        executor.execute(this::refresh);
        executor.scheduleAtFixedRate(this::refresh, 30, 30, TimeUnit.SECONDS);
    }

    public static HolyWorldEventsManager getInstance() {
        return INSTANCE;
    }

    public void refresh() {
        try {
            Map<String, String> newServers = parseServers(get(SERVERS_URL));
            String eventsJson = get(EVENTS_URL);
            List<EventInfo> newEvents = parseEvents(newServers, eventsJson);
            servers = Collections.unmodifiableMap(new LinkedHashMap<>(newServers));
            events = Collections.unmodifiableList(newEvents);
            lastUpdate = System.currentTimeMillis();
            error = "";
            loading = false;
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "request failed" : e.getMessage());
            loading = false;
        }
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private Map<String, String> parseServers(String json) {
        Map<String, String> result = new LinkedHashMap<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                result.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
        return result;
    }

    private List<EventInfo> parseEvents(Map<String, String> serverMap, String json) {
        List<EventInfo> result = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        for (Map.Entry<String, JsonElement> serverEntry : root.entrySet()) {
            String serverId = serverEntry.getKey();
            String serverName = serverMap.getOrDefault(serverId, serverId);
            String serverType = detectType(serverName, serverId);

            if (!serverEntry.getValue().isJsonArray()) continue;
            for (JsonElement element : serverEntry.getValue().getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject obj = element.getAsJsonObject();
                String instanceId = string(obj, "instanceId");
                String id = string(obj, "id");

                JsonObject metadata = obj.has("metadata") && obj.get("metadata").isJsonObject()
                        ? obj.getAsJsonObject("metadata") : new JsonObject();
                String displayName = string(metadata, "displayName");
                String rarity = normalizeRarity(string(metadata, "rare"), id);

                result.add(new EventInfo(serverId, serverName, serverType, instanceId, id, displayName, rarity));
            }
        }

        result.sort(Comparator.comparing(EventInfo::serverName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(EventInfo::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    /**
     * Converts HolyWorld API rarity/internal variants into the six GUI rarities.
     * ship_default is the normal version of the Mysterious Ship, while
     * ship_roskoshni / ship_roskoshni_f are its luxurious versions.
     */
    private static String normalizeRarity(String raw, String eventId) {
        if (raw == null || raw.isBlank()) return "Обычный";
        String r = raw.toLowerCase(Locale.ROOT).trim();

        if (r.equals("ship_default") || r.equals("default") || r.equals("normal")
                || r.equals("peaceful") || r.equals("обычный")) return "Обычный";
        if (r.equals("ship_roskoshni") || r.equals("ship_roskoshni_f")
                || r.contains("роскош")) return "Роскошный";
        if (r.equals("rare") || r.contains("rare_") || r.equals("редкий")) return "Редкий";
        if (r.equals("epic") || r.contains("epic_") || r.equals("эпический")) return "Эпический";
        if (r.equals("legendary") || r.contains("legendary_") || r.equals("легендарный")) return "Легендарный";

        // Unknown event-specific API variants are not exposed as separate
        // filters; they stay in the common/default rarity bucket.
        return "Обычный";
    }

    private static String detectType(String serverName, String serverId) {
        String value = (serverName + " " + serverId).toLowerCase(Locale.ROOT);
        if (value.contains("клан") || value.contains("clan")) return "Клан";
        if (value.contains("трио") || value.contains("trio")) return "Трио";
        if (value.contains("дуо") || value.contains("duo")) return "Дуо";
        if (value.contains("соло") || value.contains("solo")) return "Соло";
        return "Другое";
    }

    public List<EventInfo> getEvents() {
        return events;
    }

    public Map<String, String> getServers() {
        return servers;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getError() {
        return error;
    }

    public static final class EventInfo {
        private final String serverId;
        private final String serverName;
        private final String serverType;
        private final String instanceId;
        private final String id;
        private final String displayName;
        private final String rarity;

        public EventInfo(String serverId, String serverName, String serverType, String instanceId,
                         String id, String displayName, String rarity) {
            this.serverId = serverId;
            this.serverName = serverName;
            this.serverType = serverType;
            this.instanceId = instanceId;
            this.id = id;
            this.displayName = displayName;
            this.rarity = rarity;
        }

        public String serverId() { return serverId; }
        public String serverName() { return serverName; }
        public String serverType() { return serverType; }
        public String instanceId() { return instanceId; }
        public String id() { return id; }
        public String displayName() { return displayName; }
        public String rarity() { return rarity; }
    }
}

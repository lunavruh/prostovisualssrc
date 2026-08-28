package dev.prostovisuals.client.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.prostovisuals.client.network.FeatureControlPayload;
import dev.prostovisuals.client.ui.clickgui.ClickGui;
import dev.prostovisuals.client.ui.colorgui.ColorPickerScreen;
import dev.prostovisuals.client.ui.spatial.SpatialDisplayScreen;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.prostovisuals;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * HolyWorld LiteAPI integration for server-side feature control.
 *
 * Channel: liteapi:feature-control
 * Client id is intentionally stable and must not change between versions.
 */
public final class HolyWorldFeatureControlManager implements Wrapper {
    public static final String CLIENT_ID = "prostovisuals";
    private static final long REQUEST_COOLDOWN_MS = 10_000L;
    private static final int CHANNEL_WAIT_TICKS = 100; // wait up to ~5 seconds for REGISTER negotiation

    private static final HolyWorldFeatureControlManager INSTANCE = new HolyWorldFeatureControlManager();

    // These IDs are the public/stable names that should be configured in HolyWorld's block-list.
    private static final Map<String, String> FEATURE_IDS_BY_MODULE_NAME;
    static {
        LinkedHashMap<String, String> ids = new LinkedHashMap<>();
        ids.put("NoRender", "norender");
        ids.put("Fullbright", "fullbright");
        ids.put("Crosshair", "crosshair");
        ids.put("ViewModel", "viewmodel");
        ids.put("TargetEsp", "targetesp");
        ids.put("AutoSprint", "autosprint");
        ids.put("UI", "ui");
        ids.put("Aspect Ratio", "aspect_ratio");
        ids.put("HitSound", "hitsound");
        ids.put("AutoRespawn", "autorespawn");
        ids.put("CustomHitBox", "customhitbox");
        ids.put("ChinaHat", "chinahat");
        ids.put("JumpCircle", "jumpcircle");
        ids.put("ClientSound", "clientsound");
        ids.put("TotemCounter", "totemcounter");
        ids.put("DamageParticles", "damageparticles");
        ids.put("SwingAnimation", "swinganimation");
        ids.put("ItemPhysic", "itemphysic");
        ids.put("FriendHelper", "friendhelper");
        ids.put("Predictions", "predictions");
        ids.put("BlockOverlay", "blockoverlay");
        ids.put("BetterMinecraft", "betterminecraft");
        ids.put("Zoom", "zoom");
        ids.put("Trails", "trails");
        ids.put("HitBubbles", "hitbubbles");
        ids.put("HitColor", "hitcolor");
        ids.put("NeonSteps", "neonsteps");
        ids.put("Pulsive", "pulsive");
        ids.put("CustomSky", "customsky");
        ids.put("MotionBlur", "motionblur");
        ids.put("SpatialDisplay", "spatialdisplay");
        ids.put("CustomModels", "custommodels");
        ids.put("SeeInvisible", "seeinvisible");
        ids.put("DiscordRPC", "discordrpc");
        ids.put("Cape", "cape");
        ids.put("HolyWorld Events", "holyworld_events");
        FEATURE_IDS_BY_MODULE_NAME = Collections.unmodifiableMap(ids);
    }

    private final Set<String> blockedFeatures = new HashSet<>();
    private final Set<String> pendingRequestIds = new HashSet<>();

    private boolean initialized;
    private boolean waitingForChannel;
    private int channelWaitTicks;
    private long lastRequestAt;

    private HolyWorldFeatureControlManager() {}

    public static HolyWorldFeatureControlManager getInstance() {
        return INSTANCE;
    }

    public void init() {
        if (initialized) return;
        initialized = true;

        // The server and client use the same channel in both directions.
        PayloadTypeRegistry.playC2S().register(FeatureControlPayload.ID, FeatureControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeatureControlPayload.ID, FeatureControlPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(FeatureControlPayload.ID, (payload, context) ->
                context.client().execute(() -> handleResponse(payload.json()))
        );

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            clearServerState();
            waitingForChannel = true;
            channelWaitTicks = 0;
            trySendJoinCheck();
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clearServerState();
            refreshVisibleUi();
        });

        // Some plugin-message channels are announced a few ticks after JOIN.
        // This only waits for local channel availability; it does not spam the server API.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!waitingForChannel) return;
            if (++channelWaitTicks > CHANNEL_WAIT_TICKS) {
                waitingForChannel = false;
                return;
            }
            trySendJoinCheck();
        });
    }

    public boolean isBlocked(Module module) {
        return module != null && isBlockedFeatureId(getFeatureId(module));
    }

    public boolean isBlockedFeatureId(String featureId) {
        if (featureId == null) return false;
        return blockedFeatures.contains(featureId.toLowerCase(Locale.ROOT));
    }

    public Set<String> getBlockedFeatures() {
        return Collections.unmodifiableSet(new HashSet<>(blockedFeatures));
    }

    public String getFeatureId(Module module) {
        if (module == null) return "";
        String mapped = FEATURE_IDS_BY_MODULE_NAME.get(module.getName());
        if (mapped != null) return mapped;

        // Future modules still get a deterministic ID, but the explicit map above keeps
        // all current server-facing IDs stable even if internal formatting changes.
        return module.getName().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public List<String> getAllFeatureIds() {
        prostovisuals pv = prostovisuals.getInstance();
        if (pv == null || pv.getModuleManager() == null) return List.of();

        ArrayList<String> ids = new ArrayList<>();
        for (Module module : pv.getModuleManager().getModules()) {
            String id = getFeatureId(module);
            if (!id.isBlank()) ids.add(id);
        }
        return List.copyOf(ids);
    }

    private void trySendJoinCheck() {
        if (!waitingForChannel) return;
        if (!ClientPlayNetworking.canSend(FeatureControlPayload.ID)) return;
        waitingForChannel = false;
        requestAllFeatures();
    }

    /** Sends exactly one check for the complete current module registry. */
    public boolean requestAllFeatures() {
        long now = System.currentTimeMillis();
        if (now - lastRequestAt < REQUEST_COOLDOWN_MS) return false;
        if (!ClientPlayNetworking.canSend(FeatureControlPayload.ID)) return false;

        List<String> features = getAllFeatureIds();
        if (features.isEmpty()) return false;

        String requestId = UUID.randomUUID().toString();
        JsonObject root = new JsonObject();
        root.addProperty("id", requestId);
        root.addProperty("method", "checkFeatures");

        JsonObject payload = new JsonObject();
        payload.addProperty("client", CLIENT_ID);
        JsonArray featureArray = new JsonArray();
        for (String feature : features) featureArray.add(feature);
        payload.add("features", featureArray);
        root.add("payload", payload);

        try {
            ClientPlayNetworking.send(new FeatureControlPayload(root.toString()));
            lastRequestAt = now;
            pendingRequestIds.add(requestId);
            prostovisuals.LOGGER.info("[LiteAPI] Sent checkFeatures for {} features.", features.size());
            return true;
        } catch (Throwable t) {
            prostovisuals.LOGGER.warn("[LiteAPI] Failed to send feature-control request.", t);
            return false;
        }
    }

    private void handleResponse(String json) {
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return;
            JsonObject root = parsed.getAsJsonObject();

            String id = getString(root, "id");
            if (id == null || !pendingRequestIds.remove(id)) {
                // Ignore stale/foreign messages on the shared channel.
                return;
            }

            boolean ok = root.has("ok") && root.get("ok").isJsonPrimitive() && root.get("ok").getAsBoolean();
            if (!ok) {
                String error = getString(root, "error");
                String message = getString(root, "message");
                prostovisuals.LOGGER.warn("[LiteAPI] feature-control error: {} ({})", error, message);
                return;
            }

            Set<String> newBlocklist = new HashSet<>();
            JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
                    ? root.getAsJsonObject("payload") : null;
            if (payload != null && payload.has("blocklist") && payload.get("blocklist").isJsonArray()) {
                for (JsonElement entry : payload.getAsJsonArray("blocklist")) {
                    if (entry.isJsonPrimitive() && entry.getAsJsonPrimitive().isString()) {
                        String feature = entry.getAsString().trim().toLowerCase(Locale.ROOT);
                        if (!feature.isEmpty()) newBlocklist.add(feature);
                    }
                }
            }

            applyBlocklist(newBlocklist);
            prostovisuals.LOGGER.info("[LiteAPI] Feature blocklist applied: {} blocked.", newBlocklist.size());
        } catch (Throwable t) {
            prostovisuals.LOGGER.warn("[LiteAPI] Invalid feature-control response.", t);
        }
    }

    private void applyBlocklist(Set<String> newBlocklist) {
        blockedFeatures.clear();
        blockedFeatures.addAll(newBlocklist);

        prostovisuals pv = prostovisuals.getInstance();
        if (pv != null && pv.getModuleManager() != null) {
            for (Module module : pv.getModuleManager().getModules()) {
                if (module.isToggled() && isBlocked(module)) {
                    // Keep config untouched, but stop the feature immediately and without
                    // a notification that would reveal the now-hidden feature name.
                    module.setToggledFromServer(false);
                }
            }
        }
        refreshVisibleUi();
    }

    private void clearServerState() {
        blockedFeatures.clear();
        pendingRequestIds.clear();
        waitingForChannel = false;
        channelWaitTicks = 0;
        lastRequestAt = 0L;
    }

    private void refreshVisibleUi() {
        try {
            prostovisuals pv = prostovisuals.getInstance();
            if (pv == null) return;
            if (pv.getClickGui() != null) pv.getClickGui().refreshModuleVisibility();

            if (mc.currentScreen instanceof ColorPickerScreen || mc.currentScreen instanceof SpatialDisplayScreen) {
                mc.setScreen(null);
            } else if (mc.currentScreen instanceof ClickGui clickGui) {
                clickGui.refreshModuleVisibility();
            }
        } catch (Throwable t) {
            prostovisuals.LOGGER.debug("[LiteAPI] Could not refresh feature UI.", t);
        }
    }

    private static String getString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) return null;
        try {
            return object.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }
}

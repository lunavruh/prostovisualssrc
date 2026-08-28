package dev.prostovisuals.client.custommodels;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Figura cosmetic loader adapted for ProstoVisual using the same local-avatar flow as the source pack.
 * Cosmetics remain Figura avatars (.bbmodel + Lua + textures + avatar.json),
 * so their original scripts/physics/animations keep working instead of being flattened.
 */
public final class FiguraCosmeticsEngine {
    private static final MinecraftClient MC = MinecraftClient.getInstance();
    private static final String RESOURCE_ROOT = "assets/prostovisuals/cosmetics";
    private static final String INSTALL_MARKER = ".prostovisual_cosmetics_v14";
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("#U([0-9a-fA-F]{4})");

    /** Keeps selected local avatar UserData strongly referenced while the cosmetic is active. */
    private static final ConcurrentHashMap<UUID, Object> LOCAL_AVATARS = new ConcurrentHashMap<>();
    private static volatile boolean installed;
    private static volatile List<CosmeticEntry> cachedEntries = List.of();
    private static volatile String catalogSignature = "";
    private static volatile boolean figuraPrepared;

    private FiguraCosmeticsEngine() {}

    public static Path cosmeticsDirectory() {
        return MC.runDirectory.toPath().resolve("prostovisuals").resolve("cosmetics");
    }

    /** Extract bundled cosmetics once into the normal writable run directory. */
    public static synchronized void ensureInstalled() {
        if (installed && Files.isDirectory(cosmeticsDirectory())) return;
        Path out = cosmeticsDirectory();
        try {
            Files.createDirectories(out);
            Path marker = out.resolve(INSTALL_MARKER);
            if (!Files.exists(marker)) {
                var container = FabricLoader.getInstance().getModContainer("prostovisuals")
                        .or(() -> FabricLoader.getInstance().getModContainer("wyvern")).orElse(null);
                if (container != null) {
                    Optional<Path> rootOpt = container.findPath(RESOURCE_ROOT);
                    if (rootOpt.isPresent()) {
                        Path root = rootOpt.get();
                        try (Stream<Path> stream = Files.walk(root)) {
                            for (Path source : stream.toList()) {
                                if (Files.isDirectory(source)) continue;
                                Path rel = root.relativize(source);
                                Path target = out.resolve(rel.toString());
                                Files.createDirectories(target.getParent());
                                // Built-ins should be deterministic; overwrite bundled files on version change.
                                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                        Files.writeString(marker, "bundled-v14", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        installed = true;
        refreshCatalog(true);
    }

    public static List<CosmeticEntry> getCatalog() {
        ensureInstalled();
        // The bundled catalog is immutable while the game is running. Avoid walking
        // dozens of avatar folders every rendered frame of the picker.
        if (cachedEntries.isEmpty()) refreshCatalog(true);
        return cachedEntries;
    }

    public static synchronized void reloadCatalog() {
        ensureInstalled();
        refreshCatalog(true);
    }

    private static synchronized void refreshCatalog(boolean force) {
        Path root = cosmeticsDirectory();
        if (!Files.isDirectory(root)) {
            cachedEntries = List.of();
            catalogSignature = "";
            return;
        }
        try {
            List<Path> dirs = new ArrayList<>();
            collectAvatarDirectories(root, dirs);
            dirs.sort(Comparator.comparing(p -> displayName(root, p), String.CASE_INSENSITIVE_ORDER));
            String signature = dirs.stream().map(p -> p.toAbsolutePath().normalize().toString()).reduce((a,b)->a+"|"+b).orElse("");
            if (!force && signature.equals(catalogSignature)) return;

            List<CosmeticEntry> result = new ArrayList<>();
            for (Path dir : dirs) {
                String rel = root.relativize(dir).toString().replace('\\', '/');
                String low = rel.toLowerCase(Locale.ROOT);
                String top = low.contains("/") ? low.substring(0, low.indexOf('/')) : low;
                String display = displayName(root, dir);
                // Wings/Halo are intentionally not Figura wardrobe items anymore.
                // They live in the dedicated Render -> Wings module so they can never
                // remain attached after the cosmetic card is disabled.
                String displayLower = display == null ? "" : display.toLowerCase(Locale.ROOT);
                if (displayLower.contains("wing") || displayLower.contains("halo")) continue;

                CosmeticEntry.Kind kind = classifyCosmetic(dir, top, display);
                result.add(new CosmeticEntry(display, rel, dir, kind));
            }
            cachedEntries = List.copyOf(result);
            catalogSignature = signature;
        } catch (Throwable ignored) {
        }
    }

    private static CosmeticEntry.Kind classifyCosmetic(Path dir, String top, String displayName) {
        String name = displayName == null ? "" : displayName.toLowerCase(Locale.ROOT);
        String description = "";
        String category = "";
        try {
            Path avatar = dir.resolve("avatar.json");
            if (Files.isRegularFile(avatar)) {
                var json = JsonParser.parseString(Files.readString(avatar));
                if (json.isJsonObject()) {
                    var obj = json.getAsJsonObject();
                    if (obj.has("description")) description = obj.get("description").getAsString().toLowerCase(Locale.ROOT);
                    if (obj.has("category")) category = obj.get("category").getAsString().toLowerCase(Locale.ROOT);
                }
            }
        } catch (Throwable ignored) {}

        // Only Mothli is a pet. The three model-pack entries that happen to mention
        // companion/plush/goat are normal models and must stay in Models.
        if (name.equals("mothli")) return CosmeticEntry.Kind.PET;
        if (top.startsWith("w") || category.equals("weapon")) return CosmeticEntry.Kind.WEAPON;
        // Head-pack folders win over generic words like "hat" so head cosmetics stay together.
        if (top.startsWith("h")) return CosmeticEntry.Kind.HEAD;
        if (name.contains("hat") || name.contains("pumpkin")) return CosmeticEntry.Kind.HAT;
        return CosmeticEntry.Kind.MODEL;
    }

    private static void collectAvatarDirectories(Path dir, List<Path> out) {
        if (!Files.isDirectory(dir) || isIgnoredDirectory(dir)) return;
        if (hasAvatarJson(dir)) out.add(dir);
        // Some packs contain a valid avatar inside another valid avatar folder
        // (for example Peter/Peter). Keep traversing so every cosmetic is listed.
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.filter(Files::isDirectory).toList()) collectAvatarDirectories(child, out);
        } catch (Throwable ignored) {}
    }

    private static boolean isIgnoredDirectory(Path path) {
        Path name = path.getFileName();
        return name != null && name.toString().startsWith(".");
    }

    private static boolean hasAvatarJson(Path path) {
        try (Stream<Path> stream = Files.list(path)) {
            return stream.anyMatch(p -> p.getFileName().toString().equalsIgnoreCase("avatar.json"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static CosmeticEntry findByRelativePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return null;
        for (CosmeticEntry e : getCatalog()) if (e.relativePath().equalsIgnoreCase(relativePath)) return e;
        return null;
    }

    public static String displayName(Path root, Path dir) {
        // Folder names are intentionally short to avoid Windows MAX_PATH issues.
        // The visible cosmetic name comes from Figura's avatar.json instead.
        try {
            Path avatar = dir.resolve("avatar.json");
            if (Files.isRegularFile(avatar)) {
                var json = JsonParser.parseString(Files.readString(avatar));
                if (json.isJsonObject() && json.getAsJsonObject().has("name")) {
                    String value = json.getAsJsonObject().get("name").getAsString();
                    if (value != null && !value.isBlank()) return value.trim();
                }
            }
        } catch (Throwable ignored) {}

        String s = dir.startsWith(root) ? root.relativize(dir).toString().replace('\\','/')
                : String.valueOf(dir.getFileName());
        return s.replace('/', ' ').replaceAll("\\s+", " ").trim();
    }

    /** Strip UTF-8 BOM from Lua files before Figura compiles them. */
    private static void sanitizeLua(Path root) {
        if (root == null || !Files.isDirectory(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : stream.filter(Files::isRegularFile).filter(FiguraCosmeticsEngine::isLua).toList()) stripBom(p);
        } catch (Throwable ignored) {}
    }

    private static boolean isLua(Path p) {
        Path n = p.getFileName();
        return n != null && n.toString().toLowerCase(Locale.ROOT).endsWith(".lua");
    }

    private static void stripBom(Path p) {
        try {
            byte[] b = Files.readAllBytes(p);
            if (b.length >= 3 && (b[0]&255)==239 && (b[1]&255)==187 && (b[2]&255)==191) {
                Files.write(p, Arrays.copyOfRange(b, 3, b.length));
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isFiguraAvailable() {
        try { Class.forName("org.figuramc.figura.avatar.local.LocalAvatarLoader"); return true; }
        catch (Throwable ignored) { return false; }
    }

    /** Prepare the Figura runtime pieces used by the original Zenith cosmetics loader. */
    private static synchronized void prepareFiguraRuntime() {
        if (figuraPrepared) return;
        try {
            Class<?> pm = Class.forName("org.figuramc.figura.permissions.PermissionManager");
            try {
                Field categories = pm.getDeclaredField("CATEGORIES");
                categories.setAccessible(true);
                Object v = categories.get(null);
                boolean empty = v instanceof Map<?,?> map ? map.isEmpty()
                        : v instanceof Iterable<?> it && !it.iterator().hasNext();
                if (empty) {
                    Method init = findMethod(pm, "init");
                    if (init != null) init.invoke(null);
                }
            } catch (Throwable ignored) {}

            try {
                Class<?> configs = Class.forName("org.figuramc.figura.config.Configs");
                Field mainDir = configs.getDeclaredField("MAIN_DIR");
                mainDir.setAccessible(true);
                Object setting = mainDir.get(null);
                if (setting != null) {
                    Method set = findMethod(setting.getClass(), "setValue", Object.class);
                    if (set == null) set = findMethod(setting.getClass(), "setValue", String.class);
                    if (set != null) set.invoke(setting, "prostovisuals/cosmetics");
                }
            } catch (Throwable ignored) {}

            figuraPrepared = true;
        } catch (Throwable ignored) {
            figuraPrepared = false;
        }
    }

    /** Load a selected Figura avatar onto the local player. Returns true when the handoff succeeded. */
    public static synchronized boolean applyLocal(CosmeticEntry entry) {
        if (entry == null || MC.player == null || !Files.isDirectory(entry.directory())) return false;
        ensureInstalled();
        sanitizeLua(entry.directory());
        prepareFiguraRuntime();
        try {
            UUID uuid = MC.player.getUuid();
            Object userData = newUserData(uuid);
            if (userData == null) return false;

            Class<?> loader = Class.forName("org.figuramc.figura.avatar.local.LocalAvatarLoader");
            Method load = findMethod(loader, "loadAvatar", Path.class, userData.getClass());
            if (load == null) load = findMethodByArity(loader, "loadAvatar", 2);
            if (load == null) return false;
            load.setAccessible(true);
            load.invoke(null, entry.directory(), userData);
            LOCAL_AVATARS.put(uuid, userData);
            installIntoFiguraManager(uuid, userData);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Remove the local Figura override and let the vanilla/other renderer take over. */
    public static synchronized void clearLocal() {
        if (MC.player == null) return;
        UUID uuid = MC.player.getUuid();
        LOCAL_AVATARS.remove(uuid);
        try {
            Map<Object,Object> map = findFiguraUserMap();
            if (map != null) map.remove(uuid);
        } catch (Throwable ignored) {}
    }

    private static Object newUserData(UUID uuid) {
        try {
            Class<?> c = Class.forName("org.figuramc.figura.avatar.UserData");
            Constructor<?> ctor = c.getConstructor(UUID.class);
            ctor.setAccessible(true);
            return ctor.newInstance(uuid);
        } catch (Throwable ignored) { return null; }
    }

    /** Mirror the loaded UserData into Figura's UUID map when present. */
    @SuppressWarnings("unchecked")
    private static void installIntoFiguraManager(UUID uuid, Object userData) {
        try {
            Map<Object,Object> map = findFiguraUserMap();
            if (map != null) map.put(uuid, userData);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static Map<Object,Object> findFiguraUserMap() {
        try {
            Class<?> c = Class.forName("org.figuramc.figura.avatar.AvatarManager");
            for (Field f : c.getDeclaredFields()) {
                if (!ConcurrentHashMap.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof ConcurrentHashMap<?,?> map) return (Map<Object,Object>) map;
            }
            for (Field f : c.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v = f.get(null);
                if (v instanceof Map<?,?> map) return (Map<Object,Object>) map;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... types) {
        try { Method m = c.getMethod(name, types); m.setAccessible(true); return m; }
        catch (Throwable ignored) {
            try { Method m = c.getDeclaredMethod(name, types); m.setAccessible(true); return m; }
            catch (Throwable ignored2) { return null; }
        }
    }

    private static Method findMethodByArity(Class<?> c, String name, int count) {
        for (Method m : c.getDeclaredMethods()) if (m.getName().equals(name) && m.getParameterCount()==count) return m;
        for (Method m : c.getMethods()) if (m.getName().equals(name) && m.getParameterCount()==count) return m;
        return null;
    }
}

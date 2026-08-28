package dev.prostovisuals.client.ui.clickgui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/** Independent ClickGUI language selector. It never changes Minecraft's global language. */
public final class ClickGuiLanguage {
    private static final Path LANGUAGE_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("prostovisuals-clickgui-language.txt");
    private static final Map<String, String> ENGLISH = loadLanguage("en_us");
    private static final Map<String, String> RUSSIAN = loadLanguage("ru_ru");
    private static Language language;

    static {
        add("prostovisuals.clickgui.search", "Search", "Поиск");
        add("prostovisuals.clickgui.bottom_hint",
                "LMB toggle   •   RMB settings   •   MMB bind menu",
                "ЛКМ включить   •   ПКМ настройки   •   СКМ меню бинда");

        add("setting.intensity", "Intensity", "Интенсивность");
        add("setting.radius", "Radius", "Радиус");
        add("setting.duration", "Duration", "Длительность");
        add("setting.self", "Self", "На себе");
        add("setting.players", "Players", "Игроки");
        add("setting.landingDimming", "Landing Dimming", "Затемнение при приземлении");
        add("setting.stars", "Stars", "Звёзды");
        add("setting.rotation", "Rotation", "Поворот");
        add("setting.meteorFrequency", "Meteor Frequency", "Частота метеоров");
        add("setting.haloOpacity", "Halo Opacity", "Прозрачность нимба");
        add("setting.haloSpeed", "Halo Speed", "Скорость нимба");
        add("prostovisuals.theme.custom_rgb", "Custom RGB", "Свой цвет RGB");

        add("Cosmos", "Cosmos", "Космос");
        add("Water Color", "Water color", "Цвет воды");
        add("Caustic Color", "Caustic color", "Цвет каустики");
        add("Aurora Color", "Aurora color", "Цвет авроры");
        add("Water", "Water", "Вода");
        add("Caustic", "Caustic", "Каустика");
        add("Meteor Shower", "Meteor Shower", "Метеоритный дождь");
        add("Neon", "Neon", "Неон");
        add("Lightning", "Lightning", "Молния");
        add("Nova", "Nova", "Нова");
        add("Vortex", "Vortex", "Вихрь");
        add("Footprint", "Footprint", "След");
        add("Wings", "Wings", "Крылья");
        add("Slash", "Slash", "Разрез");
        add("Blossom", "Blossom", "Цветение");

        add("Animation", "Animation", "Анимация");
        add("Circle Size", "Circle Size", "Размер круга");
        add("Circle Range", "Expansion Range", "Дальность расширения");
        add("Ambience", "World Ambience", "Атмосфера мира");
        add("Custom Time", "Custom Time", "Свое время");
        add("World Time", "World Time", "Время мира");
        add("Custom Fog", "Custom Fog", "Свой туман");
        add("Fog Distance", "Fog End Distance", "Конец тумана");
        add("Fog End Distance", "Fog end distance", "Конец тумана");
        add("Fog Start Distance", "Fog Start Distance", "Начало тумана");
        add("Fog Saturation", "Fog Saturation", "Насыщенность тумана");
        add("Particles", "Particles", "Частицы");
        add("Spark", "Spark", "Искра");
        add("Sparkle", "Sparkle", "Сияние");
        add("Dollar", "Dollar", "Доллар");
        add("Glow", "Glow", "Свечение");
        add("Snow", "Snow", "Снег");
        add("Star", "Star", "Звезда");
        add("Particle Texture", "Particle Texture", "Текстура частиц");
        add("Particle Count", "Particle Count", "Количество частиц");
        add("Particle Glow", "Particle Glow", "Свечение частиц");
        add("Spawn While Idle", "Spawn While Idle", "При бездействии");
        add("Spawn While Running", "Spawn While Running", "При беге");
        add("Spawn On Hit", "Spawn On Hit", "При ударе");
        add("Spawn On Pearl Landing", "Spawn On Pearl Landing", "При падении жемчуга");
        add("Spawn On Trident Landing", "Spawn On Trident Landing", "При падении трезубца");
        add("Spawn On Totem Pop", "Spawn On Totem Pop", "При срабатывании тотема");
        add("Spark 1", "Spark 1", "Спарк 1");
        add("Spark 2", "Spark 2", "Спарк 2");
        add("Spark 3", "Spark 3", "Спарк 3");
        add("Sparkle", "Sparkle", "Сияние");
        add("Dollar", "Dollar", "Доллар");
        add("Bloom", "Bloom", "Блум");
        add("Snow", "Snow", "Снег");
        add("Duration", "Duration", "Длительность");
        add("Effect Speed", "Speed", "Скорость");
        add("Effect Scale", "Size", "Размер");
        add("Pattern Strength", "Intensity", "Интенсивность");
        add("Opacity", "Opacity", "Прозрачность");
        add("First", "First", "Первый");
        add("Grow", "Grow", "Рост");
        add("Highlight Players", "Highlight Players", "Подсвечивать игроков");
        add("Intensity", "Intensity", "Интенсивность");
        add("Intensity / Glow", "Intensity / Glow", "Интенсивность / свечение");
        add("Landing Dimming", "Landing Dimming", "Затемнение при приземлении");
        add("Life Time", "Life Time", "Время жизни");
        add("Meteor Frequency", "Meteor Frequency", "Частота метеоров");
        add("Mode", "Mode", "Режим");
        add("Players", "Players", "Игроки");
        add("Pulse", "Pulse", "Пульсация");
        add("Radius", "Radius", "Радиус");
        add("Ripple", "Ripple", "Рябь");
        add("Rotation", "Rotation", "Поворот");
        add("Second", "Second", "Второй");
        add("Self", "Self", "На себе");
        add("Show Bow", "Show Bow", "Показывать лук");
        add("Show Crossbow", "Show Crossbow", "Показывать арбалет");
        add("Show Landing Info", "Show Landing Info", "Показывать данные приземления");
        add("Show Pearl", "Show Pearl", "Показывать жемчуг");
        add("Show Trident", "Show Trident", "Показывать трезубец");
        add("Show When Holding", "Show When Holding", "Показывать в руке");
        add("Size", "Size", "Размер");
        add("Sound", "Sound", "Звук");
        add("Speed", "Speed", "Скорость");
        add("Stars", "Stars", "Звёзды");
        add("Texture", "Texture", "Текстура");

        add("Взлет", "Takeoff", "Взлёт");
        add("Глянцевые", "Glossy", "Глянцевые");
        add("Динамический разрыв", "Dynamic Break", "Динамический разрыв");
        add("Длина", "Length", "Длина");
        add("Замена ника", "Nickname Replacement", "Замена ника");
        add("Интервал спавна", "Spawn Interval", "Интервал спавна");
        add("Количество", "Amount", "Количество");
        add("Простой", "Simple", "Простой");
        add("Размер", "Size", "Размер");
        add("Разрыв", "Break", "Разрыв");
        add("Рандомный цвет", "Random Color", "Рандомный цвет");
        add("Скрывать ники друзей", "Hide Friend Names", "Скрывать ники друзей");
        add("Толщина", "Thickness", "Толщина");
        add("Цвет при наведении", "Hover Color", "Цвет при наведении");


        // Complete UI/module vocabulary used by OneClientClickGui.
        add("Modules", "Modules", "Модули");
        add("Combat", "Combat", "Бой");
        add("Render", "Visuals", "Визуалы");
        add("Utility", "Utility", "Разное");
        add("Customize", "Customize", "Кастомизация");
        add("Monitors", "Monitors", "Мониторы");
        add("Themes", "Themes", "Темы");
        add("Keybinds", "Keybinds", "Бинды");
        add("HUD Elements", "HUD Elements", "Элементы HUD");
        add("Watermark", "Watermark", "Ватермарка");
        add("Potions", "Potion Effects", "Эффекты зелий");
        add("Notifications", "Notifications", "Уведомления");
        add("Information", "Information", "Информация");
        add("Keybinds HUD", "Keybinds", "Бинды");
        add("Target HUD", "Target HUD", "HUD цели");
        add("Module List", "Module List", "Список модулей");
        add("Models", "Models", "Модели");
        add("Heads", "Heads", "Головы");
        add("Hats", "Hats", "Шляпы");
        add("Weapons", "Weapons", "Оружие");
        add("Pets", "Pets", "Питомцы");
        add("MODEL", "Model", "Модель");
        add("HEAD", "Head", "Голова");
        add("HAT", "Hat", "Шляпа");
        add("WEAPON", "Weapon", "Оружие");
        add("PET", "Pet", "Питомец");
        add("Style", "Style", "Стиль");
        add("Butterfly", "Butterfly", "Бабочка");
        add("Classic", "Classic", "Классические");
        add("Combined", "Combined", "Совмещённые");
        add("Follow", "Follow behind", "Следовать сзади");
        add("In front", "Stay in front", "Держаться впереди");
        add("Orbit", "Orbit around you", "Кружить вокруг");
        add("Assist target", "Rush your target", "Лететь к вашей цели");
        add("Enabled", "Enabled", "Включено");
        add("Disabled", "Disabled", "Выключено");
        add("General", "General", "Основные");
        add("No extra settings", "No extra settings", "Дополнительных настроек нет");
        add("Select a module", "Select a module", "Выберите модуль");
        add("Its settings will appear here", "Its settings will appear here", "Здесь появятся его настройки");
        add("Search modules...", "Search modules...", "Поиск модулей...");
        add("Search results", "Search results", "Результаты поиска");
        add("Interface settings", "Interface settings", "Настройки интерфейса");
        add("Settings language", "Interface language", "Язык интерфейса");
        add("Theme", "Theme", "Тема");
        add("Custom color", "Custom color", "Свой цвет");
        add("Wardrobe", "Wardrobe", "Гардероб");
        add("Click an equipped item again to remove", "Click an equipped item again to remove it", "Нажмите на надетый предмет ещё раз, чтобы снять");
        add("PET BEHAVIOR", "PET BEHAVIOR", "ПОВЕДЕНИЕ ПИТОМЦА");
        add("REMOVE COSMETIC", "REMOVE COSMETIC", "СНЯТЬ КОСМЕТИКУ");
        add("No cosmetic active", "No cosmetic active", "Косметика не выбрана");
        add("Choose one from the wardrobe", "Choose one from the wardrobe", "Выберите предмет в гардеробе");
        add("EQUIPPED", "EQUIPPED", "НАДЕТО");
        add("PREVIEW", "PREVIEW", "ПРЕДПРОСМОТР");

        add("NoRender", "No Render", "Отключение рендера");
        add("Rain / Snow", "Rain / Snow", "Дождь / снег");
        add("HighlightLowDuration", "Highlight short-duration effects", "Подсвечивать эффекты с малым временем");
        add("Scale", "Scale", "Масштаб");
        add("ShowNegative", "Show negative effects", "Показывать негативные эффекты");
        add("displayAbsorption", "Show absorption", "Показывать поглощение");
        add("Fullbright", "Full Bright", "Полная яркость");
        add("Crosshair", "Crosshair", "Прицел");
        add("ViewModel", "View Model", "Положение рук");
        add("TargetEsp", "Target ESP", "Подсветка цели");
        add("UI", "Click GUI", "Клик GUI");
        add("Aspect Ratio", "Aspect Ratio", "Соотношение сторон");
        add("HitSound", "Hit Sound", "Звук удара");
        add("CustomHitBox", "Custom Hitbox", "Настройка хитбоксов");
        add("JumpCircle", "Jump Circle", "Круг при прыжке");
        add("ClientSound", "Client Sounds", "Звуки клиента");
        add("TotemCounter", "Totem Counter", "Счётчик тотемов");
        add("DamageParticles", "Damage Particles", "Частицы урона");
        add("HolyWorld Events", "HolyWorld Events", "События HolyWorld");
        add("SwingAnimation", "Swing Animation", "Анимация взмаха");
        add("ItemPhysic", "Item Physics", "Физика предметов");
        add("FriendHelper", "Friend Helper", "Друзья");
        add("Predictions", "Projectile Predictions", "Траектории снарядов");
        add("BlockOverlay", "Block Overlay", "Обводка блока");
        add("BetterMinecraft", "Better Minecraft", "Улучшенный Minecraft");
        add("Zoom", "Zoom", "Приближение");
        add("Trails", "Trails", "Следы");
        add("HitBubbles", "Hit Bubbles", "Эффекты удара");
        add("HitColor", "Hit Color", "Цвет удара");
        add("NeonSteps", "Neon Steps", "Неоновые шаги");
        add("Pulsive", "Pulsive", "Пульсация");
        add("CustomSky", "Custom Sky", "Кастомное небо");
        add("MotionBlur", "Motion Blur", "Размытие движения");
        add("SeeInvisible", "See Invisible", "Невидимые игроки");
        add("DiscordRPC", "Discord Rich Presence", "Discord статус");
        add("Cape", "Cape", "Плащ");
        add("Wings", "Wings", "Крылья");
        add("NameProtect", "Name Protect", "Скрытие ника");
        add("AutoRespawn", "Auto Respawn", "Автовозрождение");

        add("Color", "Color", "Цвет");
        add("Custom Color", "Custom Color", "Свой цвет");
        add("Fog Blur", "Fog Blur", "Размытие тумана");
        add("Fog Blur Strength", "Fog Blur Strength", "Сила размытия тумана");
        add("Fog Color", "Fog Color", "Цвет тумана");
        add("Fog Start", "Fog Start Distance", "Начало тумана");
        add("Shader Scale", "Shader Scale", "Масштаб шейдера");
        add("Shader Speed", "Shader Speed", "Скорость шейдера");
        add("Strength", "Strength", "Сила");
        add("Selected Model", "Selected Model", "Выбранная модель");
        add("Customize Menu", "Customize Menu Key", "Клавиша кастомизации");
        add("Aurora", "Aurora", "Аврора");
        add("ChinaHat", "China Hat", "Китайская шляпа");
        add("Hat Style", "Hat Style", "Вид шляпы");
        add("ProstoVisual", "ProstoVisual", "ProstoVisual");
        add("Wyvern", "Wyvern", "Wyvern");
        add("Crown", "Crown", "Корона");
        add("Energy", "Energy", "Энергия");
        add("Galaxy", "Galaxy", "Галактика");
        add("Glow", "Glow", "Свечение");
        add("Heart", "Heart", "Сердце");
        add("new", "New", "Новый");
        add("old", "Old", "Старый");
        add("Plasma", "Plasma", "Плазма");
        add("Snowflake", "Snowflake", "Снежинка");
        add("Star", "Star", "Звезда");
        add("Starfall", "Starfall", "Звездопад");
        add("Режим", "Mode", "Режим");

        add("Water Texture Color", "Water texture color", "Цвет текстуры воды");
        add("Caustic Texture Color", "Caustic texture color", "Цвет текстуры каустики");
        add("Aurora Texture Color", "Aurora texture color", "Цвет текстуры авроры");

        language = loadSelection();
    }

    private ClickGuiLanguage() {}

    public static String translate(String key) {
        if (key == null || key.isEmpty()) return "";
        Map<String, String> table = language == Language.RU ? RUSSIAN : ENGLISH;
        String value = table.get(key);
        if (value != null) return value;
        // Settings commonly come through as translation-style ids. Resolve the readable tail
        // instead of leaking raw setting.fooBar names into either language.
        String tail = key;
        int dot = tail.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < tail.length()) tail = tail.substring(dot + 1);
        tail = tail.replace('_', ' ').replace('-', ' ');
        tail = tail.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        if (language == Language.EN) return titleCase(tail);
        return titleCase(tail);
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) return "";
        String[] parts = value.trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

    public static String getCode() {
        return language.name();
    }

    /** Returns both explicit language aliases for search, regardless of the active UI language. */
    public static String searchAliases(String key) {
        if (key == null || key.isBlank()) return "";
        String en = ENGLISH.getOrDefault(key, humanize(key));
        String ru = RUSSIAN.getOrDefault(key, humanize(key));
        return key + " " + en + " " + ru;
    }

    private static String humanize(String key) {
        String tail = key;
        int dot = tail.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < tail.length()) tail = tail.substring(dot + 1);
        tail = tail.replace('_', ' ').replace('-', ' ');
        tail = tail.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
        return titleCase(tail);
    }

    public static boolean isRussian() {
        return language == Language.RU;
    }

    public static void toggle() {
        language = language == Language.RU ? Language.EN : Language.RU;
        saveSelection();
    }

    private static Map<String, String> loadLanguage(String code) {
        Map<String, String> result = new HashMap<>();
        String path = "/assets/prostovisuals/lang/" + code + ".json";
        try (InputStream stream = ClickGuiLanguage.class.getResourceAsStream(path)) {
            if (stream == null) return result;
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()) {
                        result.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void add(String key, String english, String russian) {
        ENGLISH.putIfAbsent(key, english);
        RUSSIAN.putIfAbsent(key, russian);
    }

    public static String marquee(String text, float maxWidth, float fontSize, boolean hovered) {
        if (text == null || text.isEmpty()) return "";
        try {
            float width = dev.prostovisuals.client.util.renderer.fonts.Fonts.REGULAR.getWidth(text, fontSize);
            if (width <= maxWidth) return text;
            if (!hovered) {
                String out = text;
                while (out.length() > 2 && dev.prostovisuals.client.util.renderer.fonts.Fonts.REGULAR.getWidth(out + "…", fontSize) > maxWidth) {
                    out = out.substring(0, out.length() - 1);
                }
                return out + "…";
            }
            String loop = text + "     " + text;
            int period = Math.max(1, text.length() + 5);
            int start = (int)((System.currentTimeMillis() / 155L) % period);
            String out = loop.substring(Math.min(start, loop.length() - 1));
            while (out.length() > 1 && dev.prostovisuals.client.util.renderer.fonts.Fonts.REGULAR.getWidth(out, fontSize) > maxWidth) {
                out = out.substring(0, out.length() - 1);
            }
            return out;
        } catch (Throwable ignored) {
            return text;
        }
    }

    private static Language loadSelection() {
        try {
            if (Files.isRegularFile(LANGUAGE_FILE)) {
                return Language.valueOf(Files.readString(LANGUAGE_FILE, StandardCharsets.UTF_8).trim());
            }
        } catch (Exception ignored) {
        }
        return Language.RU;
    }

    private static void saveSelection() {
        try {
            Files.createDirectories(LANGUAGE_FILE.getParent());
            Files.writeString(LANGUAGE_FILE, language.name(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    private enum Language {
        RU,
        EN
    }
}

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

        language = loadSelection();
    }

    private ClickGuiLanguage() {}

    public static String translate(String key) {
        if (key == null || key.isEmpty()) return "";
        return (language == Language.RU ? RUSSIAN : ENGLISH).getOrDefault(key, key);
    }

    public static String getCode() {
        return language.name();
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

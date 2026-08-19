package dev.prostovisuals.client.managers;

import java.awt.Color;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight theme manager.
 *
 * No background scheduler: static themes do not need a 20-40 Hz listener
 * broadcast. Custom color changes notify immediately.
 */
public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();

    private final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final LightTheme white = new LightTheme();
    private final StaticTheme ice = new StaticTheme("Ice", new Color(120, 205, 255));
    private final StaticTheme violet = new StaticTheme("Violet", new Color(177, 116, 255));
    private final StaticTheme emerald = new StaticTheme("Emerald", new Color(67, 232, 161));
    private final StaticTheme sunset = new StaticTheme("Sunset", new Color(255, 139, 91));
    private final CustomTheme custom = new CustomTheme(new Color(130, 170, 255));

    private final Theme[] themes = {white, ice, violet, emerald, sunset, custom};
    private Theme currentTheme = white;

    private ThemeManager() {}

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public Theme[] getAvailableThemes() {
        // Themes are fixed for the whole client lifetime. Returning the stable
        // array avoids a per-frame clone in ClickGUI.
        return themes;
    }

    public Color getThemeColor() {
        return currentTheme.getAccentColor();
    }

    public CustomTheme getCustomTheme() {
        return custom;
    }

    public void setCustomColor(Color color) {
        custom.setColor(color);
        if (currentTheme == custom) notifyListeners();
    }

    public Color getCustomColor() {
        return custom.getAccentColor();
    }

    public void setTheme(Theme theme) {
        if (theme == null) return;
        currentTheme = theme;
        notifyListeners();
    }

    public void addThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(currentTheme);
            } catch (Throwable ignored) {}
        }
    }

    public interface Theme {
        Color getBackgroundColor();
        Color getBorderColor();
        Color getTextColor();
        Color getAccentColor();
        Color getSecondaryBackgroundColor();
        String getName();
    }

    public interface ThemeChangeListener {
        void onThemeChanged(Theme theme);
    }

    private static Color withAlpha(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    /** White is intentionally the default theme. */
    public static class LightTheme implements Theme {
        private static final Color BACKGROUND = new Color(255,255,255,42);
        private static final Color BORDER = new Color(255,255,255,135);
        private static final Color SECONDARY = new Color(255,255,255,24);
        @Override public Color getBackgroundColor() { return BACKGROUND; }
        @Override public Color getBorderColor() { return BORDER; }
        @Override public Color getTextColor() { return Color.WHITE; }
        @Override public Color getAccentColor() { return Color.WHITE; }
        @Override public Color getSecondaryBackgroundColor() { return SECONDARY; }
        @Override public String getName() { return "White"; }
    }

    public static class StaticTheme implements Theme {
        private final String name;
        protected Color color;
        private Color backgroundColor;
        private Color borderColor;
        private Color secondaryBackgroundColor;

        public StaticTheme(String name, Color color) {
            this.name = name;
            updateColorCache(color);
        }

        protected final void updateColorCache(Color nextColor) {
            this.color = new Color(nextColor.getRed(), nextColor.getGreen(), nextColor.getBlue());
            this.backgroundColor = withAlpha(this.color, 50);
            this.borderColor = withAlpha(this.color, 145);
            this.secondaryBackgroundColor = withAlpha(this.color, 25);
        }

        @Override public Color getBackgroundColor() { return backgroundColor; }
        @Override public Color getBorderColor() { return borderColor; }
        @Override public Color getTextColor() { return color; }
        @Override public Color getAccentColor() { return color; }
        @Override public Color getSecondaryBackgroundColor() { return secondaryBackgroundColor; }
        @Override public String getName() { return name; }
    }

    public static final class CustomTheme extends StaticTheme {
        private CustomTheme(Color color) {
            super("Custom", color);
        }

        private void setColor(Color color) {
            if (color != null) updateColorCache(color);
        }
    }
}

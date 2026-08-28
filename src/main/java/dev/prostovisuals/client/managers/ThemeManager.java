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
    private static final long DEFAULT_TRANSITION_DURATION_MS = 1080L;
    private static final float TRANSITION_BAND = 42.0f;

    private final List<ThemeChangeListener> listeners = new CopyOnWriteArrayList<>();

    private final LightTheme white = new LightTheme();
    private final StaticTheme ice = new StaticTheme("Ice", new Color(120, 205, 255));
    private final StaticTheme violet = new StaticTheme("Violet", new Color(177, 116, 255));
    private final StaticTheme emerald = new StaticTheme("Emerald", new Color(67, 232, 161));
    private final StaticTheme sunset = new StaticTheme("Sunset", new Color(255, 139, 91));
    private final CustomTheme custom = new CustomTheme(new Color(130, 170, 255));

    private final Theme[] themes = {white, ice, violet, emerald, sunset, custom};
    private Theme currentTheme = white;

    /*
     * Theme animation is owned here rather than by ClickGUI. Every HUD/world
     * renderer therefore sees the same transition at the same time. The
     * ClickGUI only starts the animation and draws its glass wave front.
     */
    private Theme transitionFrom;
    private Theme transitionTarget;
    private long transitionStartedAt;
    private long transitionDurationMs = DEFAULT_TRANSITION_DURATION_MS;
    private long lastTransitionBroadcastAt;
    private float transitionCenterX;
    private float transitionCenterY;
    private float transitionMaxRadius = 1.0f;
    private final Theme renderedTransitionTheme = new RenderedTransitionTheme();

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
        return getRenderedAccentColor();
    }

    /** Accent sampled for renderers that do not have a meaningful 2D point. */
    public Color getRenderedAccentColor() {
        if (!isTransitioning()) return currentTheme.getAccentColor();
        return lerp(transitionFrom.getAccentColor(), transitionTarget.getAccentColor(), getTransitionProgress());
    }

    /** Accent behind/ahead of the shared radial wave at a screen position. */
    public Color getRenderedAccentColorAt(float x, float y) {
        if (!isTransitioning()) return currentTheme.getAccentColor();
        return lerp(transitionFrom.getAccentColor(), transitionTarget.getAccentColor(), getLocalTransitionMix(x, y));
    }

    public Color getRenderedBackgroundColor() {
        if (!isTransitioning()) return currentTheme.getBackgroundColor();
        return lerp(transitionFrom.getBackgroundColor(), transitionTarget.getBackgroundColor(), getTransitionProgress());
    }

    public Color getRenderedSecondaryBackgroundColor() {
        if (!isTransitioning()) return currentTheme.getSecondaryBackgroundColor();
        return lerp(transitionFrom.getSecondaryBackgroundColor(), transitionTarget.getSecondaryBackgroundColor(), getTransitionProgress());
    }

    public Color getRenderedBorderColor() {
        if (!isTransitioning()) return currentTheme.getBorderColor();
        return lerp(transitionFrom.getBorderColor(), transitionTarget.getBorderColor(), getTransitionProgress());
    }

    public Color getRenderedTextColor() {
        if (!isTransitioning()) return currentTheme.getTextColor();
        return lerp(transitionFrom.getTextColor(), transitionTarget.getTextColor(), getTransitionProgress());
    }

    public CustomTheme getCustomTheme() {
        return custom;
    }

    public void setCustomColor(Color color) {
        custom.setColor(color);
        if (currentTheme == custom || transitionTarget == custom) notifyListeners(getRenderedTheme());
    }

    public Color getCustomColor() {
        return custom.getAccentColor();
    }

    public void setTheme(Theme theme) {
        if (theme == null) return;
        clearTransition();
        currentTheme = theme;
        notifyListeners(theme);
    }

    /** Starts one client-wide water-wave transition from the supplied centre. */
    public void startRadialTransition(Theme target, float centerX, float centerY,
                                      float viewportWidth, float viewportHeight) {
        if (target == null) return;
        if (!isTransitioning() && currentTheme == target) return;

        // A rapid second palette click continues from the color already on
        // screen instead of snapping back to the previous committed theme.
        transitionFrom = isTransitioning() ? snapshotRenderedTheme() : currentTheme;
        transitionTarget = target;
        transitionCenterX = centerX;
        transitionCenterY = centerY;
        transitionMaxRadius = maxCornerDistance(centerX, centerY, viewportWidth, viewportHeight) + TRANSITION_BAND;
        transitionDurationMs = DEFAULT_TRANSITION_DURATION_MS;
        transitionStartedAt = System.currentTimeMillis();
        lastTransitionBroadcastAt = 0L;
        notifyListeners(renderedTransitionTheme);
    }

    /** Advances cached theme listeners so world effects and HUD move together. */
    public void updateTransition() {
        if (!isTransitioning()) return;
        long now = System.currentTimeMillis();
        if (now - transitionStartedAt >= transitionDurationMs) {
            Theme completed = transitionTarget;
            clearTransition();
            currentTheme = completed;
            notifyListeners(completed);
            return;
        }
        if (now - lastTransitionBroadcastAt >= 16L) {
            lastTransitionBroadcastAt = now;
            notifyListeners(renderedTransitionTheme);
        }
    }

    public void finishTransitionImmediately() {
        if (!isTransitioning()) return;
        Theme completed = transitionTarget;
        clearTransition();
        currentTheme = completed;
        notifyListeners(completed);
    }

    public boolean isTransitioning() {
        return transitionTarget != null && transitionFrom != null && transitionStartedAt > 0L;
    }

    public Theme getTransitionTarget() {
        return transitionTarget;
    }

    public Theme getRenderedTheme() {
        return isTransitioning() ? renderedTransitionTheme : currentTheme;
    }

    public float getTransitionProgress() {
        if (!isTransitioning()) return 1.0f;
        float linear = clamp01((System.currentTimeMillis() - transitionStartedAt) / (float) transitionDurationMs);
        // Smooth start followed by a wide, fluid expansion.
        return smoothstep(linear);
    }

    public float getLocalTransitionMix(float x, float y) {
        if (!isTransitioning()) return 1.0f;
        float distance = (float) Math.hypot(x - transitionCenterX, y - transitionCenterY);
        float radius = getTransitionRadius();
        return smoothstep(clamp01((radius - distance + TRANSITION_BAND * 0.55f) / TRANSITION_BAND));
    }

    public float getTransitionRadius() {
        if (!isTransitioning()) return transitionMaxRadius;
        return -TRANSITION_BAND + (transitionMaxRadius + TRANSITION_BAND * 1.35f) * getTransitionProgress();
    }

    public float getTransitionCenterX() { return transitionCenterX; }
    public float getTransitionCenterY() { return transitionCenterY; }
    public float getTransitionBand() { return TRANSITION_BAND; }

    public Color getTransitionFromAccent() {
        return transitionFrom == null ? currentTheme.getAccentColor() : transitionFrom.getAccentColor();
    }

    public Color getTransitionTargetAccent() {
        return transitionTarget == null ? currentTheme.getAccentColor() : transitionTarget.getAccentColor();
    }

    public void addThemeChangeListener(ThemeChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeThemeChangeListener(ThemeChangeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(Theme theme) {
        for (ThemeChangeListener listener : listeners) {
            try {
                listener.onThemeChanged(theme);
            } catch (Throwable ignored) {}
        }
    }

    private void clearTransition() {
        transitionFrom = null;
        transitionTarget = null;
        transitionStartedAt = 0L;
        lastTransitionBroadcastAt = 0L;
    }

    private Theme snapshotRenderedTheme() {
        return new SnapshotTheme(
                getRenderedBackgroundColor(),
                getRenderedBorderColor(),
                getRenderedTextColor(),
                getRenderedAccentColor(),
                getRenderedSecondaryBackgroundColor(),
                transitionTarget == null ? currentTheme.getName() : transitionTarget.getName()
        );
    }

    private static float maxCornerDistance(float cx, float cy, float width, float height) {
        float d0 = (float) Math.hypot(cx, cy);
        float d1 = (float) Math.hypot(width - cx, cy);
        float d2 = (float) Math.hypot(cx, height - cy);
        float d3 = (float) Math.hypot(width - cx, height - cy);
        return Math.max(Math.max(d0, d1), Math.max(d2, d3));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float smoothstep(float value) {
        float t = clamp01(value);
        return t * t * (3.0f - 2.0f * t);
    }

    private static Color lerp(Color from, Color to, float amount) {
        float t = clamp01(amount);
        return new Color(
                Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t)
        );
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

    private final class RenderedTransitionTheme implements Theme {
        @Override public Color getBackgroundColor() { return getRenderedBackgroundColor(); }
        @Override public Color getBorderColor() { return getRenderedBorderColor(); }
        @Override public Color getTextColor() { return getRenderedTextColor(); }
        @Override public Color getAccentColor() { return getRenderedAccentColor(); }
        @Override public Color getSecondaryBackgroundColor() { return getRenderedSecondaryBackgroundColor(); }
        @Override public String getName() { return transitionTarget == null ? currentTheme.getName() : transitionTarget.getName(); }
    }

    private record SnapshotTheme(Color backgroundColor, Color borderColor, Color textColor,
                                 Color accentColor, Color secondaryBackgroundColor,
                                 String name) implements Theme {
        @Override public Color getBackgroundColor() { return backgroundColor; }
        @Override public Color getBorderColor() { return borderColor; }
        @Override public Color getTextColor() { return textColor; }
        @Override public Color getAccentColor() { return accentColor; }
        @Override public Color getSecondaryBackgroundColor() { return secondaryBackgroundColor; }
        @Override public String getName() { return name; }
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

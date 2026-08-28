package dev.prostovisuals.client.ui.clickgui;

import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.ui.clickgui.components.impl.ModuleComponent;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.managers.ThemeManager; // Import ThemeManager
import dev.prostovisuals.client.managers.HolyWorldEventsManager;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.impl.render.UI;
import dev.prostovisuals.modules.impl.utility.ClientSound;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.client.sound.PositionedSoundInstance;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.*;
import java.util.List;

public class ClickGui extends Screen implements Wrapper {
    private static final Identifier GUI_LOGO = Identifier.of("prostovisuals", "hud/amongus.png");
    private static final Identifier KIMIKO_COMBAT_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/combat.png");
    private static final Identifier KIMIKO_RENDER_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/render.png");
    private static final Identifier KIMIKO_UTILITY_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/utility.png");
    private static final Identifier KIMIKO_THEME_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/theme.png");
    private static final Identifier KIMIKO_EVENTS_ICON = Identifier.of("prostovisuals", "hud/kimiko_icons/events.png");
    private static final int COLOR_WHEEL_SEGMENTS = 96;
    private static final float[] COLOR_WHEEL_COS = new float[COLOR_WHEEL_SEGMENTS + 1];
    private static final float[] COLOR_WHEEL_SIN = new float[COLOR_WHEEL_SEGMENTS + 1];
    private static final int[] COLOR_WHEEL_COLORS = new int[COLOR_WHEEL_SEGMENTS + 1];

    static {
        for (int i = 0; i <= COLOR_WHEEL_SEGMENTS; i++) {
            float hue = i / (float) COLOR_WHEEL_SEGMENTS;
            double angle = hue * Math.PI * 2.0 - Math.PI * 0.5;
            COLOR_WHEEL_COS[i] = (float) Math.cos(angle);
            COLOR_WHEEL_SIN[i] = (float) Math.sin(angle);
            COLOR_WHEEL_COLORS[i] = Color.HSBtoRGB(hue % 1f, 1f, 1f);
        }
    }

    private final Animation yAnimation = new Animation(360, 1f, true, Easing.OUT_QUART);
    private final ThemeManager themeManager;
    private String searchQuery = "";
    private boolean searchFocused;
    private String eventSearchQuery = "";
    private boolean eventSearchFocused;
    private String eventRarityFilter = "Все";
    private String eventTypeFilter = "Все";
    private int customPickerDrag = 0;
    private float customHue;
    private float customSaturation;
    private float customBrightness;

    private String description = "";
    private boolean closing = false;
    private float uiAlpha = 0f;
    private float contentOffsetY = 0f;
    private final ArrayList<ClickRipple> clickRipples = new ArrayList<>();

    private static final class ClickRipple {
        final float x, y;
        final long startNanos;
        ClickRipple(float x, float y) { this.x = x; this.y = y; this.startNanos = System.nanoTime(); }
    }

    private static final Category[] TABS = {
            Category.Combat,
            Category.Render,
            Category.Utility,
            Category.Theme,
            Category.Events
    };

    private Category selectedCategory = Category.Render;

    private float x, y, width, height;

    private final Map<Category, List<ModuleComponent>> componentsByCategory = new EnumMap<>(Category.class);
    private final Map<Category, Animation> tabAnimations = new EnumMap<>(Category.class);

    private float scrollY = 0f;
    private float maxScroll = 0f;
    private float tabScrollY = 0f;
    private float maxTabScroll = 0f;
    private float scrollYTarget = 0f;

    private static final int COLS = 2;
    private static final float GAP = 8f;
    private static final int THEME_COLS = 2;
    private static final float THEME_CARD_H = 42f;
    private static final float THEME_CARD_GAP = 7f;

    private ModuleComponent activeSettings = null;
    private float settingsScrollY = 0f;
    private float settingsMaxScroll = 0f;
    private float settingsScrollYTarget = 0f;
    private long lastRenderNs = 0L;
    private float uiDeltaSeconds = 1.0f / 60.0f;
    private final Animation settingsAnimation = new Animation(320, 1f, true, Easing.OUT_QUART);
    private final Animation categoryAnimation = new Animation(260, 1f, true, Easing.OUT_QUART);

    public ClickGui() {
        super(Text.of("prostovisuals-clickgui"));
        this.themeManager = ThemeManager.getInstance(); // Initialize ThemeManager
        for (Category category : TABS) {
            Animation animation = new Animation(180, 1f, false, Easing.EASE_OUT_CUBIC);
            // A reverse Animation historically starts at 1 and fades to 0.
            // Tabs should be visually idle on the very first GUI frame.
            animation.setDuration(0);
            animation.update(false);
            animation.setDuration(180);
            tabAnimations.put(category, animation);
        }
    }

    @Override
    public void init() {
        super.init();
        this.width = 370f;
        this.height = 310f;
        this.x = (mc.getWindow().getScaledWidth() - this.width) / 2f;
        this.y = (mc.getWindow().getScaledHeight() - this.height) / 2f; // фиксированная позиция по центру

        buildComponentsCache();
        scrollY = 0f;
        scrollYTarget = 0f;
        tabScrollY = 0f;

        closing = false;
        lastRenderNs = 0L;
        uiDeltaSeconds = 1.0f / 60.0f;
        yAnimation.update(true); // Animation for opening (fade)
    }

    public void refreshModuleVisibility() {
        buildComponentsCache();
    }

    private void buildComponentsCache() {
        componentsByCategory.clear();
        for (Category cat : TABS) {
            if (cat != Category.Theme && cat != Category.Events) { // Events is a data catalog, not a module category
                List<Module> mods = prostovisuals.getInstance().getModuleManager().getModules(cat);
                List<ModuleComponent> comps = new ArrayList<>(mods.size());
                for (Module m : mods) comps.add(new ModuleComponent(m));
                componentsByCategory.put(cat, comps);
            }
        }
    }

    // Method to play sound on module toggle or theme change
    private void playToggleSound(boolean wasToggled) {
        ClientSound clientSound = prostovisuals.getInstance().getModuleManager().getModule(ClientSound.class);
        if (clientSound != null && clientSound.isToggled()) {
            String soundId = wasToggled ? clientSound.getDisableSoundId() : clientSound.getEnableSoundId();
            float volume = clientSound.getVolume().getValue();
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(
                            SoundEvent.of(Identifier.of(soundId)),
                            1.0f,
                            volume
                    )
            );
        }
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            yAnimation.update(false); // Animation for closing (fade out)
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        long renderNow = System.nanoTime();
        if (lastRenderNs != 0L) {
            uiDeltaSeconds = Math.max(0.001f, Math.min(0.05f, (renderNow - lastRenderNs) / 1_000_000_000.0f));
        }
        lastRenderNs = renderNow;

        float targetY = (mc.getWindow().getScaledHeight() - this.height) / 2f;

        if (closing) {
            yAnimation.update(false);
            // Плавное смещение к центру при закрытии (чуть ниже → к центру)
            float offset = (1f - yAnimation.getValue()) * 12f;
            this.y = targetY + offset;
            if (yAnimation.getValue() <= 0.01f) {
                prostovisuals.getInstance().getModuleManager().getModule(UI.class).setToggled(false);
                super.close();
                return;
            }
        } else {
            yAnimation.update(true);
            // Появление: старт чуть ниже и плавно встаём по центру
            float offset = (1f - yAnimation.getValue()) * 12f;
            this.y = targetY + offset;
        }

        this.x = (mc.getWindow().getScaledWidth() - this.width) / 2f;

        // Используем fade по альфе + небольшой вертикальный оффсет для контента
        uiAlpha = Math.max(0f, Math.min(1f, yAnimation.getValue()));
        contentOffsetY = (1f - uiAlpha) * 8f;

        // Capture the clean world once before any menu dimming. Every glass panel reuses it.
        LiquidGlassUtil.captureFrame();

        // Soft cinematic dim behind the menu.
        int backdropAlpha = (int) (88 * uiAlpha);
        if (backdropAlpha > 0) {
            Render2D.drawRect(context.getMatrices(), 0f, 0f,
                    mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                    new Color(0, 0, 0, backdropAlpha));
        }

        float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
        LiquidGlassUtil.drawLiquidGlass(
                context, x, y, width, height, liquidTime,
                5.2f, 0.17f, 0.0018f, 16.0f, 0.66f, 1.045f
        );
        // Dark Rockstar-like body over the real refracted world.
        Render2D.drawRoundedRect(context.getMatrices(), x + 1f, y + 1f, width - 2f, height - 2f, 15f,
                new Color(7, 9, 13, (int) (192 * uiAlpha)));
        Render2D.drawRoundedRect(context.getMatrices(), x + 8f, y + 8f, 68f, height - 16f, 11f,
                new Color(6, 8, 12, (int) (208 * uiAlpha)));

        renderCategories(context, mouseX, mouseY);
        renderTopDescription(context);
        renderModulesArea(context, mouseX, mouseY, delta);
        renderBottomHints(context);
        renderClickRipples(context);
    }

    private void renderBottomHints(DrawContext ctx) {
        if (uiAlpha <= 0f) return;
        String hint = ClickGuiLanguage.translate("prostovisuals.clickgui.bottom_hint");
        float size = 7.5f;
        float w = Fonts.MEDIUM.getWidth(hint, size);
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(size), hint,
                x + 86f + (width - 100f - w) * 0.5f,
                y + height + 7f,
                new Color(205, 210, 220, (int) (205 * uiAlpha)));
    }

    private void renderPanelGlow(DrawContext ctx) {
        // LiquidGlass itself provides the optical rim; no old blur glow.
    }

    private void renderCategories(DrawContext ctx, int mouseX, int mouseY) {
        float railX = x + 14f;
        float railY = y + 18f + contentOffsetY;
        float tabW = 56f;
        float tabH = 25f;
        float gap = 7f;

        int titleAlpha = (int) (255 * uiAlpha);
        Color logoTintBase = themeManager.getRenderedAccentColor();
        Color logoTint = new Color(logoTintBase.getRed(), logoTintBase.getGreen(), logoTintBase.getBlue(), titleAlpha);
        Render2D.drawTexture(ctx.getMatrices(), railX + 17f, railY - 3f,
                25f, 25f, 4.0f, GUI_LOGO, logoTint);

        float drawY = railY + 30f;
        for (Category cat : TABS) {
            boolean active = cat == selectedCategory;
            boolean hovered = mouseX >= railX && mouseX <= railX + tabW
                    && mouseY >= drawY && mouseY <= drawY + tabH;
            Animation tabAnimation = tabAnimations.get(cat);
            tabAnimation.update(active || hovered);
            float tabT = Math.max(0f, Math.min(1f, tabAnimation.getValue()));
            Color accent = themeManager.getRenderedAccentColor();
            Render2D.drawRoundedRect(ctx.getMatrices(), railX, drawY, tabW, tabH, 8.5f,
                    new Color(16, 19, 26, (int) ((108 + 54 * tabT) * uiAlpha)));
            if (tabT > 0.01f) {
                Render2D.drawRoundedRect(ctx.getMatrices(), railX + 1f, drawY + 1f, tabW - 2f, tabH - 2f, 7.5f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                                (int) ((active ? 44 : 18) * tabT * uiAlpha)));
            }
            if (active) {
                Render2D.drawRoundedRect(ctx.getMatrices(), railX + 4f, drawY + 6f, 2f, tabH - 12f, 1f,
                        new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (235 * uiAlpha)));
            }

            String label = cat.name();
            Color color = active
                    ? new Color(255, 255, 255, (int) (255 * uiAlpha))
                    : new Color(196, 200, 208, (int) (220 * uiAlpha));
            Identifier categoryIcon = switch (cat) {
                case Combat -> KIMIKO_COMBAT_ICON;
                case Render -> KIMIKO_RENDER_ICON;
                case Utility -> KIMIKO_UTILITY_ICON;
                case Theme -> KIMIKO_THEME_ICON;
                case Events -> KIMIKO_EVENTS_ICON;
                case Hud -> KIMIKO_UTILITY_ICON;
            };
            Color iconTint = active
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (245 * uiAlpha))
                    : new Color(220, 224, 232, (int) (205 * uiAlpha));
            Render2D.drawTexture(ctx.getMatrices(), railX + 9f, drawY + 7f, 11f, 11f, 0f, categoryIcon, iconTint);
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(7.6f), label,
                    railX + 23f, drawY + 8f, color);
            drawY += tabH + gap;
        }

        // Search field anchored to the bottom of the glass sidebar.
        float searchX = railX;
        float searchY = y + height - 38f;
        float searchW = tabW;
        float searchH = 20f;

        // Independent RU / EN selector. Module names deliberately stay English.
        float languageX = searchX;
        float languageY = searchY - 27f;
        boolean languageHovered = mouseX >= languageX && mouseX <= languageX + searchW
                && mouseY >= languageY && mouseY <= languageY + searchH;
        Render2D.drawRoundedRect(ctx.getMatrices(), languageX, languageY, searchW, searchH, 7f,
                new Color(16, 19, 26, (int) ((languageHovered ? 205 : 168) * uiAlpha)));
        if (languageHovered) {
            Color languageAccent = themeManager.getRenderedAccentColor();
            Render2D.drawRoundedRect(ctx.getMatrices(), languageX + 1f, languageY + 1f,
                    searchW - 2f, searchH - 2f, 6f,
                    new Color(languageAccent.getRed(), languageAccent.getGreen(), languageAccent.getBlue(),
                            (int) (35 * uiAlpha)));
        }
        renderLanguageFlag(ctx, languageX + 6f, languageY + 5.5f, 14f, 9f);
        Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(7.2f), ClickGuiLanguage.getCode(),
                languageX + 25f, languageY + 6f,
                new Color(235, 238, 245, (int) (245 * uiAlpha)));

        Render2D.drawRoundedRect(ctx.getMatrices(), searchX, searchY, searchW, searchH, 7f,
                new Color(16, 19, 26, (int) ((searchFocused ? 216 : 170) * uiAlpha)));

        Color accent = themeManager.getRenderedAccentColor();
        // Magnifier drawn with primitives only. Do not use a Unicode glyph here:
        // the custom font renderer can produce an empty BufferBuilder for unsupported glyphs.
        Color searchIcon = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (230 * uiAlpha));
        Color searchCutout = new Color(10, 12, 17, (int) (210 * uiAlpha));
        Render2D.drawRoundedRect(ctx.getMatrices(), searchX + 6f, searchY + 5f, 7f, 7f, 3.5f, searchIcon);
        Render2D.drawRoundedRect(ctx.getMatrices(), searchX + 7.5f, searchY + 6.5f, 4f, 4f, 2f, searchCutout);
        Render2D.drawRoundedRect(ctx.getMatrices(), searchX + 11.4f, searchY + 11.0f, 5f, 1.5f, 0.75f, searchIcon);

        String shown = searchQuery.isEmpty()
                ? ClickGuiLanguage.translate("prostovisuals.clickgui.search") : searchQuery;
        String clipped = shown;
        while (clipped.length() > 2 && Fonts.REGULAR.getWidth(clipped, 6.5f) > searchW - 20f) {
            clipped = clipped.substring(1);
        }
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.5f),
                clipped, searchX + 17f, searchY + 6f,
                searchFocused
                        ? new Color(245, 247, 250, (int) (255 * uiAlpha))
                        : new Color(190, 195, 205, (int) (220 * uiAlpha)));
    }

    private void renderLanguageFlag(DrawContext ctx, float flagX, float flagY, float flagW, float flagH) {
        int alpha = (int) (255 * uiAlpha);
        Render2D.drawRoundedRect(ctx.getMatrices(), flagX - 0.6f, flagY - 0.6f,
                flagW + 1.2f, flagH + 1.2f, 1.5f, new Color(5, 7, 10, (int) (190 * uiAlpha)));
        if (ClickGuiLanguage.isRussian()) {
            Render2D.drawRect(ctx.getMatrices(), flagX, flagY, flagW, flagH / 3f,
                    new Color(255, 255, 255, alpha));
            Render2D.drawRect(ctx.getMatrices(), flagX, flagY + flagH / 3f, flagW, flagH / 3f,
                    new Color(24, 76, 173, alpha));
            Render2D.drawRect(ctx.getMatrices(), flagX, flagY + flagH * 2f / 3f, flagW, flagH / 3f,
                    new Color(210, 34, 45, alpha));
            return;
        }

        Color blue = new Color(28, 55, 112, alpha);
        Color white = new Color(255, 255, 255, alpha);
        Color red = new Color(200, 28, 45, alpha);
        Render2D.drawRect(ctx.getMatrices(), flagX, flagY, flagW, flagH, blue);
        Render2D.drawLine(ctx.getMatrices(), flagX, flagY, flagX + flagW, flagY + flagH, 2.5f, white);
        Render2D.drawLine(ctx.getMatrices(), flagX + flagW, flagY, flagX, flagY + flagH, 2.5f, white);
        Render2D.drawLine(ctx.getMatrices(), flagX, flagY, flagX + flagW, flagY + flagH, 0.85f, red);
        Render2D.drawLine(ctx.getMatrices(), flagX + flagW, flagY, flagX, flagY + flagH, 0.85f, red);
        Render2D.drawRect(ctx.getMatrices(), flagX, flagY + flagH * 0.34f, flagW, flagH * 0.32f, white);
        Render2D.drawRect(ctx.getMatrices(), flagX + flagW * 0.36f, flagY, flagW * 0.28f, flagH, white);
        Render2D.drawRect(ctx.getMatrices(), flagX, flagY + flagH * 0.41f, flagW, flagH * 0.18f, red);
        Render2D.drawRect(ctx.getMatrices(), flagX + flagW * 0.42f, flagY, flagW * 0.16f, flagH, red);
    }

    private void renderTopDescription(DrawContext ctx) {
        if (description == null || description.isEmpty()) return;

        String descText = ClickGuiLanguage.translate(description);
        float textW = Fonts.MEDIUM.getWidth(descText, 9f);

        // Place just above the panel and make it overhang the panel width
        float bgOverhang = 12f; // how far to extend past the panel on each side
        float bgX = x - bgOverhang;
        float bgY = y - 14f; // slightly above the ClickGUI panel
        float bgW = width + bgOverhang * 2f;
        float bgH = 14f;

        // Center text relative to the ClickGUI panel
        float textX = x + (width - textW) / 2f;
        float textY = bgY - 5f;

        int panelAlpha = (int) (120 * uiAlpha);
        int textAlpha = (int) (255 * uiAlpha);

        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(9f), descText, textX, textY,
                new Color(255, 255, 255, textAlpha));
        description = "";
    }

    private void startScissorScaled(DrawContext ctx, float rx, float ry, float rw, float rh) {
        Render2D.startScissor(ctx, rx, ry, rw, rh);
    }

    private List<ModuleComponent> getVisibleModuleComponents() {
        if (searchQuery == null || searchQuery.isBlank()) {
            return componentsByCategory.getOrDefault(selectedCategory, Collections.emptyList());
        }

        String q = searchQuery.toLowerCase(Locale.ROOT);
        List<ModuleComponent> result = new ArrayList<>();
        for (Category cat : TABS) {
            if (cat == Category.Theme || cat == Category.Events) continue;
            for (ModuleComponent component : componentsByCategory.getOrDefault(cat, Collections.emptyList())) {
                Module module = component.getModule();
                String name = module.getName() == null ? "" : module.getName().toLowerCase(Locale.ROOT);
                String desc = module.getDescription() == null ? "" : module.getDescription().toLowerCase(Locale.ROOT);
                if (name.contains(q) || desc.contains(q)) result.add(component);
            }
        }
        return result;
    }

    private List<HolyWorldEventsManager.EventInfo> getVisibleEvents() {
        HolyWorldEventsManager manager = HolyWorldEventsManager.getInstance();
        String query = eventSearchQuery == null ? "" : eventSearchQuery.trim().toLowerCase(Locale.ROOT);
        List<HolyWorldEventsManager.EventInfo> result = new ArrayList<>();
        for (HolyWorldEventsManager.EventInfo event : manager.getEvents()) {
            if (!query.isEmpty()) {
                String haystack = (event.displayName() + " " + event.id() + " " + event.serverName()
                        + " " + event.serverType() + " " + event.rarity()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(query)) continue;
            }
            if (!"Все".equals(eventRarityFilter) && !eventRarityFilter.equalsIgnoreCase(event.rarity())) continue;
            if (!"Все".equals(eventTypeFilter) && !eventTypeFilter.equalsIgnoreCase(event.serverType())) continue;
            result.add(event);
        }
        return result;
    }

    private void renderEventsCatalog(DrawContext ctx, int mouseX, int mouseY,
                                     float leftX, float leftY, float leftW, float leftH) {
        HolyWorldEventsManager manager = HolyWorldEventsManager.getInstance();
        float pad = 2f;
        float searchY = leftY + 1f;
        float searchH = 23f;
        float searchW = leftW - 4f;
        Render2D.drawRoundedRect(ctx.getMatrices(), leftX + pad, searchY, searchW, searchH, 8f,
                new Color(14, 17, 23, eventSearchFocused ? 220 : 184));

        Color accent = themeManager.getRenderedAccentColor();
        Render2D.drawRoundedRect(ctx.getMatrices(), leftX + 9f, searchY + 7f, 7f, 7f, 3.5f,
                accent);
        Render2D.drawRoundedRect(ctx.getMatrices(), leftX + 10.5f, searchY + 8.5f, 4f, 4f, 2f,
                new Color(10, 12, 17, 220));
        Render2D.drawRoundedRect(ctx.getMatrices(), leftX + 14.3f, searchY + 13f, 5f, 1.5f, 0.75f,
                accent);

        String searchText = eventSearchQuery.isEmpty() ? "Search events..." : eventSearchQuery;
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(7f), searchText,
                leftX + 22f, searchY + 7f,
                eventSearchFocused ? Color.WHITE : new Color(190, 195, 205, 230));

        float right = leftX + searchW - 4f;
        float typeW = 47f;
        float rarityW = 58f;
        float gap = 5f;
        float rarityX = right - rarityW;
        float typeX = rarityX - gap - typeW;

        drawEventFilter(ctx, typeX, searchY + 3f, typeW, 17f, "Type: " + eventTypeFilter,
                mouseX >= typeX && mouseX <= typeX + typeW && mouseY >= searchY + 3f && mouseY <= searchY + 20f);
        drawEventFilter(ctx, rarityX, searchY + 3f, rarityW, 17f, "Rare: " + eventRarityFilter,
                mouseX >= rarityX && mouseX <= rarityX + rarityW && mouseY >= searchY + 3f && mouseY <= searchY + 20f);

        List<HolyWorldEventsManager.EventInfo> visible = getVisibleEvents();
        float cardTop = searchY + searchH + 7f;
        float cardGap = 7f;
        float cardW = (leftW - 7f) / 2f;
        float cardH = 53f;
        int columns = 2;

        if (manager.isLoading() && visible.isEmpty()) {
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(8f),
                    "Loading HolyWorld events...", leftX + 7f, cardTop + 12f,
                    new Color(220, 224, 232, 235));
            maxScroll = 0f;
            return;
        }

        if (visible.isEmpty()) {
            String text = manager.getError().isEmpty() ? "No events found" : "HolyWorld API unavailable";
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(8f),
                    text, leftX + 7f, cardTop + 12f,
                    new Color(220, 224, 232, 235));
            maxScroll = 0f;
            return;
        }

        for (int i = 0; i < visible.size(); i++) {
            HolyWorldEventsManager.EventInfo event = visible.get(i);
            int col = i % columns;
            int row = i / columns;
            float cardX = leftX + 2f + col * (cardW + cardGap);
            float cardY = cardTop + row * (cardH + cardGap) - scrollY;
            boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW
                    && mouseY >= cardY && mouseY <= cardY + cardH;

            Render2D.drawRoundedRect(ctx.getMatrices(), cardX, cardY, cardW, cardH, 8f,
                    new Color(14, 17, 23, hovered ? 214 : 184));

            Color rarityColor = eventRarityColor(event.rarity());
            Render2D.drawRoundedRect(ctx.getMatrices(), cardX + 7f, cardY + 8f, 3f, cardH - 16f, 1.5f,
                    new Color(rarityColor.getRed(), rarityColor.getGreen(), rarityColor.getBlue(), 220));

            String title = event.displayName().isEmpty() ? event.id() : event.displayName();
            Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(8f), title,
                    cardX + 16f, cardY + 8f, Color.WHITE);

            Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.7f), event.serverName(),
                    cardX + 16f, cardY + 22f, new Color(210, 214, 223, 235));

            Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.4f),
                    event.serverType() + "  •  " + prettyRarity(event.rarity()),
                    cardX + 16f, cardY + 35f, rarityColor);

            Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.8f),
                    event.id(), cardX + 16f, cardY + 44f,
                    new Color(150, 156, 168, 210));
        }

        int rows = (visible.size() + columns - 1) / columns;
        float totalHeight = searchH + 7f + rows * cardH + Math.max(0, rows - 1) * cardGap;
        maxScroll = Math.max(0f, totalHeight - leftH);
        scrollYTarget = clamp(scrollYTarget, 0f, maxScroll);
        scrollY = clamp(scrollY, 0f, maxScroll);

        if (!manager.getError().isEmpty()) {
            Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.8f),
                    "API: " + manager.getError(), leftX + 7f, leftY + leftH - 8f,
                    new Color(255, 120, 120, 190));
        }
    }

    private String nextEventTypeFilter(String current) {
        String[] values = {"Все", "Соло", "Дуо", "Трио", "Клан"};
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) return values[(i + 1) % values.length];
        }
        return values[0];
    }

    private String nextEventRarityFilter(String current) {
        String[] values = {
                "Все",
                "Обычный",
                "Редкий",
                "Эпический",
                "Легендарный",
                "Роскошный"
        };
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(current)) return values[(i + 1) % values.length];
        }
        return "Все";
    }

    private void drawEventFilter(DrawContext ctx, float x, float y, float w, float h,
                                 String label, boolean hovered) {
        Color accent = themeManager.getRenderedAccentColor();
        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 6f,
                new Color(16, 19, 26, hovered ? 220 : 176));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.7f), label,
                x + 5f, y + 5f, hovered ? Color.WHITE :
                        new Color(190, 195, 205, 230));
    }

    private Color eventRarityColor(String rarity) {
        String r = rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
        if (r.contains("myth") || r.contains("миф")) return new Color(255, 90, 220, 255);
        if (r.contains("legend") || r.contains("легенд")) return new Color(255, 180, 55, 255);
        if (r.contains("epic") || r.contains("эпич")) return new Color(180, 95, 255, 255);
        if (r.contains("rare") || r.contains("редк")) return new Color(65, 160, 255, 255);
        if (r.contains("deadly") || r.contains("explosive")) return new Color(255, 70, 70, 255);
        if (r.contains("peaceful") || r.contains("normal") || r.contains("default")
                || r.contains("обыч")) return new Color(170, 178, 190, 255);
        return themeManager.getRenderedAccentColor();
    }

    private String prettyRarity(String rarity) {
        if (rarity == null || rarity.isEmpty()) return "Обычный";
        return rarity;
    }

    private void renderModulesArea(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Left content (modules/themes)
        float leftX = x + 86f;
        float categoryT = Math.max(0f, Math.min(1f, categoryAnimation.getValue()));
        float categorySlide = (1f - categoryT) * 13f;
        float categoryAlpha = uiAlpha * categoryT;
        float leftY = y + 14f + contentOffsetY + categorySlide;
        float leftW = width - 100f;
        float leftH = height - 28f;

        // Frame-rate independent smoothing: the menu feels the same at 60,
        // 144 or 240 FPS instead of becoming slower/faster with refresh rate.
        float listSmooth = smoothingFactor(12.0f);
        scrollY += (scrollYTarget - scrollY) * listSmooth;
        scrollY = clamp(scrollY, 0f, maxScroll);

        startScissorScaled(ctx, leftX, leftY, leftW, leftH);

        if (selectedCategory == Category.Events) {
            renderEventsCatalog(ctx, mouseX, mouseY, leftX, leftY, leftW, leftH);
        } else if (selectedCategory == Category.Theme) {
            float themeStartY = leftY + 2f - scrollY;
            float usableW = leftW - 7f;
            float cardW = (usableW - THEME_CARD_GAP) / THEME_COLS;
            Color currentAccent = themeManager.getRenderedAccentColor();
            ThemeManager.Theme[] themes = themeManager.getAvailableThemes();

            for (int index = 0; index < themes.length; index++) {
                ThemeManager.Theme theme = themes[index];
                int col = index % THEME_COLS;
                int row = index / THEME_COLS;
                float cardX = leftX + 2f + col * (cardW + THEME_CARD_GAP);
                float cardY = themeStartY + row * (THEME_CARD_H + THEME_CARD_GAP);
                boolean selected = theme == themeManager.getCurrentTheme()
                        || theme.getName().equals(themeManager.getCurrentTheme().getName());
                boolean hovered = mouseX >= cardX && mouseX <= cardX + cardW
                        && mouseY >= cardY && mouseY <= cardY + THEME_CARD_H;

                Render2D.drawRoundedRect(ctx.getMatrices(), cardX, cardY, cardW, THEME_CARD_H, 8f,
                        new Color(14, 17, 23, selected ? 226 : (hovered ? 210 : 180)));

                Color c = theme.getAccentColor();
                if (selected || hovered) {
                    Render2D.drawRoundedRect(ctx.getMatrices(), cardX + 1f, cardY + 1f,
                            cardW - 2f, THEME_CARD_H - 2f, 7f,
                            new Color(c.getRed(), c.getGreen(), c.getBlue(),
                                    (int) ((selected ? 42 : 20) * uiAlpha)));
                }

                // Wide color preview makes every theme identifiable at a glance.
                Render2D.drawRoundedRect(ctx.getMatrices(), cardX + 7f, cardY + 7f,
                        cardW - 14f, 9f, 4.5f,
                        new Color(c.getRed(), c.getGreen(), c.getBlue(), 235));
                Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(7.8f),
                        theme.getName(), cardX + 8f, cardY + 25f,
                        selected
                                ? new Color(currentAccent.getRed(), currentAccent.getGreen(), currentAccent.getBlue(), 255)
                                : new Color(230, 232, 238, 230));

                if (selected) {
                    Render2D.drawRoundedRect(ctx.getMatrices(), cardX + cardW - 16f, cardY + 24f,
                            8f, 8f, 4f, new Color(c.getRed(), c.getGreen(), c.getBlue(), 255));
                    Render2D.drawRoundedRect(ctx.getMatrices(), cardX + cardW - 13.5f, cardY + 26.5f,
                            3f, 3f, 1.5f, Color.WHITE);
                }
            }

            int themeRows = (themes.length + THEME_COLS - 1) / THEME_COLS;
            float gridHeight = themeRows * THEME_CARD_H + Math.max(0, themeRows - 1) * THEME_CARD_GAP;
            float customPanelY = themeStartY + gridHeight + 9f;
            float totalHeight = gridHeight;

            if (themeManager.getCurrentTheme() == themeManager.getCustomTheme()) {
                Color custom = themeManager.getCustomColor();
                if (customPickerDrag == 0) {
                    float[] hsb = Color.RGBtoHSB(custom.getRed(), custom.getGreen(), custom.getBlue(), null);
                    customHue = hsb[0];
                    customSaturation = hsb[1];
                    customBrightness = hsb[2];
                }
                float panelH = 126f;
                Render2D.drawRoundedRect(ctx.getMatrices(), leftX + 2f, customPanelY, usableW, panelH, 8f,
                        new Color(14, 17, 23, 188));
                Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(7.4f),
                        ClickGuiLanguage.translate("prostovisuals.theme.custom_rgb"),
                        leftX + 9f, customPanelY + 7f, Color.WHITE);

                float pickerCx = leftX + 62f;
                float pickerCy = customPanelY + 72f;
                float outerRadius = 45f;
                float innerRadius = 37f;
                float squareSize = 50f;
                float squareX = pickerCx - squareSize * 0.5f;
                float squareY = pickerCy - squareSize * 0.5f;

                if (customPickerDrag != 0 && GLFW.glfwGetMouseButton(
                        mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
                    updateCustomPicker(mouseX, mouseY, pickerCx, pickerCy,
                            innerRadius, outerRadius, squareX, squareY, squareSize);
                    custom = themeManager.getCustomColor();
                }

                drawHueWheel(ctx, pickerCx, pickerCy, innerRadius, outerRadius);
                drawSaturationBrightnessSquare(ctx, squareX, squareY, squareSize);
                drawColorPickerMarkers(ctx, pickerCx, pickerCy, innerRadius, outerRadius,
                        squareX, squareY, squareSize);

                float previewX = leftX + usableW - 68f;
                float previewY = customPanelY + 38f;
                Render2D.drawRoundedRect(ctx.getMatrices(), previewX - 3f, previewY - 3f,
                        38f, 38f, 19f, new Color(255, 255, 255, 55));
                Render2D.drawRoundedRect(ctx.getMatrices(), previewX, previewY,
                        32f, 32f, 16f, custom);
                String hex = String.format(Locale.ROOT, "#%02X%02X%02X",
                        custom.getRed(), custom.getGreen(), custom.getBlue());
                Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(7f), hex,
                        previewX - 3f, previewY + 43f, new Color(240, 242, 247, 240));
                Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.2f),
                        "LIVE", previewX + 3f, previewY + 58f,
                        new Color(custom.getRed(), custom.getGreen(), custom.getBlue(), 255));
                totalHeight += 9f + panelH;
            }

            maxScroll = Math.max(0f, totalHeight - leftH);
            scrollYTarget = clamp(scrollYTarget, 0f, maxScroll);
            scrollY = clamp(scrollY, 0f, maxScroll);
        } else {
            List<ModuleComponent> comps = getVisibleModuleComponents();
            int cols = COLS;
            float gap = GAP;
            float colW = (leftW - gap * (cols - 1)) / cols;
            float[] baseY = new float[cols];
            Arrays.fill(baseY, leftY + 2f);
            float maxBottom = leftY;
            int placed = 0;
            for (ModuleComponent mcComp : comps) {
                int col = placed % cols;
                float cx = leftX + col * (colW + gap);
                float cyDraw = baseY[col] - scrollY;
                mcComp.setX(cx);
                mcComp.setY(cyDraw);
                mcComp.setWidth(colW);
                mcComp.setRenderExternally(true);
                mcComp.setGlobalAlpha(categoryAlpha);
                // принудительно скрыть внутренние дети (не рисовать раскрытые настройки слева)
                if (mcComp.getOpenAnimation().getValue() > 0f && mcComp != activeSettings) {
                    // оставляем как есть — внутренний рендер отключён флагом renderExternally
                }
                mcComp.render(ctx, mouseX, mouseY, delta);
                float totalH = mcComp.getHeight();
                baseY[col] += totalH + gap;
                maxBottom = Math.max(maxBottom, baseY[col]);
                placed++;
            }
            float contentBottom = leftY + leftH;
            maxScroll = Math.max(0f, (maxBottom - gap) - contentBottom);
            scrollYTarget = clamp(scrollYTarget, 0f, maxScroll);
            scrollY = clamp(scrollY, 0f, maxScroll);

            // Мягкий оверлей для плавного исчезновения модулей при открытии/закрытии
            float tModules = 1f - uiAlpha;
            float easedModules = tModules * tModules * (3f - 2f * tModules); // smoothstep
            int modulesOverlayAlpha = (int) (62 * easedModules);
            if (modulesOverlayAlpha > 2) {
                Render2D.drawRoundedRect(ctx.getMatrices(), leftX + 1f, leftY + 1f, leftW - 2f, leftH - 2f, 3f,
                        new Color(30, 30, 30, modulesOverlayAlpha));
            }
        }

        Render2D.stopScissor(ctx);

        // Final overlay pass: bind menus are intentionally rendered after every
        // module card and outside the list scissor so they always stay on top.
        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            for (ModuleComponent overlayComponent : getVisibleModuleComponents()) {
                if (overlayComponent.isBindModeMenuOpen()) {
                    overlayComponent.renderBindMenuOverlay(ctx);
                }
            }
        }

        // Scrollbar for modules/themes list when content overflows (rendered to the right of ClickGUI)
        if (maxScroll > 0.5f) {
            float trackX = x + width - 4f;
            float trackY = leftY;
            float trackW = 2f;
            float trackH = leftH;
            Render2D.drawRect(ctx.getMatrices(), trackX, trackY, trackW, trackH,
                    new Color(0, 0, 0, Math.min(90, (int) (90f * uiAlpha))));

            float visibleRatio = leftH / Math.max(leftH + maxScroll, 1f);
            float thumbH = Math.max(14f, trackH * visibleRatio);
            float maxThumbTravel = trackH - thumbH;
            float scrollRatio = maxScroll <= 0f ? 0f : (scrollY / maxScroll);
            float thumbY = trackY + maxThumbTravel * scrollRatio;
            Color thumbColor = new Color(
                    themeManager.getRenderedAccentColor().getRed(),
                    themeManager.getRenderedAccentColor().getGreen(),
                    themeManager.getRenderedAccentColor().getBlue(),
                    Math.min(180, (int) (180f * uiAlpha))
            );
            Render2D.drawRoundedRect(ctx.getMatrices(), trackX - 0.5f, thumbY, trackW + 1f, thumbH, 1.5f, thumbColor);
        }

        ModuleComponent target = activeSettings;
        boolean hasSettings = target != null && !target.getComponents().isEmpty();
        // settings panel animation
        settingsAnimation.update(hasSettings);
        float anim = settingsAnimation.getValue();
        if (anim > 0.01f) {
            // right settings panel (slides in from right with fade)
            float basePanelX = x + width + 5f;
            float panelY = y + 18f + contentOffsetY; // применяем оффсет
            float panelW = 132f;
            float panelH = height - 36f;
            float slideOffset = (1f - anim) * 40f;
            float drawPanelX = basePanelX + slideOffset;
            float panelScale = 0.965f + 0.035f * anim;
            float scaledPanelW = panelW * panelScale;
            float scaledPanelH = panelH * panelScale;
            drawPanelX += (panelW - scaledPanelW) * 0.5f;
            panelY += (panelH - scaledPanelH) * 0.5f;
            panelW = scaledPanelW;
            panelH = scaledPanelH;

            int settingsAlpha = (int) (255 * Math.min(1f, anim * uiAlpha));
            float smoothPanel = (float) (Math.pow(Math.min(1f, anim * uiAlpha), 2) * (3 - 2 * Math.min(1f, anim * uiAlpha)));
            int panelA = (int) (255 * smoothPanel);

            float settingsGlassTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
            LiquidGlassUtil.drawLiquidGlass(
                    ctx, drawPanelX, panelY, panelW, panelH, settingsGlassTime,
                    5.0f, 0.12f, 0.00125f, 11.0f, 0.52f * anim, 1.032f
            );
            Render2D.drawRoundedRect(ctx.getMatrices(), drawPanelX + 1f, panelY + 1f, panelW - 2f, panelH - 2f, 10f,
                    new Color(8, 10, 14, Math.min(12, panelA)));

            float rContentX = drawPanelX + 8f;
            float rContentY = panelY + 8f;
            float rContentW = panelW - 16f;
            float rContentH = panelH - 16f;

            float total = 0f;
            if (hasSettings) {
                target.setGlobalAlpha(Math.min(1f, anim * uiAlpha));
                total = target.renderSettingsExternally(ctx, rContentX, rContentY, rContentW,
                        rContentX, rContentY, rContentW, rContentH, mouseX, mouseY, delta, settingsScrollY);
            }
            settingsMaxScroll = Math.max(0f, total - rContentH);
            scrollYTarget = clamp(scrollYTarget, 0f, maxScroll);
            scrollY = clamp(scrollY, 0f, maxScroll);
            settingsScrollYTarget = clamp(settingsScrollYTarget, 0f, settingsMaxScroll);
            // Same frame-rate independent response on the settings rail.
            float settingsSmooth = smoothingFactor(13.5f);
            settingsScrollY += (settingsScrollYTarget - settingsScrollY) * settingsSmooth;
            settingsScrollY = clamp(settingsScrollY, 0f, settingsMaxScroll);

            // vertical scrollbar on the right of settings content
            if (settingsMaxScroll > 0.5f) {
                float trackX = drawPanelX + panelW - 3f;
                float trackY = rContentY;
                float trackW = 2f;
                float trackH = rContentH;
                Render2D.drawRect(ctx.getMatrices(), trackX, trackY, trackW, trackH,
                        new Color(0, 0, 0, Math.min(90, (int) (90f * anim * uiAlpha))));

                float visibleRatio = rContentH / Math.max(rContentH + settingsMaxScroll, 1f);
                float thumbH = Math.max(14f, trackH * visibleRatio);
                float maxThumbTravel = trackH - thumbH;
                float scrollRatio = settingsMaxScroll <= 0f ? 0f : (settingsScrollY / settingsMaxScroll);
                float thumbY = trackY + maxThumbTravel * scrollRatio;
                Color thumbColor = new Color(
                        themeManager.getRenderedAccentColor().getRed(),
                        themeManager.getRenderedAccentColor().getGreen(),
                        themeManager.getRenderedAccentColor().getBlue(),
                        Math.min(180, (int) (180f * anim * uiAlpha))
                );
                Render2D.drawRoundedRect(ctx.getMatrices(), trackX - 0.5f, thumbY, trackW + 1f, thumbH, 1.5f, thumbColor);
            }

            // Мягкий скруглённый оверлей над содержимым (снижаем альфу и убираем жёсткие края) с плавной кривой
            float tSettings = 1f - Math.min(1f, anim * uiAlpha);
            float easedSettings = tSettings * tSettings * (3f - 2f * tSettings); // smoothstep
            int contentOverlayAlpha = (int) (70 * easedSettings);
            if (contentOverlayAlpha > 6) {
                Render2D.drawRoundedRect(ctx.getMatrices(), rContentX, rContentY, rContentW, rContentH, 2f,
                        new Color(30, 30, 30, contentOverlayAlpha));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return false;
        if (button == 0 && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height) {
            clickRipples.add(new ClickRipple((float) mouseX, (float) mouseY));
            if (clickRipples.size() > 10) clickRipples.remove(0);
        }

        // Bind popup / bind capture is global and modal.
        // It must receive input even when the floating popup lies outside
        // the module-list rectangle or overlaps another panel.
        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            List<ModuleComponent> modalComponents = getVisibleModuleComponents();

            for (ModuleComponent component : modalComponents) {
                if (component.isBinding()) {
                    component.mouseClicked(mouseX, mouseY, button);
                    return true;
                }
            }

            for (ModuleComponent component : modalComponents) {
                if (component.isBindModeMenuOpen()) {
                    component.mouseClicked(mouseX, mouseY, button);
                    return true;
                }
            }
        }

        float railX = x + 14f;
        float railY = y + 18f + contentOffsetY + 30f;
        float tabW = 56f;
        float tabH = 25f;
        float gap = 7f;

        float searchX = x + 14f;
        float searchY = y + height - 38f;
        float languageY = searchY - 27f;
        if (mouseX >= searchX && mouseX <= searchX + 56f
                && mouseY >= languageY && mouseY <= languageY + 20f && button == 0) {
            ClickGuiLanguage.toggle();
            searchFocused = false;
            activeSettings = null;
            description = "";
            return true;
        }
        if (mouseX >= searchX && mouseX <= searchX + 56f &&
                mouseY >= searchY && mouseY <= searchY + 20f && button == 0) {
            searchFocused = true;
            activeSettings = null;
            return true;
        } else if (button == 0) {
            searchFocused = false;
        }

        // Handle vertical sidebar category clicks.
        float drawY = railY;
        for (Category cat : TABS) {
            if (mouseX >= railX && mouseX <= railX + tabW &&
                    mouseY >= drawY && mouseY <= drawY + tabH) {
                if (selectedCategory != cat) {
                    selectedCategory = cat;
                    categoryAnimation.reset();
                    categoryAnimation.update(true);
                }
                scrollY = 0f;
                scrollYTarget = 0f;
                activeSettings = null;
                eventSearchFocused = false;
                return true;
            }
            drawY += tabH + gap;
        }

        // Main content area bounds to the right of sidebar.
        float leftX = x + 86f;
        float leftY = y + 14f + contentOffsetY;
        float leftW = width - 100f;
        float leftH = height - 28f;

        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= leftY && mouseY <= leftY + leftH) {
            if (selectedCategory == Category.Events && button == 0) {
                float searchYEvents = leftY + 1f;
                float searchHEvents = 23f;
                float searchWEvents = leftW - 4f;
                if (mouseY >= searchYEvents && mouseY <= searchYEvents + searchHEvents) {
                    float rightEvents = leftX + searchWEvents - 4f;
                    float typeWEvents = 47f;
                    float rarityWEvents = 58f;
                    float gapEvents = 5f;
                    float rarityXEvents = rightEvents - rarityWEvents;
                    float typeXEvents = rarityXEvents - gapEvents - typeWEvents;
                    if (mouseX >= typeXEvents && mouseX <= typeXEvents + typeWEvents) {
                        eventTypeFilter = nextEventTypeFilter(eventTypeFilter);
                        scrollY = scrollYTarget = 0f;
                        return true;
                    }
                    if (mouseX >= rarityXEvents && mouseX <= rarityXEvents + rarityWEvents) {
                        eventRarityFilter = nextEventRarityFilter(eventRarityFilter);
                        scrollY = scrollYTarget = 0f;
                        return true;
                    }
                    eventSearchFocused = true;
                    return true;
                }
            } else if (selectedCategory == Category.Theme && button == 0) {
                float themeStartY = leftY + 2f - scrollY;
                float usableW = leftW - 7f;
                float cardW = (usableW - THEME_CARD_GAP) / THEME_COLS;
                ThemeManager.Theme[] themes = themeManager.getAvailableThemes();
                for (int index = 0; index < themes.length; index++) {
                    ThemeManager.Theme theme = themes[index];
                    int col = index % THEME_COLS;
                    int row = index / THEME_COLS;
                    float cardX = leftX + 2f + col * (cardW + THEME_CARD_GAP);
                    float cardY = themeStartY + row * (THEME_CARD_H + THEME_CARD_GAP);
                    if (mouseX >= cardX && mouseX <= cardX + cardW &&
                            mouseY >= cardY && mouseY <= cardY + THEME_CARD_H) {
                        ThemeManager.Theme previousTheme = themeManager.getCurrentTheme();
                        themeManager.setTheme(theme);
                        if (previousTheme != theme) playToggleSound(true);
                        return true;
                    }
                }

                if (themeManager.getCurrentTheme() == themeManager.getCustomTheme()) {
                    int themeRows = (themes.length + THEME_COLS - 1) / THEME_COLS;
                    float gridHeight = themeRows * THEME_CARD_H
                            + Math.max(0, themeRows - 1) * THEME_CARD_GAP;
                    float customPanelY = themeStartY + gridHeight + 9f;
                    float pickerCx = leftX + 62f;
                    float pickerCy = customPanelY + 72f;
                    float outerRadius = 45f;
                    float innerRadius = 37f;
                    float squareSize = 50f;
                    float squareX = pickerCx - squareSize * 0.5f;
                    float squareY = pickerCy - squareSize * 0.5f;
                    float dx = (float) mouseX - pickerCx;
                    float dy = (float) mouseY - pickerCy;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    if (distance >= innerRadius - 2f && distance <= outerRadius + 3f) {
                        customPickerDrag = 1;
                        updateCustomPicker(mouseX, mouseY, pickerCx, pickerCy,
                                innerRadius, outerRadius, squareX, squareY, squareSize);
                        return true;
                    }
                    if (mouseX >= squareX && mouseX <= squareX + squareSize
                            && mouseY >= squareY && mouseY <= squareY + squareSize) {
                        customPickerDrag = 2;
                        updateCustomPicker(mouseX, mouseY, pickerCx, pickerCy,
                                innerRadius, outerRadius, squareX, squareY, squareSize);
                        return true;
                    }
                }
            } else {
                List<ModuleComponent> comps = getVisibleModuleComponents();
                for (ModuleComponent mcComp : comps) {
                    boolean wasToggled = mcComp.getModule().isToggled();
                    float headerH = 24f;
                    if (button == 1 && mouseX >= mcComp.getX() && mouseX <= mcComp.getX() + mcComp.getWidth() &&
                            mouseY >= mcComp.getY() && mouseY <= mcComp.getY() + headerH) {
                        ModuleComponent nextSettings = mcComp.getComponents().isEmpty() ? null : mcComp;
                        if (activeSettings != nextSettings) {
                            activeSettings = nextSettings;
                            settingsAnimation.reset();
                            settingsAnimation.update(activeSettings != null);
                        }
                        settingsScrollY = 0f;
                        settingsScrollYTarget = 0f;
                    }
                    mcComp.mouseClicked(mouseX, mouseY, button);
                    if (button == 0 && wasToggled != mcComp.getModule().isToggled()) {
                        playToggleSound(wasToggled);
                    }
                }
            }
        }

        // Right panel interactions for settings (only if shown)
        if (activeSettings != null && !activeSettings.getComponents().isEmpty()) {
            float anim = settingsAnimation.getValue();
            if (anim > 0.2f) {
                float basePanelX = x + width + 5f;
                float panelY = y + 18f + contentOffsetY;
                float panelW = 132f;
                float panelH = height - 36f;
                float slideOffset = (1f - anim) * 40f;
                float drawPanelX = basePanelX + slideOffset;
            float panelScale = 0.965f + 0.035f * anim;
            float scaledPanelW = panelW * panelScale;
            float scaledPanelH = panelH * panelScale;
            drawPanelX += (panelW - scaledPanelW) * 0.5f;
            panelY += (panelH - scaledPanelH) * 0.5f;
            panelW = scaledPanelW;
            panelH = scaledPanelH;
                float rContentX = drawPanelX + 8f;
                float rContentY = panelY + 8f;
                float rContentW = panelW - 16f;
                float rContentH = panelH - 16f;
                if (mouseX >= rContentX && mouseX <= rContentX + rContentW && mouseY >= rContentY && mouseY <= rContentY + rContentH) {
                    activeSettings.mouseClickedExternal(mouseX, mouseY, button);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) customPickerDrag = 0;
        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            List<ModuleComponent> comps = getVisibleModuleComponents();
            for (ModuleComponent mcComp : comps) {
                if (mcComp.isBindModeMenuOpen()) {
                    mcComp.mouseReleased(mouseX, mouseY, button);
                    return true;
                }
            }
        }
        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            List<ModuleComponent> comps = getVisibleModuleComponents();
            for (ModuleComponent mcComp : comps) {
                mcComp.mouseReleased(mouseX, mouseY, button);
            }
        }
        if (activeSettings != null) {
            activeSettings.mouseReleasedExternal(mouseX, mouseY, button);
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float leftX = x + 86f;
        float leftY = y + 14f + contentOffsetY;
        float leftW = width - 100f;
        float leftH = height - 28f;
        float step = (float) (-vertical * 10f);
        if (mouseX >= leftX && mouseX <= leftX + leftW && mouseY >= leftY && mouseY <= leftY + leftH) {
            scrollYTarget = clamp(scrollYTarget + step, 0f, maxScroll);
            return true;
        }

        if (activeSettings != null && !activeSettings.getComponents().isEmpty()) {
            float anim = settingsAnimation.getValue();
            if (anim > 0.2f) {
                float basePanelX = x + width + 5f;
                float panelY = y + 18f + contentOffsetY;
                float panelW = 132f;
                float panelH = height - 36f;
                float slideOffset = (1f - anim) * 40f;
                float drawPanelX = basePanelX + slideOffset;
            float panelScale = 0.965f + 0.035f * anim;
            float scaledPanelW = panelW * panelScale;
            float scaledPanelH = panelH * panelScale;
            drawPanelX += (panelW - scaledPanelW) * 0.5f;
            panelY += (panelH - scaledPanelH) * 0.5f;
            panelW = scaledPanelW;
            panelH = scaledPanelH;
                float rContentX = drawPanelX + 8f;
                float rContentY = panelY + 8f;
                float rContentW = panelW - 16f;
                float rContentH = panelH - 16f;
                if (mouseX >= rContentX && mouseX <= rContentX + rContentW && mouseY >= rContentY && mouseY <= rContentY + rContentH) {
                    settingsScrollYTarget = clamp(settingsScrollYTarget + step, 0f, settingsMaxScroll);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closing) return false;

        // Give an active bind capture first chance at every key, including ESC/DELETE.
        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            for (ModuleComponent component : getVisibleModuleComponents()) {
                if (component.isBinding()) {
                    component.keyPressed(keyCode, scanCode, modifiers);
                    return true;
                }
            }
        }

        if (selectedCategory == Category.Events && eventSearchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                eventSearchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !eventSearchQuery.isEmpty()) {
                eventSearchQuery = eventSearchQuery.substring(0, eventSearchQuery.length() - 1);
                scrollY = scrollYTarget = 0f;
                return true;
            }
        }

        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                scrollY = scrollYTarget = 0f;
                activeSettings = null;
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }

        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            List<ModuleComponent> comps = getVisibleModuleComponents();
            for (ModuleComponent mcComp : comps) {
                mcComp.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        if (activeSettings != null) {
            activeSettings.keyPressedExternal(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (closing) return false;

        if (selectedCategory == Category.Events && eventSearchFocused && !Character.isISOControl(chr)) {
            eventSearchQuery += chr;
            scrollY = scrollYTarget = 0f;
            return true;
        }

        if (searchFocused && !Character.isISOControl(chr)) {
            searchQuery += chr;
            scrollY = scrollYTarget = 0f;
            activeSettings = null;
            return true;
        }

        if (selectedCategory != Category.Theme && selectedCategory != Category.Events) {
            List<ModuleComponent> comps = getVisibleModuleComponents();
            for (ModuleComponent mcComp : comps) {
                mcComp.charTyped(chr, modifiers);
            }
        }
        if (activeSettings != null) {
            activeSettings.charTypedExternal(chr, modifiers);
        }

        return super.charTyped(chr, modifiers);
    }


    private void renderClickRipples(DrawContext ctx) {
        if (clickRipples.isEmpty() || uiAlpha <= 0.01f) return;
        long now = System.nanoTime();
        Color accent = themeManager.getRenderedAccentColor();
        Iterator<ClickRipple> it = clickRipples.iterator();
        while (it.hasNext()) {
            ClickRipple ripple = it.next();
            float age = (now - ripple.startNanos) / 1_000_000_000.0f;
            float t = age / 0.34f;
            if (t >= 1.0f) { it.remove(); continue; }
            float eased = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
            float radius = 5.0f + 46.0f * eased;
            float alpha = (1.0f - t) * (1.0f - t) * 0.40f * uiAlpha;
            drawRippleRing(ctx, ripple.x, ripple.y, radius, 2.0f + 1.5f * (1.0f - t),
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.max(0, Math.min(255, (int)(255f * alpha)))));
        }
    }

    private void drawRippleRing(DrawContext ctx, float cx, float cy, float radius, float thickness, Color color) {
        final int segments = 44;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        int argb = color.getRGB();
        float inner = Math.max(0.0f, radius - thickness);
        for (int i = 0; i <= segments; i++) {
            double a = (Math.PI * 2.0 * i) / segments;
            float c = (float)Math.cos(a), si = (float)Math.sin(a);
            buffer.vertex(matrix, cx + c * radius, cy + si * radius, 0f).color(argb);
            buffer.vertex(matrix, cx + c * inner, cy + si * inner, 0f).color(argb);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawHueWheel(DrawContext ctx, float cx, float cy, float innerRadius, float outerRadius) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= COLOR_WHEEL_SEGMENTS; i++) {
            float cos = COLOR_WHEEL_COS[i];
            float sin = COLOR_WHEEL_SIN[i];
            int color = COLOR_WHEEL_COLORS[i];
            buffer.vertex(matrix, cx + cos * outerRadius, cy + sin * outerRadius, 0f).color(color);
            buffer.vertex(matrix, cx + cos * innerRadius, cy + sin * innerRadius, 0f).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void drawSaturationBrightnessSquare(DrawContext ctx, float x, float y, float size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);

        Matrix4f matrix = ctx.getMatrices().peek().getPositionMatrix();
        BufferBuilder buffer = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int steps = 18;
        float cell = size / steps;
        for (int iy = 0; iy < steps; iy++) {
            float v0 = 1f - iy / (float) steps;
            float v1 = 1f - (iy + 1f) / steps;
            for (int ix = 0; ix < steps; ix++) {
                float s0 = ix / (float) steps;
                float s1 = (ix + 1f) / steps;
                float x0 = x + ix * cell;
                float x1 = x + (ix + 1f) * cell;
                float y0 = y + iy * cell;
                float y1 = y + (iy + 1f) * cell;
                buffer.vertex(matrix, x0, y0, 0f).color(Color.HSBtoRGB(customHue, s0, v0));
                buffer.vertex(matrix, x0, y1, 0f).color(Color.HSBtoRGB(customHue, s0, v1));
                buffer.vertex(matrix, x1, y1, 0f).color(Color.HSBtoRGB(customHue, s1, v1));
                buffer.vertex(matrix, x1, y0, 0f).color(Color.HSBtoRGB(customHue, s1, v0));
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.enableCull();
        RenderSystem.disableBlend();

        Render2D.drawBorder(ctx.getMatrices(), x - 1f, y - 1f, size + 2f, size + 2f,
                2f, 0.2f, 0.6f, new Color(255, 255, 255, 105));
    }

    private void drawColorPickerMarkers(DrawContext ctx, float cx, float cy,
                                        float innerRadius, float outerRadius,
                                        float squareX, float squareY, float squareSize) {
        float markerRadius = (innerRadius + outerRadius) * 0.5f;
        double angle = customHue * Math.PI * 2.0 - Math.PI * 0.5;
        float hueX = cx + (float) Math.cos(angle) * markerRadius;
        float hueY = cy + (float) Math.sin(angle) * markerRadius;
        Render2D.drawRoundedRect(ctx.getMatrices(), hueX - 4.5f, hueY - 4.5f,
                9f, 9f, 4.5f, new Color(15, 16, 20, 225));
        Render2D.drawRoundedRect(ctx.getMatrices(), hueX - 3f, hueY - 3f,
                6f, 6f, 3f, Color.WHITE);

        float svX = squareX + customSaturation * squareSize;
        float svY = squareY + (1f - customBrightness) * squareSize;
        Render2D.drawRoundedRect(ctx.getMatrices(), svX - 4.5f, svY - 4.5f,
                9f, 9f, 4.5f, new Color(8, 9, 12, 235));
        Render2D.drawRoundedRect(ctx.getMatrices(), svX - 3f, svY - 3f,
                6f, 6f, 3f, Color.WHITE);
        Render2D.drawRoundedRect(ctx.getMatrices(), svX - 1.5f, svY - 1.5f,
                3f, 3f, 1.5f, Color.getHSBColor(customHue, customSaturation, customBrightness));
    }

    private void updateCustomPicker(double mouseX, double mouseY,
                                    float cx, float cy, float innerRadius, float outerRadius,
                                    float squareX, float squareY, float squareSize) {
        if (customPickerDrag == 1) {
            double angle = Math.atan2(mouseY - cy, mouseX - cx) + Math.PI * 0.5;
            if (angle < 0.0) angle += Math.PI * 2.0;
            customHue = (float) (angle / (Math.PI * 2.0));
        } else if (customPickerDrag == 2) {
            customSaturation = clamp(((float) mouseX - squareX) / squareSize, 0f, 1f);
            customBrightness = 1f - clamp(((float) mouseY - squareY) / squareSize, 0f, 1f);
        }
        themeManager.setCustomColor(Color.getHSBColor(customHue, customSaturation, customBrightness));
    }

    public void setDescription(String text) {
        this.description = text == null ? "" : text;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
    private float smoothingFactor(float responsePerSecond) {
        return 1.0f - (float) Math.exp(-responsePerSecond * uiDeltaSeconds);
    }

}

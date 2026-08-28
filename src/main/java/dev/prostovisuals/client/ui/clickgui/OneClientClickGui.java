package dev.prostovisuals.client.ui.clickgui;

import dev.prostovisuals.client.custommodels.CosmeticEntry;
import dev.prostovisuals.client.custommodels.CosmeticPreviewCache;
import dev.prostovisuals.client.custommodels.FiguraCosmeticsEngine;
import dev.prostovisuals.client.managers.CosmeticsManager;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.spatial.CaptureSource;
import dev.prostovisuals.client.spatial.CaptureSourceRegistry;
import dev.prostovisuals.client.spatial.MonitorsController;
import dev.prostovisuals.client.spatial.SpatialDisplayManager;
import dev.prostovisuals.client.spatial.SpatialMonitor;
import dev.prostovisuals.client.ui.clickgui.components.impl.ModuleComponent;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.impl.render.UI;
import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.modules.settings.api.Nameable;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ProstoVisual Glass Deck.
 *
 * A purpose-built interface for ProstoVisual: one real refracted world capture,
 * layered liquid-glass surfaces, compact module cards, a dedicated settings
 * inspector, bind studio, appearance studio, wardrobe and monitor workspace.
 */
public final class OneClientClickGui extends ClickGui {
    private static final Identifier LOGO = Identifier.of("prostovisuals", "hud/amongus.png");
    private static final Identifier ICON_MODULES = Identifier.of("prostovisuals", "hud/kimiko_icons/render.png");
    private static final Identifier ICON_BINDS = Identifier.of("prostovisuals", "hud/kimiko_icons/utility.png");
    private static final Identifier ICON_THEMES = Identifier.of("prostovisuals", "hud/kimiko_icons/theme.png");
    private static final Identifier ICON_COSMETICS = Identifier.of("prostovisuals", "hud/kimiko_icons/customize.png");
    private static final Identifier ICON_MONITORS = Identifier.of("prostovisuals", "hud/kimiko_icons/events.png");
    private static final Identifier ICON_SEARCH = Identifier.of("prostovisuals", "hud/kimiko_icons/search.png");
    private static final Identifier ICON_RENDER = Identifier.of("prostovisuals", "hud/kimiko_icons/render.png");
    private static final Identifier ICON_COMBAT = Identifier.of("prostovisuals", "hud/kimiko_icons/combat.png");
    private static final Identifier ICON_UTILITY = Identifier.of("prostovisuals", "hud/kimiko_icons/utility.png");
    private static final Identifier ICON_SETTINGS = Identifier.of("prostovisuals", "hud/kimiko_icons/settings.png");

    /** The three real module groups used by the original ProstoVisual UI. */
    private enum GuiCategory {
        COMBAT("Combat", ICON_COMBAT, Category.Combat),
        RENDER("Render", ICON_RENDER, Category.Render),
        UTILITY("Utility", ICON_UTILITY, Category.Utility);

        final String label;
        final Identifier icon;
        final Category source;

        GuiCategory(String label, Identifier icon, Category source) {
            this.label = label;
            this.icon = icon;
            this.source = source;
        }
    }

    private enum Page {
        MODULES("Modules", "Your visual workspace", ICON_MODULES),
        BINDS("Keybinds", "Fast access, no clutter", ICON_BINDS),
        THEMES("Themes", "Shape the whole client", ICON_THEMES),
        COSMETICS("Customize", "Your avatar, your style", ICON_COSMETICS),
        MONITORS("Monitors", "Desktop windows in world", ICON_MONITORS);

        final String title;
        final String subtitle;
        final Identifier icon;
        Page(String title, String subtitle, Identifier icon) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
        }
    }

    private static final List<Page> VISUAL_PAGES = List.of(Page.MODULES, Page.THEMES, Page.COSMETICS);
    private static final List<Page> TOOL_PAGES = List.of(Page.MONITORS);

    private enum CosmeticTab {
        MODELS("Models", CosmeticEntry.Kind.MODEL),
        HEADS("Heads", CosmeticEntry.Kind.HEAD),
        HATS("Hats", CosmeticEntry.Kind.HAT),
        WEAPONS("Weapons", CosmeticEntry.Kind.WEAPON),
        PETS("Pets", CosmeticEntry.Kind.PET);

        final String label;
        final CosmeticEntry.Kind kind;
        CosmeticTab(String label, CosmeticEntry.Kind kind) {
            this.label = label;
            this.kind = kind;
        }
    }

    private record Box(float x, float y, float w, float h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
    private record NavHit(Page page, Box box) {}
    private record CategoryHit(GuiCategory category, Box box) {}
    private record ModuleHit(ModuleComponent component, Box card, Box settings, Box toggle) {}
    private record BindHit(Module module, Box row) {}
    private record ThemeHit(ThemeManager.Theme theme, Box box) {}
    private record CosmeticHit(CosmeticEntry entry, Box box) {}
    private record CosmeticTabHit(CosmeticTab tab, Box box) {}
    private record PetModeHit(CosmeticsManager.PetBehavior behavior, Box box) {}
    private record MonitorHit(int index, Box box) {}
    private record SourceHit(int index, Box box) {}
    private record KeyHit(String label, int key, Box box) {}
    private record KeySpec(String label, int key, float units) {}

    private final ThemeManager themes = ThemeManager.getInstance();
    private final Map<Category, List<ModuleComponent>> components = new EnumMap<>(Category.class);
    private final Map<String, Float> hover = new HashMap<>();
    private final List<NavHit> navHits = new ArrayList<>();
    private final List<CategoryHit> categoryHits = new ArrayList<>();
    private final List<ModuleHit> moduleHits = new ArrayList<>();
    private final List<BindHit> bindHits = new ArrayList<>();
    private final List<ThemeHit> themeHits = new ArrayList<>();
    private final List<CosmeticHit> cosmeticHits = new ArrayList<>();
    private final List<CosmeticTabHit> cosmeticTabHits = new ArrayList<>();
    private final List<PetModeHit> petModeHits = new ArrayList<>();
    private final List<MonitorHit> monitorHits = new ArrayList<>();
    private final List<SourceHit> sourceHits = new ArrayList<>();
    private final List<KeyHit> keyHits = new ArrayList<>();

    private Page page = Page.MODULES;
    private GuiCategory moduleCategory = GuiCategory.RENDER;
    private CosmeticTab cosmeticTab = CosmeticTab.MODELS;
    private ModuleComponent selectedModule;
    private boolean moduleInspectorOpen;
    private Module selectedBindModule;
    private Module bindingModule;
    private long bindOverlayOpenedAt;

    private String search = "";
    private boolean searchFocused;

    private float shellX, shellY, shellW, shellH;
    private float inspectorPanelX, inspectorPanelY, inspectorPanelW, inspectorPanelH;
    private float sidebarX, sidebarY, sidebarW, sidebarH;
    private float headerX, headerY, headerW, headerH;
    private float contentX, contentY, contentW, contentH;
    private Box searchBox;
    private Box settingsBox, closeBox;
    private Box globalSettingsBox, languageBox;
    private boolean globalSettingsOpen;
    private float globalSettingsProgress;

    private float moduleScroll, moduleScrollTarget, moduleMaxScroll;
    private float settingsScroll, settingsScrollTarget, settingsMaxScroll;
    private float bindScroll, bindScrollTarget, bindMaxScroll;
    private float cosmeticScroll, cosmeticScrollTarget, cosmeticMaxScroll;
    private int sourceScroll;
    private int monitorVisibleRows = 8;

    private long openedAt = System.currentTimeMillis();
    private long pageChangedAt = openedAt;
    private long selectedChangedAt = openedAt;
    private long closingAt;
    private long lastFrameNanos;
    private boolean closing;
    private float frameDelta = 1f / 60f;
    private float navIndicatorY = Float.NaN;

    private Box inspectorToggleBox, inspectorCloseBox;
    private Box bindKeyBox, bindToggleBox, bindHoldBox, bindClearBox;
    private Box bindOverlayToggleBox, bindOverlayHoldBox, bindOverlayClearBox, bindOverlayCloseBox;
    private Box customSatValBox, customHueBox;
    private boolean draggingCustomSatVal, draggingCustomHue;
    private float customHue, customSat, customVal;
    private final List<ClickRipple> clickRipples = new ArrayList<>();
    private Box cosmeticClearBox;
    private Box monitorToggleBox, fpsMinusBox, fpsPlusBox, opacityMinusBox, opacityPlusBox;
    private Box addMonitorBox, setSourceBox, removeMonitorBox, refreshSourcesBox;

    private List<CaptureSource> monitorSources = new ArrayList<>();
    private boolean scanningSources;
    private int selectedMonitor = -1;
    private int selectedSource;

    private record ClickRipple(float x, float y, long startedAt) {}

    public OneClientClickGui() {
        super();
    }

    @Override
    public void init() {
        openedAt = System.currentTimeMillis();
        pageChangedAt = openedAt;
        selectedChangedAt = openedAt;
        closingAt = 0L;
        lastFrameNanos = 0L;
        closing = false;
        // Keep page/moduleCategory/cosmeticTab: this screen instance is reused,
        // so reopening ClickGUI returns to the exact workspace the user left.
        globalSettingsOpen = false;
        globalSettingsProgress = 0f;
        clickRipples.clear();
        searchFocused = false;
        rebuildModules();
        updateLayout();
        Color customColor = themes.getCustomColor();
        float[] hsb = Color.RGBtoHSB(customColor.getRed(), customColor.getGreen(), customColor.getBlue(), null);
        customHue = hsb[0]; customSat = hsb[1]; customVal = hsb[2];
        if (page == Page.MONITORS && monitorSources.isEmpty()) refreshMonitorSources();
    }

    @Override
    public void refreshModuleVisibility() {
        rebuildModules();
    }

    private void rebuildModules() {
        Module previouslySelected = selectedModule == null ? null : selectedModule.getModule();
        components.clear();
        selectedModule = null;

        for (Category category : List.of(Category.Render, Category.Combat, Category.Utility)) {
            List<ModuleComponent> list = new ArrayList<>();
            for (Module module : prostovisuals.getInstance().getModuleManager().getModules(category)) {
                if (module instanceof UI) continue;
                ModuleComponent component = new ModuleComponent(module);
                component.setRenderExternally(true);
                list.add(component);
                if (previouslySelected == module) selectedModule = component;
            }
            components.put(category, list);
        }

        if (selectedModule == null) selectFirstVisibleModule();
        if (selectedBindModule == null) {
            List<Module> all = allVisibleModules();
            if (!all.isEmpty()) selectedBindModule = all.get(0);
        }
    }

    private void updateLayout() {
        float sw = Math.max(1f, mc.getWindow().getScaledWidth());
        float sh = Math.max(1f, mc.getWindow().getScaledHeight());

        /*
         * Responsive Wyvern-style shell.
         *
         * The old code multiplied the shell by 0.56/0.54 of the scaled window.
         * When leaving F11 Minecraft can halve its logical GUI size, which made the
         * shell halve *again* and all fixed-size children overlap.  Keep a stable
         * logical canvas and only shrink it when it physically cannot fit.
         */
        float margin = sw < 420f || sh < 260f ? 7f : 11f;
        shellW = Math.min(560f, Math.max(220f, sw - margin * 2f));
        shellH = Math.min(326f, Math.max(176f, sh - margin * 2f));

        // On short windows prefer width over dead vertical space, but never let the
        // workspace become so flat that monitor/settings controls overlap.
        float preferredH = Math.min(shellH, Math.max(214f, shellW * 0.57f));
        shellH = Math.min(sh - margin * 2f, preferredH);

        shellX = (sw - shellW) * 0.5f;
        shellY = (sh - shellH) * 0.5f;

        headerX = shellX;
        headerY = shellY;
        headerW = shellW;
        headerH = clamp(shellH * 0.145f, 31f, 40f);

        sidebarW = clamp(shellW * 0.205f, 88f, 112f);
        sidebarX = shellX;
        sidebarY = shellY + headerH;
        sidebarH = shellH - headerH;

        float pad = clamp(shellW * 0.018f, 6f, 10f);
        contentX = sidebarX + sidebarW + pad;
        contentY = sidebarY + pad;
        contentW = Math.max(90f, shellX + shellW - pad - contentX);
        contentH = Math.max(80f, shellY + shellH - pad - contentY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        closingAt = System.currentTimeMillis();
        searchFocused = false;
        globalSettingsOpen = false;
    }

    private void finishClose() {
        themes.finishTransitionImmediately();
        mc.setScreen(null);
        UI ui = prostovisuals.getInstance().getModuleManager().getModule(UI.class);
        if (ui != null && ui.isToggled()) ui.setToggled(false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        updateLayout();
        long nowNanos = System.nanoTime();
        if (lastFrameNanos != 0L) {
            frameDelta = clamp((nowNanos - lastFrameNanos) / 1_000_000_000f, 1f / 300f, 1f / 20f);
        }
        lastFrameNanos = nowNanos;
        long now = System.currentTimeMillis();
        themes.updateTransition();

        float enter = easeOutCubic(clamp((now - openedAt) / 320f, 0f, 1f));
        float exit = closing ? smoothstep(clamp((now - closingAt) / 260f, 0f, 1f)) : 0f;
        if (closing && exit >= .999f) {
            finishClose();
            return;
        }
        float visibility = enter * (1f - exit);
        globalSettingsProgress = animateTowards(globalSettingsProgress, globalSettingsOpen ? 1f : 0f, 14f);
        float time = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);

        Render2D.drawRect(ctx.getMatrices(), 0, 0,
                mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
                new Color(0, 0, 0, (int) (72 * visibility)));

        ctx.getMatrices().push();
        float scale = .965f + .035f * enter - .035f * exit;
        float cx = shellX + shellW / 2f;
        float cy = shellY + shellH / 2f;
        ctx.getMatrices().translate(cx, cy + (1f - enter) * 24f + exit * 18f, 0);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-cx, -cy, 0);

        // Main VisionOS-like lens: the captured world remains visible through
        // the cool glass instead of being covered by an opaque black slab.
        Render2D.drawRoundedRect(ctx.getMatrices(), shellX, shellY, shellW, shellH, 9,
                new Color(7, 7, 10, 248));
        Render2D.drawRoundedRect(ctx.getMatrices(), shellX + 1, shellY + 1, shellW - 2, shellH - 2, 8,
                new Color(10, 10, 14, 246));
        Render2D.drawBorder(ctx.getMatrices(), shellX, shellY, shellW, shellH, 9, .20f, .32f,
                new Color(255, 255, 255, 16));
        Render2D.drawGradientRect(ctx.getMatrices(), shellX + 26f, shellY + 1f,
                shellW - 52f, 1.2f, new Color(255,255,255,92), new Color(255,255,255,0), true);
        Render2D.drawRect(ctx.getMatrices(), shellX + 10f, shellY + headerH - 1f, shellW - 20f, 1f,
                new Color(255, 255, 255, 13));

        renderSidebar(ctx, mouseX, mouseY, time);
        renderHeader(ctx, mouseX, mouseY, time);

        float pageT = easeOutCubic(clamp((now - pageChangedAt) / 300f, 0f, 1f));
        ctx.getMatrices().push();
        ctx.getMatrices().translate((1f - pageT) * 22f, 0, 0);
        switch (page) {
            case MODULES -> renderModules(ctx, mouseX, mouseY, delta, time, pageT);
            case BINDS -> renderBinds(ctx, mouseX, mouseY, time, pageT);
            case THEMES -> renderThemes(ctx, mouseX, mouseY, time, pageT);
            case COSMETICS -> renderCosmetics(ctx, mouseX, mouseY, time, pageT);
            case MONITORS -> renderMonitors(ctx, mouseX, mouseY, time, pageT);
        }
        ctx.getMatrices().pop();

        if (globalSettingsProgress > .01f) renderGlobalSettings(ctx, mouseX, mouseY, time, globalSettingsProgress);

        renderClickRipples(ctx);
        ctx.getMatrices().pop();
        ThemeWaveRenderer.render(ctx, time);
        if (bindingModule != null) renderBindOverlay(ctx, mouseX, mouseY, time);
    }

    private void renderSidebar(DrawContext ctx, int mx, int my, float time) {
        navHits.clear();
        categoryHits.clear();
        Color accent = themeAccentAt(sidebarX + sidebarW * .5f, sidebarY + sidebarH * .5f);

        float profileH = 39f;
        float navH = sidebarH - profileH - 5f;
        Render2D.drawRoundedRect(ctx.getMatrices(), sidebarX, sidebarY, sidebarW, navH, 0,
                new Color(8, 9, 12, 244));
        Render2D.drawBorder(ctx.getMatrices(), sidebarX, sidebarY, sidebarW, navH, 9, .20f, .38f,
                new Color(229, 247, 255, 30));

        float navX = sidebarX + 12f;
        float navW = sidebarW - 24f;
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(5.0f),
                ClickGuiLanguage.isRussian() ? "ФУНКЦИИ" : "FEATURES", navX + 3f, sidebarY + 11f,
                new Color(92, 96, 108, 220));
        float gap = 2f;
        int navItems = GuiCategory.values().length + 2;
        float availableNavH = Math.max(90f, navH - 31f);
        float itemH = Math.min(33f, Math.max(25f, (availableNavH - gap * (navItems - 1)) / navItems));
        float y = sidebarY + 27f;
        for (GuiCategory category : GuiCategory.values()) {
            drawCategoryItem(ctx, mx, my, category, navX, y, navW, itemH, themeAccentAt(navX + navW * .5f, y + itemH * .5f));
            y += itemH + gap;
        }
        drawPageItem(ctx, mx, my, Page.COSMETICS, "Customize", ICON_COSMETICS, navX, y, navW, itemH);
        y += itemH + gap;
        drawPageItem(ctx, mx, my, Page.MONITORS, "Monitors", ICON_MONITORS, navX, y, navW, itemH);

        float profileY = sidebarY + sidebarH - profileH;
        Render2D.drawRoundedRect(ctx.getMatrices(), sidebarX, profileY, sidebarW, profileH, 0,
                new Color(8, 9, 12, 244));
        Render2D.drawBorder(ctx.getMatrices(), sidebarX, profileY, sidebarW, profileH, 8, .20f, .38f,
                new Color(235, 249, 255, 34));

        float head = sidebarW < 100f ? 20f : 24f;
        float headX = sidebarX + (sidebarW < 100f ? 7f : 10f);
        float headY = profileY + (profileH - head) * .5f;
        Render2D.drawRoundedRect(ctx.getMatrices(), headX, headY, head, head, 4,
                new Color(255,255,255,20));
        if (mc.player != null) {
            Render2D.drawTexture(ctx.getMatrices(), headX, headY, head, head, 4,
                    .125f, .125f, .125f, .125f,
                    ((AbstractClientPlayerEntity) mc.player).getSkinTextures().texture(), Color.WHITE);
        } else {
            Render2D.drawTexture(ctx.getMatrices(), headX + 4, headY + 4, head - 8, head - 8, 3, LOGO, Color.WHITE);
        }

        String profile = mc.player != null ? mc.player.getGameProfile().getName() : "ProstoPlayer";
        float textX = headX + head + (sidebarW < 100f ? 5f : 7f);
        boolean showPremium = sidebarW >= 104f;
        float premiumW = showPremium ? 34f : 0f;
        float premiumX = sidebarX + sidebarW - premiumW - 6f;
        float profileTextW = showPremium ? Math.max(8f, premiumX - textX - 4f) : Math.max(8f, sidebarX + sidebarW - textX - 6f);
        drawClippedMarquee(ctx, profile, textX, profileY + 9f, profileTextW, sidebarW < 100f ? 5.1f : 5.5f,
                mx>=sidebarX && mx<=sidebarX+sidebarW && my>=profileY && my<=profileY+profileH, new Color(243,248,253));
        if (showPremium) {
            Render2D.drawRoundedRect(ctx.getMatrices(),premiumX,profileY+6f,premiumW,13f,5f,
                    new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),52));
            centered(ctx,ClickGuiLanguage.isRussian()?"Премиум":"Premium",premiumX,profileY+9.5f,premiumW,3.7f,new Color(244,250,254));
        }
        String uid = mc.player == null ? "UID: offline" : "UID: " + mc.player.getUuidAsString().replace("-", "").substring(0, 12);
        float uidW = Math.max(8f, sidebarX + sidebarW - textX - 6f);
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(sidebarW < 100f ? 3.7f : 4.1f),
                trim(uid, uidW, sidebarW < 100f ? 3.7f : 4.1f), textX, profileY + 23f, new Color(166, 190, 211));
    }

    private void drawCategoryItem(DrawContext ctx, int mx, int my, GuiCategory item,
                                  float x, float y, float w, float h, Color accent) {
        Box box = new Box(x, y, w, h);
        boolean active = page == Page.MODULES && moduleCategory == item && search.isBlank();
        float hv = anim("category:" + item.name(), box.contains(mx, my) ? 1f : 0f, .22f);
        float selected = anim("category-active:" + item.name(), active ? 1f : 0f, .18f);
        Render2D.drawRoundedRect(ctx.getMatrices(), box.x, box.y, box.w, box.h, 6,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(50f * selected)));
        if (selected < .99f) Render2D.drawRoundedRect(ctx.getMatrices(), box.x, box.y, box.w, box.h, 6,
                new Color(255,255,255,(int)((3f + 11f*hv) * (1f-selected))));
        Render2D.drawBorder(ctx.getMatrices(), box.x, box.y, box.w, box.h, 6, .20f, .38f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(18f + 87f*selected + 10f*hv)));
        Render2D.drawTexture(ctx.getMatrices(), box.x + 10, box.y + (h - 16f) * .5f, 16, 16, 3, item.icon,
                active ? accentText(accent, 255) : new Color(224, 235, 245, (int)(220 + 35*hv)));
        drawClippedMarquee(ctx, ClickGuiLanguage.translate(item.label), box.x + 38f, box.y + (h - 11f) * .5f,
                Math.max(8f, box.w - 43f), 7.1f, box.contains(mx,my), active ? Color.WHITE : new Color(226, 235, 245));
        categoryHits.add(new CategoryHit(item, box));
    }

    private void drawPageItem(DrawContext ctx, int mx, int my, Page target, String label, Identifier icon,
                              float x, float y, float w, float h) {
        Box box = new Box(x, y, w, h);
        boolean active = page == target;
        float hv = anim("page-item:" + target.name(), box.contains(mx, my) ? 1f : 0f, .20f);
        float selected = anim("page-active:" + target.name(), active ? 1f : 0f, .18f);
        Color accent = themeAccentAt(x + w * .5f, y + h * .5f);
        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 6f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(50f * selected)));
        if (selected < .99f) {
            Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 6f,
                    new Color(255,255,255,(int)((3f + 11f * hv) * (1f - selected))));
        }
        Render2D.drawBorder(ctx.getMatrices(), x, y, w, h, 6f, .20f, .38f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(18f + 87f * selected)));
        Render2D.drawTexture(ctx.getMatrices(), x + 10f, y + (h - 16f) * .5f, 16f, 16f, 3f, icon,
                active ? accentText(accent,255) : new Color(224,235,245,(int)(220 + 35 * hv)));
        drawClippedMarquee(ctx, ClickGuiLanguage.translate(label), x + 38f, y + (h - 11f) * .5f,
                Math.max(8f, w - 43f), 7.1f, box.contains(mx,my), active ? Color.WHITE : new Color(226,235,245));
        navHits.add(new NavHit(target, box));
    }

    private void renderHeader(DrawContext ctx, int mx, int my, float time) {
        Color accent = themeAccentAt(shellX + 44f, shellY + 26f);

        // Actual ProstoVisual crewmate mark. The old text-only "PV" monogram
        // looked like a placeholder and did not match the rest of the client.
        float logoSize = 20f;
        float logoX = shellX + sidebarW * .5f - logoSize * .5f;
        float logoY = shellY + headerH * .5f - logoSize * .5f;
        Render2D.drawTexture(ctx.getMatrices(), logoX, logoY, logoSize, logoSize, 5f, LOGO, Color.WHITE);

        closeBox = new Box(-100f, -100f, 0f, 0f);
        settingsBox = new Box(shellX + shellW - 34f, shellY + 7f, 24f, 24f);

        float searchX = shellX + sidebarW + 10f;
        float searchW = clamp(settingsBox.x - searchX - 10f, 68f, Math.max(68f, shellX + shellW - searchX - 44f));
        searchBox = new Box(searchX, shellY + 7f, searchW, 24f);
        Color searchAccent = themeAccentAt(searchBox.x + searchBox.w * .5f, searchBox.y + searchBox.h * .5f);
        float hf = anim("search", searchBox.contains(mx, my) || searchFocused ? 1f : 0f, .22f);
        Render2D.drawRoundedRect(ctx.getMatrices(), searchBox.x, searchBox.y, searchBox.w, searchBox.h, 7,
                new Color(213, 234, 247, (int)(18 + 13*hf)));
        Render2D.drawBorder(ctx.getMatrices(), searchBox.x, searchBox.y, searchBox.w, searchBox.h, 7, .20f, .38f,
                searchFocused ? new Color(searchAccent.getRed(),searchAccent.getGreen(),searchAccent.getBlue(),115)
                        : new Color(239,249,255,24));
        Render2D.drawTexture(ctx.getMatrices(), searchBox.x + 8, searchBox.y + 7, 11, 11, 2, ICON_SEARCH,
                new Color(224, 239, 250, 220));
        String shown = search.isBlank() ? ClickGuiLanguage.translate("Search modules...") : search;
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.4f), trim(shown, searchBox.w - 36, 5.4f),
                searchBox.x + 25, searchBox.y + 8,
                search.isBlank() ? new Color(197,217,234) : new Color(244,249,253));

        drawHeaderButton(ctx, settingsBox, mx, my, globalSettingsOpen ? 1f : 0f);
        Color icon = new Color(226, 229, 238, 236);
        for (int i = 0; i < 3; i++) {
            float yy = settingsBox.y + 8f + i * 4f;
            Render2D.drawLine(ctx.getMatrices(), settingsBox.x + 8f, yy, settingsBox.x + 20f, yy, 1.1f, icon);
        }
    }

    private void drawHeaderButton(DrawContext ctx, Box box, int mx, int my, float active) {
        float hv = anim("header:" + Math.round(box.x), box.contains(mx, my) ? 1f : 0f, .22f);
        Color accent = themeAccentAt(box.x + box.w * .5f, box.y + box.h * .5f);
        int r = (int)(215 + (accent.getRed() - 215) * active);
        int g = (int)(235 + (accent.getGreen() - 235) * active);
        int b = (int)(248 + (accent.getBlue() - 248) * active);
        Render2D.drawRoundedRect(ctx.getMatrices(), box.x, box.y, box.w, box.h, 7,
                new Color(r, g, b, (int)(18 + 19*hv + 16*active)));
        Render2D.drawBorder(ctx.getMatrices(), box.x, box.y, box.w, box.h, 7, .20f, .38f,
                new Color(242, 251, 255, (int)(20 + 18*hv + 18*active)));
    }

    private void renderGlobalSettings(DrawContext ctx, int mx, int my, float time, float progress) {
        themeHits.clear();
        ThemeManager.Theme displayedTheme = displayedTheme();
        boolean customActive = displayedTheme == themes.getCustomTheme();
        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();
        float w = Math.min(190f, Math.max(132f, screenW - 12f));
        float wantedH = customActive ? 232f : 136f;
        float h = Math.min(wantedH, Math.max(96f, screenH - 12f));
        float desiredX = shellX + shellW + 6f;
        float x;
        if (desiredX + w <= screenW - 5f) x = desiredX;
        else if (shellX - w - 6f >= 5f) x = shellX - w - 6f;
        else x = clamp(shellX + shellW - w - 5f, 5f, Math.max(5f, screenW - w - 5f));
        float y = clamp(shellY + 5f, 5f, Math.max(5f, screenH - h - 5f));
        globalSettingsBox = new Box(x, y, w, h);

        ctx.getMatrices().push();
        float scale = .86f + .14f * easeOutCubic(progress);
        float pivotX = settingsBox.x + settingsBox.w * .5f;
        float pivotY = settingsBox.y + settingsBox.h * .5f;
        ctx.getMatrices().translate(pivotX, pivotY, 0f);
        ctx.getMatrices().scale(scale, scale, 1f);
        ctx.getMatrices().translate(-pivotX, -pivotY, 0f);

        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 9f,
                new Color(6, 7, 10, 252));
        Render2D.drawBorder(ctx.getMatrices(), x, y, w, h, 10f, .22f, .40f,
                new Color(235, 249, 255, 42));
        Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(7.2f), ClickGuiLanguage.translate("Interface settings"),
                x + 12f, y + 11f, new Color(255,255,255));
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(5.0f), ClickGuiLanguage.translate("Theme"),
                x + 12f, y + 29f, new Color(218,228,238));

        ThemeManager.Theme[] available = themes.getAvailableThemes();
        float gap = 5f;
        float cardW = (w - 22f - gap * 2f) / 3f;
        float cardH = 23f;
        for (int i = 0; i < available.length; i++) {
            ThemeManager.Theme theme = available[i];
            int col = i % 3;
            int row = i / 3;
            Box box = new Box(x + 11f + col * (cardW + gap), y + 36f + row * (cardH + gap), cardW, cardH);
            boolean selected = displayedTheme == theme;
            float hv = anim("global-theme:" + theme.getName(), box.contains(mx,my) ? 1f : 0f, .22f);
            Color c = theme.getAccentColor();
            Render2D.drawRoundedRect(ctx.getMatrices(), box.x, box.y, box.w, box.h, 6f,
                    selected ? new Color(c.getRed(),c.getGreen(),c.getBlue(),36)
                            : new Color(230,243,251,(int)(9 + 9*hv)));
            Render2D.drawBorder(ctx.getMatrices(), box.x, box.y, box.w, box.h, 6f, .18f, .34f,
                    selected ? new Color(c.getRed(),c.getGreen(),c.getBlue(),115)
                            : new Color(242,251,255,(int)(15 + 9*hv)));
            Render2D.drawRoundedRect(ctx.getMatrices(), box.x + 7f, box.y + 8f, 7f, 7f, 3.5f,
                    new Color(c.getRed(),c.getGreen(),c.getBlue(),245));
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(4.9f), trim(theme.getName(), box.w - 23f, 4.9f),
                    box.x + 19f, box.y + 7.5f, new Color(244,248,252));
            themeHits.add(new ThemeHit(theme, box));
        }

        if (customActive) {
            Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(4.5f), ClickGuiLanguage.translate("Custom color"),
                    x + 11f, y + 96f, new Color(176,198,215));
            renderCustomColorPicker(ctx, x + 11f, y + 108f, w - 22f, 76f);
            Color custom = themes.getCustomColor();
            String rgb = "RGB  " + custom.getRed() + "  " + custom.getGreen() + "  " + custom.getBlue();
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(4.7f), rgb,
                    x + 11f, y + 191f, new Color(221,233,243));
        } else {
            customSatValBox = null;
            customHueBox = null;
        }

        languageBox = new Box(x + 11f, y + h - 28f, w - 22f, 19f);
        Render2D.drawRoundedRect(ctx.getMatrices(), languageBox.x, languageBox.y, languageBox.w, languageBox.h, 6f,
                new Color(230,243,251,12));
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(5.1f), ClickGuiLanguage.translate("Settings language"),
                languageBox.x + 8f, languageBox.y + 5.8f, new Color(246,249,252));
        String language = ClickGuiLanguage.isRussian() ? "RU" : "EN";
        float pillW = 28f;
        Color languageAccent = themeAccentAt(languageBox.x + languageBox.w - pillW * .5f - 4f, languageBox.y + languageBox.h * .5f);
        Render2D.drawRoundedRect(ctx.getMatrices(), languageBox.x + languageBox.w - pillW - 4f,
                languageBox.y + 3f, pillW, 13f, 5f,
                new Color(languageAccent.getRed(),languageAccent.getGreen(),languageAccent.getBlue(),42));
        centered(ctx, language, languageBox.x + languageBox.w - pillW - 4f,
                languageBox.y + 7f, pillW, 4.5f, Color.WHITE);
        ctx.getMatrices().pop();
    }

    private void renderModules(DrawContext ctx, int mx, int my, float delta, float time, float pageAlpha) {
        moduleHits.clear();
        inspectorToggleBox = null;
        inspectorCloseBox = null;

        // Settings are a dedicated view inside the content region. They never cover the
        // module grid or escape outside the client, and their wheel scrolling is isolated.
        if (moduleInspectorOpen && selectedModule != null) {
            inspectorPanelX = contentX; inspectorPanelY = contentY; inspectorPanelW = contentW; inspectorPanelH = contentH;
            inspectorCloseBox = new Box(contentX + 3f, contentY + 1f, 20f, 18f);
            float hv = anim("inspector-back", inspectorCloseBox.contains(mx,my) ? 1f : 0f, .22f);
            Render2D.drawRoundedRect(ctx.getMatrices(), inspectorCloseBox.x, inspectorCloseBox.y, inspectorCloseBox.w, inspectorCloseBox.h, 5f,
                    new Color(255,255,255,(int)(7 + 12*hv)));
            Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(7.0f), "‹", inspectorCloseBox.x + 7f, inspectorCloseBox.y + 4f, new Color(235,242,248));
            renderModuleInspector(ctx, mx, my, delta, contentX + 27f, contentY, contentW - 27f, contentH, pageAlpha);
            return;
        }

        inspectorPanelX=inspectorPanelY=inspectorPanelW=inspectorPanelH=0f;
        List<ModuleComponent> visible = visibleModuleComponents();
        String title = search.isBlank() ? ClickGuiLanguage.translate(moduleCategory.label) : ClickGuiLanguage.translate("Search results");
        Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(7.2f), title,
                contentX + 3f, contentY + 2f, new Color(244,247,251));

        String count = ClickGuiLanguage.isRussian() ? visible.size()+" мод." : visible.size()+" modules";
        float countW = Fonts.MEDIUM.getWidth(count,4.2f)+12f;
        Render2D.drawRoundedRect(ctx.getMatrices(),contentX+contentW-countW-2f,contentY,countW,16f,5f,new Color(255,255,255,7));
        centered(ctx,count,contentX+contentW-countW-2f,contentY+4.5f,countW,4.2f,new Color(174,184,197));

        float gridY = contentY + 23f;
        float gridH = contentH - 23f;
        float colGap = 5f;
        float cardGap = 3f;
        int cols = contentW < 250f ? 1 : 2;
        float cardW = cols == 1 ? contentW : (contentW-colGap)/2f;
        float cardH = 27f;
        int rows = (visible.size()+cols-1)/cols;
        moduleMaxScroll = Math.max(0f, rows*(cardH+cardGap)-cardGap-gridH);
        moduleScrollTarget = clamp(moduleScrollTarget,0,moduleMaxScroll);
        moduleScroll = animateTowards(moduleScroll, moduleScrollTarget, 18f);

        ctx.enableScissor((int)contentX,(int)gridY,(int)(contentX+contentW),(int)(gridY+gridH));
        for(int i=0;i<visible.size();i++){
            int col=i%cols; int row=i/cols;
            float x=contentX+col*(cardW+colGap);
            float y=gridY+row*(cardH+cardGap)-moduleScroll;
            // Do not create hitboxes for cards clipped outside the viewport. The old
            // implementation still added invisible card hitboxes, so a click on one
            // visible module could toggle another module sitting off-screen.
            if (y + cardH < gridY || y > gridY + gridH) continue;
            float entry=easeOutCubic(clamp((System.currentTimeMillis()-pageChangedAt-i*14f)/220f,0f,1f));
            drawModuleCard(ctx,mx,my,visible.get(i),x,y+(1f-entry)*5f,cardW,cardH,pageAlpha*entry);
        }
        ctx.disableScissor();
    }

    private void drawModuleCard(DrawContext ctx, int mx, int my, ModuleComponent component,
                                float x, float y, float w, float h, float pageAlpha) {
        Module module = component.getModule();
        Color accent = themeAccentAt(x + w * .5f, y + h * .5f);
        Box card = new Box(x,y,w,h);
        Box settings = new Box(x + w - 17f, y + 7f, 11f, 14f);
        Box toggle = new Box(x + w - 47f, y + 8f, 22f, 11f);
        float hv = anim("mod:" + module.getName(), card.contains(mx,my) ? 1f : 0f, .20f);
        boolean selected = selectedModule == component;
        float selectedProgress = anim("module-selected:" + module.getName(), selected ? 1f : 0f, .18f);
        boolean enabled = module.isToggled();

        int baseAlpha = (int)((enabled ? 176f : 154f) * pageAlpha);
        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 4.5f,
                enabled
                        ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), Math.min(74, baseAlpha))
                        : new Color(0, 0, 0, Math.min(205, baseAlpha + (int)(20f * hv))));
        if (!enabled && hv > .01f) {
            Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 4.5f,
                    new Color(255,255,255,(int)(10f*hv*pageAlpha)));
        }
        if (selectedProgress > .01f) {
            Render2D.drawRoundedRect(ctx.getMatrices(), x, y, 2.0f, h, 1.7f,
                    new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(220*selectedProgress*pageAlpha)));
        }

        float textMax = w - 72f;
        drawClippedMarquee(ctx, ClickGuiLanguage.translate(module.getName()), x + 11f, y + 9.5f, textMax, 6.2f,
                card.contains(mx,my), new Color(242,248,253,(int)(255*pageAlpha)));

        if (!component.getComponents().isEmpty()) {
            float sh = anim("module-settings:" + module.getName(), settings.contains(mx,my) ? 1f : 0f, .25f);
            Render2D.drawTexture(ctx.getMatrices(), settings.x, settings.y + 1f, 12f, 12f, 2f, ICON_SETTINGS,
                    new Color(224,238,248,(int)((180 + 75*sh)*pageAlpha)));
        }
        drawSwitch(ctx, "module:" + module.getName(), toggle.x, toggle.y, toggle.w, toggle.h, enabled, accent, pageAlpha);
        moduleHits.add(new ModuleHit(component, card, settings, toggle));
    }

    private void renderModuleInspector(DrawContext ctx, int mx, int my, float delta,
                                       float x, float y, float w, float h, float pageAlpha) {
        if (selectedModule == null) {
            Render2D.drawTexture(ctx.getMatrices(), x + w/2 - 10, y + 72, 20, 20, 4, ICON_SETTINGS,
                    new Color(204,224,239));
            centered(ctx, ClickGuiLanguage.translate("Select a module"), x, y + 108, w, 7.2f, new Color(224,235,245));
            centered(ctx, ClickGuiLanguage.translate("Its settings will appear here"), x, y + 127, w, 5.2f, new Color(151,175,196));
            settingsMaxScroll = settingsScroll = settingsScrollTarget = 0;
            return;
        }

        Module module = selectedModule.getModule();
        Color accent = themeAccentAt(x + w * .5f, y + h * .5f);
        float selectionT = easeOutCubic(clamp((System.currentTimeMillis() - selectedChangedAt) / 250f, 0f, 1f));
        pageAlpha *= selectionT;
        ctx.getMatrices().push();
        ctx.getMatrices().translate((1f - selectionT) * 10f, 0f, 0f);
        drawClippedMarquee(ctx, ClickGuiLanguage.translate(module.getName()), x + 14f, y + 11f,
                Math.max(20f, w - 101f), 8.2f, mx >= x && mx <= x + w && my >= y && my <= y + 34f,
                new Color(247,251,254,(int)(255f * pageAlpha)));

        String state = module.isToggled() ? ClickGuiLanguage.translate("Enabled") : ClickGuiLanguage.translate("Disabled");
        float stateProgress = anim("inspector-state:" + module.getName(), module.isToggled() ? 1f : 0f, .18f);
        float stateW = Fonts.MEDIUM.getWidth(state, 5f) + 27f;
        inspectorToggleBox = new Box(x + w - stateW - 10f, y + 9f, stateW, 21f);
        Render2D.drawRoundedRect(ctx.getMatrices(), inspectorToggleBox.x, inspectorToggleBox.y,
                inspectorToggleBox.w, inspectorToggleBox.h, 7f,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(13f + 18f * stateProgress)));
        Render2D.drawBorder(ctx.getMatrices(), inspectorToggleBox.x, inspectorToggleBox.y,
                inspectorToggleBox.w, inspectorToggleBox.h, 7f, .18f, .34f,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(18f + 54f * stateProgress)));
        Render2D.drawRoundedRect(ctx.getMatrices(), inspectorToggleBox.x + 8f, inspectorToggleBox.y + 8f,
                5f, 5f, 2.5f, lerpColor(new Color(167,187,203), accentText(accent,245), stateProgress));
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(5f), state,
                inspectorToggleBox.x + 18f, inspectorToggleBox.y + 7f,
                module.isToggled() ? new Color(238,249,255) : new Color(190,208,222));

        float descriptionH = 30f;
        float descriptionY = y + h - descriptionH - 7f;
        float sectionY = y + 39f;
        float sectionH = descriptionY - sectionY - 6f;
        Render2D.drawRoundedRect(ctx.getMatrices(), x + 6f, sectionY, w - 12f, sectionH, 8f,
                new Color(5, 6, 9, 205));
        Render2D.drawBorder(ctx.getMatrices(), x + 6f, sectionY, w - 12f, sectionH, 8f, .18f, .34f,
                new Color(237,249,255,24));
        Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(5.7f), ClickGuiLanguage.translate("General"),
                x + 14f, sectionY + 8f, new Color(236,245,252));

        float clipX = x + 10f;
        float clipY = sectionY + 22f;
        float clipW = w - 20f;
        float clipH = sectionH - 27f;

        if (selectedModule.getComponents().isEmpty()) {
            centered(ctx, ClickGuiLanguage.translate("No extra settings"), clipX, clipY + 24f, clipW, 5.8f, new Color(165,186,203));
            settingsMaxScroll = settingsScroll = settingsScrollTarget = 0;
        } else {
            selectedModule.setGlobalAlpha(pageAlpha);
            float total = selectedModule.renderSettingsExternally(ctx,
                    clipX + 1f, clipY, clipW - 2f,
                    clipX, clipY, clipW, clipH,
                    mx, my, delta, settingsScroll);
            settingsMaxScroll = Math.max(0, total - clipH);
            settingsScrollTarget = clamp(settingsScrollTarget, 0, settingsMaxScroll);
            settingsScroll += (settingsScrollTarget - settingsScroll) * .23f;
            if (settingsMaxScroll > 1) {
                float trackH = clipH - 8;
                float thumbH = Math.max(24, trackH * (clipH / (clipH + settingsMaxScroll)));
                float thumbY = clipY + 4 + (trackH - thumbH) * (settingsScroll / settingsMaxScroll);
                Render2D.drawRoundedRect(ctx.getMatrices(), x + w - 7, thumbY, 2, thumbH, 1,
                        new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),110));
            }
        }

        String desc = moduleDescription(module);
        Render2D.drawRoundedRect(ctx.getMatrices(), x + 6f, descriptionY, w - 12f, descriptionH, 7f,
                new Color(216,235,246,16));
        Render2D.drawBorder(ctx.getMatrices(), x + 6f, descriptionY, w - 12f, descriptionH, 7f, .18f, .34f,
                new Color(242,251,255,20));
        Render2D.drawRoundedRect(ctx.getMatrices(), x + 14f, descriptionY + 10f, 9f, 9f, 4.5f,
                new Color(232,245,253,32));
        centered(ctx, "i", x + 14f, descriptionY + 12f, 9f, 4.7f, new Color(235,247,253));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(4.7f), trim(desc, w - 48f, 4.7f),
                x + 30f, descriptionY + 10f, new Color(211,226,238));
        ctx.getMatrices().pop();
    }

    private List<ModuleComponent> visibleModuleComponents() {
        List<ModuleComponent> source = new ArrayList<>();
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        for (ModuleComponent component : allModuleComponents()) {
            Module module = component.getModule();
            if (q.isBlank()) {
                if (groupFor(module) == moduleCategory) source.add(component);
            } else {
                if (module.getName().toLowerCase(Locale.ROOT).contains(q)) source.add(component);
            }
        }
        return source;
    }

    private List<ModuleComponent> allModuleComponents() {
        List<ModuleComponent> out = new ArrayList<>();
        for (Category category : List.of(Category.Render, Category.Combat, Category.Utility)) {
            out.addAll(components.getOrDefault(category, List.of()));
        }
        return out;
    }

    private GuiCategory groupFor(Module module) {
        if (module == null || module.getCategory() == Category.Utility) return GuiCategory.UTILITY;
        return module.getCategory() == Category.Combat ? GuiCategory.COMBAT : GuiCategory.RENDER;
    }

    private void selectFirstVisibleModule() {
        List<ModuleComponent> list = visibleModuleComponents();
        selectModule(list.isEmpty() ? null : list.get(0));
    }

    private void selectModule(ModuleComponent component) {
        if (selectedModule == component) return;
        selectedModule = component;
        selectedChangedAt = System.currentTimeMillis();
        settingsScroll = settingsScrollTarget = 0f;
    }

    private void renderBinds(DrawContext ctx, int mx, int my, float time, float pageAlpha) {
        bindHits.clear();
        bindKeyBox = bindToggleBox = bindHoldBox = bindClearBox = null;
        Color accent = themes.getRenderedAccentColor();
        float gap = 11f;
        float studioW = Math.min(270f, contentW * .39f);
        float listW = contentW - studioW - gap;

        Render2D.drawRoundedRect(ctx.getMatrices(), contentX, contentY, listW, contentH, 13,
                new Color(7,14,23,92));
        Render2D.drawBorder(ctx.getMatrices(), contentX, contentY, listW, contentH, 13, .25f, .45f,
                new Color(255,255,255,12));

        List<Module> list = filteredVisibleModules();
        float rowH = 46f;
        float rowGap = 7f;
        float y = contentY + 8 - bindScroll;
        ctx.enableScissor((int)contentX, (int)contentY, (int)(contentX+listW), (int)(contentY+contentH));
        for (Module module : list) {
            Box row = new Box(contentX + 8, y, listW - 16, rowH);
            float hv = anim("bindrow:" + module.getName(), row.contains(mx,my) ? 1f : 0f, .18f);
            boolean selected = selectedBindModule == module;
            Render2D.drawRoundedRect(ctx.getMatrices(), row.x,row.y,row.w,row.h,10,
                    selected ? new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),28)
                            : new Color(255,255,255,(int)(6 + 10*hv)));
            Render2D.drawBorder(ctx.getMatrices(), row.x,row.y,row.w,row.h,10,.24f,.42f,
                    selected ? new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),92)
                            : new Color(255,255,255,12));
            Render2D.drawRoundedRect(ctx.getMatrices(), row.x+9,row.y+9,28,28,8,new Color(255,255,255,9));
            Render2D.drawTexture(ctx.getMatrices(), row.x+17,row.y+17,12,12,2,categoryIcon(module.getCategory()),
                    new Color(184,201,220));
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(6.8f), trim(ClickGuiLanguage.translate(module.getName()), row.w-155, 6.8f),
                    row.x+46,row.y+9,new Color(233,239,247));
            Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.2f), ClickGuiLanguage.translate(categoryLabel(module.getCategory())),
                    row.x+46,row.y+25,new Color(111,132,157));
            String key = module.getBind() == null || module.getBind().getKey() < 0 ? "NONE" : module.getBind().toString();
            float kw = Math.max(50, Fonts.MEDIUM.getWidth(key,5.4f)+18);
            Render2D.drawRoundedRect(ctx.getMatrices(), row.x+row.w-kw-9,row.y+11,kw,24,8,
                    new Color(3,9,16,100));
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(5.4f), trim(key, kw-14, 5.4f),
                    row.x+row.w-kw,row.y+19, selected ? accentText(accent,225) : new Color(167,184,204));
            bindHits.add(new BindHit(module,row));
            y += rowH + rowGap;
        }
        ctx.disableScissor();
        bindMaxScroll = Math.max(0,(list.size()*(rowH+rowGap)-rowGap)+16-contentH);
        bindScrollTarget = clamp(bindScrollTarget,0,bindMaxScroll);
        bindScroll += (bindScrollTarget-bindScroll)*.23f;

        float sx = contentX + listW + gap;
        Render2D.drawRoundedRect(ctx.getMatrices(),sx,contentY,studioW,contentH,13,new Color(8,16,26,145));
        Render2D.drawBorder(ctx.getMatrices(),sx,contentY,studioW,contentH,13,.28f,.45f,new Color(255,255,255,16));
        renderBindStudio(ctx,mx,my,sx,contentY,studioW,contentH,accent);
    }

    private void renderBindStudio(DrawContext ctx,int mx,int my,float x,float y,float w,float h,Color accent) {
        Module module = selectedBindModule;
        if (module == null) {
            centered(ctx,ClickGuiLanguage.translate("Select a module"),x,y+100,w,7f,new Color(204,216,230));
            return;
        }
        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(8.4f),"Bind Studio",x+16,y+16,new Color(242,247,252));
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(5.4f),"One place for every shortcut",x+16,y+34,new Color(117,138,161));

        Render2D.drawRoundedRect(ctx.getMatrices(),x+14,y+58,w-28,60,12,new Color(255,255,255,8));
        Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(5.4f),"SELECTED MODULE",x+25,y+70,new Color(113,133,157));
        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(7.5f),trim(ClickGuiLanguage.translate(module.getName()),w-72,7.5f),x+25,y+86,new Color(235,241,249));

        String key = bindingModule == module ? "PRESS A KEY" : (module.getBind()==null||module.getBind().getKey()<0?"UNBOUND":module.getBind().toString());
        bindKeyBox = new Box(x+14,y+132,w-28,62);
        float kh = bindKeyBox.contains(mx,my) ? 1f : 0f;
        Render2D.drawBlurredRect(ctx.getMatrices(),bindKeyBox.x,bindKeyBox.y,bindKeyBox.w,bindKeyBox.h,12,10,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(13+12*kh)));
        Render2D.drawRoundedRect(ctx.getMatrices(),bindKeyBox.x,bindKeyBox.y,bindKeyBox.w,bindKeyBox.h,12,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),bindingModule==module?42:22));
        Render2D.drawBorder(ctx.getMatrices(),bindKeyBox.x,bindKeyBox.y,bindKeyBox.w,bindKeyBox.h,12,.28f,.45f,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),bindingModule==module?140:72));
        centered(ctx,key,bindKeyBox.x,bindKeyBox.y+17,bindKeyBox.w,8.2f,Color.WHITE);
        centered(ctx,bindingModule==module?"Keyboard or mouse":"Click to rebind",bindKeyBox.x,bindKeyBox.y+39,bindKeyBox.w,5.1f,new Color(125,145,169));

        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(5.2f),"ACTIVATION MODE",x+16,y+215,new Color(119,140,164));
        float segGap=7f, segW=(w-28-segGap)/2f;
        bindToggleBox=new Box(x+14,y+230,segW,34);
        bindHoldBox=new Box(x+14+segW+segGap,y+230,segW,34);
        Bind.Mode mode=module.getBind()==null?Bind.Mode.TOGGLE:module.getBind().getMode();
        drawModeSegment(ctx,bindToggleBox,"Toggle",mode==Bind.Mode.TOGGLE,accent);
        drawModeSegment(ctx,bindHoldBox,"Hold",mode==Bind.Mode.HOLD,accent);

        bindClearBox=new Box(x+14,y+h-45,w-28,30);
        Render2D.drawRoundedRect(ctx.getMatrices(),bindClearBox.x,bindClearBox.y,bindClearBox.w,bindClearBox.h,9,
                new Color(255,255,255,bindClearBox.contains(mx,my)?15:8));
        centered(ctx,"CLEAR BIND",bindClearBox.x,bindClearBox.y+10,bindClearBox.w,5.3f,new Color(153,170,191));
    }

    private void drawModeSegment(DrawContext ctx,Box box,String label,boolean active,Color accent){
        Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,9,
                active?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),38):new Color(255,255,255,8));
        Render2D.drawBorder(ctx.getMatrices(),box.x,box.y,box.w,box.h,5,.20f,.36f,
                active?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),100):new Color(255,255,255,12));
        centered(ctx,label,box.x,box.y+11,box.w,5.8f,active?Color.WHITE:new Color(160,178,199));
    }

    private void renderThemes(DrawContext ctx,int mx,int my,float time,float pageAlpha){
        themeHits.clear();
        customSatValBox = customHueBox = null;
        float previewW=Math.min(244f,contentW*.40f);
        Color accent=themes.getRenderedAccentColorAt(contentX + previewW * .5f, contentY + contentH * .5f);
        float gap=10f;
        float gridX=contentX+previewW+gap;
        float gridW=contentW-previewW-gap;
        Render2D.drawRoundedRect(ctx.getMatrices(),contentX,contentY,previewW,contentH,13,new Color(8,16,26,142));
        Render2D.drawBorder(ctx.getMatrices(),contentX,contentY,previewW,contentH,13,.27f,.45f,new Color(255,255,255,17));

        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(7.8f),"Glass Lab",contentX+14,contentY+13,new Color(242,247,252));
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(5.1f),"Live accent and liquid response",contentX+14,contentY+29,new Color(113,134,159));

        float px=contentX+14,py=contentY+48,pw=previewW-28,ph=92;
        Render2D.drawBlurredRect(ctx.getMatrices(),px+24,py+17,pw-48,45,13,13,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),28));
        Render2D.drawRoundedRect(ctx.getMatrices(),px,py,pw,ph,13,new Color(4,10,17,124));
        Render2D.drawBorder(ctx.getMatrices(),px,py,pw,ph,13,.25f,.44f,new Color(255,255,255,18));
        Render2D.drawRoundedRect(ctx.getMatrices(),px+11,py+12,30,30,9,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),38));
        Render2D.drawTexture(ctx.getMatrices(),px+19,py+20,14,14,3,LOGO,Color.WHITE);
        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(6.7f),"ProstoVisual",px+50,py+14,new Color(241,246,252));
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(4.9f),displayedTheme().getName()+" theme",px+50,py+31,accentText(accent,220));
        Render2D.drawRoundedRect(ctx.getMatrices(),px+11,py+56,pw-22,20,7,new Color(255,255,255,8));
        Render2D.drawRoundedRect(ctx.getMatrices(),px+17,py+63,(pw-34)*.58f,6,3,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),150));

        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(4.9f),"CUSTOM COLOR",contentX+14,contentY+154,new Color(116,138,163));
        renderCustomColorPicker(ctx, contentX+14, contentY+169, previewW-28, 98f);

        String rgb="RGB  "+themes.getCustomColor().getRed()+"  "+themes.getCustomColor().getGreen()+"  "+themes.getCustomColor().getBlue();
        Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(5.0f),rgb,contentX+14,contentY+278,new Color(209,220,234));
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(4.7f),"Click or drag to make Custom yours",contentX+14,contentY+292,new Color(104,125,150));

        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(4.9f),"PALETTES",gridX,contentY+2,new Color(116,138,163));
        ThemeManager.Theme[] available=themes.getAvailableThemes();
        int cols=2;float cg=8f;float cardW=(gridW-cg)/2f;float cardH=72f;
        float startY=contentY+18;
        for(int i=0;i<available.length;i++){
            int row=i/cols,col=i%cols;float x=gridX+col*(cardW+cg),y=startY+row*(cardH+cg);
            ThemeManager.Theme theme=available[i];Color c=theme.getAccentColor();boolean active=displayedTheme()==theme;
            Box box=new Box(x,y,cardW,cardH);float hv=anim("theme:"+theme.getName(),box.contains(mx,my)?1f:0f,.18f);
            if(active) Render2D.drawBlurredRect(ctx.getMatrices(),x+8,y+5,cardW-16,cardH-10,10,10,new Color(c.getRed(),c.getGreen(),c.getBlue(),22));
            Render2D.drawRoundedRect(ctx.getMatrices(),x,y,cardW,cardH,11,active?new Color(c.getRed(),c.getGreen(),c.getBlue(),27):new Color(255,255,255,(int)(6+9*hv)));
            Render2D.drawBorder(ctx.getMatrices(),x,y,cardW,cardH,11,.24f,.43f,active?new Color(c.getRed(),c.getGreen(),c.getBlue(),108):new Color(255,255,255,13));
            Render2D.drawGradientRect(ctx.getMatrices(),x+9,y+9,cardW-18,20,new Color(c.getRed(),c.getGreen(),c.getBlue(),180),new Color(c.getRed(),c.getGreen(),c.getBlue(),25),true);
            Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(5.9f),theme.getName(),x+10,y+39,new Color(234,240,248));
            Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(4.7f),active?"ACTIVE":"APPLY",x+10,y+54,active?accentText(c,225):new Color(106,127,151));
            Render2D.drawRoundedRect(ctx.getMatrices(),x+cardW-22,y+48,10,10,5,accentText(c,230));
            themeHits.add(new ThemeHit(theme,box));
        }
    }

    private void renderCustomColorPicker(DrawContext ctx, float x, float y, float w, float h) {
        float hueW = 12f;
        float gap = 7f;
        customSatValBox = new Box(x, y, w - hueW - gap, h);
        customHueBox = new Box(x + w - hueW, y, hueW, h);
        Color hueColor = Color.getHSBColor(customHue, 1f, 1f);
        Render2D.drawGradientRect(ctx.getMatrices(), customSatValBox.x, customSatValBox.y, customSatValBox.w, customSatValBox.h,
                Color.WHITE, hueColor, true);
        Render2D.drawGradientRect(ctx.getMatrices(), customSatValBox.x, customSatValBox.y, customSatValBox.w, customSatValBox.h,
                new Color(0,0,0,0), new Color(0,0,0,245), false);
        Render2D.drawBorder(ctx.getMatrices(),customSatValBox.x,customSatValBox.y,customSatValBox.w,customSatValBox.h,8,.25f,.42f,new Color(255,255,255,35));

        float sy = customSatValBox.y + (1f-customVal)*customSatValBox.h;
        float sx = customSatValBox.x + customSat*customSatValBox.w;
        Render2D.drawRoundedRect(ctx.getMatrices(),sx-3.5f,sy-3.5f,7,7,3.5f,new Color(255,255,255,235));
        Render2D.drawRoundedRect(ctx.getMatrices(),sx-2f,sy-2f,4,4,2f,Color.getHSBColor(customHue,customSat,customVal));

        Color[] stops = {Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED};
        float seg = h/6f;
        for(int i=0;i<6;i++) Render2D.drawGradientRect(ctx.getMatrices(),customHueBox.x,customHueBox.y+i*seg,customHueBox.w,seg+1,stops[i],stops[i+1],false);
        Render2D.drawBorder(ctx.getMatrices(),customHueBox.x,customHueBox.y,customHueBox.w,customHueBox.h,6,.25f,.42f,new Color(255,255,255,40));
        float hy=customHueBox.y+customHue*customHueBox.h;
        Render2D.drawRoundedRect(ctx.getMatrices(),customHueBox.x-2,hy-1.5f,customHueBox.w+4,3,1.5f,Color.WHITE);
    }

    private void updateCustomColor(double mouseX, double mouseY) {
        if (draggingCustomSatVal && customSatValBox != null) {
            customSat = clamp((float)((mouseX-customSatValBox.x)/customSatValBox.w),0,1);
            customVal = 1f-clamp((float)((mouseY-customSatValBox.y)/customSatValBox.h),0,1);
        }
        if (draggingCustomHue && customHueBox != null) {
            customHue = clamp((float)((mouseY-customHueBox.y)/customHueBox.h),0,1);
        }
        Color c = Color.getHSBColor(customHue,customSat,customVal);
        themes.setCustomColor(c);
        if (themes.getTransitionTarget() != themes.getCustomTheme()
                && themes.getCurrentTheme() != themes.getCustomTheme()) {
            startThemeTransition(themes.getCustomTheme(), settingsBox == null ? shellX + shellW : settingsBox.x);
        }
    }

    private void renderCosmetics(DrawContext ctx,int mx,int my,float time,float pageAlpha){
        cosmeticHits.clear();
        cosmeticTabHits.clear();
        petModeHits.clear();
        cosmeticClearBox=null;
        Color accent=themeAccentAt(contentX + contentW * .5f, contentY + contentH * .5f);

        float tabsY=contentY;
        float tabGap=4f;
        CosmeticTab[] tabs=CosmeticTab.values();
        float tabW=Math.max(36f,(contentW-tabGap*(tabs.length-1))/tabs.length);
        float tx=contentX;
        for(CosmeticTab tab:tabs){
            String tabText=ClickGuiLanguage.translate(tab.label); float w=tabW;
            Box box=new Box(tx,tabsY,w,26);boolean active=cosmeticTab==tab;
            float tabHover=anim("cos-tab-hover:"+tab.name(),box.contains(mx,my)?1f:0f,.18f);
            float tabActive=anim("cos-tab-active:"+tab.name(),active?1f:0f,.16f);
            Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,5,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(38*tabActive)));
            if(tabActive<.99f) Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,5,new Color(255,255,255,(int)(5+10*tabHover)));
            Render2D.drawBorder(ctx.getMatrices(),box.x,box.y,box.w,box.h,5,.20f,.36f,active?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),95):new Color(255,255,255,12));
            drawClippedMarquee(ctx,tabText,box.x+5f,box.y+8.2f,box.w-10f,5.2f,box.contains(mx,my),active?Color.WHITE:new Color(166,184,204));
            cosmeticTabHits.add(new CosmeticTabHit(tab,box));tx+=w+tabGap;
        }

        float bodyY=contentY+31;float bodyH=contentH-31;float detailW=Math.min(148f,Math.max(132f,contentW*.40f));float gap=7;float gridW=contentW-detailW-gap;float detailX=contentX+gridW+gap;
        Render2D.drawRoundedRect(ctx.getMatrices(),contentX,bodyY,gridW,bodyH,13,new Color(7,14,23,92));
        Render2D.drawBorder(ctx.getMatrices(),contentX,bodyY,gridW,bodyH,13,.25f,.45f,new Color(255,255,255,12));

        List<CosmeticEntry> entries=cosmeticsForPage();int cols=gridW>300?3:(gridW>150?2:1);float cg=6;float cardW=(gridW-12-(cols-1)*cg)/cols;float cardH=88;float sy=bodyY+8-cosmeticScroll;
        CosmeticsManager manager=prostovisuals.getInstance().getCosmeticsManager();
        ctx.enableScissor((int)contentX,(int)bodyY,(int)(contentX+gridW),(int)(bodyY+bodyH));
        for(int i=0;i<entries.size();i++){
            int row=i/cols,col=i%cols;float x=contentX+8+col*(cardW+cg),y=sy+row*(cardH+cg);CosmeticEntry entry=entries.get(i);boolean selected=manager!=null&&manager.isSelected(entry);Box box=new Box(x,y,cardW,cardH);
            float hv=anim("cos:"+entry.relativePath(),box.contains(mx,my)?1f:0f,.17f);
            Render2D.drawRoundedRect(ctx.getMatrices(),x,y,cardW,cardH,11,selected?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),24):new Color(255,255,255,(int)(6+9*hv)));
            Render2D.drawBorder(ctx.getMatrices(),x,y,cardW,cardH,11,.24f,.43f,selected?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),105):new Color(255,255,255,13));
            float px=x+6,py=y+6,pw=cardW-12,ph=50;Render2D.drawRoundedRect(ctx.getMatrices(),px,py,pw,ph,8,new Color(4,10,17,126));
            Identifier preview=CosmeticPreviewCache.get(entry);if(preview!=null){float s=Math.min(pw,ph)-5;Render2D.drawTexture(ctx.getMatrices(),px+(pw-s)/2,py+2,s,s,7,preview,Color.WHITE);} else {centered(ctx,ClickGuiLanguage.translate("PREVIEW"),px,py+26,pw,5f,new Color(91,112,137));}
            Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(5.8f),trim(entry.name(),cardW-18,5.8f),x+7,y+63,new Color(231,238,246));
            Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(4.8f),selected?ClickGuiLanguage.translate("EQUIPPED"):ClickGuiLanguage.translate(entry.kind().name()),x+7,y+76,selected?accentText(accent,220):new Color(104,125,150));
            cosmeticHits.add(new CosmeticHit(entry,box));
        }
        ctx.disableScissor();
        int rows=(entries.size()+cols-1)/cols;cosmeticMaxScroll=Math.max(0,rows*(cardH+cg)-cg+16-bodyH);cosmeticScrollTarget=clamp(cosmeticScrollTarget,0,cosmeticMaxScroll);cosmeticScroll+=(cosmeticScrollTarget-cosmeticScroll)*.23f;
        Render2D.drawRoundedRect(ctx.getMatrices(),detailX,bodyY,detailW,bodyH,13,new Color(8,16,26,145));
        Render2D.drawBorder(ctx.getMatrices(),detailX,bodyY,detailW,bodyH,13,.28f,.45f,new Color(255,255,255,16));
        renderCosmeticDetail(ctx,mx,my,detailX,bodyY,detailW,bodyH,accent,manager);
    }

    private void renderCosmeticDetail(DrawContext ctx,int mx,int my,float x,float y,float w,float h,Color accent,CosmeticsManager manager){
        CosmeticEntry selected=selectedCosmetic(manager);
        float pad = 8f;
        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(6.6f),ClickGuiLanguage.translate("Wardrobe"),x+pad,y+8f,new Color(242,247,252));
        if(selected==null){
            centered(ctx,ClickGuiLanguage.translate("No cosmetic active"),x,y+h*.43f,w,5.1f,new Color(189,203,219));
            centered(ctx,ClickGuiLanguage.translate("Choose one from the wardrobe"),x+5f,y+h*.43f+13f,w-10f,4.2f,new Color(108,129,154));
            cosmeticClearBox=null;
            return;
        }

        boolean pet = selected.kind()==CosmeticEntry.Kind.PET;
        // Keep enough vertical room for the 2x2 pet behaviour grid + remove button.
        float previewH = pet ? clamp(h * .25f, 34f, 50f) : clamp(h * .38f, 42f, 70f);
        float px=x+pad, py=y+24f, pw=w-pad*2f, ph=previewH;
        Render2D.drawRoundedRect(ctx.getMatrices(),px,py,pw,ph,7,new Color(5,7,11,235));
        Identifier preview=CosmeticPreviewCache.get(selected);
        if(preview!=null){
            float sz=Math.max(24f,Math.min(pw,ph)-7f);
            Render2D.drawTexture(ctx.getMatrices(),px+(pw-sz)/2f,py+(ph-sz)/2f,sz,sz,6,preview,Color.WHITE);
        }

        float infoY=py+ph+6f;
        drawClippedMarquee(ctx,selected.name(),x+pad,infoY,w-pad*2f,5.3f,
                mx>=x+pad&&mx<=x+w-pad&&my>=infoY-2&&my<=infoY+10,new Color(236,242,249));
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(4.2f),
                (ClickGuiLanguage.isRussian()?"Тип: ":"Type: ")+ClickGuiLanguage.translate(selected.kind().name()),
                x+pad,infoY+11f,new Color(116,137,161));

        float nextY=infoY+23f;
        if(pet&&manager!=null){
            float gap=4f;
            float bw=(w-pad*2f-gap)/2f;
            float bh=15f;
            int idx=0;
            for(CosmeticsManager.PetBehavior behavior:CosmeticsManager.PetBehavior.values()){
                int row=idx/2,col=idx%2;
                Box b=new Box(x+pad+col*(bw+gap),nextY+row*(bh+gap),bw,bh);
                boolean active=manager.getPetBehavior()==behavior;
                float hv=anim("petmode:"+behavior.name(),b.contains(mx,my)?1f:0f,.20f);
                Render2D.drawRoundedRect(ctx.getMatrices(),b.x,b.y,b.w,b.h,4f,
                        active?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),48)
                              :new Color(255,255,255,(int)(6+8*hv)));
                drawClippedMarquee(ctx,ClickGuiLanguage.translate(behavior.label()),b.x+5f,b.y+4.2f,b.w-10f,4.0f,
                        b.contains(mx,my),active?Color.WHITE:new Color(165,181,199));
                petModeHits.add(new PetModeHit(behavior,b));idx++;
            }
            nextY += bh*2f + gap + 6f;
        }

        float clearY=Math.min(y+h-23f, Math.max(nextY, y+24f));
        cosmeticClearBox=new Box(x+pad,clearY,w-pad*2f,16f);
        float ch=anim("cos-clear",cosmeticClearBox.contains(mx,my)?1f:0f,.20f);
        Render2D.drawRoundedRect(ctx.getMatrices(),cosmeticClearBox.x,cosmeticClearBox.y,cosmeticClearBox.w,cosmeticClearBox.h,5f,
                new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(20+18*ch)));
        centered(ctx,ClickGuiLanguage.translate("REMOVE COSMETIC"),cosmeticClearBox.x,cosmeticClearBox.y+4.7f,cosmeticClearBox.w,4.1f,Color.WHITE);
    }

    private List<CosmeticEntry> cosmeticsForPage(){
        List<CosmeticEntry> out=new ArrayList<>();Set<String> seen=new HashSet<>();String q=search==null?"":search.toLowerCase(Locale.ROOT);
        for(CosmeticEntry entry:FiguraCosmeticsEngine.getCatalog()){
            if(entry.kind()!=cosmeticTab.kind)continue;String key=entry.name().trim().toLowerCase(Locale.ROOT);if(!seen.add(key))continue;if(!q.isBlank()&&!key.contains(q))continue;out.add(entry);
        }
        return out;
    }

    private CosmeticEntry selectedCosmetic(CosmeticsManager manager){
        if(manager==null||!manager.hasSelection())return null;
        for(CosmeticEntry entry:FiguraCosmeticsEngine.getCatalog())if(manager.isSelected(entry))return entry;
        return null;
    }

    private void renderMonitors(DrawContext ctx,int mx,int my,float time,float pageAlpha){
        monitorHits.clear();
        sourceHits.clear();

        MonitorsController controller = MonitorsController.getInstance();
        SpatialDisplayManager manager = SpatialDisplayManager.getInstance();
        Color accent = themeAccentAt(contentX + contentW * .5f, contentY + contentH * .5f);

        // Fully responsive monitor workspace. All vertical sizes are derived from the
        // current content bounds, so leaving F11 cannot make controls spill over the shell.
        float pad = 5f;
        float titleH = clamp(contentH * .16f, 28f, 39f);
        float actionH = clamp(contentH * .12f, 28f, 38f);
        float bodyY = contentY + titleH + pad;
        float bodyH = Math.max(48f, contentH - titleH - actionH - pad * 2f);

        Render2D.drawRoundedRect(ctx.getMatrices(),contentX,contentY,contentW,titleH,4f,new Color(0,0,0,190));
        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(6.5f),
                ClickGuiLanguage.isRussian()?"Мониторы":"Spatial monitors",contentX+8f,contentY+6f,new Color(244,248,252));

        monitorToggleBox=new Box(contentX+contentW-34f,contentY+5f,25f,12f);
        drawSwitch(ctx,"monitors:enabled",monitorToggleBox.x,monitorToggleBox.y,monitorToggleBox.w,monitorToggleBox.h,
                controller.isEnabled(),accent,1f);

        float controlsY=contentY+titleH-17f;
        float controlGap=5f;
        float controlW=(contentW-controlGap)/2f;
        fpsMinusBox=new Box(contentX,controlsY,13f,13f);
        fpsPlusBox=new Box(contentX+controlW-13f,controlsY,13f,13f);
        opacityMinusBox=new Box(contentX+controlW+controlGap,controlsY,13f,13f);
        opacityPlusBox=new Box(contentX+controlW+controlGap+controlW-13f,controlsY,13f,13f);
        drawStepControl(ctx,contentX,controlsY,controlW,13f,"FPS "+controller.getCaptureFps(),fpsMinusBox,fpsPlusBox,accent);
        drawStepControl(ctx,contentX+controlW+controlGap,controlsY,controlW,13f,
                (ClickGuiLanguage.isRussian()?"Прозрачность ":"Opacity ")+Math.round(controller.getOpacity()*100)+"%",
                opacityMinusBox,opacityPlusBox,accent);

        float colGap=5f;
        float colW=(contentW-colGap)/2f;
        float monX=contentX, srcX=contentX+colW+colGap;
        drawMonitorColumn(ctx,monX,bodyY,colW,bodyH,ClickGuiLanguage.isRussian()?"МОНИТОРЫ":"MONITORS",manager.getMonitors().size(),time);
        drawMonitorColumn(ctx,srcX,bodyY,colW,bodyH,ClickGuiLanguage.isRussian()?"ОКНА":"WINDOWS",monitorSources.size(),time);

        float listY=bodyY+20f;
        float rowH=clamp(bodyH*.16f,14f,18f);
        float rowGap=2f;
        monitorVisibleRows=Math.max(1,(int)((bodyH-24f)/(rowH+rowGap)));
        List<SpatialMonitor> monitors=manager.getMonitors();

        ctx.enableScissor((int)monX,(int)(bodyY+18f),(int)(monX+colW),(int)(bodyY+bodyH));
        if(monitors.isEmpty()){
            centered(ctx,ClickGuiLanguage.isRussian()?"Нет мониторов":"No monitors",monX,listY+12f,colW,4.2f,new Color(142,158,177));
        }else{
            for(int i=0;i<Math.min(monitorVisibleRows,monitors.size());i++){
                Box box=new Box(monX+4f,listY+i*(rowH+rowGap),colW-8f,rowH);
                boolean sel=selectedMonitor==i;
                float hv=anim("mon:"+i,box.contains(mx,my)?1f:0f,.18f);
                Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,4f,
                        sel?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),38)
                           :new Color(255,255,255,(int)(5+7*hv)));
                drawClippedMarquee(ctx,(i+1)+" · "+monitors.get(i).getName(),box.x+5f,box.y+(rowH-8f)*.5f,
                        box.w-10f,4.2f,box.contains(mx,my),new Color(220,229,240));
                monitorHits.add(new MonitorHit(i,box));
            }
        }
        ctx.disableScissor();

        ctx.enableScissor((int)srcX,(int)(bodyY+18f),(int)(srcX+colW),(int)(bodyY+bodyH));
        if(monitorSources.isEmpty()){
            centered(ctx,scanningSources?(ClickGuiLanguage.isRussian()?"Поиск...":"Scanning...")
                    :(ClickGuiLanguage.isRussian()?"Окон нет":"No windows"),srcX,listY+12f,colW,4.2f,new Color(142,158,177));
        }else{
            int visible=monitorVisibleRows;
            int start=Math.max(0,Math.min(sourceScroll,Math.max(0,monitorSources.size()-visible)));
            for(int j=0;j<Math.min(visible,monitorSources.size()-start);j++){
                int i=start+j;
                Box box=new Box(srcX+4f,listY+j*(rowH+rowGap),colW-8f,rowH);
                boolean sel=selectedSource==i;
                float hv=anim("src:"+i,box.contains(mx,my)?1f:0f,.18f);
                Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,4f,
                        sel?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),38)
                           :new Color(255,255,255,(int)(5+7*hv)));
                drawClippedMarquee(ctx,monitorSources.get(i).name(),box.x+5f,box.y+(rowH-8f)*.5f,
                        box.w-10f,4.0f,box.contains(mx,my),new Color(220,229,240));
                sourceHits.add(new SourceHit(i,box));
            }
        }
        ctx.disableScissor();

        // Fixed two-row action strip inside the content area.
        float actionY=contentY+contentH-actionH;
        float btnGap=4f;
        float bh=(actionH-btnGap)/2f;
        float bw=(contentW-btnGap)/2f;
        addMonitorBox=new Box(contentX,actionY,bw,bh);
        setSourceBox=new Box(contentX+bw+btnGap,actionY,bw,bh);
        removeMonitorBox=new Box(contentX,actionY+bh+btnGap,bw,bh);
        refreshSourcesBox=new Box(contentX+bw+btnGap,actionY+bh+btnGap,bw,bh);
        drawActionButton(ctx,mx,my,addMonitorBox,ClickGuiLanguage.isRussian()?"ДОБАВИТЬ":"ADD MONITOR",accent,!monitorSources.isEmpty());
        drawActionButton(ctx,mx,my,setSourceBox,ClickGuiLanguage.isRussian()?"ВЫБРАТЬ ОКНО":"SET SOURCE",accent,selectedMonitor>=0&&!monitorSources.isEmpty());
        drawActionButton(ctx,mx,my,removeMonitorBox,ClickGuiLanguage.isRussian()?"УДАЛИТЬ":"REMOVE",accent,selectedMonitor>=0);
        drawActionButton(ctx,mx,my,refreshSourcesBox,scanningSources?(ClickGuiLanguage.isRussian()?"ПОИСК...":"SCANNING")
                :(ClickGuiLanguage.isRussian()?"ОБНОВИТЬ":"REFRESH"),accent,!scanningSources);
    }

    private void renderBindOverlay(DrawContext ctx, int mx, int my, float time) {
        keyHits.clear();
        Color accent = themes.getRenderedAccentColor();
        float sw=mc.getWindow().getScaledWidth(), sh=mc.getWindow().getScaledHeight();
        Render2D.drawRect(ctx.getMatrices(),0,0,sw,sh,new Color(0,3,8,142));

        float panelW=Math.min(748f,sw-28f), panelH=Math.min(344f,sh-28f);
        float x=(sw-panelW)/2f, y=(sh-panelH)/2f;
        float open=easeOutCubic(clamp((System.currentTimeMillis()-bindOverlayOpenedAt)/180f,0,1));
        ctx.getMatrices().push();
        float sc=.965f+.035f*open,cx=x+panelW/2f,cy=y+panelH/2f;
        ctx.getMatrices().translate(cx,cy+(1-open)*8f,0);ctx.getMatrices().scale(sc,sc,1);ctx.getMatrices().translate(-cx,-cy,0);
        Render2D.drawRoundedRect(ctx.getMatrices(),x,y,panelW,panelH,20,new Color(6,12,20,178));
        Render2D.drawBorder(ctx.getMatrices(),x,y,panelW,panelH,20,.35f,.60f,new Color(255,255,255,34));
        Render2D.drawGradientRect(ctx.getMatrices(),x+28,y+2,panelW-56,1.2f,new Color(255,255,255,70),new Color(255,255,255,0),true);

        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(9.2f),"Bind keyboard",x+18,y+14,new Color(244,248,253));
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(5.3f),"Middle-click any module to open this keyboard",x+18,y+32,new Color(113,134,159));
        Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(6.2f),trim(bindingModule.getName(),180,6.2f),x+18,y+49,accentText(accent,230));

        float modeW=142f, modeX=x+panelW-modeW-52f, modeY=y+15f, modeH=28f;
        Bind.Mode mode=bindingModule.getBind()==null?Bind.Mode.TOGGLE:bindingModule.getBind().getMode();
        Render2D.drawRoundedRect(ctx.getMatrices(),modeX,modeY,modeW,modeH,10,new Color(255,255,255,8));
        float p=anim("bind-overlay-mode",mode==Bind.Mode.HOLD?1f:0f,.25f);
        float seg=(modeW-4)/2f;
        float ix=modeX+2+p*seg;
        Render2D.drawBlurredRect(ctx.getMatrices(),ix,modeY+2,seg,modeH-4,8,7,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),22));
        Render2D.drawRoundedRect(ctx.getMatrices(),ix,modeY+2,seg,modeH-4,8,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),42));
        bindOverlayToggleBox=new Box(modeX+2,modeY+2,seg,modeH-4);
        bindOverlayHoldBox=new Box(modeX+2+seg,modeY+2,seg,modeH-4);
        centered(ctx,"Toggle",bindOverlayToggleBox.x,bindOverlayToggleBox.y+8,bindOverlayToggleBox.w,5.5f,mode==Bind.Mode.TOGGLE?Color.WHITE:new Color(148,166,188));
        centered(ctx,"Hold",bindOverlayHoldBox.x,bindOverlayHoldBox.y+8,bindOverlayHoldBox.w,5.5f,mode==Bind.Mode.HOLD?Color.WHITE:new Color(148,166,188));
        bindOverlayCloseBox=new Box(x+panelW-34,y+15,20,20);
        Render2D.drawRoundedRect(ctx.getMatrices(),bindOverlayCloseBox.x,bindOverlayCloseBox.y,20,20,7,new Color(255,255,255,bindOverlayCloseBox.contains(mx,my)?18:8));
        centered(ctx,"X",bindOverlayCloseBox.x,bindOverlayCloseBox.y+6,20,5.5f,new Color(190,204,221));

        float kbX=x+17,kbY=y+76,kbW=panelW-34;
        float mainW=kbW-174f, rightX=kbX+mainW+9f, rightW=165f;
        float rh=27f, rg=5f;
        drawKeyboardRow(ctx,mx,my,kbX,kbY,mainW,rh,new KeySpec[]{
                new KeySpec("ESC",GLFW.GLFW_KEY_ESCAPE,1.35f),new KeySpec("F1",GLFW.GLFW_KEY_F1,1),new KeySpec("F2",GLFW.GLFW_KEY_F2,1),new KeySpec("F3",GLFW.GLFW_KEY_F3,1),new KeySpec("F4",GLFW.GLFW_KEY_F4,1),new KeySpec("F5",GLFW.GLFW_KEY_F5,1),new KeySpec("F6",GLFW.GLFW_KEY_F6,1),new KeySpec("F7",GLFW.GLFW_KEY_F7,1),new KeySpec("F8",GLFW.GLFW_KEY_F8,1),new KeySpec("F9",GLFW.GLFW_KEY_F9,1),new KeySpec("F10",GLFW.GLFW_KEY_F10,1),new KeySpec("F11",GLFW.GLFW_KEY_F11,1),new KeySpec("F12",GLFW.GLFW_KEY_F12,1)
        },accent);
        kbY+=rh+rg;
        drawKeyboardRow(ctx,mx,my,kbX,kbY,mainW,rh,new KeySpec[]{
                new KeySpec("`",GLFW.GLFW_KEY_GRAVE_ACCENT,1),new KeySpec("1",GLFW.GLFW_KEY_1,1),new KeySpec("2",GLFW.GLFW_KEY_2,1),new KeySpec("3",GLFW.GLFW_KEY_3,1),new KeySpec("4",GLFW.GLFW_KEY_4,1),new KeySpec("5",GLFW.GLFW_KEY_5,1),new KeySpec("6",GLFW.GLFW_KEY_6,1),new KeySpec("7",GLFW.GLFW_KEY_7,1),new KeySpec("8",GLFW.GLFW_KEY_8,1),new KeySpec("9",GLFW.GLFW_KEY_9,1),new KeySpec("0",GLFW.GLFW_KEY_0,1),new KeySpec("-",GLFW.GLFW_KEY_MINUS,1),new KeySpec("=",GLFW.GLFW_KEY_EQUAL,1),new KeySpec("BACK",GLFW.GLFW_KEY_BACKSPACE,1.8f)
        },accent);
        kbY+=rh+rg;
        drawKeyboardRow(ctx,mx,my,kbX,kbY,mainW,rh,new KeySpec[]{
                new KeySpec("TAB",GLFW.GLFW_KEY_TAB,1.45f),new KeySpec("Q",GLFW.GLFW_KEY_Q,1),new KeySpec("W",GLFW.GLFW_KEY_W,1),new KeySpec("E",GLFW.GLFW_KEY_E,1),new KeySpec("R",GLFW.GLFW_KEY_R,1),new KeySpec("T",GLFW.GLFW_KEY_T,1),new KeySpec("Y",GLFW.GLFW_KEY_Y,1),new KeySpec("U",GLFW.GLFW_KEY_U,1),new KeySpec("I",GLFW.GLFW_KEY_I,1),new KeySpec("O",GLFW.GLFW_KEY_O,1),new KeySpec("P",GLFW.GLFW_KEY_P,1),new KeySpec("[",GLFW.GLFW_KEY_LEFT_BRACKET,1),new KeySpec("]",GLFW.GLFW_KEY_RIGHT_BRACKET,1),new KeySpec("\\",GLFW.GLFW_KEY_BACKSLASH,1.45f)
        },accent);
        kbY+=rh+rg;
        drawKeyboardRow(ctx,mx,my,kbX,kbY,mainW,rh,new KeySpec[]{
                new KeySpec("CAPS",GLFW.GLFW_KEY_CAPS_LOCK,1.7f),new KeySpec("A",GLFW.GLFW_KEY_A,1),new KeySpec("S",GLFW.GLFW_KEY_S,1),new KeySpec("D",GLFW.GLFW_KEY_D,1),new KeySpec("F",GLFW.GLFW_KEY_F,1),new KeySpec("G",GLFW.GLFW_KEY_G,1),new KeySpec("H",GLFW.GLFW_KEY_H,1),new KeySpec("J",GLFW.GLFW_KEY_J,1),new KeySpec("K",GLFW.GLFW_KEY_K,1),new KeySpec("L",GLFW.GLFW_KEY_L,1),new KeySpec(";",GLFW.GLFW_KEY_SEMICOLON,1),new KeySpec("'",GLFW.GLFW_KEY_APOSTROPHE,1),new KeySpec("ENTER",GLFW.GLFW_KEY_ENTER,2.05f)
        },accent);
        kbY+=rh+rg;
        drawKeyboardRow(ctx,mx,my,kbX,kbY,mainW,rh,new KeySpec[]{
                new KeySpec("SHIFT",GLFW.GLFW_KEY_LEFT_SHIFT,2.2f),new KeySpec("Z",GLFW.GLFW_KEY_Z,1),new KeySpec("X",GLFW.GLFW_KEY_X,1),new KeySpec("C",GLFW.GLFW_KEY_C,1),new KeySpec("V",GLFW.GLFW_KEY_V,1),new KeySpec("B",GLFW.GLFW_KEY_B,1),new KeySpec("N",GLFW.GLFW_KEY_N,1),new KeySpec("M",GLFW.GLFW_KEY_M,1),new KeySpec(",",GLFW.GLFW_KEY_COMMA,1),new KeySpec(".",GLFW.GLFW_KEY_PERIOD,1),new KeySpec("/",GLFW.GLFW_KEY_SLASH,1),new KeySpec("SHIFT",GLFW.GLFW_KEY_RIGHT_SHIFT,2.25f)
        },accent);
        kbY+=rh+rg;
        drawKeyboardRow(ctx,mx,my,kbX,kbY,mainW,rh,new KeySpec[]{
                new KeySpec("CTRL",GLFW.GLFW_KEY_LEFT_CONTROL,1.4f),new KeySpec("WIN",GLFW.GLFW_KEY_LEFT_SUPER,1.25f),new KeySpec("ALT",GLFW.GLFW_KEY_LEFT_ALT,1.3f),new KeySpec("SPACE",GLFW.GLFW_KEY_SPACE,6.1f),new KeySpec("ALT",GLFW.GLFW_KEY_RIGHT_ALT,1.3f),new KeySpec("MENU",GLFW.GLFW_KEY_MENU,1.25f),new KeySpec("CTRL",GLFW.GLFW_KEY_RIGHT_CONTROL,1.4f)
        },accent);

        // Full-size keyboard side clusters: navigation/arrows + numpad.
        float sideGap=7f, navW=(rightW-sideGap)*.46f, numW=rightW-sideGap-navW;
        float nx=rightX, px2=rightX+navW+sideGap, sy0=y+76;
        drawKeyboardRow(ctx,mx,my,nx,sy0,navW,rh,new KeySpec[]{new KeySpec("PRT",GLFW.GLFW_KEY_PRINT_SCREEN,1),new KeySpec("SCR",GLFW.GLFW_KEY_SCROLL_LOCK,1),new KeySpec("PAUSE",GLFW.GLFW_KEY_PAUSE,1)},accent);
        drawKeyboardRow(ctx,mx,my,px2,sy0,numW,rh,new KeySpec[]{new KeySpec("NUM",GLFW.GLFW_KEY_NUM_LOCK,1),new KeySpec("/",GLFW.GLFW_KEY_KP_DIVIDE,1),new KeySpec("*",GLFW.GLFW_KEY_KP_MULTIPLY,1),new KeySpec("-",GLFW.GLFW_KEY_KP_SUBTRACT,1)},accent);
        drawKeyboardRow(ctx,mx,my,nx,sy0+(rh+rg),navW,rh,new KeySpec[]{new KeySpec("INS",GLFW.GLFW_KEY_INSERT,1),new KeySpec("HOME",GLFW.GLFW_KEY_HOME,1)},accent);
        drawKeyboardRow(ctx,mx,my,px2,sy0+(rh+rg),numW,rh,new KeySpec[]{new KeySpec("7",GLFW.GLFW_KEY_KP_7,1),new KeySpec("8",GLFW.GLFW_KEY_KP_8,1),new KeySpec("9",GLFW.GLFW_KEY_KP_9,1)},accent);
        drawKeyboardRow(ctx,mx,my,nx,sy0+2*(rh+rg),navW,rh,new KeySpec[]{new KeySpec("DEL",GLFW.GLFW_KEY_DELETE,1),new KeySpec("END",GLFW.GLFW_KEY_END,1)},accent);
        drawKeyboardRow(ctx,mx,my,px2,sy0+2*(rh+rg),numW,rh,new KeySpec[]{new KeySpec("4",GLFW.GLFW_KEY_KP_4,1),new KeySpec("5",GLFW.GLFW_KEY_KP_5,1),new KeySpec("6",GLFW.GLFW_KEY_KP_6,1)},accent);
        drawKeyboardRow(ctx,mx,my,nx,sy0+3*(rh+rg),navW,rh,new KeySpec[]{new KeySpec("PGUP",GLFW.GLFW_KEY_PAGE_UP,1),new KeySpec("PGDN",GLFW.GLFW_KEY_PAGE_DOWN,1)},accent);
        drawKeyboardRow(ctx,mx,my,px2,sy0+3*(rh+rg),numW,rh,new KeySpec[]{new KeySpec("1",GLFW.GLFW_KEY_KP_1,1),new KeySpec("2",GLFW.GLFW_KEY_KP_2,1),new KeySpec("3",GLFW.GLFW_KEY_KP_3,1)},accent);
        drawKeyboardRow(ctx,mx,my,nx+navW*.33f,sy0+4*(rh+rg),navW*.34f,rh,new KeySpec[]{new KeySpec("UP",GLFW.GLFW_KEY_UP,1)},accent);
        drawKeyboardRow(ctx,mx,my,px2,sy0+4*(rh+rg),numW,rh,new KeySpec[]{new KeySpec("0",GLFW.GLFW_KEY_KP_0,2),new KeySpec(".",GLFW.GLFW_KEY_KP_DECIMAL,1)},accent);
        drawKeyboardRow(ctx,mx,my,nx,sy0+5*(rh+rg),navW,rh,new KeySpec[]{new KeySpec("LEFT",GLFW.GLFW_KEY_LEFT,1),new KeySpec("DOWN",GLFW.GLFW_KEY_DOWN,1),new KeySpec("RIGHT",GLFW.GLFW_KEY_RIGHT,1)},accent);
        drawKeyboardRow(ctx,mx,my,px2,sy0+5*(rh+rg),numW,rh,new KeySpec[]{new KeySpec("+",GLFW.GLFW_KEY_KP_ADD,1),new KeySpec("ENTER",GLFW.GLFW_KEY_KP_ENTER,2)},accent);

        bindOverlayClearBox=new Box(x+panelW-166,y+panelH-34,132,22);
        Render2D.drawRoundedRect(ctx.getMatrices(),bindOverlayClearBox.x,bindOverlayClearBox.y,bindOverlayClearBox.w,bindOverlayClearBox.h,8,new Color(255,255,255,bindOverlayClearBox.contains(mx,my)?15:7));
        centered(ctx,"CLEAR BIND",bindOverlayClearBox.x,bindOverlayClearBox.y+7,bindOverlayClearBox.w,5.0f,new Color(157,175,196));
        String current=bindingModule.getBind()==null||bindingModule.getBind().getKey()<0?"UNBOUND":bindingModule.getBind().toString();
        Render2D.drawFont(ctx.getMatrices(),Fonts.REGULAR.getFont(4.9f),"Current: "+current,x+18,y+panelH-27,new Color(111,132,157));
        ctx.getMatrices().pop();
    }

    private void drawKeyboardRow(DrawContext ctx,int mx,int my,float x,float y,float w,float h,KeySpec[] specs,Color accent){
        float total=0;for(KeySpec s:specs)total+=s.units;float gap=4f;float unit=(w-gap*(specs.length-1))/Math.max(.01f,total);float px=x;
        for(KeySpec spec:specs){
            float kw=unit*spec.units;Box box=new Box(px,y,kw,h);float hv=anim("key:"+spec.key,box.contains(mx,my)?1f:0f,.24f);
            Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,7,new Color(255,255,255,(int)(8+14*hv)));
            Render2D.drawBorder(ctx.getMatrices(),box.x,box.y,box.w,box.h,7,.22f,.40f,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(20+55*hv)));
            centered(ctx,spec.label,box.x,box.y+8,box.w,4.8f,hv>.35f?Color.WHITE:new Color(183,198,216));
            keyHits.add(new KeyHit(spec.label,spec.key,box));px+=kw+gap;
        }
    }

    private void drawMonitorColumn(DrawContext ctx,float x,float y,float w,float h,String title,int count,float time){
        Render2D.drawRoundedRect(ctx.getMatrices(),x,y,w,h,4f,new Color(0,0,0,132));
        Render2D.drawBorder(ctx.getMatrices(),x,y,w,h,4f,.18f,.32f,new Color(255,255,255,12));
        Render2D.drawFont(ctx.getMatrices(),Fonts.BOLD.getFont(5.3f),title,x+11,y+12,new Color(126,147,171));
        if(count>0){String c=String.valueOf(count);float cw=Fonts.MEDIUM.getWidth(c,5.2f);Render2D.drawRoundedRect(ctx.getMatrices(),x+w-cw-20,y+8,cw+12,16,8,new Color(255,255,255,9));Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(5.2f),c,x+w-cw-14,y+13,new Color(194,208,225));}
    }

    private void drawStepControl(DrawContext ctx,float x,float y,float w,float h,String label,Box minus,Box plus,Color accent){
        Render2D.drawRoundedRect(ctx.getMatrices(),x,y,w,h,4,new Color(0,0,0,125));
        Render2D.drawRoundedRect(ctx.getMatrices(),minus.x,minus.y,minus.w,minus.h,4,new Color(255,255,255,8));
        Render2D.drawRoundedRect(ctx.getMatrices(),plus.x,plus.y,plus.w,plus.h,4,new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),35));
        centered(ctx,"-",minus.x,minus.y+(minus.h-6f)*.5f,minus.w,6f,new Color(178,195,214));centered(ctx,"+",plus.x,plus.y+(plus.h-6f)*.5f,plus.w,6f,Color.WHITE);centered(ctx,label,x+16,y+(h-5.2f)*.5f,w-32,5.2f,new Color(198,211,226));
    }

    private void drawActionButton(DrawContext ctx,int mx,int my,Box box,String label,Color accent,boolean enabled){
        float hv=enabled&&box.contains(mx,my)?1f:0f;Color bg=enabled?new Color(0,0,0,(int)(145+28*hv)):new Color(0,0,0,90);
        Render2D.drawRoundedRect(ctx.getMatrices(),box.x,box.y,box.w,box.h,4,bg);
        Render2D.drawBorder(ctx.getMatrices(),box.x,box.y,box.w,box.h,4,.18f,.32f,enabled?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(35+55*hv)):new Color(255,255,255,8));
        centered(ctx,label,box.x,box.y+Math.max(1f,(box.h-5.2f)*.5f),box.w,5.2f,enabled?Color.WHITE:new Color(98,117,140));
    }

    private void drawSwitch(DrawContext ctx,float x,float y,float w,float h,boolean on,Color accent,float alpha){
        drawSwitch(ctx,"switch:"+Math.round(x)+":"+Math.round(y),x,y,w,h,on,accent,alpha);
    }

    private void drawSwitch(DrawContext ctx,String id,float x,float y,float w,float h,boolean on,Color accent,float alpha){
        accent = themeAccentAt(x + w * .5f, y + h * .5f);
        float p=anim("switch:"+id,on?1f:0f,.24f);
        float knob=Math.max(8,h-4);
        int r=(int)(38+(accent.getRed()-38)*p),g=(int)(49+(accent.getGreen()-49)*p),b=(int)(62+(accent.getBlue()-62)*p);
        Render2D.drawRoundedRect(ctx.getMatrices(),x,y,w,h,h/2,new Color(r,g,b,(int)(185*alpha)));
        float kx=x+2+(w-knob-4)*p;
        Render2D.drawRoundedRect(ctx.getMatrices(),kx,y+(h-knob)/2,knob,knob,knob/2,new Color(255,255,255,(int)(245*alpha)));
    }

    private void renderClickRipples(DrawContext ctx) {
        long now = System.currentTimeMillis();
        for (int i = clickRipples.size() - 1; i >= 0; i--) {
            ClickRipple ripple = clickRipples.get(i);
            float t = clamp((now - ripple.startedAt) / 420f, 0f, 1f);
            if (t >= 1f) {
                clickRipples.remove(i);
                continue;
            }
            float radius = 3f + 24f * easeOutCubic(t);
            int alpha = (int)(72f * (1f - t) * (1f - t));
            Color accent = themeAccentAt(ripple.x, ripple.y);
            Render2D.drawBorder(ctx.getMatrices(), ripple.x - radius, ripple.y - radius,
                    radius * 2f, radius * 2f, radius, .25f, .45f,
                    new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),alpha));
        }
    }

    private List<Module> allVisibleModules(){
        List<Module> out=new ArrayList<>();for(Category c:List.of(Category.Render,Category.Combat,Category.Utility))for(Module m:prostovisuals.getInstance().getModuleManager().getModules(c))if(!(m instanceof UI))out.add(m);return out;
    }

    private List<Module> filteredVisibleModules(){
        List<Module> all = allVisibleModules();
        if (search == null || search.isBlank()) return all;
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        for (Module module : all) {
            if (moduleMatchesSearch(module, q)) out.add(module);
        }
        return out;
    }

    /** Search is intentionally language-independent: typing Russian finds English-named modules and vice versa. */
    private boolean moduleMatchesSearch(Module module, String q) {
        if (module == null || q == null || q.isBlank()) return true;
        StringBuilder haystack = new StringBuilder();
        appendSearchTerm(haystack, module.getName());
        appendSearchTerm(haystack, module.getDescription());
        appendSearchTerm(haystack, module.getCategory().name());
        for (Setting<?> setting : module.getSettings()) {
            appendSearchTerm(haystack, setting.getName());
            Object value = setting.getValue();
            if (value instanceof Nameable nameable) appendSearchTerm(haystack, nameable.getName());
            else if (value instanceof Enum<?> e) {
                for (Object constant : e.getDeclaringClass().getEnumConstants()) {
                    if (constant instanceof Nameable n) appendSearchTerm(haystack, n.getName());
                    else appendSearchTerm(haystack, String.valueOf(constant));
                }
            } else if (setting instanceof ListSetting list) {
                for (Setting<?> child : list.getValue()) appendSearchTerm(haystack, child.getName());
            } else if (value != null) appendSearchTerm(haystack, String.valueOf(value));
        }
        return haystack.toString().toLowerCase(Locale.ROOT).contains(q);
    }

    private void appendSearchTerm(StringBuilder out, String key) {
        if (key == null || key.isBlank()) return;
        out.append(' ').append(ClickGuiLanguage.searchAliases(key));
    }

    private String moduleDescription(Module module) {
        if (module == null) return "";
        if (ClickGuiLanguage.isRussian()) {
            return switch (module.getName()) {
                case "NoRender" -> "Скрывает выбранные визуальные эффекты, включая дождь, снег и лишние оверлеи.";
                case "Fullbright" -> "Делает тёмные места полностью видимыми без изменения серверного освещения.";
                case "Crosshair" -> "Заменяет стандартный прицел на настраиваемый вариант с реакцией на цель.";
                case "ViewModel" -> "Настраивает положение предметов в правой и левой руке от первого лица.";
                case "TargetEsp" -> "Подсвечивает выбранную цель маркером, призраком или анимированным кольцом.";
                case "UI" -> "Настройки интерфейса ProstoVisual и поведения ClickGUI.";
                case "Aspect Ratio" -> "Меняет соотношение сторон картинки для более широкого или узкого вида.";
                case "HitSound" -> "Проигрывает выбранный локальный звук при успешном ударе по сущности.";
                case "CustomHitBox" -> "Рисует хитбоксы сущностей с отдельными настройками игроков, мобов, заливки и контура.";
                case "Wings" -> "Отдельный рендер крыльев Wyvern: бабочка, классические или оба вида вместе.";
                case "ChinaHat" -> "Рисует настраиваемую 3D-шляпу с вариантами ProstoVisual и Wyvern.";
                case "JumpCircle" -> "Создаёт плавно появляющийся и исчезающий круг при прыжке или приземлении.";
                case "ClientSound" -> "Настраивает звуки интерфейса ProstoVisual и их громкость.";
                case "TotemCounter" -> "Показывает количество тотемов рядом с прицелом.";
                case "Ambience" -> "Меняет только визуальные время и туман, не затрагивая загрузку чанков.";
                case "Particles" -> "Использует оригинальный рендер частиц Wyvern с выбранными триггерами и текстурами.";
                case "DamageParticles" -> "Создаёт отдельные частицы в точке удара с собственной физикой и временем жизни.";
                case "SwingAnimation" -> "Меняет анимации взмаха и положения предмета от первого лица.";
                case "ItemPhysic" -> "Делает отображение выпавших предметов в мире более естественным.";
                case "FriendHelper" -> "Позволяет добавлять и удалять игроков из друзей средней кнопкой мыши.";
                case "Predictions" -> "Показывает траектории и точки приземления снарядов.";
                case "BlockOverlay" -> "Заменяет стандартную обводку выбранного блока на настраиваемую.";
                case "BetterMinecraft" -> "Добавляет более плавные переходы списка игроков и камеры от третьего лица.";
                case "Zoom" -> "Плавно приближает камеру с настройкой силы и скорости.";
                case "Trails" -> "Рисует цветной след за игроком во время движения.";
                case "HitBubbles" -> "Создаёт анимированные эффекты в точке удара.";
                case "HitColor" -> "Подкрашивает получившую урон сущность цветом текущей темы.";
                case "NeonSteps" -> "Создаёт неоновые следы или вспышки в местах шагов игрока.";
                case "Pulsive" -> "Создаёт неоновую волну по поверхности после настоящего приземления.";
                case "CustomSky" -> "Заменяет обычное небо на оптимизированные анимированные режимы шейдеров.";
                case "MotionBlur" -> "Добавляет настраиваемое размытие при движении камеры.";
                case "SeeInvisible" -> "Подсвечивает подходящих невидимых игроков и показывает их имя.";
                case "DiscordRPC" -> "Показывает статус ProstoVisual в Discord Rich Presence.";
                case "Cape" -> "Рисует фирменный плащ Among Us на игроке.";
                default -> "Визуальная функция ProstoVisual.";
            };
        }
        return switch (module.getName()) {
            case "NoRender" -> "Hides selected overlays and visual effects such as rain, snow and unnecessary screen effects.";
            case "Fullbright" -> "Keeps dark areas fully visible without changing the server-side light level.";
            case "Crosshair" -> "Replaces the vanilla crosshair with adjustable size, gap and dynamic target feedback.";
            case "ViewModel" -> "Moves the main-hand and off-hand item models independently in first person.";
            case "TargetEsp" -> "Highlights your selected target with marker, ghost or animated ring effects.";
            case "UI" -> "Controls ProstoVisual interface and ClickGUI behaviour.";
            case "Aspect Ratio" -> "Overrides the game aspect ratio for a wider or narrower presentation.";
            case "HitSound" -> "Plays a selected local sound every time you successfully hit an entity.";
            case "CustomHitBox" -> "Draws configurable entity hitboxes with independent player and mob options.";
            case "Wings" -> "Uses Wyvern's original wing renderer with Butterfly, Classic or Combined modes.";
            case "ChinaHat" -> "Renders a configurable 3D hat with both ProstoVisual and Wyvern styles.";
            case "JumpCircle" -> "Spawns a smoothly animated themed circle after a jump or landing.";
            case "ClientSound" -> "Changes ProstoVisual interface sounds and their playback volume.";
            case "TotemCounter" -> "Shows your total Totems of Undying beside the crosshair.";
            case "Ambience" -> "Changes visual time and fog only; it never changes chunk or render distance.";
            case "Particles" -> "Controls Wyvern's original particle renderer with selected textures and triggers.";
            case "DamageParticles" -> "Bursts dedicated particles at hit points with their own physics and lifetime.";
            case "SwingAnimation" -> "Reworks first-person swing and equip transforms with presets and custom transforms.";
            case "ItemPhysic" -> "Gives dropped item models a more natural physical presentation in the world.";
            case "FriendHelper" -> "Middle-click players to add or remove them from your friends list.";
            case "Predictions" -> "Predicts trajectories and landing points for projectiles.";
            case "BlockOverlay" -> "Replaces the block selection outline with a configurable themed overlay.";
            case "BetterMinecraft" -> "Adds smoother tab-list transitions and smoother third-person camera zoom.";
            case "Zoom" -> "Smoothly narrows the field of view with adjustable zoom strength and speed.";
            case "Trails" -> "Draws a theme-colored ribbon trail behind your player while you move.";
            case "HitBubbles" -> "Spawns animated themed effects at the exact point where you hit an entity.";
            case "HitColor" -> "Tints hurt entities with the current theme color and adjustable opacity.";
            case "NeonSteps" -> "Places glowing neon footprints or flashes at real foot contacts.";
            case "Pulsive" -> "Creates a terrain-following neon shockwave after a real landing.";
            case "CustomSky" -> "Replaces the vanilla sky with optimized animated shader sky modes.";
            case "MotionBlur" -> "Adds configurable motion blur while the camera is turning.";
            case "SeeInvisible" -> "Reveals qualifying invisible players with a themed marker and name.";
            case "DiscordRPC" -> "Publishes your ProstoVisual game status to Discord Rich Presence.";
            case "Cape" -> "Renders the custom Among Us cape on your player.";
            default -> {
                String d = module.getDescription();
                if (d == null || d.isBlank() || d.startsWith("module.")) yield "ProstoVisual visual feature.";
                yield d;
            }
        };
    }

    private String categoryLabel(Category category){return category==Category.Render?"Visuals":category==Category.Combat?"Combat":"Utility";}
    private Identifier categoryIcon(Category category){return category==Category.Combat?ICON_COMBAT:category==Category.Utility?ICON_UTILITY:ICON_RENDER;}

    public static Color referenceAccentColor(){
        return ThemeManager.getInstance().getRenderedAccentColor();
    }

    public static Color referenceAccentColorAt(float x){
        if (MinecraftClient.getInstance().currentScreen instanceof OneClientClickGui gui) {
            return gui.themeAccentAt(Float.isNaN(x) ? gui.shellX + gui.shellW * .5f : x,
                    gui.shellY + gui.shellH * .5f);
        }
        return ThemeManager.getInstance().getRenderedAccentColor();
    }

    public static Color referenceAccentColorAt(float x, float y){
        return ThemeManager.getInstance().getRenderedAccentColorAt(x, y);
    }

    private ThemeManager.Theme displayedTheme() {
        ThemeManager.Theme target = themes.getTransitionTarget();
        return target == null ? themes.getCurrentTheme() : target;
    }

    private void startThemeTransition(ThemeManager.Theme target, float originX) {
        if (target == null) return;
        themes.startRadialTransition(target,
                shellX + shellW * .5f,
                shellY + shellH * .5f,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
    }

    private Color themeAccentAt(float x) {
        return themeAccentAt(x, shellY + shellH * .5f);
    }

    private Color themeAccentAt(float x, float y) {
        return themes.getRenderedAccentColorAt(x, y);
    }

    private float animateTowards(float current, float target, float speedPerSecond) {
        float factor = 1f - (float)Math.exp(-speedPerSecond * frameDelta);
        float result = current + (target - current) * factor;
        return Math.abs(target - result) < .001f ? target : result;
    }

    private float anim(String key,float target,float speed){float current=hover.getOrDefault(key,target);float factor=1f-(float)Math.pow(1f-clamp(speed,0f,.99f),frameDelta*60f);current+=(target-current)*factor;if(Math.abs(target-current)<.001f)current=target;hover.put(key,current);return current;}
    private static float easeOutCubic(float t){t=clamp(t,0,1);float u=1-t;return 1-u*u*u;}
    private static float smoothstep(float t){t=clamp(t,0,1);return t*t*(3f-2f*t);}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
    private static Color accentText(Color c,int a){return new Color(c.getRed(),c.getGreen(),c.getBlue(),Math.max(0,Math.min(255,a)));}
    private static Color lerpColor(Color from,Color to,float t){t=clamp(t,0,1);return new Color((int)(from.getRed()+(to.getRed()-from.getRed())*t),(int)(from.getGreen()+(to.getGreen()-from.getGreen())*t),(int)(from.getBlue()+(to.getBlue()-from.getBlue())*t),(int)(from.getAlpha()+(to.getAlpha()-from.getAlpha())*t));}

    private void centered(DrawContext ctx,String text,float x,float y,float w,float size,Color color){float tw=Fonts.MEDIUM.getWidth(text,size);Render2D.drawFont(ctx.getMatrices(),Fonts.MEDIUM.getFont(size),text,x+(w-tw)/2,y,color);}
    private void drawClippedMarquee(DrawContext ctx, String text, float x, float y, float maxW, float size, boolean hovered, Color color) {
        if (text == null || maxW <= 1f) return;
        float width = Fonts.MEDIUM.getWidth(text, size);
        float offset = 0f;
        if (hovered && width > maxW) {
            float travel = width - maxW + 10f;
            float phase = (System.currentTimeMillis() % 3600L) / 3600f;
            float ping = .5f - .5f * (float)Math.cos(phase * Math.PI * 2.0);
            offset = travel * ping;
        }
        ctx.enableScissor((int)x, (int)(y-2f), (int)(x+maxW), (int)(y+size+5f));
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(size), text, x-offset, y, color);
        ctx.disableScissor();
    }

    private String trim(String s,float max,float size){if(s==null)return "";if(Fonts.MEDIUM.getWidth(s,size)<=max)return s;String t=s;while(t.length()>2&&Fonts.MEDIUM.getWidth(t+"...",size)>max)t=t.substring(0,t.length()-1);return t+"...";}

    private void switchPage(Page next){if(page==next)return;page=next;moduleInspectorOpen=false;pageChangedAt=System.currentTimeMillis();search="";searchFocused=false;moduleScroll=moduleScrollTarget=0;bindScroll=bindScrollTarget=0;cosmeticScroll=cosmeticScrollTarget=0;settingsScroll=settingsScrollTarget=0;bindingModule=null;if(next==Page.MONITORS&&monitorSources.isEmpty())refreshMonitorSources();}

    private void refreshMonitorSources(){
        if(scanningSources)return;scanningSources=true;CaptureSourceRegistry.refreshAsync().whenComplete((found,error)->{if(mc==null)return;mc.execute(()->{scanningSources=false;if(error==null&&found!=null)monitorSources=new ArrayList<>(found);selectedSource=Math.max(0,Math.min(selectedSource,Math.max(0,monitorSources.size()-1)));sourceScroll=Math.max(0,Math.min(sourceScroll,Math.max(0,monitorSources.size()-monitorVisibleRows)));});});
    }

    @Override
    public boolean mouseClicked(double mouseX,double mouseY,int button){
        if (closing) return true;
        if (button == 0 && mouseX >= shellX && mouseX <= shellX + shellW
                && mouseY >= shellY && mouseY <= shellY + shellH) {
            clickRipples.add(new ClickRipple((float)mouseX, (float)mouseY, System.currentTimeMillis()));
        }
        if(bindingModule!=null){
            if(button!=0)return true;
            if(bindOverlayCloseBox!=null&&bindOverlayCloseBox.contains(mouseX,mouseY)){bindingModule=null;return true;}
            Bind old=bindingModule.getBind();Bind.Mode mode=old==null?Bind.Mode.TOGGLE:old.getMode();
            if(bindOverlayToggleBox!=null&&bindOverlayToggleBox.contains(mouseX,mouseY)){bindingModule.setBind(new Bind(old==null?-1:old.getKey(),old!=null&&old.isMouse(),Bind.Mode.TOGGLE));return true;}
            if(bindOverlayHoldBox!=null&&bindOverlayHoldBox.contains(mouseX,mouseY)){bindingModule.setBind(new Bind(old==null?-1:old.getKey(),old!=null&&old.isMouse(),Bind.Mode.HOLD));return true;}
            if(bindOverlayClearBox!=null&&bindOverlayClearBox.contains(mouseX,mouseY)){bindingModule.setBind(new Bind(-1,false,mode));bindingModule=null;return true;}
            for(KeyHit hit:keyHits)if(hit.box.contains(mouseX,mouseY)){bindingModule.setBind(new Bind(hit.key,false,mode));bindingModule=null;return true;}
            return true;
        }

        if(button==0&&closeBox!=null&&closeBox.contains(mouseX,mouseY)){close();return true;}
        if(button==0&&settingsBox!=null&&settingsBox.contains(mouseX,mouseY)){
            globalSettingsOpen=!globalSettingsOpen;searchFocused=false;return true;
        }

        if(globalSettingsOpen){
            if(button==0&&customSatValBox!=null&&customSatValBox.contains(mouseX,mouseY)){draggingCustomSatVal=true;draggingCustomHue=false;updateCustomColor(mouseX,mouseY);return true;}
            if(button==0&&customHueBox!=null&&customHueBox.contains(mouseX,mouseY)){draggingCustomHue=true;draggingCustomSatVal=false;updateCustomColor(mouseX,mouseY);return true;}
            if(button==0&&languageBox!=null&&languageBox.contains(mouseX,mouseY)){ClickGuiLanguage.toggle();return true;}
            for(ThemeHit hit:themeHits)if(button==0&&hit.box.contains(mouseX,mouseY)){
                startThemeTransition(hit.theme,hit.box.x+hit.box.w/2f);return true;
            }
            if(globalSettingsBox!=null&&globalSettingsBox.contains(mouseX,mouseY))return true;
            globalSettingsOpen=false;
            return true;
        }

        if(searchBox!=null&&searchBox.contains(mouseX,mouseY)){searchFocused=true;return true;}else searchFocused=false;

        for (NavHit hit : navHits) if (button == 0 && hit.box.contains(mouseX,mouseY)) {
            switchPage(hit.page);
            return true;
        }
        for(CategoryHit hit:categoryHits)if(hit.box.contains(mouseX,mouseY)&&button==0){page=Page.MODULES;moduleCategory=hit.category;moduleInspectorOpen=false;pageChangedAt=System.currentTimeMillis();search="";moduleScroll=moduleScrollTarget=0;settingsScroll=settingsScrollTarget=0;selectFirstVisibleModule();return true;}

        if(page==Page.MODULES){
            if(inspectorCloseBox!=null&&inspectorCloseBox.contains(mouseX,mouseY)&&button==0){moduleInspectorOpen=false;settingsScroll=settingsScrollTarget=0;return true;}
            if(inspectorToggleBox!=null&&inspectorToggleBox.contains(mouseX,mouseY)&&button==0&&selectedModule!=null){selectedModule.getModule().toggle();return true;}
            for(ModuleHit hit:moduleHits){
                if(hit.settings.contains(mouseX,mouseY)&&button==0&&!hit.component.getComponents().isEmpty()){selectModule(hit.component);moduleInspectorOpen=true;return true;}
                if(hit.toggle.contains(mouseX,mouseY)&&button==0){hit.component.getModule().toggle();return true;}
                if(hit.card.contains(mouseX,mouseY)){
                    if(button==GLFW.GLFW_MOUSE_BUTTON_MIDDLE){bindingModule=hit.component.getModule();bindOverlayOpenedAt=System.currentTimeMillis();return true;}
                    if(button==1&&!hit.component.getComponents().isEmpty()){selectModule(hit.component);moduleInspectorOpen=true;return true;}
                    if(button==0){hit.component.getModule().toggle();return true;}
                }
            }
            if(selectedModule!=null && moduleInspectorOpen && inspectorPanelW>0f &&
                    mouseX>=inspectorPanelX && mouseX<=inspectorPanelX+inspectorPanelW &&
                    mouseY>=inspectorPanelY && mouseY<=inspectorPanelY+inspectorPanelH){
                selectedModule.mouseClickedExternal(mouseX,mouseY,button);
                return true;
            }
        }else if(page==Page.BINDS){
            for(BindHit hit:bindHits)if(hit.row.contains(mouseX,mouseY)&&button==0){selectedBindModule=hit.module;return true;}
            if(selectedBindModule!=null){
                if(bindKeyBox!=null&&bindKeyBox.contains(mouseX,mouseY)&&button==0){bindingModule=selectedBindModule;return true;}
                if(bindToggleBox!=null&&bindToggleBox.contains(mouseX,mouseY)&&button==0){Bind b=selectedBindModule.getBind();selectedBindModule.setBind(new Bind(b==null?-1:b.getKey(),b!=null&&b.isMouse(),Bind.Mode.TOGGLE));return true;}
                if(bindHoldBox!=null&&bindHoldBox.contains(mouseX,mouseY)&&button==0){Bind b=selectedBindModule.getBind();selectedBindModule.setBind(new Bind(b==null?-1:b.getKey(),b!=null&&b.isMouse(),Bind.Mode.HOLD));return true;}
                if(bindClearBox!=null&&bindClearBox.contains(mouseX,mouseY)&&button==0){Bind b=selectedBindModule.getBind();selectedBindModule.setBind(new Bind(-1,false,b==null?Bind.Mode.TOGGLE:b.getMode()));return true;}
            }
        }else if(page==Page.THEMES){
            if(button==0&&customSatValBox!=null&&customSatValBox.contains(mouseX,mouseY)){draggingCustomSatVal=true;draggingCustomHue=false;updateCustomColor(mouseX,mouseY);return true;}
            if(button==0&&customHueBox!=null&&customHueBox.contains(mouseX,mouseY)){draggingCustomHue=true;draggingCustomSatVal=false;updateCustomColor(mouseX,mouseY);return true;}
            for(ThemeHit hit:themeHits)if(hit.box.contains(mouseX,mouseY)&&button==0){startThemeTransition(hit.theme,hit.box.x+hit.box.w/2f);return true;}
        }else if(page==Page.COSMETICS){
            for(CosmeticTabHit hit:cosmeticTabHits)if(hit.box.contains(mouseX,mouseY)&&button==0){cosmeticTab=hit.tab;cosmeticScroll=cosmeticScrollTarget=0;return true;}
            CosmeticsManager manager=prostovisuals.getInstance().getCosmeticsManager();
            if(cosmeticClearBox!=null&&cosmeticClearBox.contains(mouseX,mouseY)&&button==0&&manager!=null){manager.clear();return true;}
            for(PetModeHit hit:petModeHits)if(hit.box.contains(mouseX,mouseY)&&button==0){if(manager!=null)manager.setPetBehavior(hit.behavior);return true;}
            for(CosmeticHit hit:cosmeticHits)if(hit.box.contains(mouseX,mouseY)&&button==0){if(manager!=null)manager.toggle(hit.entry);return true;}
        }else if(page==Page.MONITORS){
            MonitorsController controller=MonitorsController.getInstance();SpatialDisplayManager manager=SpatialDisplayManager.getInstance();
            if(monitorToggleBox!=null&&monitorToggleBox.contains(mouseX,mouseY)&&button==0){controller.setEnabled(!controller.isEnabled());return true;}
            if(fpsMinusBox!=null&&fpsMinusBox.contains(mouseX,mouseY)&&button==0){controller.setCaptureFps(controller.getCaptureFps()-30);return true;}
            if(fpsPlusBox!=null&&fpsPlusBox.contains(mouseX,mouseY)&&button==0){controller.setCaptureFps(controller.getCaptureFps()+30);return true;}
            if(opacityMinusBox!=null&&opacityMinusBox.contains(mouseX,mouseY)&&button==0){controller.setOpacity(controller.getOpacity()-.05f);return true;}
            if(opacityPlusBox!=null&&opacityPlusBox.contains(mouseX,mouseY)&&button==0){controller.setOpacity(controller.getOpacity()+.05f);return true;}
            for(MonitorHit hit:monitorHits)if(hit.box.contains(mouseX,mouseY)&&button==0){selectedMonitor=hit.index;return true;}
            for(SourceHit hit:sourceHits)if(hit.box.contains(mouseX,mouseY)&&button==0){selectedSource=hit.index;return true;}
            if(addMonitorBox!=null&&addMonitorBox.contains(mouseX,mouseY)&&button==0&&!monitorSources.isEmpty()){CaptureSource src=CaptureSourceRegistry.reopen(monitorSources.get(selectedSource));if(src!=null){manager.addMonitor(src,4.5f);selectedMonitor=manager.getMonitors().size()-1;controller.setEnabled(true);}return true;}
            if(setSourceBox!=null&&setSourceBox.contains(mouseX,mouseY)&&button==0&&selectedMonitor>=0&&selectedMonitor<manager.getMonitors().size()&&!monitorSources.isEmpty()){CaptureSource src=CaptureSourceRegistry.reopen(monitorSources.get(selectedSource));if(src!=null)manager.getMonitors().get(selectedMonitor).setSource(src);return true;}
            if(removeMonitorBox!=null&&removeMonitorBox.contains(mouseX,mouseY)&&button==0&&selectedMonitor>=0){manager.remove(selectedMonitor);selectedMonitor=Math.min(selectedMonitor,manager.getMonitors().size()-1);return true;}
            if(refreshSourcesBox!=null&&refreshSourcesBox.contains(mouseX,mouseY)&&button==0){refreshMonitorSources();return true;}
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX,double mouseY,int button){
        draggingCustomSatVal=false;draggingCustomHue=false;
        if(page==Page.MODULES&&selectedModule!=null&&moduleInspectorOpen&&inspectorPanelW>0f&&
                mouseX>=inspectorPanelX&&mouseX<=inspectorPanelX+inspectorPanelW&&
                mouseY>=inspectorPanelY&&mouseY<=inspectorPanelY+inspectorPanelH)
            selectedModule.mouseReleasedExternal(mouseX,mouseY,button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX,double mouseY,int button,double dx,double dy){
        if((globalSettingsOpen||page==Page.THEMES)&&(draggingCustomSatVal||draggingCustomHue)){updateCustomColor(mouseX,mouseY);return true;}
        return super.mouseDragged(mouseX,mouseY,button,dx,dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(bindingModule!=null)return true;
        if(globalSettingsOpen)return true;
        float amount=(float)(-vertical*26f);
        if(page==Page.MODULES){
            if(moduleInspectorOpen && inspectorPanelW>0f &&
                    mouseX>=inspectorPanelX && mouseX<=inspectorPanelX+inspectorPanelW &&
                    mouseY>=inspectorPanelY && mouseY<=inspectorPanelY+inspectorPanelH) {
                settingsScrollTarget=clamp(settingsScrollTarget+amount,0,settingsMaxScroll);
                return true;
            }
            if(mouseX>=contentX&&mouseX<=contentX+contentW&&mouseY>=contentY+27f&&mouseY<=contentY+contentH){
                moduleScrollTarget=clamp(moduleScrollTarget+amount,0,moduleMaxScroll);
                return true;
            }
        }else if(page==Page.BINDS){bindScrollTarget=clamp(bindScrollTarget+amount,0,bindMaxScroll);return true;}
        else if(page==Page.COSMETICS){cosmeticScrollTarget=clamp(cosmeticScrollTarget+amount,0,cosmeticMaxScroll);return true;}
        else if(page==Page.MONITORS){sourceScroll=Math.max(0,Math.min(sourceScroll+(vertical<0?1:-1),Math.max(0,monitorSources.size()-monitorVisibleRows)));return true;}
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode,int scanCode,int modifiers){
        if(bindingModule!=null){Bind old=bindingModule.getBind();Bind.Mode mode=old==null?Bind.Mode.TOGGLE:old.getMode();if(keyCode==GLFW.GLFW_KEY_ESCAPE){bindingModule=null;return true;}if(keyCode==GLFW.GLFW_KEY_DELETE||keyCode==GLFW.GLFW_KEY_BACKSPACE)bindingModule.setBind(new Bind(-1,false,mode));else bindingModule.setBind(new Bind(keyCode,false,mode));bindingModule=null;return true;}
        if(globalSettingsOpen&&keyCode==GLFW.GLFW_KEY_ESCAPE){globalSettingsOpen=false;draggingCustomSatVal=draggingCustomHue=false;return true;}
        if(searchFocused){if(keyCode==GLFW.GLFW_KEY_ESCAPE){searchFocused=false;return true;}if(keyCode==GLFW.GLFW_KEY_BACKSPACE&&!search.isEmpty()){search=search.substring(0,search.length()-1);pageChangedAt=System.currentTimeMillis();resetPageScroll();selectFirstVisibleModule();return true;}}
        if(keyCode==GLFW.GLFW_KEY_ESCAPE){close();return true;}
        if(page==Page.MODULES&&selectedModule!=null)selectedModule.keyPressedExternal(keyCode,scanCode,modifiers);
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode,int scanCode,int modifiers){if(page==Page.MODULES&&selectedModule!=null)selectedModule.keyReleasedExternal(keyCode,scanCode,modifiers);return true;}

    @Override
    public boolean charTyped(char chr,int modifiers){
        if(searchFocused&&!Character.isISOControl(chr)){search+=chr;pageChangedAt=System.currentTimeMillis();resetPageScroll();selectFirstVisibleModule();return true;}
        if(page==Page.MODULES&&selectedModule!=null)selectedModule.charTypedExternal(chr,modifiers);
        return true;
    }

    private void resetPageScroll(){moduleScroll=moduleScrollTarget=0;bindScroll=bindScrollTarget=0;cosmeticScroll=cosmeticScrollTarget=0;}
}

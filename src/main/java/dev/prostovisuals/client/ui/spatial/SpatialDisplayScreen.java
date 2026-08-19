package dev.prostovisuals.client.ui.spatial;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.spatial.CaptureSource;
import dev.prostovisuals.client.spatial.CaptureSourceRegistry;
import dev.prostovisuals.client.spatial.SpatialDisplayManager;
import dev.prostovisuals.client.spatial.SpatialMonitor;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class SpatialDisplayScreen extends Screen {
    private final Screen parent;
    private final SpatialDisplayManager manager = SpatialDisplayManager.getInstance();
    private final ThemeManager themeManager = ThemeManager.getInstance();
    private List<CaptureSource> sources = new ArrayList<>();
    private int selectedMonitor = -1;
    private int selectedSource = 0;
    private int sourceScroll = 0;
    private String status = "Select any open app window and create a spatial screen";
    private long statusUntil;
    private boolean scanning;

    private float panelX, panelY, panelW, panelH;
    private float leftX, rightX, listY, listW, listH;

    public SpatialDisplayScreen(Screen parent) {
        super(Text.literal("Spatial Display"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // F10 must open instantly: draw from the shared cache and refresh Windows sources off-thread.
        sources = CaptureSourceRegistry.cached();
        selectedSource = clamp(selectedSource, 0, Math.max(0, sources.size() - 1));
        if (!manager.getMonitors().isEmpty() && selectedMonitor < 0) selectedMonitor = 0;
        if (sources.isEmpty() || CaptureSourceRegistry.cacheAgeMs() > 2500L) refreshSources(false);
    }

    private void refreshSources(boolean notify) {
        if (scanning) {
            if (notify) flash("Already scanning open windows…");
            return;
        }
        scanning = true;
        if (notify || sources.isEmpty()) flash("Scanning open windows…");
        CaptureSourceRegistry.refreshAsync().whenComplete((found, error) -> {
            if (client == null) return;
            client.execute(() -> {
                scanning = false;
                if (error != null) {
                    flash("Window scan failed");
                    return;
                }
                sources = found == null ? new ArrayList<>() : new ArrayList<>(found);
                selectedSource = clamp(selectedSource, 0, Math.max(0, sources.size() - 1));
                sourceScroll = clamp(sourceScroll, 0, Math.max(0, sources.size() - 8));
                if (notify || sources.isEmpty())
                    flash(sources.isEmpty() ? "No capturable windows found" : "Found " + sources.size() + " sources");
            });
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        panelW = Math.min(660f, width - 34f);
        panelH = Math.min(390f, height - 34f);
        panelX = (width - panelW) * .5f;
        panelY = (height - panelH) * .5f;

        LiquidGlassUtil.captureFrame();
        float t = (float)((System.nanoTime() / 1_000_000_000.0) % 10000.0);
        LiquidGlassUtil.drawLiquidGlass(ctx, panelX, panelY, panelW, panelH, t,
                5.0f, 0.16f, 0.0016f, 18f, 0.67f, 1.04f);
        Render2D.drawRoundedRect(ctx.getMatrices(), panelX + 1, panelY + 1, panelW - 2, panelH - 2, 17,
                new Color(7, 9, 13, 206));

        Color accent = themeManager.getCurrentTheme().getAccentColor();
        Render2D.drawRoundedRect(ctx.getMatrices(), panelX + 18, panelY + 16, 4, 30, 2,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 235));
        Render2D.drawFont(ctx.getMatrices(), Fonts.SEMIBOLD.getFont(12f), "Spatial Display",
                panelX + 31, panelY + 16, new Color(248, 249, 252, 255));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(7.5f), "Vision-style windows inside the world",
                panelX + 31, panelY + 34, new Color(145, 151, 164, 235));

        leftX = panelX + 18;
        rightX = panelX + panelW * .5f + 7;
        listY = panelY + 78;
        listW = panelW * .5f - 32;
        listH = panelH - 148;

        section(ctx, leftX, panelY + 60, "MONITORS", manager.getMonitors().size(), accent);
        section(ctx, rightX, panelY + 60, "OPEN WINDOWS", sources.size(), accent);
        drawMonitorList(ctx, mouseX, mouseY, accent);
        drawSourceList(ctx, mouseX, mouseY, accent);

        float by = panelY + panelH - 56;
        float gap = 8;
        float bw = (panelW - 36 - gap * 3) / 4f;
        button(ctx, mouseX, mouseY, leftX, by, bw, 30, "+ Add screen", accent, !sources.isEmpty());
        button(ctx, mouseX, mouseY, leftX + bw + gap, by, bw, 30, "Set source", accent,
                selectedMonitor >= 0 && selectedMonitor < manager.getMonitors().size() && !sources.isEmpty());
        button(ctx, mouseX, mouseY, leftX + (bw + gap) * 2, by, bw, 30, "Remove", accent,
                selectedMonitor >= 0 && selectedMonitor < manager.getMonitors().size());
        button(ctx, mouseX, mouseY, leftX + (bw + gap) * 3, by, bw, 30, scanning ? "Scanning…" : "Refresh", accent, !scanning);

        String footer = System.currentTimeMillis() < statusUntil ? status :
                "Crosshair = cursor  •  LMB/RMB  •  wheel  •  click to type  •  Esc releases keyboard";
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(7.2f), footer,
                panelX + 20, panelY + panelH - 18, new Color(135, 142, 156, 235));
    }

    private void section(DrawContext ctx, float x, float y, String title, int count, Color accent) {
        Render2D.drawFont(ctx.getMatrices(), Fonts.SEMIBOLD.getFont(7.2f), title,
                x, y, new Color(191, 196, 207, 235));
        String badge = String.valueOf(count);
        float tw = Fonts.MEDIUM.getWidth(badge, 6.8f);
        Render2D.drawRoundedRect(ctx.getMatrices(), x + listW - tw - 14, y - 2, tw + 12, 15, 7,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 36));
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(6.8f), badge,
                x + listW - tw - 8, y + 2, new Color(221, 225, 233, 250));
    }

    private void drawMonitorList(DrawContext ctx, int mx, int my, Color accent) {
        Render2D.drawRoundedRect(ctx.getMatrices(), leftX, listY, listW, listH, 12, new Color(12, 15, 21, 188));
        List<SpatialMonitor> mons = manager.getMonitors();
        if (mons.isEmpty()) {
            emptyState(ctx, leftX, listY, listW, listH, "No screens yet", "Choose a window and press Add screen");
            return;
        }
        float rowH = 28;
        for (int i = 0; i < Math.min(8, mons.size()); i++) {
            float y = listY + 8 + i * rowH;
            boolean sel = selectedMonitor == i;
            boolean hover = inside(mx, my, leftX + 7, y, listW - 14, 23);
            if (sel || hover) {
                Render2D.drawRoundedRect(ctx.getMatrices(), leftX + 7, y, listW - 14, 23, 8,
                        sel ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45) : new Color(255,255,255,10));
            }
            SpatialMonitor m = mons.get(i);
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(7.3f), trim("Screen " + (i + 1) + "  •  " + m.getName(), 36),
                    leftX + 15, y + 8, new Color(238, 240, 245, 248));
        }
    }

    private void drawSourceList(DrawContext ctx, int mx, int my, Color accent) {
        Render2D.drawRoundedRect(ctx.getMatrices(), rightX, listY, listW, listH, 12, new Color(12, 15, 21, 188));
        if (sources.isEmpty()) {
            emptyState(ctx, rightX, listY, listW, listH, scanning ? "Scanning…" : "Nothing found", scanning ? "Finding Discord, Telegram, browsers and other windows" : "Press Refresh after opening an app");
            return;
        }
        int visible = 8;
        int start = clamp(sourceScroll, 0, Math.max(0, sources.size() - visible));
        float rowH = 28;
        for (int j = 0; j < Math.min(visible, sources.size() - start); j++) {
            int i = start + j;
            float y = listY + 8 + j * rowH;
            boolean sel = selectedSource == i;
            boolean hover = inside(mx, my, rightX + 7, y, listW - 14, 23);
            if (sel || hover) {
                Render2D.drawRoundedRect(ctx.getMatrices(), rightX + 7, y, listW - 14, 23, 8,
                        sel ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 45) : new Color(255,255,255,10));
            }
            CaptureSource s = sources.get(i);
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(7.2f), trim(s.name(), 38),
                    rightX + 15, y + 8, new Color(238, 240, 245, 248));
        }
    }

    private void emptyState(DrawContext ctx, float x, float y, float w, float h, String title, String sub) {
        float tw = Fonts.MEDIUM.getWidth(title, 8f);
        float sw = Fonts.REGULAR.getWidth(sub, 6.7f);
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(8f), title,
                x + (w - tw) * .5f, y + h * .5f - 10, new Color(207, 211, 220, 245));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.7f), sub,
                x + (w - sw) * .5f, y + h * .5f + 5, new Color(118, 125, 140, 230));
    }

    private void button(DrawContext ctx, int mx, int my, float x, float y, float w, float h,
                        String text, Color accent, boolean enabled) {
        boolean hover = enabled && inside(mx, my, x, y, w, h);
        Color bg = !enabled ? new Color(255,255,255,7)
                : hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 58)
                : new Color(18, 22, 30, 230);
        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 9, bg);
        if (enabled) Render2D.drawBorder(ctx.getMatrices(), x, y, w, h, 9, .6f, .6f,
                hover ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 145) : new Color(255,255,255,24));
        float tw = Fonts.MEDIUM.getWidth(text, 7.1f);
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(7.1f), text,
                x + (w - tw) * .5f, y + 11, enabled ? new Color(244,246,250,250) : new Color(110,116,128,160));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (inside(mouseX, mouseY, leftX, listY, listW, listH)) {
            int i = (int)((mouseY - (listY + 8)) / 28f);
            if (i >= 0 && i < Math.min(8, manager.getMonitors().size())) {
                selectedMonitor = i;
                flash("Selected screen " + (i + 1));
            }
            return true;
        }
        if (inside(mouseX, mouseY, rightX, listY, listW, listH)) {
            int start = clamp(sourceScroll, 0, Math.max(0, sources.size() - 8));
            int j = (int)((mouseY - (listY + 8)) / 28f);
            int i = start + j;
            if (j >= 0 && j < 8 && i >= 0 && i < sources.size()) {
                selectedSource = i;
                flash("Selected: " + trim(sources.get(i).name(), 42));
            }
            return true;
        }

        float by = panelY + panelH - 56;
        float gap = 8;
        float bw = (panelW - 36 - gap * 3) / 4f;

        if (inside(mouseX, mouseY, leftX, by, bw, 30)) {
            if (sources.isEmpty()) { flash("Open an app and press Refresh"); return true; }
            CaptureSource src = sources.get(selectedSource);
            CaptureSource owned = CaptureSourceRegistry.reopen(src);
            if (owned == null) { flash("Could not open selected source"); return true; }
            manager.addMonitor(owned, 3.4f);
            selectedMonitor = manager.getMonitors().size() - 1;
            flash("Spatial screen created");
            return true;
        }
        if (inside(mouseX, mouseY, leftX + bw + gap, by, bw, 30)) {
            if (selectedMonitor < 0 || selectedMonitor >= manager.getMonitors().size() || sources.isEmpty()) {
                flash("Select a screen and a source first"); return true;
            }
            CaptureSource owned = CaptureSourceRegistry.reopen(sources.get(selectedSource));
            if (owned == null) { flash("Could not open selected source"); return true; }
            manager.getMonitors().get(selectedMonitor).setSource(owned);
            flash("Source changed");
            return true;
        }
        if (inside(mouseX, mouseY, leftX + (bw + gap) * 2, by, bw, 30)) {
            if (selectedMonitor >= 0 && selectedMonitor < manager.getMonitors().size()) {
                manager.remove(selectedMonitor);
                selectedMonitor = Math.min(selectedMonitor, manager.getMonitors().size() - 1);
                flash("Screen removed");
            } else flash("Select a screen first");
            return true;
        }
        if (inside(mouseX, mouseY, leftX + (bw + gap) * 3, by, bw, 30)) {
            refreshSources(true);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inside(mouseX, mouseY, rightX, listY, listW, listH) && sources.size() > 8) {
            sourceScroll = clamp(sourceScroll - (int)Math.signum(vertical), 0, Math.max(0, sources.size() - 8));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    private void flash(String s) { status = s; statusUntil = System.currentTimeMillis() + 2300L; }
    private static boolean inside(double mx,double my,float x,float y,float w,float h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static String trim(String s, int n){return s == null ? "" : (s.length() <= n ? s : s.substring(0, Math.max(0,n-1)) + "…");}

    @Override
    public void close() { if (client != null) client.setScreen(parent); }
}

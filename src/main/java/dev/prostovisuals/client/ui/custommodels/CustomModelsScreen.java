package dev.prostovisuals.client.ui.custommodels;

import dev.prostovisuals.client.custommodels.CosmeticEntry;
import dev.prostovisuals.client.custommodels.CosmeticPreviewCache;
import dev.prostovisuals.client.custommodels.FiguraCosmeticsEngine;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.modules.impl.render.CustomModels;
import dev.prostovisuals.prostovisuals;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Dedicated ProstoVisual cosmetics picker. */
public final class CustomModelsScreen extends Screen {
    private final Screen parent;
    private float panelX, panelY, panelW, panelH;
    private float contentX, contentY, contentW, contentH;
    private float cardW, cardH, gap;
    private int columns;
    private double scroll;
    private double maxScroll;
    private final List<Card> cards = new ArrayList<>();
    private CategoryTab currentTab = CategoryTab.MODELS;
    private final List<TabHitbox> tabHitboxes = new ArrayList<>();

    public CustomModelsScreen(Screen parent) {
        super(Text.literal("Customize"));
        this.parent = parent;
        FiguraCosmeticsEngine.ensureInstalled();
        rebuildCards();
    }

    private void rebuildCards() {
        cards.clear();
        Set<String> seenNames = new HashSet<>();
        for (CosmeticEntry entry : FiguraCosmeticsEngine.getCatalog()) {
            if (!currentTab.matches(entry.kind())) continue;
            String key = normalizeName(entry.name());
            if (!seenNames.add(key)) continue;
            cards.add(Card.cosmetic(entry));
        }

        scroll = 0.0;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);

        panelW = Math.min(760f, width - 30f);
        panelH = Math.min(500f, height - 30f);
        panelX = (width - panelW) * 0.5f;
        panelY = (height - panelH) * 0.5f;

        LiquidGlassUtil.captureFrame();
        float t = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
        LiquidGlassUtil.drawLiquidGlass(ctx, panelX, panelY, panelW, panelH, t,
                5.0f, 0.14f, 0.0014f, 18f, 0.64f, 1.02f);
        Render2D.drawRoundedRect(ctx.getMatrices(), panelX + 1, panelY + 1, panelW - 2, panelH - 2, 17,
                new Color(7, 9, 13, 218));

        Color accent = ThemeManager.getInstance().getRenderedAccentColor();
        Render2D.drawRoundedRect(ctx.getMatrices(), panelX + 18, panelY + 16, 4, 30, 2,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 235));
        Render2D.drawFont(ctx.getMatrices(), Fonts.SEMIBOLD.getFont(12f), "Customize",
                panelX + 31, panelY + 16, new Color(248, 249, 252, 255));

        CustomModels module = prostovisuals.getInstance().getModuleManager().getModule(CustomModels.class);
        String selected = module == null || !module.isToggled() ? "Default player" : selectedCosmeticName(module);
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(7.2f),
                "3D cosmetics • .bbmodel + Lua animations/physics    Selected: " + selected,
                panelX + 31, panelY + 34, new Color(145, 151, 164, 235));

        tabHitboxes.clear();
        float tabX = panelX + 18f;
        float tabY = panelY + 58f;
        float tabGap = 8f;
        float tabH = 24f;
        float tabW = (panelW - 36f - tabGap * (CategoryTab.values().length - 1)) / CategoryTab.values().length;
        for (CategoryTab tab : CategoryTab.values()) {
            int idx = tab.ordinal();
            float x = tabX + idx * (tabW + tabGap);
            boolean active = currentTab == tab;
            boolean hover = inside(mouseX, mouseY, x, tabY, tabW, tabH);
            Color tabBg = active
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 54)
                    : hover ? new Color(255,255,255,18) : new Color(10,13,18,170);
            Render2D.drawRoundedRect(ctx.getMatrices(), x, tabY, tabW, tabH, 8, tabBg);
            Render2D.drawBorder(ctx.getMatrices(), x, tabY, tabW, tabH, 8, .6f, .6f,
                    active ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 170)
                            : new Color(255,255,255,28));
            float tw = Fonts.MEDIUM.getWidth(tab.label, 7f);
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(7f), tab.label,
                    x + (tabW - tw) * .5f, tabY + 8f,
                    active ? new Color(245,248,255,255) : new Color(163,170,184,240));
            tabHitboxes.add(new TabHitbox(tab, x, tabY, tabW, tabH));
        }

        contentX = panelX + 18f;
        contentY = panelY + 92f;
        contentW = panelW - 36f;
        contentH = panelH - 126f;
        gap = 10f;
        columns = panelW >= 690 ? 4 : panelW >= 500 ? 3 : 2;
        cardW = (contentW - gap * (columns - 1)) / columns;
        cardH = 132f;

        int rows = (cards.size() + columns - 1) / columns;
        double totalH = rows * cardH + Math.max(0, rows - 1) * gap;
        maxScroll = Math.max(0.0, totalH - contentH);
        scroll = Math.max(0.0, Math.min(scroll, maxScroll));

        ctx.enableScissor((int)contentX, (int)contentY, (int)(contentX + contentW), (int)(contentY + contentH));
        for (int i = 0; i < cards.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            float x = contentX + col * (cardW + gap);
            float y = (float)(contentY + row * (cardH + gap) - scroll);
            if (y + cardH < contentY - 2 || y > contentY + contentH + 2) continue;
            Card card = cards.get(i);
            boolean selectedCard = isSelected(module, card);
            drawCard(ctx, mouseX, mouseY, x, y, cardW, cardH, card, selectedCard, accent);
        }
        ctx.disableScissor();

        String status = FiguraCosmeticsEngine.isFiguraAvailable()
                ? "Figura engine ready"
                : "Figura engine will be loaded from the bundled 1.21.4 dependency";
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.8f), status,
                panelX + 20, panelY + panelH - 24, new Color(120, 128, 143, 220));
        String hint = "Esc to close  •  Mouse wheel to scroll  •  Bind: Customize → Menu";
        float hw = Fonts.REGULAR.getWidth(hint, 6.8f);
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(6.8f), hint,
                panelX + panelW - 20 - hw, panelY + panelH - 24, new Color(130, 137, 151, 235));
    }

    private String selectedCosmeticName(CustomModels module) {
        if (module == null || !module.isFiguraSelected()) return "Unknown";
        CosmeticEntry e = FiguraCosmeticsEngine.findByRelativePath(module.getSelectedFiguraPath());
        return e == null ? module.getSelectedFiguraPath() : e.name();
    }

    private boolean isSelected(CustomModels module, Card card) {
        if (card.type == CardType.WYVERN) {
            try {
                return wtf.wyvern.client.modules.impl.render.Cosmetics.INSTANCE.isCosmeticEnabled(card.wyvernKey);
            } catch (Throwable ignored) { return false; }
        }
        if (module == null || !module.isToggled()) return false;
        return module.isSelected(card.entry);
    }

    private void drawCard(DrawContext ctx, int mx, int my, float x, float y, float w, float h,
                          Card card, boolean selected, Color accent) {
        boolean hover = inside(mx, my, x, y, w, h) && inside(mx, my, contentX, contentY, contentW, contentH);
        Color bg = selected
                ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 38)
                : hover ? new Color(255, 255, 255, 15) : new Color(12, 15, 21, 194);
        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 12, bg);
        Render2D.drawBorder(ctx.getMatrices(), x, y, w, h, 12, .65f, .65f,
                selected ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180)
                        : new Color(255, 255, 255, hover ? 45 : 22));

        float previewX = x + 8;
        float previewY = y + 8;
        float previewW = w - 16;
        float previewH = 78;
        Render2D.drawRoundedRect(ctx.getMatrices(), previewX, previewY, previewW, previewH, 9,
                new Color(3, 5, 8, 130));

        if (card.type == CardType.COSMETIC) {
            Identifier image = CosmeticPreviewCache.get(card.entry);
            if (image != null) {
                float size = Math.min(previewW, previewH) - 8;
                Render2D.drawTexture(ctx.getMatrices(), previewX + (previewW-size)/2f, previewY+4, size, size, 8,
                        image, new Color(255,255,255,255));
            } else drawFallback(ctx, previewX, previewY, previewW, previewH, card);
        } else {
            drawFallback(ctx, previewX, previewY, previewW, previewH, card);
        }

        String name = trim(card.name, w - 18, 7.6f);
        Render2D.drawFont(ctx.getMatrices(), Fonts.SEMIBOLD.getFont(7.6f), name,
                x + 9, y + 92, new Color(242, 244, 249, 252));
        String type = selected ? "SELECTED" : card.badge;
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(6.1f), type,
                x + 9, y + 109, selected ? new Color(225, 236, 255, 252) : new Color(148, 156, 171, 235));
    }

    private void drawFallback(DrawContext ctx, float x, float y, float w, float h, Card card) {
        float cx = x + w * .5f;
        Render2D.drawRoundedRect(ctx.getMatrices(), cx - 18, y + 12, 36, 36, 8, new Color(76, 83, 99, 190));
        Render2D.drawRoundedRect(ctx.getMatrices(), cx - 14, y + 48, 28, 23, 6, new Color(58, 64, 78, 190));
    }

    private String trim(String s, float max, float size) {
        if (s == null) return "";
        if (Fonts.SEMIBOLD.getWidth(s, size) <= max) return s;
        String ell = "...";
        int n = s.length();
        while (n > 0 && Fonts.SEMIBOLD.getWidth(s.substring(0,n)+ell, size) > max) n--;
        return n <= 0 ? ell : s.substring(0,n)+ell;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (TabHitbox hitbox : tabHitboxes) {
                if (inside(mouseX, mouseY, hitbox.x, hitbox.y, hitbox.w, hitbox.h)) {
                    if (currentTab != hitbox.tab) {
                        currentTab = hitbox.tab;
                        rebuildCards();
                    }
                    return true;
                }
            }
        }
        if (button == 0 && inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            CustomModels module = prostovisuals.getInstance().getModuleManager().getModule(CustomModels.class);
            if (module != null) {
                for (int i=0;i<cards.size();i++) {
                    int row=i/columns, col=i%columns;
                    float x=contentX+col*(cardW+gap);
                    float y=(float)(contentY+row*(cardH+gap)-scroll);
                    if (!inside(mouseX,mouseY,x,y,cardW,cardH)) continue;
                    Card c=cards.get(i);
                    if (c.type == CardType.WYVERN) {
                        try {
                            wtf.wyvern.client.modules.impl.render.Cosmetics nativeCosmetics = wtf.wyvern.client.modules.impl.render.Cosmetics.INSTANCE;
                            if (!nativeCosmetics.isEnabled()) nativeCosmetics.setToggled(true);
                            nativeCosmetics.setCosmeticEnabled(c.wyvernKey, !nativeCosmetics.isCosmeticEnabled(c.wyvernKey));
                        } catch (Throwable ignored) {}
                    } else if (module.isSelected(c.entry)) {
                        module.selectDefaultPlayer();
                    } else {
                        module.selectCosmetic(c.entry);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (inside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            scroll = Math.max(0.0, Math.min(maxScroll, scroll - vertical * 34.0));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public void close() { if (client != null) client.setScreen(parent); }
    @Override public boolean shouldPause() { return false; }

    private static boolean inside(double mx,double my,float x,float y,float w,float h) {
        return mx>=x && mx<=x+w && my>=y && my<=y+h;
    }

    private enum CategoryTab {
        MODELS("Models"),
        HEADS("Heads"),
        HATS("Hats"),
        WEAPONS("Weapons"),
        PETS("Pets");

        final String label;
        CategoryTab(String label) { this.label = label; }

        boolean matches(CosmeticEntry.Kind kind) {
            return switch (this) {
                case MODELS -> kind == CosmeticEntry.Kind.MODEL;
                case HEADS -> kind == CosmeticEntry.Kind.HEAD;
                case HATS -> kind == CosmeticEntry.Kind.HAT;
                case WEAPONS -> kind == CosmeticEntry.Kind.WEAPON;
                case PETS -> kind == CosmeticEntry.Kind.PET;
            };
        }
    }

    private static final class TabHitbox {
        final CategoryTab tab;
        final float x, y, w, h;
        TabHitbox(CategoryTab tab, float x, float y, float w, float h) {
            this.tab = tab; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    private static String normalizeName(String name) {
        if (name == null) return "";
        return name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private enum CardType { COSMETIC, WYVERN }
    private static final class Card {
        final CardType type;
        final String name;
        final String badge;
        final CosmeticEntry entry;
        final String wyvernKey;

        private Card(CardType type, String name, String badge, CosmeticEntry entry, String wyvernKey) {
            this.type = type; this.name = name; this.badge = badge; this.entry = entry; this.wyvernKey = wyvernKey;
        }
        static Card cosmetic(CosmeticEntry e) { return new Card(CardType.COSMETIC, e.name(), e.kind().name(), e, null); }
        static Card wyvern(String name, String key, String badge) { return new Card(CardType.WYVERN, name, badge, null, key); }
    }
}

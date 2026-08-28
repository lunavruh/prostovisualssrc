package dev.prostovisuals.client.ui.holyworld;

import dev.prostovisuals.client.managers.HolyWorldEventsManager;
import dev.prostovisuals.client.managers.HolyWorldEventsOverlayManager;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Standalone HolyWorld event browser with the old search/type/rarity workflow. */
public final class HolyWorldEventsScreen extends Screen {
    private final Screen parent;
    private final HolyWorldEventsOverlayManager controller;

    private boolean rebinding;
    private boolean searchFocused;
    private String search = "";
    private String typeFilter = "Все";
    private String rarityFilter = "Все";
    private float scroll;
    private float scrollTarget;
    private float maxScroll;
    private float open;

    private float panelX, panelY, panelW, panelH;
    private Box searchBox, typeBox, rarityBox, bindBox, refreshBox;

    public HolyWorldEventsScreen(Screen parent, HolyWorldEventsOverlayManager controller) {
        super(Text.literal("HolyWorld Events"));
        this.parent = parent;
        this.controller = controller;
    }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        open += (1f - open) * .20f;
        scroll += (scrollTarget - scroll) * .22f;

        panelW = Math.min(620f, width - 34f);
        panelH = Math.min(390f, height - 34f);
        panelX = (width - panelW) * .5f;
        panelY = (height - panelH) * .5f;
        float scale = .95f + .05f * open;
        float cx = panelX + panelW / 2f, cy = panelY + panelH / 2f;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.getMatrices().translate(-cx, -cy, 0);

        Color accent = ThemeManager.getInstance().getRenderedAccentColor();
        Render2D.drawRoundedRect(ctx.getMatrices(), panelX, panelY, panelW, panelH, 10, new Color(8, 9, 12, 248));
        Render2D.drawBorder(ctx.getMatrices(), panelX, panelY, panelW, panelH, 10, .2f, .35f, new Color(255,255,255,26));
        Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(9f), "HolyWorld Events", panelX + 16, panelY + 13, Color.WHITE);
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.1f),
                ClickGuiLanguage.isRussian() ? "Поиск и фильтры активных событий" : "Search and filter active events",
                panelX + 16, panelY + 29, new Color(151, 162, 177));

        bindBox = new Box(panelX + panelW - 134, panelY + 11, 118, 25);
        drawButton(ctx, bindBox, rebinding ? (ClickGuiLanguage.isRussian() ? "Нажмите клавишу..." : "Press a key...") : bindLabel(), mouseX, mouseY, accent);

        float controlsY = panelY + 48;
        float controlsW = panelW - 32;
        searchBox = new Box(panelX + 16, controlsY, controlsW - 226, 25);
        typeBox = new Box(searchBox.x + searchBox.w + 8, controlsY, 92, 25);
        rarityBox = new Box(typeBox.x + typeBox.w + 8, controlsY, 118, 25);

        Render2D.drawRoundedRect(ctx.getMatrices(), searchBox.x, searchBox.y, searchBox.w, searchBox.h, 7,
                new Color(255,255,255, searchFocused ? 17 : 9));
        Render2D.drawBorder(ctx.getMatrices(), searchBox.x, searchBox.y, searchBox.w, searchBox.h, 7, .2f, .35f,
                searchFocused ? new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),110) : new Color(255,255,255,20));
        String searchText = search.isEmpty() ? (ClickGuiLanguage.isRussian() ? "Поиск событий..." : "Search events...") : search;
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5.5f), searchText, searchBox.x + 9, searchBox.y + 8,
                search.isEmpty() ? new Color(142,151,164) : new Color(236,241,247));

        drawFilter(ctx, typeBox, (ClickGuiLanguage.isRussian() ? "Тип: " : "Type: ") + prettyType(typeFilter), mouseX, mouseY, accent);
        drawFilter(ctx, rarityBox, (ClickGuiLanguage.isRussian() ? "Редкость: " : "Rarity: ") + prettyRarity(rarityFilter), mouseX, mouseY, accent);

        List<HolyWorldEventsManager.EventInfo> visible = filteredEvents();
        float listX = panelX + 16;
        float listY = controlsY + 35;
        float listW = panelW - 32;
        float listH = panelH - 126;
        float cardGap = 7f;
        float cardW = (listW - cardGap) / 2f;
        float cardH = 54f;
        int rows = (visible.size() + 1) / 2;
        maxScroll = Math.max(0f, rows * (cardH + cardGap) - listH);
        scrollTarget = clamp(scrollTarget, 0, maxScroll);
        scroll = clamp(scroll, 0, maxScroll);

        Render2D.startScissor(ctx, listX, listY, listW, listH);
        HolyWorldEventsManager manager = HolyWorldEventsManager.getInstance();
        if (manager.isLoading() && visible.isEmpty()) {
            drawEmpty(ctx, listX, listY, ClickGuiLanguage.isRussian() ? "Загрузка событий..." : "Loading events...");
        } else if (visible.isEmpty()) {
            drawEmpty(ctx, listX, listY, manager.getError().isBlank()
                    ? (ClickGuiLanguage.isRussian() ? "Ничего не найдено" : "No events found")
                    : (ClickGuiLanguage.isRussian() ? "API HolyWorld недоступен" : "HolyWorld API unavailable"));
        } else {
            for (int i = 0; i < visible.size(); i++) {
                int col = i & 1;
                int row = i / 2;
                float x = listX + col * (cardW + cardGap);
                float y = listY + row * (cardH + cardGap) - scroll;
                if (y + cardH < listY || y > listY + listH) continue;
                renderEventCard(ctx, visible.get(i), x, y, cardW, cardH, mouseX, mouseY, accent);
            }
        }
        Render2D.stopScissor(ctx);

        refreshBox = new Box(panelX + 16, panelY + panelH - 31, 92, 20);
        drawButton(ctx, refreshBox, ClickGuiLanguage.isRussian() ? "Обновить" : "Refresh", mouseX, mouseY, accent);
        String count = visible.size() + " / " + manager.getEvents().size();
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(4.8f), count, panelX + panelW - 48, panelY + panelH - 24, new Color(142,153,168));

        ctx.getMatrices().pop();
    }

    private void renderEventCard(DrawContext ctx, HolyWorldEventsManager.EventInfo event, float x, float y, float w, float h,
                                 int mouseX, int mouseY, Color accent) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        Render2D.drawRoundedRect(ctx.getMatrices(), x, y, w, h, 7, new Color(255,255,255, hover ? 12 : 7));
        Color rarity = rarityColor(event.rarity(), accent);
        Render2D.drawRoundedRect(ctx.getMatrices(), x + 8, y + 8, 3, h - 16, 1.5f, new Color(rarity.getRed(), rarity.getGreen(), rarity.getBlue(), 230));
        String title = event.displayName().isBlank() ? event.id() : event.displayName();
        Render2D.drawFont(ctx.getMatrices(), Fonts.BOLD.getFont(6.2f), title, x + 17, y + 8, new Color(242,246,250));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(5f), event.serverName(), x + 17, y + 23, new Color(190,199,211));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(4.6f), prettyType(event.serverType()) + "  •  " + prettyRarity(event.rarity()), x + 17, y + 37, rarity);
    }

    private List<HolyWorldEventsManager.EventInfo> filteredEvents() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        List<HolyWorldEventsManager.EventInfo> out = new ArrayList<>();
        for (HolyWorldEventsManager.EventInfo event : HolyWorldEventsManager.getInstance().getEvents()) {
            if (!q.isEmpty()) {
                String haystack = (event.displayName()+" "+event.id()+" "+event.serverName()+" "+event.serverType()+" "+event.rarity()).toLowerCase(Locale.ROOT);
                if (!haystack.contains(q)) continue;
            }
            if (!"Все".equals(typeFilter) && !typeFilter.equalsIgnoreCase(event.serverType())) continue;
            if (!"Все".equals(rarityFilter) && !rarityFilter.equalsIgnoreCase(event.rarity())) continue;
            out.add(event);
        }
        return out;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && bindBox != null && bindBox.contains(mx,my)) { rebinding = true; searchFocused = false; return true; }
        if (button == 0 && searchBox != null && searchBox.contains(mx,my)) { searchFocused = true; return true; }
        searchFocused = false;
        if (button == 0 && typeBox != null && typeBox.contains(mx,my)) { typeFilter = nextType(typeFilter); resetScroll(); return true; }
        if (button == 0 && rarityBox != null && rarityBox.contains(mx,my)) { rarityFilter = nextRarity(rarityFilter); resetScroll(); return true; }
        if (button == 0 && refreshBox != null && refreshBox.contains(mx,my)) { HolyWorldEventsManager.getInstance().refresh(); return true; }
        return super.mouseClicked(mx,my,button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (rebinding) {
            if (keyCode != GLFW.GLFW_KEY_ESCAPE) controller.setBind(keyCode);
            rebinding = false;
            return true;
        }
        if (searchFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !search.isEmpty()) { search = search.substring(0, search.length()-1); resetScroll(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocused && !Character.isISOControl(chr)) { search += chr; resetScroll(); return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget = clamp(scrollTarget - (float)verticalAmount * 24f, 0, maxScroll);
        return true;
    }

    private void resetScroll() { scroll = scrollTarget = 0; }

    private String bindLabel() {
        String key = GLFW.glfwGetKeyName(controller.getBind(), 0);
        if (key == null) {
            int f = controller.getBind() - GLFW.GLFW_KEY_F1 + 1;
            key = f >= 1 && f <= 25 ? "F" + f : Integer.toString(controller.getBind());
        }
        return (ClickGuiLanguage.isRussian() ? "Бинд: " : "Bind: ") + key.toUpperCase(Locale.ROOT);
    }

    private void drawFilter(DrawContext ctx, Box box, String text, int mx, int my, Color accent) {
        boolean hover = box.contains(mx,my);
        Render2D.drawRoundedRect(ctx.getMatrices(), box.x, box.y, box.w, box.h, 7, new Color(255,255,255,hover?13:8));
        Render2D.drawBorder(ctx.getMatrices(), box.x, box.y, box.w, box.h, 7, .2f, .35f, new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),hover?75:28));
        Render2D.drawFont(ctx.getMatrices(), Fonts.REGULAR.getFont(4.8f), text, box.x+8, box.y+8, new Color(224,231,239));
    }

    private void drawButton(DrawContext ctx, Box box, String text, int mx, int my, Color accent) {
        boolean hover = box.contains(mx,my);
        Render2D.drawRoundedRect(ctx.getMatrices(), box.x, box.y, box.w, box.h, 7, new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),hover?35:18));
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(4.9f), text, box.x+9, box.y+7, new Color(229,235,242));
    }

    private void drawEmpty(DrawContext ctx, float x, float y, String text) {
        Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(6f), text, x+4, y+12, new Color(188,198,211));
    }

    private static String nextType(String current) {
        String[] values = {"Все", "Соло", "Дуо", "Трио", "Клан"};
        for (int i=0;i<values.length;i++) if (values[i].equals(current)) return values[(i+1)%values.length];
        return values[0];
    }

    private static String nextRarity(String current) {
        String[] values = {"Все", "Обычный", "Редкий", "Эпический", "Легендарный", "Роскошный"};
        for (int i=0;i<values.length;i++) if (values[i].equalsIgnoreCase(current)) return values[(i+1)%values.length];
        return values[0];
    }

    private static String prettyType(String v) {
        if (!ClickGuiLanguage.isRussian()) return switch (v) {
            case "Все" -> "All"; case "Соло" -> "Solo"; case "Дуо" -> "Duo"; case "Трио" -> "Trio"; case "Клан" -> "Clan"; default -> v;
        };
        return v;
    }

    private static String prettyRarity(String v) {
        if (!ClickGuiLanguage.isRussian()) return switch (v) {
            case "Все" -> "All"; case "Обычный" -> "Common"; case "Редкий" -> "Rare"; case "Эпический" -> "Epic"; case "Легендарный" -> "Legendary"; case "Роскошный" -> "Luxurious"; default -> v;
        };
        return v;
    }

    private static Color rarityColor(String rarity, Color fallback) {
        String r = rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
        if (r.contains("легенд") || r.contains("legend")) return new Color(255,180,55);
        if (r.contains("эпич") || r.contains("epic")) return new Color(180,95,255);
        if (r.contains("редк") || r.contains("rare")) return new Color(65,160,255);
        if (r.contains("роскош") || r.contains("lux")) return new Color(255,105,210);
        return fallback;
    }

    private static boolean inside(double mx,double my,float x,float y,float w,float h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    private static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}

    @Override public void close(){ if(client!=null) client.setScreen(parent); }

    private record Box(float x,float y,float w,float h){ boolean contains(double mx,double my){return inside(mx,my,x,y,w,h);} }
}

package wtf.wyvern.client.modules.impl.render;

import com.darkmagician6.eventapi.EventTarget;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import lombok.Generated;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.util.math.Vector2f;
import wtf.wyvern.Wyvern;
import wtf.wyvern.base.events.impl.input.EventMouse;
import wtf.wyvern.base.events.impl.input.EventSetScreen;
import wtf.wyvern.base.events.impl.other.EventWindowResize;
import wtf.wyvern.base.events.impl.player.EventUpdate;
import wtf.wyvern.base.events.impl.render.EventHudRender;
import wtf.wyvern.client.hud.elements.component.ArrayListComponent;
import wtf.wyvern.client.hud.elements.component.InformationComponent;
import wtf.wyvern.client.hud.elements.component.KeybindsComponent;
import wtf.wyvern.client.hud.elements.component.NotifyComponent;
import wtf.wyvern.client.hud.elements.component.PotionsComponent;
import wtf.wyvern.client.hud.elements.component.TargetHudComponent;
import wtf.wyvern.client.hud.elements.component.WatermarkComponent;
import wtf.wyvern.client.hud.elements.draggable.DraggableHudElement;
import wtf.wyvern.client.modules.api.Category;
import wtf.wyvern.client.modules.api.Module;
import wtf.wyvern.client.modules.api.ModuleAnnotation;
import wtf.wyvern.client.modules.api.setting.impl.MultiBooleanSetting;
import wtf.wyvern.utility.math.MathUtil;
import wtf.wyvern.utility.render.display.Render2DUtil;
import wtf.wyvern.utility.render.display.base.BorderRadius;
import wtf.wyvern.utility.render.display.base.CustomDrawContext;
import wtf.wyvern.utility.render.display.base.color.ColorRGBA;
import wtf.wyvern.utility.render.display.base.GuiUtil;

@ModuleAnnotation(
        name = "HUD",
        category = Category.RENDER,
        description = "Интерфейс Клиента"
)
public final class Interface extends Module {
    public static final Interface INSTANCE = new Interface();
    private final wtf.wyvern.client.modules.api.setting.impl.ModeSetting hudMode = new wtf.wyvern.client.modules.api.setting.impl.ModeSetting("HUD Режим", "Hud1", "Hud2");
    private final MultiBooleanSetting elementsSetting = MultiBooleanSetting.create("Элементы", List.of("Ватермарка", "Эффекты", "Уведомления", "Информация", "Бинды", "Таргет худ", "Список модулей"));
    private final List<DraggableHudElement> elementsHud1 = new ArrayList();
    private final List<DraggableHudElement> elementsHud2 = new ArrayList();
    private DraggableHudElement draggingElement = null;
    private float dragOffsetX;
    private float dragOffsetY;
    private DraggableHudElement resizingElement;
    private float resizeStartScale;
    private float resizeTargetScale;
    private float resizeAnchorX;
    private float resizeAnchorY;
    private float resizeVectorX;
    private float resizeVectorY;
    private int resizeCorner = -1;
    private long resizeLastNanos;
    long init = 0L;

    private Interface() {

        this.elementsHud1.add(new WatermarkComponent("Watermark", 0.0F, 0.0F, 960.0F, 495.5F, 10.0F, 10.0F, DraggableHudElement.Align.TOP_LEFT, false));
        this.elementsHud1.add(new PotionsComponent("Potions", 0.0F, 0.0F, 960.0F, 495.5F, 119.15234F, 73.0F, DraggableHudElement.Align.TOP_LEFT, false));
        NotifyComponent notifyComponent = new NotifyComponent("Notify", 0.0F, 0.0F, 960.0F, 495.5F, 0.0F, 50.0F, DraggableHudElement.Align.CENTER);
        this.elementsHud1.add(notifyComponent);
        Wyvern.getInstance().getNotifyManager().setNotifyComponent(notifyComponent);
        this.elementsHud1.add(new InformationComponent("Information", 0.0F, 0.0F, 960.0F, 495.5F, 10.0F, 41.5F, DraggableHudElement.Align.TOP_LEFT));
        this.elementsHud1.add(new KeybindsComponent("Keybinds", 349.0F, 0.0F, 960.0F, 495.5F, -122.0F, 73.0F, DraggableHudElement.Align.TOP_RIGHT, false));
        this.elementsHud1.add(new TargetHudComponent("TargetHUD", 166.5F, 128.5F, 960.0F, 495.5F, 0.0F, 31.75F, DraggableHudElement.Align.CENTER));
        this.elementsHud1.add(new ArrayListComponent("ArrayList", 0.0F, 0.0F, 960.0F, 495.5F, -10.0F, 10.0F, DraggableHudElement.Align.TOP_RIGHT));

        this.elementsHud2.add(new WatermarkComponent("WatermarkV2", 0.0F, 0.0F, 960.0F, 495.5F, 5.0F, 5.0F, DraggableHudElement.Align.TOP_LEFT, true));
        this.elementsHud2.add(new KeybindsComponent("KeybindsV2", 0.0F, 0.0F, 960.0F, 495.5F, 5.0F, 30.0F, DraggableHudElement.Align.TOP_LEFT, true));
        this.elementsHud2.add(new PotionsComponent("PotionsV2", 0.0F, 0.0F, 960.0F, 495.5F, 5.0F, 100.0F, DraggableHudElement.Align.TOP_LEFT, true));
        this.elementsHud2.add(new TargetHudComponent("TargetHUDV2", 0.0F, 0.0F, 960.0F, 495.5F, 0.0F, 0.0F, DraggableHudElement.Align.CENTER, false));
        this.elementsHud2.add(new ArrayListComponent("ArrayListV2", 0.0F, 0.0F, 960.0F, 495.5F, -5.0F, 5.0F, DraggableHudElement.Align.TOP_RIGHT));
    }

    private List<DraggableHudElement> getActiveElements() {
        return hudMode.is("Hud1") ? elementsHud1 : elementsHud2;
    }

    public boolean isLiquidHudEnabled() {
        return false;
    }

    public void onEnable() {
        this.init = System.currentTimeMillis();
        super.onEnable();
    }

    public JsonObject save() {
        JsonObject object = super.save();
        JsonObject propertiesObject = new JsonObject();

        for (DraggableHudElement element : this.elementsHud1) {
            propertiesObject.add(element.getName(), element.save());
        }
        for (DraggableHudElement element : this.elementsHud2) {
            propertiesObject.add(element.getName(), element.save());
        }

        object.add("HudElements", propertiesObject);
        return object;
    }

    public void load(JsonObject object) {
        super.load(object);
        if (object.has("HudElements") && object.get("HudElements").isJsonObject()) {
            JsonObject propertiesObject = object.getAsJsonObject("HudElements");

            for (DraggableHudElement element : this.elementsHud1) {
                String key = element.getName();
                if (propertiesObject.has(key) && propertiesObject.get(key).isJsonObject()) {
                    element.load(propertiesObject.getAsJsonObject(key));
                }
            }
            for (DraggableHudElement element : this.elementsHud2) {
                String key = element.getName();
                if (propertiesObject.has(key) && propertiesObject.get(key).isJsonObject()) {
                    element.load(propertiesObject.getAsJsonObject(key));
                }
            }
        }

    }

    private void addElement(DraggableHudElement element) {

    }

    @EventTarget
    public void onRender(EventHudRender event) {
        if (!(mc.currentScreen instanceof ChatScreen)) {
            if (this.draggingElement != null) {
                this.draggingElement.release();
                this.draggingElement = null;
            }
            if (this.resizingElement != null) {
                this.resizingElement.release();
                this.resizingElement = null;
                this.resizeCorner = -1;
            }
        }

        CustomDrawContext ctx = event.getContext();
        float width = (float)mc.getWindow().getWidth() / this.getCustomScale();
        float height = (float)mc.getWindow().getHeight() / this.getCustomScale();
        if (!mc.options.hudHidden) {
            List<DraggableHudElement> elements = getActiveElements();
            Iterator var5 = elements.iterator();

            while(var5.hasNext()) {
                DraggableHudElement element = (DraggableHudElement)var5.next();
                if (this.shouldRender(element)) {
                    try {
                        float scale = element.getScale();
                        ctx.getMatrices().push();
                        ctx.getMatrices().translate(element.getX(), element.getY(), 0.0F);
                        ctx.getMatrices().scale(scale, scale, 1.0F);
                        ctx.getMatrices().translate(-element.getX(), -element.getY(), 0.0F);
                        element.render(ctx);
                        ctx.getMatrices().pop();
                    } catch (Exception var10) {
                        try { ctx.getMatrices().pop(); } catch (Throwable ignored) {}
                    }

                    if (mc.currentScreen instanceof ChatScreen) {
                        drawResizeHandles(ctx, element);
                    }

                    if (this.draggingElement != element && this.resizingElement != element && System.currentTimeMillis() - this.init < 5000L) {
                        element.windowResized(width, height);
                    }
                }
            }
        }

        if (mc.currentScreen instanceof ChatScreen) {
            Vector2f mousePos = GuiUtil.getMouse((double)this.getCustomScale());
            double mouseX = (double)mousePos.getX();
            double mouseY = (double)mousePos.getY();
            if (this.resizingElement != null) {
                updateResizeTarget((float)mouseX, (float)mouseY);

                // Smooth towards the cursor target instead of multiplying the already-scaled
                // size every frame. This removes the huge/small oscillation from the old
                // center-distance implementation while still feeling responsive.
                float current = this.resizingElement.getScale();
                long now = System.nanoTime();
                float dt = this.resizeLastNanos == 0L ? 1.0F / 60.0F
                        : Math.min(0.05F, Math.max(0.001F, (now - this.resizeLastNanos) / 1_000_000_000.0F));
                this.resizeLastNanos = now;
                float blend = 1.0F - (float)Math.exp(-18.0F * dt);
                float next = current + (this.resizeTargetScale - current) * blend;
                if (Math.abs(this.resizeTargetScale - next) < 0.0025F) next = this.resizeTargetScale;
                applyResizeScale(next, width, height);
            } else if (this.draggingElement != null) {
                this.draggingElement.set(ctx, (float)mouseX - this.dragOffsetX, (float)mouseY - this.dragOffsetY, this, width, height);
            }
        }

    }

    private boolean shouldRender(DraggableHudElement element) {
        String name = element.getName().replace("V2", "");
        List<String> settingNames = List.of("Ватермарка", "Эффекты", "Уведомления", "Информация", "Бинды", "Таргет худ", "Список модулей");
        List<String> componentNames = List.of("Watermark", "Potions", "Notify", "Information", "Keybinds", "TargetHUD", "ArrayList");

        int nameIndex = componentNames.indexOf(name);
        if (nameIndex != -1 && nameIndex < elementsSetting.getBooleanSettings().size()) {
            return ((MultiBooleanSetting.Value)elementsSetting.getBooleanSettings().get(nameIndex)).isEnabled();
        }

        return true;
    }

    @EventTarget
    public void onMouse(EventMouse event) {
        if (!(mc.currentScreen instanceof ChatScreen)) {
            if (this.draggingElement != null) {
                this.draggingElement.release();
                this.draggingElement = null;
            }
        } else {
            Vector2f mousePos = GuiUtil.getMouse((double)this.getCustomScale());
            double mouseX = (double)mousePos.getX();
            double mouseY = (double)mousePos.getY();
            if (event.getAction() == 1 && event.getButton() == 0) {
                List<DraggableHudElement> reversedElements = new ArrayList(getActiveElements());
                Collections.reverse(reversedElements);
                Iterator var8 = reversedElements.iterator();

                while(var8.hasNext()) {
                    DraggableHudElement element = (DraggableHudElement)var8.next();
                    int corner = element.getResizeCorner(mouseX, mouseY);
                    if (this.shouldRender(element) && corner != -1) {
                        beginResize(element, corner);
                        updateResizeTarget((float)mouseX, (float)mouseY);
                        break;
                    }
                    if (this.shouldRender(element) && element.isMouseOver(mouseX, mouseY)) {
                        this.draggingElement = element;
                        this.dragOffsetX = (float)mouseX - element.getX();
                        this.dragOffsetY = (float)mouseY - element.getY();
                        break;
                    }
                }
            } else if (event.getAction() == 0) {
                if (this.draggingElement != null) {
                    this.draggingElement.release();
                    this.draggingElement = null;
                }
                if (this.resizingElement != null) {
                    // Land exactly on the requested size on release, then save alignment.
                    float width = (float)mc.getWindow().getWidth() / this.getCustomScale();
                    float height = (float)mc.getWindow().getHeight() / this.getCustomScale();
                    applyResizeScale(this.resizeTargetScale, width, height);
                    this.resizingElement.release();
                    this.resizingElement = null;
                    this.resizeCorner = -1;
                }
                try {
                    if (Wyvern.getInstance().getConfigManager() != null) {
                        Wyvern.getInstance().getConfigManager().saveConfig("current_config");
                    }
                } catch (Throwable ignored) {}
            }

        }
    }


    private void drawResizeHandles(CustomDrawContext ctx, DraggableHudElement element) {
        float x = element.getX();
        float y = element.getY();
        float w = element.getRenderedWidth();
        float h = element.getRenderedHeight();

        // App/window-editor look: a subtle selection frame with compact rounded
        // corner handles. No debug-red L shapes.
        ColorRGBA accent = Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor();
        boolean active = this.resizingElement == element || this.draggingElement == element;
        ColorRGBA frame = accent.withAlpha(active ? 185 : 105);
        ctx.drawRoundedBorder(x - 2.0F, y - 2.0F, w + 4.0F, h + 4.0F,
                active ? 1.15F : 0.75F, BorderRadius.all(4.5F), frame);

        Vector2f mouse = GuiUtil.getMouse((double)this.getCustomScale());
        int hovered = element.getResizeCorner(mouse.getX(), mouse.getY());
        float handle = 5.5F;
        float half = handle * 0.5F;
        drawResizeHandle(ctx, x, y, handle, half, accent, hovered == 0, this.resizeCorner == 0 && this.resizingElement == element);
        drawResizeHandle(ctx, x + w, y, handle, half, accent, hovered == 1, this.resizeCorner == 1 && this.resizingElement == element);
        drawResizeHandle(ctx, x, y + h, handle, half, accent, hovered == 2, this.resizeCorner == 2 && this.resizingElement == element);
        drawResizeHandle(ctx, x + w, y + h, handle, half, accent, hovered == 3, this.resizeCorner == 3 && this.resizingElement == element);
    }

    private void drawResizeHandle(CustomDrawContext ctx, float cx, float cy, float size, float half,
                                  ColorRGBA accent, boolean hovered, boolean active) {
        ColorRGBA outer = new ColorRGBA(8, 11, 16, 235);
        ColorRGBA inner = active || hovered ? accent.withAlpha(255) : new ColorRGBA(215, 222, 232, 235);
        ctx.drawRoundedRect(cx - half - 1.0F, cy - half - 1.0F, size + 2.0F, size + 2.0F,
                BorderRadius.all(2.2F), outer);
        ctx.drawRoundedRect(cx - half, cy - half, size, size, BorderRadius.all(1.8F), inner);
    }

    private void beginResize(DraggableHudElement element, int corner) {
        this.resizingElement = element;
        this.resizeCorner = corner;
        this.resizeStartScale = element.getScale();
        this.resizeTargetScale = this.resizeStartScale;
        this.resizeLastNanos = System.nanoTime();

        float x = element.getX();
        float y = element.getY();
        float w = element.getRenderedWidth();
        float h = element.getRenderedHeight();

        // Opposite corner is the fixed anchor, exactly like resizing an app window.
        switch (corner) {
            case 0 -> { this.resizeAnchorX = x + w; this.resizeAnchorY = y + h; this.resizeVectorX = -w; this.resizeVectorY = -h; }
            case 1 -> { this.resizeAnchorX = x;     this.resizeAnchorY = y + h; this.resizeVectorX =  w; this.resizeVectorY = -h; }
            case 2 -> { this.resizeAnchorX = x + w; this.resizeAnchorY = y;     this.resizeVectorX = -w; this.resizeVectorY =  h; }
            default -> { this.resizeAnchorX = x;    this.resizeAnchorY = y;     this.resizeVectorX =  w; this.resizeVectorY =  h; }
        }
    }

    private void updateResizeTarget(float mouseX, float mouseY) {
        if (this.resizingElement == null) return;
        float vx = mouseX - this.resizeAnchorX;
        float vy = mouseY - this.resizeAnchorY;
        float denom = this.resizeVectorX * this.resizeVectorX + this.resizeVectorY * this.resizeVectorY;
        if (denom < 1.0F) return;

        // Project the cursor onto the original diagonal. This is stable even when
        // the mouse moves mostly horizontally/vertically and avoids scale spikes.
        float ratio = (vx * this.resizeVectorX + vy * this.resizeVectorY) / denom;
        ratio = Math.max(0.15F, ratio);
        this.resizeTargetScale = Math.max(0.55F, Math.min(2.25F, this.resizeStartScale * ratio));
    }

    private void applyResizeScale(float scale, float screenWidth, float screenHeight) {
        if (this.resizingElement == null) return;
        this.resizingElement.setScale(scale);
        float w = this.resizingElement.getRenderedWidth();
        float h = this.resizingElement.getRenderedHeight();

        // Keep the opposite corner fixed while the whole widget scales uniformly.
        float nx;
        float ny;
        switch (this.resizeCorner) {
            case 0 -> { nx = this.resizeAnchorX - w; ny = this.resizeAnchorY - h; }
            case 1 -> { nx = this.resizeAnchorX;     ny = this.resizeAnchorY - h; }
            case 2 -> { nx = this.resizeAnchorX - w; ny = this.resizeAnchorY; }
            default -> { nx = this.resizeAnchorX;    ny = this.resizeAnchorY; }
        }
        this.resizingElement.set(nx, ny);
        this.resizingElement.update(screenWidth, screenHeight);
    }


    public boolean isHudElementEnabled(int index) {
        return index >= 0 && index < elementsSetting.getBooleanSettings().size()
                && elementsSetting.getBooleanSettings().get(index).isEnabled();
    }

    public void setHudElementEnabled(int index, boolean enabled) {
        if (index < 0 || index >= elementsSetting.getBooleanSettings().size()) return;
        elementsSetting.getBooleanSettings().get(index).setEnabled(enabled);
    }

    public int getHudElementCount() { return elementsSetting.getBooleanSettings().size(); }
    public float getCustomScale() {
        return 2.0F;
    }

    public org.joml.Vector2f getNearest(float x, float y) {
        float minDeltaX = Float.MAX_VALUE;
        float minDeltaY = Float.MAX_VALUE;
        float thoroughness = 0.0F;
        org.joml.Vector2f nearest = new org.joml.Vector2f(-1.0F, -1.0F);
        Iterator var7 = getActiveElements().iterator();

        float minX;
        float minY;
        float deltaX;
        float deltaY;
        while(var7.hasNext()) {
            DraggableHudElement s = (DraggableHudElement)var7.next();
            if (!s.equals(this.draggingElement)) {
                minX = s.getX();
                minY = s.getY();
                deltaX = s.getX() + s.getRenderedWidth();
                deltaY = s.getY() + s.getRenderedHeight();
                float tempXC = s.getX() + s.getRenderedWidth() / 2.0F;
                float tempYC = s.getY() + s.getRenderedHeight() / 2.0F;
                float nearestX = this.getNearest(minX, deltaX, tempXC, x);
                float nearestY = this.getNearest(minY, deltaY, tempYC, y);
                float nearestDeltaX = MathUtil.goodSubtract(nearestX, x);
                float nearestDeltaY = MathUtil.goodSubtract(nearestY, y);
                if (nearestDeltaX < minDeltaX) {
                    minDeltaX = nearestDeltaX;
                    if (nearestDeltaX < thoroughness) {
                        nearest.x = nearestX;
                    }
                }

                if (nearestDeltaY < minDeltaY) {
                    minDeltaY = nearestDeltaY;
                    if (nearestDeltaY < thoroughness) {
                        nearest.y = nearestY;
                    }
                }
            }
        }

        if (nearest.x == -1.0F || nearest.y == -1.0F) {
            float tempXA = (float)mc.getWindow().getScaledWidth() / 2.0F;
            float tempYA = (float)mc.getWindow().getScaledHeight() / 2.0F;
            minX = this.getNearest(tempXA, tempXA, tempXA, x);
            minY = this.getNearest(tempYA, tempYA, tempYA, y);
            deltaX = MathUtil.goodSubtract(minX, x);
            deltaY = MathUtil.goodSubtract(minY, y);
            if (deltaX < minDeltaX && deltaX < thoroughness) {
                nearest.x = minX;
            }

            if (deltaY < minDeltaY && deltaY < thoroughness) {
                nearest.y = minY;
            }
        }

        return nearest;
    }

    public float getNearest(float a, float b, float c, float target) {
        float nearest = a;
        if (MathUtil.goodSubtract(b, target) < MathUtil.goodSubtract(a, target)) {
            nearest = b;
        }

        if (MathUtil.goodSubtract(c, target) < MathUtil.goodSubtract(nearest, target)) {
            nearest = c;
        }

        return nearest;
    }

    public boolean isEnableScoreBar() {
        return false;
    }

    public boolean isEnableHotBar() {
        return false;
    }

    public boolean isEnableTab() {
        return false;
    }

    @EventTarget
    public void resize(EventWindowResize eventWindowResize) {
        float width = (float)mc.getWindow().getWidth() / this.getCustomScale();
        float height = (float)mc.getWindow().getHeight() / this.getCustomScale();
        Iterator var4 = getActiveElements().iterator();

        while(var4.hasNext()) {
            DraggableHudElement element = (DraggableHudElement)var4.next();
            element.windowResized(width, height);
        }

    }

    @EventTarget
    public void update(EventUpdate eventUpdate) {
        if (Render2DUtil.glowCache.size() > 400) {
            Render2DUtil.glowCache.values().removeIf((v) -> {
                if (v.tick()) {
                    v.destroy();
                    return true;
                } else {
                    return false;
                }
            });
        }

        Iterator var2 = getActiveElements().iterator();

        while(var2.hasNext()) {
            DraggableHudElement draggableHudElement = (DraggableHudElement)var2.next();
            draggableHudElement.tick();
        }

    }

    @EventTarget
    public void screenEvent(EventSetScreen event) {
        if (event.getScreen() instanceof ChatScreen) {
            this.init = System.currentTimeMillis();
        }

    }

    @Generated
    public DraggableHudElement getDraggingElement() {
        return this.draggingElement;
    }

    public int getGlowRadius() {
        return 10;
    }
}
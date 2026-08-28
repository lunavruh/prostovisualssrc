package wtf.wyvern.client.hud.elements.draggable;

import com.google.gson.JsonObject;
import wtf.wyvern.Wyvern;
import wtf.wyvern.client.modules.api.setting.Setting;
import wtf.wyvern.client.modules.impl.render.Interface;
import wtf.wyvern.utility.interfaces.IMinecraft;
import wtf.wyvern.utility.render.display.base.BorderRadius;
import wtf.wyvern.utility.render.display.base.CustomDrawContext;
import wtf.wyvern.utility.render.display.base.color.ColorRGBA;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Generated;
import org.joml.Vector2f;

public abstract class DraggableHudElement implements IMinecraft {
    private final String name;
    protected float x;
    protected float y;
    protected float width;
    protected float height;
    private float windowWidth;
    private float windowHeight;
    protected float newX = -1.0F;
    protected float newY = -1.0F;
    private Align align;
    private float offsetX;
    private float offsetY;
    private float scale = 1.0F;

    public void tick() {
    }

    public List<Setting> getSettings() {
        return (List)Arrays.stream(this.getClass().getDeclaredFields()).map((field) -> {
            try {
                field.setAccessible(true);
                return field.get(this);
            } catch (IllegalAccessException var3) {

                return null;
            }
        }).filter((field) -> field instanceof Setting).map((field) -> (Setting)field).collect(Collectors.toList());
    }

    public DraggableHudElement(String name, float initialX, float initialY, float windowWidth, float windowHeight, float offsetX, float offsetY, Align align) {
        this.align = DraggableHudElement.Align.TOP_LEFT;
        this.offsetX = 0.0F;
        this.offsetY = 0.0F;
        this.name = name;
        this.x = initialX;
        this.y = initialY;
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.align = align;
    }

    public abstract void render(CustomDrawContext var1);

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= (double)this.x && mouseX <= (double)(this.x + this.width * this.scale)
                && mouseY >= (double)this.y && mouseY <= (double)(this.y + this.height * this.scale);
    }

    public boolean isResizeHandle(double mouseX, double mouseY) {
        return getResizeCorner(mouseX, mouseY) != -1;
    }

    /** 0=top-left, 1=top-right, 2=bottom-left, 3=bottom-right. */
    public int getResizeCorner(double mouseX, double mouseY) {
        float rw = this.width * this.scale, rh = this.height * this.scale;
        float r = 7.0F;
        if (near(mouseX, mouseY, this.x, this.y, r)) return 0;
        if (near(mouseX, mouseY, this.x + rw, this.y, r)) return 1;
        if (near(mouseX, mouseY, this.x, this.y + rh, r)) return 2;
        if (near(mouseX, mouseY, this.x + rw, this.y + rh, r)) return 3;
        return -1;
    }

    private static boolean near(double mx, double my, float x, float y, float r) {
        return Math.abs(mx - x) <= r && Math.abs(my - y) <= r;
    }

    public void setScale(float scale) { this.scale = Math.max(0.55F, Math.min(2.25F, scale)); }
    public float getScale() { return this.scale; }
    public float getRenderedWidth() { return this.width * this.scale; }
    public float getRenderedHeight() { return this.height * this.scale; }

    protected void drawBorder(CustomDrawContext ctx) {
        float borderThickness = 5.5F;
        float borderRadius = 6.0F;
        ColorRGBA borderColor = new ColorRGBA(179, 145, 255, 255);
        ctx.drawRoundedBorder(this.x, this.y, this.width, this.height, borderThickness, BorderRadius.all(borderRadius), borderColor);
    }

    public void set(CustomDrawContext ctx, float x, float y, Interface dragManager, float widthScreen, float heightScreen) {
        Vector2f nerest = dragManager.getNearest(x, y);
        SheetCode x0 = new SheetCode(this, nerest.x, 0.0F);
        SheetCode y0 = new SheetCode(this, nerest.y, 0.0F);
        Vector2f nerest2 = dragManager.getNearest(x + this.width * this.scale, y + this.height * this.scale);
        SheetCode x1 = new SheetCode(this, nerest2.x, -this.width * this.scale);
        SheetCode y1 = new SheetCode(this, nerest2.y, -this.height * this.scale);
        Vector2f nerest3 = dragManager.getNearest(x + this.width * this.scale / 2.0F, y + this.height * this.scale / 2.0F);
        SheetCode x2 = new SheetCode(this, nerest3.x, -this.width * this.scale / 2.0F);
        SheetCode y2 = new SheetCode(this, nerest3.y, -this.height * this.scale / 2.0F);
        this.x = x;
        this.y = y;
        this.windowWidth = widthScreen;
        this.windowHeight = heightScreen;
        SheetCode x3 = this.getValue(x0, x1, x2);
        SheetCode y3 = this.getValue(y0, y1, y2);
        this.renderXLine(ctx, x3);
        this.renderYLine(ctx, y3);
        this.update(widthScreen, heightScreen);
    }

    private SheetCode getValue(SheetCode value, SheetCode value2, SheetCode value3) {
        if (value.pos != -1.0F) {
            return value;
        } else {
            return value2.pos != -1.0F ? value2 : value3;
        }
    }

    protected void renderYLine(CustomDrawContext ctx, SheetCode nearest) {
        if (nearest.pos == -1.0F) {
            this.newY = nearest.pos;
        } else {
            ctx.drawRoundedRect(0.0F, nearest.pos, (float)ctx.getScaledWindowWidth(), 1.0F, BorderRadius.ZERO, Wyvern.getInstance().getThemeManager().getCurrentTheme().getColor().withAlpha(255));
            this.newY = nearest.pos + nearest.offset;
        }

    }

    protected void renderXLine(CustomDrawContext ctx, SheetCode nearest) {
        if (nearest.pos == -1.0F) {
            this.newX = nearest.pos;
        } else {
            this.newX = nearest.pos + nearest.offset;
        }

    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void windowResized(float newWindowWidth, float newWindowHeight) {
        if (!(newWindowHeight <= 0.0F) && !(newWindowWidth <= 0.0F)) {
            float baseX = this.alignToX(this.align, newWindowWidth);
            float baseY = this.alignToY(this.align, newWindowHeight);
            this.x = baseX + this.offsetX;
            this.y = baseY + this.offsetY;
            this.windowWidth = newWindowWidth;
            this.windowHeight = newWindowHeight;
            this.update(newWindowWidth, newWindowHeight);
        }

    }

    public void update(float widthScreen, float heightScreen) {
        if (this.x < 0.0F) {
            this.x = 0.0F;
        }

        if (this.y < 0.0F) {
            this.y = 0.0F;
        }

        if (this.x + this.width * this.scale > widthScreen) {
            this.x = widthScreen - this.width * this.scale;
        }

        if (this.y + this.height * this.scale > heightScreen) {
            this.y = heightScreen - this.height * this.scale;
        }

    }

    public void release() {
        if (this.newX != -1.0F) {
            this.x = this.newX;
        }

        if (this.newY != -1.0F) {
            this.y = this.newY;
        }

        Align newAlign = this.determineAlign(this.x, this.y, this.windowWidth, this.windowHeight);
        float baseX = this.alignToX(newAlign, this.windowWidth);
        float baseY = this.alignToY(newAlign, this.windowHeight);
        this.align = newAlign;
        this.offsetX = this.x - baseX;
        this.offsetY = this.y - baseY;
    }

    private Align determineAlign(float x, float y, float screenWidth, float screenHeight) {
        boolean left = x + this.width * this.scale / 2.0F < screenWidth / 3.0F;
        boolean right = x + this.width * this.scale / 2.0F > screenWidth * 2.0F / 3.0F;
        boolean centerX = !left && !right;
        boolean top = y + this.height * this.scale / 2.0F < screenHeight / 3.0F;
        boolean bottom = y + this.height * this.scale / 2.0F > screenHeight * 2.0F / 3.0F;
        boolean centerY = !top && !bottom;
        if (top) {
            if (left) {
                return DraggableHudElement.Align.TOP_LEFT;
            } else {
                return centerX ? DraggableHudElement.Align.TOP_CENTER : DraggableHudElement.Align.TOP_RIGHT;
            }
        } else if (centerY) {
            if (left) {
                return DraggableHudElement.Align.CENTER_LEFT;
            } else {
                return centerX ? DraggableHudElement.Align.CENTER : DraggableHudElement.Align.CENTER_RIGHT;
            }
        } else if (left) {
            return DraggableHudElement.Align.BOTTOM_LEFT;
        } else {
            return centerX ? DraggableHudElement.Align.BOTTOM_CENTER : DraggableHudElement.Align.BOTTOM_RIGHT;
        }
    }

    private float alignToX(Align align, float screenWidth) {
        float var10000;
        switch (align.ordinal()) {
            case 0:
            case 3:
            case 6:
                var10000 = 0.0F;
                break;
            case 1:
            case 4:
            case 7:
                var10000 = screenWidth / 2.0F - this.width * this.scale / 2.0F;
                break;
            case 2:
            case 5:
            case 8:
                var10000 = screenWidth - this.width * this.scale;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + align);
        }

        return var10000;
    }

    private float alignToY(Align align, float screenHeight) {
        float var10000;
        switch (align.ordinal()) {
            case 0:
            case 1:
            case 2:
                var10000 = 0.0F;
                break;
            case 3:
            case 4:
            case 5:
                var10000 = screenHeight / 2.0F - this.height * this.scale / 2.0F;
                break;
            case 6:
            case 7:
            case 8:
                var10000 = screenHeight - this.height * this.scale;
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + align);
        }

        return var10000;
    }

    public JsonObject save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", this.x);
        obj.addProperty("y", this.y);
        obj.addProperty("width", this.width);
        obj.addProperty("height", this.height);
        obj.addProperty("scale", this.scale);
        obj.addProperty("windowWidth", this.windowWidth);
        obj.addProperty("windowHeight", this.windowHeight);
        obj.addProperty("offsetX", this.offsetX);
        obj.addProperty("offsetY", this.offsetY);
        obj.addProperty("align", this.align.name());
        JsonObject settingsObject = new JsonObject();

        for(Setting setting : this.getSettings()) {
            setting.safe(settingsObject);
        }

        if (settingsObject.size() > 0) {
            obj.add("Settings", settingsObject);
        }

        return obj;
    }

    public void load(JsonObject obj) {
        if (obj.has("x")) {
            this.x = obj.get("x").getAsFloat();
        }

        if (obj.has("y")) {
            this.y = obj.get("y").getAsFloat();
        }

        if (obj.has("width")) {
            this.width = obj.get("width").getAsFloat();
        }

        if (obj.has("height")) {
            this.height = obj.get("height").getAsFloat();
        }

        if (obj.has("scale")) {
            this.scale = Math.max(0.55F, Math.min(2.25F, obj.get("scale").getAsFloat()));
        }

        if (obj.has("windowWidth")) {
            this.windowWidth = obj.get("windowWidth").getAsFloat();
        }

        if (obj.has("windowHeight")) {
            this.windowHeight = obj.get("windowHeight").getAsFloat();
        }

        if (obj.has("offsetX")) {
            this.offsetX = obj.get("offsetX").getAsFloat();
        }

        if (obj.has("offsetY")) {
            this.offsetY = obj.get("offsetY").getAsFloat();
        }

        if (obj.has("align")) {
            try {
                this.align = DraggableHudElement.Align.valueOf(obj.get("align").getAsString());
            } catch (IllegalArgumentException var6) {
                this.align = DraggableHudElement.Align.TOP_LEFT;
            }
        }

        if (obj.has("Settings") && obj.get("Settings").isJsonObject()) {
            JsonObject settingsObject = obj.getAsJsonObject("Settings");

            for(Setting setting : this.getSettings()) {
                String settingName = setting.getName();
                if (settingsObject.has(settingName)) {
                    setting.load(settingsObject);
                }
            }
        }

    }

    public void setPosition(float x, float y) {
        this.x = x;
        this.y = y;
    }

    @Generated
    public String getName() {
        return this.name;
    }

    @Generated
    public float getX() {
        return this.x;
    }

    @Generated
    public float getY() {
        return this.y;
    }

    @Generated
    public float getWidth() {
        return this.width;
    }

    @Generated
    public float getHeight() {
        return this.height;
    }

    @Generated
    public Align getAlign() {
        return this.align;
    }

    public static enum Align {
        TOP_LEFT,
        TOP_CENTER,
        TOP_RIGHT,
        CENTER_LEFT,
        CENTER,
        CENTER_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_CENTER,
        BOTTOM_RIGHT;

        private static Align[] $values() {
            return new Align[]{TOP_LEFT, TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT};
        }
    }

    protected class SheetCode {
        private float pos;
        private float offset;

        public SheetCode(final DraggableHudElement this$0x, float pos, float offset) {
            this.pos = pos;
            this.offset = offset;
        }

        @Generated
        public float getPos() {
            return this.pos;
        }

        @Generated
        public float getOffset() {
            return this.offset;
        }

        @Generated
        public void setPos(float pos) {
            this.pos = pos;
        }

        @Generated
        public void setOffset(float offset) {
            this.offset = offset;
        }
    }
}
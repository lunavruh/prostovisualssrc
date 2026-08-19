package dev.prostovisuals.client.ui.mainmenu;

import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class MainMenu extends Screen {

    private static boolean welcomePlayed = false;
    private static final Identifier MENU_BACKGROUND = Identifier.of("prostovisuals", "textures/main_menu_background.png");
    private static final float MENU_BACKGROUND_ASPECT = 1672f / 941f;

    private final List<GlassButton> buttons = new ArrayList<>();
    private final Animation openAnimation = new Animation(520, 1.0, true, Easing.OUT_QUART);
    private final ThemeManager themeManager = ThemeManager.getInstance();

    private boolean closing;
    private Screen nextScreen;

    public MainMenu() {
        super(Text.literal("ProstoVisuals"));
    }

    @Override
    protected void init() {
        buttons.clear();
        closing = false;
        nextScreen = null;
        openAnimation.reset();
        openAnimation.update(true);

        float w = 164f;
        float h = 24f;
        float gap = 6f;
        float cx = width * 0.5f - w * 0.5f;
        float cy = height * 0.5f - 45f;

        buttons.add(new GlassButton(cx, cy, w, h, I18n.translate("prostovisuals.mainmenu.singleplayer"),
                () -> beginExit(new AnimatedScreenWrapper(new SelectWorldScreen(this), this))));
        buttons.add(new GlassButton(cx, cy + (h + gap), w, h, I18n.translate("prostovisuals.mainmenu.multiplayer"),
                () -> beginExit(new AnimatedScreenWrapper(new MultiplayerScreen(this), this))));
        buttons.add(new GlassButton(cx, cy + (h + gap) * 2f, w, h, I18n.translate("prostovisuals.mainmenu.options"),
                () -> beginExit(new AnimatedScreenWrapper(new OptionsScreen(this, client.options), this))));

        float half = (w - gap) * 0.5f;
        buttons.add(new GlassButton(cx, cy + (h + gap) * 3f, half, h, I18n.translate("prostovisuals.mainmenu.altmanager"),
                () -> beginExit(new AnimatedScreenWrapper(new AltManagerScreen(this), this))));
        buttons.add(new GlassButton(cx + half + gap, cy + (h + gap) * 3f, half, h, I18n.translate("prostovisuals.mainmenu.quit"),
                () -> beginExit(null)));

        if (!welcomePlayed) {
            welcomePlayed = true;
            MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvent.of(Identifier.of("prostovisuals:welcome")), 1.0f, 1.0f)
            );
        }
    }

    private void beginExit(Screen screen) {
        if (closing) return;
        closing = true;
        nextScreen = screen;
        openAnimation.update(false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        openAnimation.update(!closing);
        float a = Math.max(0f, Math.min(1f, openAnimation.getValue()));

        if (closing && a <= 0.015f) {
            if (nextScreen == null) client.scheduleStop();
            else client.setScreen(nextScreen);
            return;
        }

        float t = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
        Color accent = themeManager.getCurrentTheme().getAccentColor();

        // Cover the whole screen without stretching the supplied 16:9 artwork.
        float screenAspect = width / (float) Math.max(1, height);
        float u = 0f, v = 0f, texW = 1f, texH = 1f;
        if (screenAspect > MENU_BACKGROUND_ASPECT) {
            texH = MENU_BACKGROUND_ASPECT / screenAspect;
            v = (1f - texH) * 0.5f;
        } else {
            texW = screenAspect / MENU_BACKGROUND_ASPECT;
            u = (1f - texW) * 0.5f;
        }
        Render2D.drawTexture(ctx.getMatrices(), 0f, 0f, width, height, 0f,
                u, v, texW, texH, MENU_BACKGROUND, Color.WHITE);
        Render2D.drawRect(ctx.getMatrices(), 0f, 0f, width, height,
                new Color(2, 5, 10, (int) (50 + 28 * (1f - a))));

        // Header brand, floating separately from the controls.
        String brand = "PROSTOVISUALS";
        float brandSize = 16f;
        float brandW = Fonts.BOLD.getWidth(brand, brandSize);
        Render2D.drawFont(
                ctx.getMatrices(),
                Fonts.BOLD.getFont(brandSize),
                brand,
                width * 0.5f - brandW * 0.5f,
                height * 0.5f - 122f + (1f - a) * 8f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (255 * a))
        );

        String tagline = "the best, the strongest, the victors";
        float tagSize = 7.2f;
        Render2D.drawFont(
                ctx.getMatrices(),
                Fonts.REGULAR.getFont(tagSize),
                tagline,
                width * 0.5f - Fonts.REGULAR.getWidth(tagline, tagSize) * 0.5f,
                height * 0.5f - 98f + (1f - a) * 8f,
                new Color(218, 223, 232, (int) (190 * a))
        );

        // Main floating glass dock.
        float panelW = 204f;
        float panelH = 154f;
        float panelX = width * 0.5f - panelW * 0.5f;
        float panelY = height * 0.5f - 66f + (1f - a) * 18f;

        LiquidGlassUtil.drawLiquidGlass(
                ctx, panelX, panelY, panelW, panelH, t,
                6.3f, 0.17f, 0.0019f, 19f, 0.72f, 1.050f
        );

        Render2D.drawRoundedRect(
                ctx.getMatrices(),
                panelX + 1f, panelY + 1f,
                panelW - 2f, panelH - 2f,
                18f,
                new Color(4, 6, 10, (int) (17 * a))
        );

        // Subtle top accent strip.
        Render2D.drawRoundedRect(
                ctx.getMatrices(),
                panelX + 23f, panelY + 10f,
                panelW - 46f, 1.2f, 0.6f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (105 * a))
        );

        for (GlassButton button : buttons) {
            button.render(ctx, mouseX, mouseY, a, t);
        }

        String status = "Ready";
        float statusSize = 6.4f;
        Render2D.drawFont(
                ctx.getMatrices(),
                Fonts.REGULAR.getFont(statusSize),
                status,
                panelX + 12f,
                panelY + panelH - 14f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (190 * a))
        );

        String copyright = I18n.translate("prostovisuals.mainmenu.copyright");
        Render2D.drawFont(
                ctx.getMatrices(),
                Fonts.REGULAR.getFont(6f),
                copyright,
                width - Fonts.REGULAR.getWidth(copyright, 6f) - 8f,
                height - 11f,
                new Color(198, 203, 214, (int) (135 * a))
        );
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (button == 0) {
            for (GlassButton b : buttons) {
                if (b.hovered(mouseX, mouseY)) {
                    b.press();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    private final class GlassButton {
        final float x, y, w, h;
        final String text;
        final Runnable action;
        final Animation hover = new Animation(190, 1.0, false, Easing.OUT_QUART);

        GlassButton(float x, float y, float w, float h, String text, Runnable action) {
            this.x=x; this.y=y; this.w=w; this.h=h; this.text=text; this.action=action;
        }

        boolean hovered(double mx, double my) {
            return mx >= x && mx <= x+w && my >= y && my <= y+h;
        }

        void press() { action.run(); }

        void render(DrawContext ctx, int mx, int my, float alpha, float t) {
            hover.update(hovered(mx, my));
            float hv = hover.getValue();
            float yy = y - hv * 1.2f;

            LiquidGlassUtil.drawLiquidGlass(ctx, x, yy, w, h, t,
                    3.5f + hv * 1.2f, 0.10f, 0.0010f + hv * 0.0005f,
                    10f, 0.38f + hv * 0.26f, 1.018f + hv * 0.009f);

            Color ac = themeManager.getCurrentTheme().getAccentColor();
            if (hv > .01f) {
                Render2D.drawRoundedRect(ctx.getMatrices(), x + 1f, yy + 1f, w - 2f, h - 2f, 9f,
                        new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int) (42 * hv * alpha)));
            }

            Color tc = hv > .25f
                    ? new Color(ac.getRed(), ac.getGreen(), ac.getBlue(), (int) (255 * alpha))
                    : new Color(238, 240, 245, (int) (235 * alpha));
            float fs = 8.2f;
            Render2D.drawFont(ctx.getMatrices(), Fonts.MEDIUM.getFont(fs), text,
                    x + w*.5f - Fonts.MEDIUM.getWidth(text, fs)*.5f,
                    yy + h*.5f - Fonts.MEDIUM.getHeight(fs)*.5f, tc);
        }
    }

    /**
     * Compatibility button used by AltManagerScreen.
     * Kept public because the alt manager historically instantiated
     * MainMenu.AnimatedButton directly.
     */
    public static class AnimatedButton {
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final String message;
        private final PressAction action;
        private final Animation hoverAnimation = new Animation(190, 1.0, false, Easing.OUT_QUART);

        public AnimatedButton(
                float x,
                float y,
                float width,
                float height,
                String message,
                PressAction action
        ) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.message = message;
            this.action = action;
        }

        public void resetAnimations() {
            hoverAnimation.reset();
        }

        public boolean isMouseOver(int mouseX, int mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        public void onPress() {
            if (action != null) action.onPress(this);
        }

        public void render(DrawContext context, int mouseX, int mouseY, float delta, float alpha) {
            hoverAnimation.update(isMouseOver(mouseX, mouseY));
            float hover = Math.max(0.0f, Math.min(1.0f, hoverAnimation.getValue()));
            float a = Math.max(0.0f, Math.min(1.0f, alpha));
            float drawY = y - hover * 1.0f;
            float time = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);

            LiquidGlassUtil.drawLiquidGlass(
                    context,
                    x,
                    drawY,
                    width,
                    height,
                    time,
                    3.2f + hover * 0.8f,
                    0.10f,
                    0.0010f + hover * 0.0004f,
                    8.0f,
                    0.34f + hover * 0.22f,
                    1.015f + hover * 0.006f
            );

            Color accent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();
            if (hover > 0.01f) {
                Render2D.drawRoundedRect(
                        context.getMatrices(),
                        x + 1.0f,
                        drawY + 1.0f,
                        width - 2.0f,
                        height - 2.0f,
                        7.0f,
                        new Color(
                                accent.getRed(),
                                accent.getGreen(),
                                accent.getBlue(),
                                (int) (42.0f * hover * a)
                        )
                );
            }

            float fontSize = 7.0f;
            float textWidth = Fonts.REGULAR.getWidth(message, fontSize);
            Color textColor = hover > 0.2f
                    ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int) (255 * a))
                    : new Color(235, 238, 245, (int) (235 * a));

            Render2D.drawFont(
                    context.getMatrices(),
                    Fonts.REGULAR.getFont(fontSize),
                    message,
                    x + (width - textWidth) * 0.5f,
                    drawY + (height - Fonts.REGULAR.getHeight(fontSize)) * 0.5f,
                    textColor
            );
        }
    }

    @FunctionalInterface
    public interface PressAction {
        void onPress(AnimatedButton button);
    }

}

package dev.prostovisuals.client.ui.hud.windows;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import dev.prostovisuals.client.ui.hud.windows.components.impl.BooleanComponent;
import dev.prostovisuals.client.ui.hud.windows.components.WindowComponent;
import dev.prostovisuals.client.ui.hud.windows.components.impl.ListComponent;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.modules.settings.Setting;

import net.minecraft.client.gui.DrawContext;
import dev.prostovisuals.modules.settings.impl.*;
import lombok.*;

@Getter @Setter
public class Window {
	private float x, y, width, height;
	private final List<Setting<?>> settings;
	private final List<WindowComponent> components = new ArrayList<>();
	private final Animation animation = new Animation(300, 1f, true, Easing.BOTH_SINE);
	// Match Watermark background color
	private final Color bgColor = new Color(30, 30, 30, 240);
	
	public Window(float x, float y, float width, float height, List<Setting<?>> settings) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.settings = settings;
		for (Setting<?> setting : settings) {
			if (setting instanceof BooleanSetting) components.add(new BooleanComponent(setting.getName(), ((BooleanSetting) setting)));
			else if (setting instanceof ListSetting) components.add(new ListComponent(setting.getName(), ((ListSetting) setting)));
		}
	}
	
	public void render(DrawContext context, int mouseX, int mouseY) {
		Render2D.startScissor(
				context,
				getX() + (getWidth() - getWidth() * animation.getValue()) / 2,
				getY() + (getFinalHeight() - getFinalHeight() * animation.getValue()) / 2,
				getWidth() * animation.getValue(),
				getFinalHeight() * animation.getValue()
		);

		// Animated Liquid Glass surface for both HUD settings and the global HUD toggle menu.
		float anim = animation.getValue();
		float drawX = getX() + (getWidth() - getWidth() * anim) / 2f;
		float drawY = getY() + (getFinalHeight() - getFinalHeight() * anim) / 2f;
		float drawW = getWidth() * anim;
		float drawH = getFinalHeight() * anim;
		if (drawW > 1f && drawH > 1f) {
			float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
			LiquidGlassUtil.drawLiquidGlass(
					context,
					drawX, drawY, drawW, drawH,
					liquidTime,
					4.6f, 0.13f, 0.0015f,
					7.5f, 0.52f, 1.03f
			);
			// Very light darkening only for text readability; glass remains visible.
			Render2D.drawRoundedRect(
					context.getMatrices(), drawX + 1f, drawY + 1f,
					Math.max(0f, drawW - 2f), Math.max(0f, drawH - 2f),
					6.5f,
					new Color(7, 9, 14, (int) (26 * anim))
			);
		}

		float finalY = y;

		for (WindowComponent component : components) {
			component.setX(x);
			component.setY(finalY);
			component.setWidth(width);
			component.setHeight(height);
			component.setAnimation(animation);
			component.render(context, mouseX, mouseY, 0);
			finalY += component.getHeight() + 4.5f;
		}

		Render2D.stopScissor(context);
	}

	public void reset() {
		animation.update(false);
	}

	public boolean closed() {
		return animation.finished(false) && animation.getValue() <= 0.01f;
	}
	
	public void mouseClicked(double mouseX, double mouseY, int button) {
		for (WindowComponent component : components) component.mouseClicked(mouseX, mouseY, button);
	}
	
	public float getFinalHeight() {
		float height = 0;
		for (WindowComponent component : components) {
			if (!component.getVisible().get()) continue;
			height += component.getHeight() + component.getAddHeight().get() + 4.5f;
		}
		
		return height;
	}
}
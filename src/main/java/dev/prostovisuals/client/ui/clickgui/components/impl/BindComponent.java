package dev.prostovisuals.client.ui.clickgui.components.impl;

import java.awt.Color;

import org.lwjgl.glfw.GLFW;

import dev.prostovisuals.modules.settings.api.Bind;
import dev.prostovisuals.modules.settings.impl.BindSetting;
import dev.prostovisuals.client.ui.clickgui.components.Component;
import dev.prostovisuals.client.ui.clickgui.ClickGuiLanguage;
import dev.prostovisuals.client.util.animations.Animation;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.animations.infinity.InfinityAnimation;
import dev.prostovisuals.client.util.math.MathUtils;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.managers.ThemeManager;
import net.minecraft.client.gui.DrawContext;

public class BindComponent extends Component {
	
	private final BindSetting setting;
	private final InfinityAnimation animation = new InfinityAnimation(Easing.LINEAR);
	private final Animation bindingAnimation = new Animation(500, 1f, false, Easing.BOTH_SINE);
	private boolean binding;
	private float referencePillX, referencePillY, referencePillW;

	public BindComponent(BindSetting setting) {
		super(setting.getName());
		this.setting = setting;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui) {
			renderReference(context, mouseX, mouseY);
			return;
		}
		bindingAnimation.update(binding);
		String text = binding ? "..." : setting.getValue().toString().replace("_", " ");
		float textWidth = Fonts.REGULAR.getWidth(text, 6.5f);
		float finalWidth = animation.animate(textWidth + 4f, 200);
		// theme colors
		Color themeText = ThemeManager.getInstance().getRenderedTextColor();
		Color accent = ThemeManager.getInstance().getRenderedAccentColor();
		Color boxColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 160);
		Color textColorNormal = new Color(themeText.getRed(), themeText.getGreen(), themeText.getBlue(), 220);
		int alphaBind = (int) (255 * bindingAnimation.getReversedValue());
		int alphaDots = (int) (255 * bindingAnimation.getValue());
		Color textColorBind = new Color(themeText.getRed(), themeText.getGreen(), themeText.getBlue(), alphaBind);
		Color textColorDots = new Color(themeText.getRed(), themeText.getGreen(), themeText.getBlue(), alphaDots);
		Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(7.5f), ClickGuiLanguage.translate(setting.getName()), x + 4f, y + 3f, textColorNormal);
        Render2D.drawRoundedRect(context.getMatrices(), x + width - finalWidth - 4f, y, finalWidth, height - 7f, 4f,
                new Color(22, 26, 34, 175));
		Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(6.5f), setting.getValue().toString().replace("_", " "), x + width - textWidth - 6f, y + 2.3f, textColorBind);
		Render2D.drawFont(context.getMatrices(), Fonts.BOLD.getFont(6.5f), "...", x + width - textWidth - 6f, y + 2f, textColorDots);
	}

	private void renderReference(DrawContext context,int mouseX,int mouseY){
		bindingAnimation.update(binding);
		float ga=Math.max(0f,Math.min(1f,getGlobalAlpha()));
		boolean hovered=MathUtils.isHovered(x,y,width,height,mouseX,mouseY);
		if(hovered)Render2D.drawRoundedRect(context.getMatrices(),x,y+1f,width,height-2f,5f,
				new Color(225,241,250,(int)(8*ga)));
		Render2D.drawFont(context.getMatrices(),Fonts.REGULAR.getFont(5.1f),
				ClickGuiLanguage.marquee(ClickGuiLanguage.translate(setting.getName()), Math.max(18f, width - 55f), 5.1f, hovered),x+3f,y+6f,
				new Color(232,241,248,(int)(245*ga)));
		String value=binding?"Press key...":(setting.getValue()==null||setting.getValue().isEmpty()?"None":setting.getValue().toString().replace("_"," "));
		referencePillW=Math.min(108f,Math.max(58f,Fonts.MEDIUM.getWidth(value,4.8f)+15f));
		referencePillX=x+width-referencePillW-3f;referencePillY=y+2f;
        Color accent=dev.prostovisuals.client.ui.clickgui.OneClientClickGui.referenceAccentColorAt(referencePillX+referencePillW*.5f,referencePillY+8f);
		Render2D.drawRoundedRect(context.getMatrices(),referencePillX,referencePillY,referencePillW,16f,5f,
				new Color(224,240,249,(int)((binding?27:16)*ga)));
		Render2D.drawBorder(context.getMatrices(),referencePillX,referencePillY,referencePillW,16f,5f,.16f,.30f,
				binding?new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),(int)(105*ga))
						:new Color(239,249,254,(int)(25*ga)));
		float tw=Fonts.MEDIUM.getWidth(value,4.8f);
		Render2D.drawFont(context.getMatrices(),Fonts.MEDIUM.getFont(4.8f),value,
				referencePillX+(referencePillW-tw)*.5f,y+7f,new Color(239,247,252,(int)(245*ga)));
	}

	@Override
	public void mouseClicked(double mouseX, double mouseY, int button) {
		if(mc.currentScreen instanceof dev.prostovisuals.client.ui.clickgui.OneClientClickGui){
			if(binding){Bind.Mode mode=setting.getValue()!=null?setting.getValue().getMode():Bind.Mode.TOGGLE;setting.setValue(new Bind(button,true,mode));binding=false;return;}
			if(button==0&&MathUtils.isHovered(referencePillX,referencePillY,referencePillW,16f,(float)mouseX,(float)mouseY))binding=true;
			return;
		}
		String text = binding ? "..." : setting.getValue().toString().replace("_", " ");
		float textWidth = Fonts.REGULAR.getWidth(text, 6.5f);
		if (MathUtils.isHovered(x + width - 8f - textWidth, y + 2f, textWidth + 4f, height - 4f, (float) mouseX, (float) mouseY) && !binding && button == 0) {
			binding = true;
			return;
		}
		
		if (binding) {
			Bind.Mode mode = setting.getValue() != null ? setting.getValue().getMode() : Bind.Mode.TOGGLE;
			setting.setValue(new Bind(button, true, mode));
			binding = false;
			return;
		}
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY, int button) {
		
	}

	@Override
	public void keyPressed(int keyCode, int scanCode, int modifiers) {
		if (binding) {
			if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_DELETE) setting.setValue(new Bind(-1, false));
			else {
				Bind.Mode mode = setting.getValue() != null ? setting.getValue().getMode() : Bind.Mode.TOGGLE;
				setting.setValue(new Bind(keyCode, false, mode));
			}
			binding = false;
		}
	}

	@Override
	public void keyReleased(int keyCode, int scanCode, int modifiers) {
		
	}

	@Override
	public void charTyped(char chr, int modifiers) {
		
	}//
}

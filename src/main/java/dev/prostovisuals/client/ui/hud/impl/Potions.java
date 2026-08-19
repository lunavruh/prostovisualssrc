package dev.prostovisuals.client.ui.hud.impl;

import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.util.animations.Easing;
import dev.prostovisuals.client.util.animations.infinity.InfinityAnimation;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.util.perf.Perf;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import net.minecraft.registry.Registries;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Potions extends HudElement implements ThemeManager.ThemeChangeListener {
    private static final Identifier POTION_HEADER_ICON =
            Identifier.of("prostovisuals", "hud/potion.png");

	private final InfinityAnimation hudFade = new InfinityAnimation(Easing.BOTH_SINE);
	private final InfinityAnimation heightAnim = new InfinityAnimation(Easing.OUT_QUAD);
	private final InfinityAnimation widthAnim = new InfinityAnimation(Easing.OUT_QUAD);

	// Per-item анимация 0..1 (1 — полностью видим, 0 — скрыт)
	private final Map<String, InfinityAnimation> itemAlpha = new LinkedHashMap<>();
	// Снапшоты последнего названия/иконки для исчезающих
	private final Map<String, String> lastText = new HashMap<>();
	private final Map<String, String> lastIconKey = new HashMap<>();
	private static final Comparator<StatusEffectInstance> EFFECT_NAME_ORDER =
			Comparator.comparing(a -> a.getEffectType().value().getName().getString());
	private final List<StatusEffectInstance> effectsBuffer = new ArrayList<>();
	private final List<String> keyBuffer = new ArrayList<>();
	private final List<String> textBuffer = new ArrayList<>();
	private final List<String> iconBuffer = new ArrayList<>();

	private final ThemeManager themeManager;
	private Color bgColor;
	private Color textColor;
	private Color negativeColor;
    private Color highlightColor;

	public final BooleanSetting showNegative = new BooleanSetting("ShowNegative", true);
	public final BooleanSetting highlightLowDuration = new BooleanSetting("HighlightLowDuration", true);

	public Potions() {
		super("Potions");
		this.themeManager = ThemeManager.getInstance();
		applyTheme(themeManager.getCurrentTheme());
		themeManager.addThemeChangeListener(this);
		this.getSettings().add(showNegative);
		this.getSettings().add(highlightLowDuration);
	}

	@Override
	public void onDisable() {
		themeManager.removeThemeChangeListener(this);
		super.onDisable();
	}

	@Override
	public void onThemeChanged(ThemeManager.Theme theme) {
		applyTheme(theme);
	}

	private void applyTheme(ThemeManager.Theme theme) {
		this.bgColor = new Color(30, 30, 30, 240);
		Color c = theme.getAccentColor();
        this.textColor = new Color(c.getRed(), c.getGreen(), c.getBlue(), 255);
		this.negativeColor = new Color(200, 80, 80, 220);
        this.highlightColor = theme.getAccentColor();
	}

	@Override
	public void onRender2D(EventRender2D e) {
        if (fullNullCheck() || closed()) return;
        Perf.tryBeginFrame();
        try (var __ = Perf.scopeCpu("Potions.onRender2D")) {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

		Collection<StatusEffectInstance> raw = player.getStatusEffects();
		boolean hasAny = !raw.isEmpty();
		boolean chatOpen = mc.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen;
		// Без fade: всегда полная альфа для контента и фона
		int contentHudAlpha = 255;

		// Сбор активных эффектов и стабилизация порядка (по названию)
		List<StatusEffectInstance> effects = effectsBuffer;
		effects.clear();
		for (StatusEffectInstance eff : raw) {
			StatusEffect type = eff.getEffectType().value();
			if (!type.isBeneficial() && !showNegative.getValue()) continue;
			effects.add(eff);
		}
		effects.sort(EFFECT_NAME_ORDER);

		// Построим списки ключей/текстов/иконок
		List<String> keys = keyBuffer;
		List<String> texts = textBuffer;
		List<String> icons = iconBuffer;
		keys.clear();
		texts.clear();
		icons.clear();
		for (StatusEffectInstance eff : effects) {
			StatusEffect type = eff.getEffectType().value();
			String name = type.getName().getString();
			String level = eff.getAmplifier() > 0 ? " " + toRoman(eff.getAmplifier() + 1) : "";
			String display = name + level;
			Identifier rid = Registries.STATUS_EFFECT.getId(type);
			String effectKey = rid == null ? "" : rid.getPath();
			String key = effectKey; // ключ по типу
			keys.add(key);
			texts.add(display);
			icons.add(effectKey);
			lastText.put(key, display);
			lastIconKey.put(key, effectKey);
		}

		// Режим предпросмотра в чате — показать пример, если эффектов нет
		boolean previewMode = chatOpen && keys.isEmpty();
		if (previewMode) {
			keys.add("speed");
			texts.add("Speed II");
			icons.add("speed");
		}

		// Обновляем цели анимации для активных/неактивных
		for (String k : itemAlpha.keySet()) {
			boolean active = keys.contains(k);
			// При очистке — сразу 0
			itemAlpha.get(k).animate(active ? 1f : 0f, (active ? 90 : (hasAny ? 70 : 0)));
		}
		for (String k : keys) {
			itemAlpha.computeIfAbsent(k, kk -> new InfinityAnimation(Easing.OUT_QUAD)).animate(1f, 90);
		}

		// Вычисляем размеры (целевые)
		float posX = getX();
		float posY = getY();
		float uiScale = 0.86f;
		float headerH = 17.4f * uiScale;
		float spacing = 2.5f * uiScale;
		float rowH = 14.4f * uiScale;
		float pad = 3.5f * uiScale;
		float icon = 12.2f * uiScale;
		float font = 7.6f * uiScale;
		float titleFont = 8.4f * uiScale;
		float yAdjust = -1.3f * uiScale;

		float targetWidth = 88f;
		for (int i = 0; i < keys.size(); i++) {
			float w = icon + 2 * pad + Fonts.MEDIUM.getWidth(texts.get(i), font) + 26f * uiScale;
			targetWidth = Math.max(targetWidth, w);
		}

		// Считаем видимые строки: активные как 1
		float visibleRows = keys.size();
		if (previewMode) visibleRows = 1f;
		float targetHeight = headerH + spacing + Math.max(0, visibleRows) * rowH;

		// IMPORTANT: grow the glass BEFORE drawing a newly appeared effect.
		// Only shrinking is animated. This prevents the row from appearing outside
		// of the panel for a few frames (the bug visible on the screenshot).
		float oldHeight = heightAnim.getValue();
		float oldWidth = widthAnim.getValue();
		if (targetHeight > oldHeight + 0.25f) heightAnim.snap(targetHeight);
		else heightAnim.animate(targetHeight, 95);
		if (targetWidth > oldWidth + 0.25f) widthAnim.snap(targetWidth);
		else widthAnim.animate(targetWidth, 95);
		float currentHeight = Math.max(targetHeight, heightAnim.getValue());
		float currentWidth = Math.max(targetWidth, widthAnim.getValue());

		// Не используем fade для скрытия — рисуем во время схлопывания
		boolean nothingVisible = false;

		// Обновляем границы элемента для корректного перетаскивания/хитбокса
		float hudScale = getHudScale();
        setBounds(getX(), getY(), currentWidth * hudScale, Math.max(headerH + spacing, currentHeight) * hudScale);
		if (nothingVisible) {
			super.onRender2D(e);
			return;
		}

		pushHudScale(e.getContext(), posX, posY);

		float liquidTime = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
		LiquidGlassUtil.drawLiquidGlass(
				e.getContext(), posX, posY, currentWidth, currentHeight, liquidTime,
				4.2f, 0.12f, 0.0012f, 6.0f * uiScale, 0.42f, 1.022f
		);

        String headerTitle = net.minecraft.client.resource.language.I18n.translate("hud.potions.title");
        Color headerAccent = themeManager.getCurrentTheme().getAccentColor();
        float headerIconSize = 11.2f * uiScale;
        float headerIconGap = 2.8f * uiScale;
        float headerTextWidth = Fonts.MEDIUM.getWidth(headerTitle, titleFont);
        float headerGroupWidth = headerIconSize + headerIconGap + headerTextWidth;
        float headerGroupX = posX + (currentWidth - headerGroupWidth) / 2f;

        Render2D.drawTexture(
                e.getContext().getMatrices(),
                headerGroupX,
                posY + (headerH - headerIconSize) / 2f,
                headerIconSize,
                headerIconSize,
                0.0f,
                POTION_HEADER_ICON,
                new Color(
                        headerAccent.getRed(),
                        headerAccent.getGreen(),
                        headerAccent.getBlue(),
                        contentHudAlpha
                )
        );

        Render2D.drawFont(
                e.getContext().getMatrices(),
                Fonts.MEDIUM.getFont(titleFont),
                headerTitle,
                headerGroupX + headerIconSize + headerIconGap,
                posY + headerH/2f - Fonts.MEDIUM.getHeight(titleFont)/2f,
                new Color(
                        headerAccent.getRed(),
                        headerAccent.getGreen(),
                        headerAccent.getBlue(),
                        contentHudAlpha
                )
        );

		float curY = posY + headerH + spacing;

		// 1) Активные строки (или предпросмотр)
		for (int i = 0; i < keys.size(); i++) {
			String k = keys.get(i);
			InfinityAnimation anim = previewMode ? null : itemAlpha.get(k);
			// Всегда рисуем сразу
			float a = 1f;
			if (a <= 0.01f) a = 0.01f;
			float xOffset = (previewMode ? 0f : 2f);
			int alpha = (int)(contentHudAlpha * Math.max(a, 0.85f));

			StatusEffectInstance eff = previewMode ? null : effects.get(i);
			StatusEffect type = eff == null ? null : eff.getEffectType().value();
			boolean negative = type != null && !type.isBeneficial();
			boolean low = eff != null && !eff.isInfinite() && eff.getDuration() <= 200;
			if (low && highlightLowDuration.getValue()) {
				float pulse = 0.82f + 0.18f * (0.5f + 0.5f *
						(float) Math.sin(System.currentTimeMillis() / 180.0));
				alpha = Math.max(1, Math.min(255, (int) (alpha * pulse)));
			}
            // Beneficial effects always follow the selected client theme.
            // Low duration may still be used for emphasis, but never changes
            // the buff to the old hard-coded yellow color.
            Color themeAccent = themeManager.getCurrentTheme().getAccentColor();
            Color draw = previewMode
                    ? themeAccent
                    : (negative
                        ? negativeColor.brighter()
                        : themeAccent);

			String iconKeyActive = icons.get(i) == null ? "" : icons.get(i);
			// Фиксированная схема: иконка слева, текст справа от иконки независимо от стороны экрана
			float iconX = (posX + pad + xOffset);
			float rowCenterY = curY + yAdjust + rowH / 2f;
			String nameText = texts.get(i) == null ? "" : texts.get(i);
			float nameW = Fonts.MEDIUM.getWidth(nameText, font);
			float nameTextX = (iconX + icon + pad);
			float nameTextY = rowCenterY - Fonts.MEDIUM.getHeight(font) / 2f;

			if (!iconKeyActive.isEmpty()) {
				Identifier tex = Identifier.of("minecraft", "textures/mob_effect/" + iconKeyActive + ".png");
				Render2D.drawTexture(e.getContext().getMatrices(), iconX, curY + yAdjust + (rowH - icon)/2f,
						icon, icon, 2f, 0f, 0f, 1f, 1f, tex,
						new Color(draw.getRed(), draw.getGreen(), draw.getBlue(), alpha));
			}

			if (!nameText.isBlank()) {
				Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(font), nameText,
						nameTextX, nameTextY,
						new Color(draw.getRed(), draw.getGreen(), draw.getBlue(), alpha));
			}

			// Таймер в собственной плашке в общем правом столбике
			boolean infiniteDuration = !previewMode && eff != null && eff.isInfinite();
			String t = previewMode ? "0:30" : (eff == null || infiniteDuration ? "" : formatDuration(eff));
			if (infiniteDuration || !t.isBlank()) {
				float tw = infiniteDuration ? mc.textRenderer.getWidth("∞") : Fonts.MEDIUM.getWidth(t, font);
				float pillW = tw + (infiniteDuration ? 5.2f : 5f) * uiScale;
				float pillH = rowH - 3.4f * uiScale;
				// Единая правая колонка для всех строк: выравниваем по правому краю панели
				float rightEdge = posX + currentWidth - pad + 1f * uiScale;
				float pillX = rightEdge - pillW;
				float pillY = curY + yAdjust + 1.7f * uiScale;
				Color pillAccent = themeManager.getCurrentTheme().getAccentColor();
				if (infiniteDuration) {
					// Strong themed rim and dark center guarantee contrast even for
					// the default white theme on a bright LiquidGlass background.
					Render2D.drawRoundedRect(e.getContext().getMatrices(), pillX, pillY,
							pillW, pillH, 2.4f * uiScale,
							new Color(pillAccent.getRed(), pillAccent.getGreen(), pillAccent.getBlue(), 155));
					Render2D.drawRoundedRect(e.getContext().getMatrices(),
							pillX + 0.9f * uiScale, pillY + 0.9f * uiScale,
							pillW - 1.8f * uiScale, pillH - 1.8f * uiScale, 1.8f * uiScale,
							new Color(7, 9, 13, 225));
				} else {
					Render2D.drawRoundedRect(e.getContext().getMatrices(), pillX, pillY,
							pillW, pillH, 1.8f * uiScale,
							new Color(pillAccent.getRed(), pillAccent.getGreen(), pillAccent.getBlue(), 48));
				}
				Color timerColor = new Color(draw.getRed(), draw.getGreen(), draw.getBlue(), alpha);
				if (infiniteDuration) {
					// Use Minecraft's own font for the actual Unicode infinity glyph.
					// The custom MSDF font does not contain it and must not be used here.
					int infinityX = Math.round(pillX + (pillW - tw) * 0.5f);
					int infinityY = Math.round(rowCenterY - mc.textRenderer.fontHeight * 0.5f);
					e.getContext().drawText(mc.textRenderer, "∞", infinityX, infinityY,
							timerColor.getRGB(), true);
				} else {
					Render2D.drawFont(e.getContext().getMatrices(), Fonts.MEDIUM.getFont(font), t,
							pillX + (pillW - tw)/2f, rowCenterY - Fonts.MEDIUM.getHeight(font)/2f,
							timerColor);
				}
			}

			curY += rowH;
		}

		// Убираем рендер исчезающих строк — мгновенное скрытие

		        e.getContext().getMatrices().pop();
        super.onRender2D(e);
        }
    }

	private String toRoman(int number) {
		switch(number) {
			case 1: return "I";
			case 2: return "II";
			case 3: return "III";
			case 4: return "IV";
			case 5: return "V";
			default: return String.valueOf(number);
		}
	}

	private String formatDuration(StatusEffectInstance effect) {
		int ticks = effect.getDuration();
		int seconds = ticks/20;
		int minutes = seconds/60;
		seconds %= 60;
		return String.format("%d:%02d", minutes, seconds);
	}

	private float clamp01(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}
}

package dev.prostovisuals.client.ui.hud.impl;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.client.util.renderer.LiquidGlassUtil;
import dev.prostovisuals.client.util.renderer.Render2D;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stable custom sidebar. Supports vanilla team-specific sidebar slots and keeps
 * a short snapshot during server objective swaps so packet timing never makes
 * the HUD randomly disappear for a frame or two.
 */
public class ScoreboardHUD extends HudElement {
    private static final int MAX_ROWS = 15;
    private Text cachedTitle = Text.empty();
    private List<Text> cachedLines = List.of();
    private Object cachedWorld;

    public ScoreboardHUD() {
        super("Scoreboard");
    }

    @Override
    public void onRender2D(EventRender2D e) {
        if (!isHudEnabled()) {
            setBounds(getX(), getY(), 0f, 0f);
            return;
        }
        renderScoreboard(e);
    }

    private boolean isHudEnabled() {
        try {
            var setting = dev.prostovisuals.prostovisuals.getInstance().getHudManager().getElements().getName("Scoreboard");
            return setting instanceof dev.prostovisuals.modules.settings.impl.BooleanSetting bs && bs.getValue();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void renderScoreboard(EventRender2D e) {
        if (cachedWorld != mc.world) {
            cachedWorld = mc.world;
            cachedTitle = Text.empty();
            cachedLines = List.of();
        }
        Scoreboard scoreboard = mc.world.getScoreboard();
        if (scoreboard == null) return;

        ScoreboardObjective objective = resolveSidebarObjective(scoreboard);
        // Servers often swap/remove sidebar objectives for a packet tick. Keep the
        // last real snapshot for the entire current world so text never blinks out.
        boolean cached = objective == null && !cachedLines.isEmpty();

        if (objective == null && !cached) {
            setBounds(getX(), getY(), 0f, 0f);
            return;
        }

        Text title;
        List<Text> lines = new ArrayList<>(MAX_ROWS);
        if (cached) {
            title = cachedTitle;
            lines.addAll(cachedLines);
        } else {
            title = objective.getDisplayName();
            List<ScoreboardEntry> entries = new ArrayList<>();
            for (ScoreboardEntry entry : scoreboard.getScoreboardEntries(objective)) {
                if (entry != null && !entry.hidden()) entries.add(entry);
            }

            entries.sort(
                    Comparator.comparingInt(ScoreboardEntry::value).reversed()
                            .thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER)
            );

            int amount = Math.min(MAX_ROWS, entries.size());
            for (int i = 0; i < amount; i++) {
                ScoreboardEntry entry = entries.get(i);
                Team team = scoreboard.getScoreHolderTeam(entry.owner());
                Text decorated = Team.decorateName(team, entry.name());
                lines.add(decorated == null ? Text.empty() : decorated);
            }

            cachedTitle = title.copy();
            cachedLines = List.copyOf(lines);
        }

        int maxTextWidth = mc.textRenderer.getWidth(title);
        for (Text line : lines) maxTextWidth = Math.max(maxTextWidth, mc.textRenderer.getWidth(line));

        float padX = 7f;
        float top = 6f;
        float headerH = 17f;
        float rowH = 10f;
        float maxAllowed = Math.max(120f, mc.getWindow().getScaledWidth() - 20f);
        float width = Math.max(100f, Math.min(maxAllowed, maxTextWidth + padX * 2f));
        float height = top + headerH + lines.size() * rowH + 5f;

        float screenW = mc.getWindow().getScaledWidth();
        float screenH = mc.getWindow().getScaledHeight();
        float x = getX();
        float y = getY();

        // PositionSetting stores NORMALIZED coordinates. An older scoreboard
        // patch accidentally wrote pixel coordinates into it, multiplying the
        // position by the screen size on the next frame and throwing the card
        // off-screen. Repair any such saved values automatically.
        float storedX = getPosition().getValue().getX();
        float storedY = getPosition().getValue().getY();
        boolean invalidStored = !Float.isFinite(storedX) || !Float.isFinite(storedY)
                || storedX < 0f || storedX > 1f || storedY < 0f || storedY > 1f;
        boolean outsideScreen = x < -width || y < -height || x > screenW || y > screenH;
        if ((storedX == 0f && storedY == 0f) || invalidStored || outsideScreen) {
            x = Math.max(4f, screenW - width - 10f);
            y = Math.min(Math.max(4f, 48f), Math.max(4f, screenH - height - 4f));
            getPosition().getValue().setX(x / Math.max(1f, screenW));
            getPosition().getValue().setY(y / Math.max(1f, screenH));
        } else {
            // Dynamic server scoreboards may grow after the saved position was
            // chosen. Clamp to the visible area without destroying the saved
            // normalized representation.
            x = Math.max(2f, Math.min(x, Math.max(2f, screenW - width - 2f)));
            y = Math.max(2f, Math.min(y, Math.max(2f, screenH - height - 2f)));
        }

        float scale = getHudScale();
        setBounds(x, y, width * scale, height * scale);
        pushHudScale(e.getContext(), x, y);
        try {
            float t = (float) ((System.nanoTime() / 1_000_000_000.0) % 10000.0);
            LiquidGlassUtil.drawLiquidGlass(
                    e.getContext(), x, y, width, height, t,
                    3.2f, 0.07f, 0.00055f, 9f, 0.34f, 1.012f
            );
            // Keep the scoreboard as real liquid glass. The previous 118-alpha
            // near-black fill was covering the captured scene and made the whole
            // card look like a solid black rectangle. Only a very light neutral
            // tint is kept so text remains readable without killing the glass.
            Render2D.drawRoundedRect(e.getContext().getMatrices(), x + 1f, y + 1f,
                    width - 2f, height - 2f, 8f, new Color(8, 11, 17, 132));

            Color accent = ThemeManager.getInstance().getRenderedAccentColor();
            Render2D.drawRoundedRect(
                    e.getContext().getMatrices(), x + 5f, y + top + 11f,
                    width - 10f, 1f, 0.5f,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210)
            );
        } finally {
            popHudScale(e.getContext());
        }

        e.getContext().getMatrices().push();
        e.getContext().getMatrices().translate(x, y, 0f);
        e.getContext().getMatrices().scale(scale, scale, 1f);
        e.getContext().getMatrices().translate(-x, -y, 0f);
        try {
            Text safeTitle = title == null ? Text.empty() : title;
            int titleX = Math.round(x + (width - mc.textRenderer.getWidth(safeTitle)) * 0.5f);
            int titleY = Math.round(y + top);
            // Render the Text object itself so the server's original formatting/colors survive.
            e.getContext().drawTextWithShadow(
                    mc.textRenderer, safeTitle, titleX, titleY,
                    0xFFFFFFFF
            );

            float yy = y + top + headerH;
            for (Text line : lines) {
                if (line != null && !line.getString().isEmpty()) {
                    // Keep HolyWorld/vanilla team prefix colors instead of flattening everything to white.
                    e.getContext().drawTextWithShadow(
                            mc.textRenderer, line, Math.round(x + padX), Math.round(yy), 0xFFFFFFFF
                    );
                }
                yy += rowH;
            }
        } finally {
            e.getContext().getMatrices().pop();
        }

        super.onRender2D(e);
    }

    private ScoreboardObjective resolveSidebarObjective(Scoreboard scoreboard) {
        if (mc.player != null) {
            Team playerTeam = mc.player.getScoreboardTeam();
            if (playerTeam != null) {
                ScoreboardDisplaySlot teamSlot = ScoreboardDisplaySlot.fromFormatting(playerTeam.getColor());
                if (teamSlot != null) {
                    ScoreboardObjective teamObjective = scoreboard.getObjectiveForSlot(teamSlot);
                    if (teamObjective != null) return teamObjective;
                }
            }
        }
        return scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
    }
}

package dev.prostovisuals.modules.impl.render;

import dev.prostovisuals.client.events.impl.EventRender2D;
import dev.prostovisuals.client.managers.HolyWorldEventsManager;
import dev.prostovisuals.client.util.renderer.Render2D;
import dev.prostovisuals.client.util.renderer.fonts.Fonts;
import dev.prostovisuals.client.managers.ThemeManager;
import dev.prostovisuals.modules.api.Category;
import dev.prostovisuals.modules.api.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.network.ServerInfo;

import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HolyWorldEvents extends Module {
    private static final Pattern NUMBER = Pattern.compile("(?:^|\\D)(\\d{1,3})(?:\\D|$)");

    public HolyWorldEvents() {
        super("HolyWorld Events", Category.Render, "Shows the active HolyWorld event for the connected anarchy.");
    }

    @EventHandler
    public void onRender2D(EventRender2D event) {
        if (fullNullCheck()) return;

        HolyWorldEventsManager manager = HolyWorldEventsManager.getInstance();
        String serverId = detectCurrentServerId();
        if (serverId == null) return;

        List<HolyWorldEventsManager.EventInfo> matches = manager.getEvents().stream()
                .filter(e -> e.serverId().equals(serverId))
                .toList();
        if (matches.isEmpty()) return;

        float x = 8f;
        float y = 8f;
        float w = 132f;
        float h = 30f + Math.min(matches.size(), 2) * 17f;
        Color accent = ThemeManager.getInstance().getCurrentTheme().getAccentColor();

        Render2D.drawRoundedRect(event.getContext().getMatrices(), x, y, w, h, 8f,
                new Color(8, 10, 14, 205));
        Render2D.drawRoundedRect(event.getContext().getMatrices(), x, y, 2.5f, h, 1.2f,
                new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 240));

        Render2D.drawFont(event.getContext().getMatrices(), Fonts.BOLD.getFont(7.5f),
                "HolyWorld Events", x + 9f, y + 7f, Color.WHITE);

        int row = 0;
        for (HolyWorldEventsManager.EventInfo info : matches) {
            if (row >= 2) break;
            Color rarity = rarityColor(info.rarity(), accent);
            String title = info.displayName().isEmpty() ? info.id() : info.displayName();
            Render2D.drawFont(event.getContext().getMatrices(), Fonts.MEDIUM.getFont(6.8f),
                    title, x + 9f, y + 19f + row * 17f,
                    new Color(235, 237, 242, 245));
            Render2D.drawFont(event.getContext().getMatrices(), Fonts.REGULAR.getFont(5.8f),
                    info.serverName() + " • " + info.rarity(), x + 9f, y + 28f + row * 17f,
                    rarity);
            row++;
        }
    }

    private String detectCurrentServerId() {
        ServerInfo server = mc.getCurrentServerEntry();
        if (server == null || server.address == null) return null;

        String address = server.address.toLowerCase(Locale.ROOT);
        if (!address.contains("holyworld")) return null;

        Matcher matcher = NUMBER.matcher(address);
        String lastNumber = null;
        while (matcher.find()) lastNumber = matcher.group(1);
        if (lastNumber == null) return null;

        try {
            int number = Integer.parseInt(lastNumber);
            return "LITE_ANARCHY_" + number;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Color rarityColor(String rarity, Color fallback) {
        String r = rarity == null ? "" : rarity.toLowerCase(Locale.ROOT);
        if (r.contains("myth") || r.contains("миф")) return new Color(255, 90, 220, 255);
        if (r.contains("legend") || r.contains("легенд")) return new Color(255, 180, 55, 255);
        if (r.contains("epic") || r.contains("эпич")) return new Color(180, 95, 255, 255);
        if (r.contains("rare") || r.contains("редк")) return new Color(65, 160, 255, 255);
        if (r.contains("deadly") || r.contains("explosive")) return new Color(255, 70, 70, 255);
        return fallback;
    }
}

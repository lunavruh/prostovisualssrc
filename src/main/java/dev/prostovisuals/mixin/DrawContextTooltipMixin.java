package dev.prostovisuals.mixin;

import dev.prostovisuals.client.managers.HolyWorldCoinRateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Final-stage HolyWorld auction tooltip patch.
 *
 * We hook DrawContext instead of ItemStack#getTooltip because HolyWorld sends
 * the auction data as server-provided minecraft:lore/custom_data. By the time
 * DrawContext receives the tooltip, all of that JSON/NBT has already been
 * converted to Text, so this works with both minecraft:lore and the legacy
 * display.Lore copy used by ViaVersion.
 */
@Mixin(DrawContext.class)
public abstract class DrawContextTooltipMixin {
    private static final String PRICE_ONE_LABEL = "Цена за 1 ед.:";
    private static final String PRICE_LABEL = "Цена:";
    private static final String COIN_SUFFIX_MARKER = "❘▍❘)";

    /**
     * drawItemTooltip in 1.21.4 ends up in this overload. Replacing the List
     * argument here means the vanilla tooltip background/positioning is kept,
     * only the two HolyWorld auction lines are extended.
     */
    @ModifyVariable(
            method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/util/Identifier;)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0
    )
    private List<Text> prostovisuals$appendHolyWorldCoinPrices(List<Text> lines) {
        if (lines == null || lines.isEmpty() || !HolyWorldCoinRateManager.hasRate()) {
            return lines;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.currentScreen instanceof HandledScreen<?>)) {
            return lines;
        }

        ArrayList<Text> changed = null;

        for (int i = 0; i < lines.size(); i++) {
            Text line = lines.get(i);
            String plain = line.getString();

            if (plain == null || !plain.contains("¤") || plain.contains(COIN_SUFFIX_MARKER)) {
                continue;
            }

            Long price = extractAuctionPrice(plain);
            if (price == null) {
                continue;
            }

            String coins = HolyWorldCoinRateManager.formatCoins(price);
            if (coins == null) {
                continue;
            }

            if (changed == null) {
                changed = new ArrayList<>(lines);
            }

            MutableText newLine = line.copy();
            newLine.append(Text.literal(" (" + coins + " ❘▍❘)")
                    .setStyle(Style.EMPTY.withColor(0xFF7A00).withItalic(false)));
            changed.set(i, newLine);
        }

        return changed != null ? changed : lines;
    }

    /**
     * Handles the actual HolyWorld text produced by the NBT, for example:
     *   "▍ Цена: 9 984¤"
     *   "▍ Цена за 1 ед.: 156¤"
     */
    private static Long extractAuctionPrice(String rawLine) {
        int labelIndex = rawLine.indexOf(PRICE_ONE_LABEL);
        String label = PRICE_ONE_LABEL;

        if (labelIndex < 0) {
            labelIndex = rawLine.indexOf(PRICE_LABEL);
            label = PRICE_LABEL;
        }

        if (labelIndex < 0) {
            return null;
        }

        int start = labelIndex + label.length();
        int currencyIndex = rawLine.indexOf('¤', start);
        if (currencyIndex < 0) {
            return null;
        }

        long value = 0L;
        boolean foundDigit = false;

        for (int i = start; i < currencyIndex; i++) {
            char c = rawLine.charAt(i);
            if (c >= '0' && c <= '9') {
                foundDigit = true;
                int digit = c - '0';
                if (value > (Long.MAX_VALUE - digit) / 10L) {
                    return null;
                }
                value = value * 10L + digit;
            }
            // Everything else between the label and ¤ (spaces/NBSP/etc.) is
            // intentionally ignored. This matches the server's "9 984¤" lore.
        }

        return foundDigit ? value : null;
    }
}

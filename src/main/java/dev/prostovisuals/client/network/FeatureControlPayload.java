package dev.prostovisuals.client.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.nio.charset.StandardCharsets;

/** Raw UTF-8 JSON payload used by HolyWorld LiteAPI feature-control. */
public record FeatureControlPayload(String json) implements CustomPayload {
    public static final Identifier CHANNEL = Identifier.of("liteapi", "feature-control");
    public static final CustomPayload.Id<FeatureControlPayload> ID = new CustomPayload.Id<>(CHANNEL);
    private static final int MAX_BYTES = 1_048_576;

    public static final PacketCodec<PacketByteBuf, FeatureControlPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                byte[] bytes = value.json().getBytes(StandardCharsets.UTF_8);
                if (bytes.length > MAX_BYTES) {
                    throw new IllegalArgumentException("Feature-control payload is too large: " + bytes.length);
                }
                // LiteAPI protocol expects the custom payload body itself to be UTF-8 JSON.
                // Do not use writeString(): it would prepend a VarInt length to the plugin message.
                buf.writeBytes(bytes);
            },
            buf -> {
                int length = buf.readableBytes();
                if (length < 0 || length > MAX_BYTES) {
                    throw new IllegalArgumentException("Invalid feature-control payload size: " + length);
                }
                byte[] bytes = new byte[length];
                buf.readBytes(bytes);
                return new FeatureControlPayload(new String(bytes, StandardCharsets.UTF_8));
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}

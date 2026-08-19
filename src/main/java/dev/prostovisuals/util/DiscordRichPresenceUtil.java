package dev.prostovisuals.util;

import dev.firstdark.rpc.DiscordRpc;
import dev.firstdark.rpc.enums.ActivityType;
import dev.firstdark.rpc.models.DiscordRichPresence;
import dev.firstdark.rpc.enums.ErrorCode;
import dev.firstdark.rpc.models.User;
import dev.firstdark.rpc.handlers.RPCEventHandler;
import net.minecraft.client.MinecraftClient;

public class DiscordRichPresenceUtil {
    private static final String DEFAULT_APP_ID = "1420446107246268511";

    private static volatile boolean running = false;
    private static Thread rpcThread;
    private static DiscordRpc rpc;

    public static String state;

    public static synchronized void discordrpc() {
        startDiscord(null);
    }

    public static synchronized void startDiscord(String applicationId) {
        if (running) return;
        String appId = applicationId;
        if (appId == null || appId.isEmpty()) {
            appId = System.getProperty("discord.app.id", System.getenv("DISCORD_APP_ID"));
        }
        if (appId == null || appId.isEmpty()) appId = DEFAULT_APP_ID;

        rpc = new DiscordRpc();
        rpc.setDebugMode(false);

        RPCEventHandler handler = new RPCEventHandler() {
            @Override
            public void ready(User user) {
                running = true;
                pushPresence();
            }

            @Override
            public void disconnected(ErrorCode errorCode, String message) {
                running = false;
            }

            @Override
            public void errored(ErrorCode errorCode, String message) {
            }
        };

        try {
            rpc.init(appId, handler, false);
        } catch (Throwable t) {
            return;
        }

        rpcThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (running) pushPresence();
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {}
            }
        }, "Discord-RPC-FirstDark-Thread");
        rpcThread.setDaemon(true);
        rpcThread.start();
    }

    public static synchronized void shutdownDiscord() {
        running = false;
        if (rpcThread != null) {
            rpcThread.interrupt();
            rpcThread = null;
        }
        try { if (rpc != null) rpc.shutdown(); } catch (Throwable ignored) {}
        rpc = null;
    }

    private static void pushPresence() {
        if (rpc == null) return;

        String details = "";
        String state = "";
        try {
            MinecraftClient mc = MinecraftClient.getInstance();

            if (mc != null && mc.world != null && mc.player != null && mc.getCurrentServerEntry() != null) {
                String nickname = mc.getSession() != null
                        ? mc.getSession().getUsername()
                        : mc.player.getName().getString();

                String server = mc.getCurrentServerEntry().address;
                if (server == null || server.isBlank()) server = "Unknown";

                details = "Ник: " + nickname;
                state = "Сервер: " + server;
            } else {
                details = "Находится в Главном Меню";
                state = "";
            }
        } catch (Throwable ignored) {
            details = "Находится в Главном Меню";
        }

        DiscordRichPresence presence = DiscordRichPresence.builder()
                .details(details)
                .state(state)
                .largeImageText("ProstoVisuals • Minecraft 1.21.4")
                .smallImageText("ProstoVisuals")
                .activityType(ActivityType.PLAYING)
                .button(DiscordRichPresence.RPCButton.of("Скачать", "https://t.me/ProstoVisuals"))
                .build();

        try {
            rpc.updatePresence(presence);
        } catch (Throwable ignored) {}
    }
}
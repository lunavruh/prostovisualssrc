package wtf.wyvern.base.discord;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import net.minecraft.util.Identifier;
import ru.nexusguard.protection.annotations.Native;
import wtf.wyvern.Wyvern;
import wtf.wyvern.utility.interfaces.IMinecraft;

import java.io.IOException;

public class DiscordManager implements IMinecraft {
    private boolean running = false;
    private DiscordInfo info = new DiscordInfo("Unknown", "", "");
    private Identifier avatarId;

    public DiscordManager() {
        this.initRPC();
    }

    @Native
    private void initRPC() {
        try {

            DiscordIPC.start(1504164549832736778L, null);

            this.running = true;
            this.updatePresence();

            Thread daemon = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(15000L);
                        if (running) updatePresence();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }, "Discord-RPC-Daemon");
            daemon.setDaemon(true);
            daemon.start();

        } catch (Throwable e) {

            this.running = false;
        }
    }

    @Native
    public void updatePresence() {
        if (!running) return;
        try {
            RichPresence presence = new RichPresence();

            presence.setDetails("Name : Release");
            presence.setState("UID : 1");
            presence.setLargeImage("logo", "Wyvern");
            presence.setStart(System.currentTimeMillis() / 1000L);

            DiscordIPC.setActivity(presence);
        } catch (Exception e) {

        }
    }

    @Native
    public void stopRPC() {
        this.running = false;
        try {
            DiscordIPC.stop();
        } catch (Exception ignored) {}
    }

    @Native
    public void load() throws IOException {
    }

    public void setInfo(DiscordInfo info) {
        this.info = info;
    }

    public boolean isRunning() {
        return this.running;
    }

    public DiscordInfo getInfo() {
        return this.info;
    }

    public Identifier getAvatarId() {
        return this.avatarId;
    }

    public static record DiscordInfo(String userName, String avatarUrl, String userId) {}
}
package dev.prostovisuals.client.managers;

import dev.prostovisuals.client.events.impl.EventKey;
import dev.prostovisuals.client.ui.holyworld.HolyWorldEventsScreen;
import dev.prostovisuals.client.util.Wrapper;
import dev.prostovisuals.prostovisuals;
import meteordevelopment.orbit.EventHandler;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone HolyWorld events menu controller. Not a toggleable module. */
public final class HolyWorldEventsOverlayManager implements Wrapper {
    private final Path bindFile = mc.runDirectory.toPath().resolve("prostovisuals").resolve("holyworld-events-bind.txt");
    private int bind = GLFW.GLFW_KEY_F10;

    public HolyWorldEventsOverlayManager() {
        load();
        prostovisuals.getInstance().getEventHandler().subscribe(this);
    }

    public int getBind() { return bind; }

    public void setBind(int key) {
        bind = key < 0 ? GLFW.GLFW_KEY_F10 : key;
        save();
    }

    @EventHandler
    public void onKey(EventKey e) {
        if (e.getAction() != GLFW.GLFW_PRESS || e.getKey() != bind) return;
        if (mc.currentScreen != null) return;
        mc.setScreen(new HolyWorldEventsScreen(null, this));
    }

    private void load() {
        try {
            if (Files.isRegularFile(bindFile)) bind = Integer.parseInt(Files.readString(bindFile, StandardCharsets.UTF_8).trim());
        } catch (Throwable ignored) { bind = GLFW.GLFW_KEY_F10; }
    }

    private void save() {
        try {
            Files.createDirectories(bindFile.getParent());
            Files.writeString(bindFile, Integer.toString(bind), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {}
    }
}

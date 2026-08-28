package wtf.wyvern;

import java.io.File;
import lombok.Generated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import ru.nexusguard.protection.annotations.Native;
import wtf.wyvern.base.comand.CommandManager;
import wtf.wyvern.base.config.ConfigManager;
import wtf.wyvern.base.discord.DiscordManager;
import wtf.wyvern.base.filemanager.impl.FriendManager;
import wtf.wyvern.base.filemanager.impl.StaffManager;
import wtf.wyvern.base.macro.MacroManager;
import wtf.wyvern.base.modules.ModuleManager;
import wtf.wyvern.base.notify.NotifyManager;
import wtf.wyvern.base.request.ScriptManager;
import wtf.wyvern.base.theme.ThemeManager;
import wtf.wyvern.base.waypoint.WaypointManager;
import wtf.wyvern.client.hud.elements.ToggleNotify;
import wtf.wyvern.client.screens.menu.MenuScreen;
import wtf.wyvern.utility.game.server.ServerHandler;
import wtf.wyvern.utility.render.display.shader.DrawUtil;
import wtf.wyvern.utility.render.display.shader.GlProgram;
import wtf.wyvern.utility.render.shader.ShaderHandsRenderer;

public enum Wyvern implements ClientModInitializer {
    INSTANCE;

    public static final String NAME = "Wyvern";
    public static final String VER = "";
    public static final String TYPE = "DEV";
    private static final String MOD_ID = "Wyvern".toLowerCase();
    public static File DIRECTORY;
    private ModuleManager moduleManager;
    private ThemeManager themeManager;
    private MenuScreen menuScreen;
    private ScriptManager scriptManager;
    private ServerHandler serverHandler;
    private FriendManager friendManager;
    private MacroManager macroManager;
    private StaffManager staffManager;
    private WaypointManager waypointManager;
    private NotifyManager notifyManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private DiscordManager discordManager;
    private ToggleNotify toggleNotify;
    private boolean initialized = false;

    @Override
    public void onInitializeClient() {
        try {
            init();
        } catch (Exception e) {

            throw e;
        }
    }

    @Native
    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        try {
            DIRECTORY = new File(MinecraftClient.getInstance().runDirectory, "Wyvern");
            if (!DIRECTORY.exists()) {
                DIRECTORY.mkdirs();
            }

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                getInstance().shutdown();
            }));

            this.friendManager = new FriendManager();
            this.macroManager = new MacroManager();
            this.staffManager = new StaffManager();
            this.notifyManager = new NotifyManager();
            this.serverHandler = new ServerHandler();
            this.themeManager = new ThemeManager();
            this.moduleManager = new ModuleManager();
            this.configManager = new ConfigManager();
            this.commandManager = new CommandManager();
            this.scriptManager = new ScriptManager();
            // ProstoVisual owns Discord Rich Presence in the merged client.
            // Do not start Wyvern's legacy RPC (it would overwrite the ProstoVisual presence).
            this.discordManager = null;
            this.toggleNotify = new ToggleNotify();
            this.waypointManager = new WaypointManager();
            this.menuScreen = new MenuScreen();
            ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return Wyvern.id("after_shader_load");
                }

                @Override
                public void reload(ResourceManager manager) {
                    GlProgram.loadAndSetupPrograms();
                }
            });
            DrawUtil.initializeShaders();
            ShaderHandsRenderer.initializeShaders();
            wtf.wyvern.utility.render.shader.ShaderBlockRenderer.initializeShaders();
        } catch (Exception e) {

            throw new RuntimeException("Wyvern initialization failed", e);
        }
    }

    @Native
    public void shutdown() {
        // Shutdown hook can run after a partial/failed initialization. Keep it safe so
        // the original startup exception is not masked by a second NPE.
        try { if (this.friendManager != null) this.friendManager.save(); } catch (Throwable ignored) {}
        try { if (this.staffManager != null) this.staffManager.save(); } catch (Throwable ignored) {}
        try { if (this.configManager != null) this.configManager.save(); } catch (Throwable ignored) {}
        try { if (this.macroManager != null) this.macroManager.save(); } catch (Throwable ignored) {}
        try { if (this.discordManager != null) this.discordManager.stopRPC(); } catch (Throwable ignored) {}
    }

    public static Identifier id(String path) {
        return Identifier.of("wyvern", path);
    }

    public static Wyvern getInstance() {
        return INSTANCE;
    }


    @Generated
    public ModuleManager getModuleManager() {
        return this.moduleManager;
    }

    @Generated
    public ThemeManager getThemeManager() {
        return this.themeManager;
    }

    @Generated
    public MenuScreen getMenuScreen() {
        return this.menuScreen;
    }

    @Generated
    public ScriptManager getScriptManager() {
        return this.scriptManager;
    }

    @Generated
    public ServerHandler getServerHandler() {
        return this.serverHandler;
    }

    @Generated
    public FriendManager getFriendManager() {
        return this.friendManager;
    }

    @Generated
    public MacroManager getMacroManager() {
        return this.macroManager;
    }

    @Generated
    public StaffManager getStaffManager() {
        return this.staffManager;
    }


    @Generated
    public WaypointManager getWaypointManager() {
        return this.waypointManager;
    }

    @Generated
    public NotifyManager getNotifyManager() {
        return this.notifyManager;
    }

    @Generated
    public CommandManager getCommandManager() {
        return this.commandManager;
    }

    @Generated
    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    @Generated
    public DiscordManager getDiscordManager() {
        return this.discordManager;
    }

    @Generated
    public ToggleNotify getToggleNotify() {
        return this.toggleNotify;
    }

    private static Wyvern[] $values() {
        return new Wyvern[]{INSTANCE};
    }
}
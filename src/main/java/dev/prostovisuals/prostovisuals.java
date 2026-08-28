package dev.prostovisuals;


import dev.prostovisuals.client.managers.*;
import dev.prostovisuals.client.ui.mainmenu.MainMenu;
import dev.prostovisuals.client.util.Wrapper;
import meteordevelopment.orbit.EventBus;
import meteordevelopment.orbit.IEventBus;
import dev.prostovisuals.client.ui.clickgui.ClickGui;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import lombok.*;

import java.io.File;
import java.lang.invoke.MethodHandles;

@Getter
public class  prostovisuals implements ModInitializer, Wrapper {

    @Getter private static prostovisuals instance;

    private IEventBus eventHandler;
    private long initTime;
    private ModuleManager moduleManager;
    private CommandManager commandManager;
    private ConfigManager configManager;
    private AutoSaveManager autoSaveManager;
    private NotifyManager notifyManager;
    private PerformanceManager performanceManager;
    private ClickGui clickGui;
    private CosmeticsManager cosmeticsManager;
    private HudManager hudManager;
    private dev.prostovisuals.client.managers.AltManager altManager;
    private MainMenu mainmenu;
    private HolyWorldEventsOverlayManager holyWorldEventsOverlayManager;
    private dev.prostovisuals.client.ui.hud.impl.WaypointOverlay waypointOverlay;

    public static Logger LOGGER = LogManager.getLogger(prostovisuals.class);
    private final File globalsDir = new File(mc.runDirectory, "prostovisuals");
    private final File configsDir = new File(globalsDir, "configs");

    @Override
    public void onInitialize() {
        LOGGER.info("[prostovisuals] Starting initialization.");
        initTime = System.currentTimeMillis();
        instance = this;

        createDirs(globalsDir, configsDir);
        eventHandler = new EventBus();

        eventHandler.registerLambdaFactory("dev.prostovisuals",
                (lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup())
        );

        FriendsManager.init(globalsDir);
        HolyWorldCoinRateManager.start();
        AltManager.init(globalsDir);
        String lastAlt = AltManager.getLastUsedNickname();
        if (lastAlt != null && !lastAlt.isEmpty()) {
            AltManager.applyNickname(lastAlt);
        }

        notifyManager = new NotifyManager();
        performanceManager = new PerformanceManager();
        moduleManager = new ModuleManager();
        HolyWorldFeatureControlManager.getInstance().init();
        holyWorldEventsOverlayManager = new HolyWorldEventsOverlayManager();
        commandManager = new CommandManager();
        configManager = new ConfigManager();
        autoSaveManager = new AutoSaveManager();
        cosmeticsManager = new CosmeticsManager();
        dev.prostovisuals.client.spatial.MonitorsController.getInstance().init();
        clickGui = new dev.prostovisuals.client.ui.clickgui.OneClientClickGui();
        hudManager = new HudManager();
        mainmenu = null; // Wyvern main menu/HUD stay authoritative

        // Загружаем автоматически сохраненную конфигурацию
        autoSaveManager.loadAutoSave();

        LOGGER.info("[prostovisuals] Successfully initialized for {} ms.", System.currentTimeMillis() - initTime);
    }

    private void createDirs(File... file) {
        for (File f : file) f.mkdirs();
    }

    public static Identifier id(String texture) {
        return Identifier.of("prostovisuals", texture);
    }
}
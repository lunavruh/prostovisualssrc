package dev.prostovisuals.client.managers;

import dev.prostovisuals.prostovisuals;
import dev.prostovisuals.modules.api.Module;
import dev.prostovisuals.modules.settings.Setting;
import dev.prostovisuals.client.ui.hud.HudElement;
import dev.prostovisuals.modules.settings.impl.BooleanSetting;
import dev.prostovisuals.modules.settings.impl.NumberSetting;
import dev.prostovisuals.modules.settings.impl.StringSetting;
import dev.prostovisuals.modules.settings.impl.EnumSetting;
import dev.prostovisuals.modules.settings.impl.ColorSetting;
import dev.prostovisuals.modules.settings.impl.ListSetting;
import dev.prostovisuals.modules.settings.impl.BindSetting;
import dev.prostovisuals.client.util.Wrapper;
import com.google.gson.*;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import dev.prostovisuals.client.managers.ThemeManager;
import net.minecraft.client.MinecraftClient;

@Getter
public class ConfigManager implements Wrapper {
    
    private static final Logger LOGGER = LogManager.getLogger(ConfigManager.class);
    private final Gson gson;
    private final File configsDir;
    private final Map<String, ConfigData> configCache = new HashMap<>();
    
    public ConfigManager() {
        this.configsDir = new File(prostovisuals.getInstance().getGlobalsDir(), "configs");
        if (!this.configsDir.exists()) {
            this.configsDir.mkdirs();
        }
        LOGGER.info("Путь к папке конфигураций: {}", this.configsDir.getAbsolutePath());
        
        // Настройка Gson для красивого форматирования
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .setLenient()
                .create();
    }
    
    /**
     * Сохраняет текущую конфигурацию в файл
     */
    public CompletableFuture<Boolean> saveConfig(String configName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConfigData configData = new ConfigData();
                
                // Persist command prefix
                try {
                    String pref = prostovisuals.getInstance().getCommandManager().getPrefix();
                    configData.setCommandPrefix(pref);
                } catch (Exception ignored) {}
                
                // Сохраняем состояние всех модулей
                for (Module module : prostovisuals.getInstance().getModuleManager().getModules()) {
                    ModuleData moduleData = new ModuleData();
                    moduleData.setToggled(module.isToggled());
                    moduleData.setBind(module.getBind());
                    
                    // Сохраняем все настройки модуля
                    Map<String, Object> settings = new HashMap<>();
                    for (Setting<?> setting : module.getSettings()) {
                        Object value = setting.getValue();
                        
                        // Специальная обработка для разных типов настроек
                        if (setting instanceof ColorSetting) {
                            // Сохраняем цвет как hex строку
                            value = String.format("%06X", (Integer) value);
                        } else if (setting instanceof BindSetting) {
                            // Сохраняем бинд как строку "key:isMouse:mode"
                            dev.prostovisuals.modules.settings.api.Bind bind = (dev.prostovisuals.modules.settings.api.Bind) value;
                            String modeName = bind.getMode() != null ? bind.getMode().name() : dev.prostovisuals.modules.settings.api.Bind.Mode.TOGGLE.name();
                            value = bind.getKey() + ":" + bind.isMouse() + ":" + modeName;
                        } else if (setting instanceof ListSetting) {
                            // Сохраняем ListSetting как Map с именами и значениями
                            ListSetting listSetting = (ListSetting) setting;
                            Map<String, Boolean> listValues = new HashMap<>();
                            for (BooleanSetting boolSetting : listSetting.getValue()) {
                                listValues.put(boolSetting.getName(), boolSetting.getValue());
                            }
                            value = listValues;
                        }
                        
                        settings.put(setting.getName(), value);
                    }
                    moduleData.setSettings(settings);
                    
                    configData.getModules().put(module.getName(), moduleData);
                }
                
                // Сохраняем выбранную тему
                configData.setCurrentTheme(ThemeManager.getInstance().getCurrentTheme().getName());
                
                // Сохраняем положение HUD элементов
                Map<String, HudPositionData> hudPositions = new HashMap<>();
                for (HudElement hudElement : prostovisuals.getInstance().getHudManager().getHudElements()) {
                    HudPositionData hudData = new HudPositionData();
                    hudData.setX(hudElement.getPosition().getValue().getX());
                    hudData.setY(hudElement.getPosition().getValue().getY());
                    try {
                        // Determine enabled state from HudManager.elements ListSetting if available
                        dev.prostovisuals.modules.settings.impl.ListSetting elementsList = prostovisuals.getInstance().getHudManager().getElements();
                        dev.prostovisuals.modules.settings.impl.BooleanSetting bs = elementsList.getName(hudElement.getName());
                        boolean enabled = bs != null ? bs.getValue() : hudElement.isToggled();
                        hudData.setEnabled(enabled);
                    } catch (Exception ignored) {
                        hudData.setEnabled(hudElement.isToggled());
                    }
                    hudPositions.put(hudElement.getName(), hudData);
                }
                configData.setHudPositions(hudPositions);
                
                // Сохраняем настройки HUD-элементов
                Map<String, Map<String, Object>> hudSettings = new HashMap<>();
                for (HudElement hudElement : prostovisuals.getInstance().getHudManager().getHudElements()) {
                    Map<String, Object> settings = new HashMap<>();
                    for (Setting<?> setting : hudElement.getSettings()) {
                        Object value = setting.getValue();
                        if (setting instanceof ColorSetting) {
                            value = String.format("%06X", (Integer) value);
                        } else if (setting instanceof ListSetting) {
                            ListSetting listSetting = (ListSetting) setting;
                            Map<String, Boolean> listValues = new HashMap<>();
                            for (BooleanSetting boolSetting : listSetting.getValue()) {
                                listValues.put(boolSetting.getName(), boolSetting.getValue());
                            }
                            value = listValues;
                        } else if (setting instanceof BindSetting) {
                            dev.prostovisuals.modules.settings.api.Bind bind = (dev.prostovisuals.modules.settings.api.Bind) value;
                            String modeName = bind.getMode() != null ? bind.getMode().name() : dev.prostovisuals.modules.settings.api.Bind.Mode.TOGGLE.name();
                            value = bind.getKey() + ":" + bind.isMouse() + ":" + modeName;
                        }
                        settings.put(setting.getName(), value);
                    }
                    hudSettings.put(hudElement.getName(), settings);
                }
                configData.setHudSettings(hudSettings);
                
                // Сохраняем в файл
                File configFile = new File(configsDir, configName + ".simple");
                String json = gson.toJson(configData);
                Files.write(configFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
                
                // Кэшируем конфигурацию
                configCache.put(configName, configData);
                
                LOGGER.info("Конфигурация '{}' успешно сохранена", configName);
                return true;
                
            } catch (Exception e) {
                LOGGER.error("Ошибка при сохранении конфигурации '{}': {}", configName, e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Загружает конфигурацию из файла
     */
    public CompletableFuture<Boolean> loadConfig(String configName) {
        // 1) Чтение файла и парсинг в фоне
        return CompletableFuture.supplyAsync(() -> {
            try {
                File configFile = new File(configsDir, configName + ".simple");
                if (!configFile.exists()) {
                    LOGGER.error("Конфигурация '{}' не найдена", configName);
                    return null;
                }
                String json = Files.readString(configFile.toPath());
                return gson.fromJson(json, ConfigData.class);
            } catch (Exception e) {
                LOGGER.error("Ошибка при чтении конфигурации '{}': {}", configName, e.getMessage());
                return null;
            }
        }).thenCompose(configData -> {
            // 2) Применение строго на рендер-потоке
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            if (configData == null) {
                result.complete(false);
                return result;
            }
            MinecraftClient.getInstance().execute(() -> {
                try {
                    // Apply command prefix first (if present)
                    try {
                        String pref = configData.getCommandPrefix();
                        if (pref != null && !pref.isEmpty()) {
                            prostovisuals.getInstance().getCommandManager().setPrefix(pref);
                        }
                    } catch (Exception ignored) {}
                    // Применяем конфигурацию к модулям
                    for (Map.Entry<String, ModuleData> entry : configData.getModules().entrySet()) {
                        String moduleName = entry.getKey();
                        ModuleData moduleData = entry.getValue();
                        
                        Module module = prostovisuals.getInstance().getModuleManager().getModuleByName(moduleName);
                        if (module != null) {
                            // Применяем состояние модуля
                            if (moduleData.isToggled() != module.isToggled()) {
                                module.setToggled(moduleData.isToggled());
                            }
                            
                            // Применяем бинд
                            if (moduleData.getBind() != null) {
                                module.setBind(moduleData.getBind());
                            }
                            
                            // Применяем настройки
                            for (Map.Entry<String, Object> settingEntry : moduleData.getSettings().entrySet()) {
                                String settingName = settingEntry.getKey();
                                Object value = settingEntry.getValue();
                                
                                Setting<?> setting = module.getSettings().stream()
                                        .filter(s -> s.getName().equals(settingName))
                                        .findFirst()
                                        .orElse(null);
                                
                                if (setting != null) {
                                    try {
                                        // Безопасно устанавливаем значение
                                        setSettingValue(setting, value);
                                    } catch (Exception e) {
                                        LOGGER.warn("Не удалось применить настройку {} для модуля {}: {}", 
                                                settingName, moduleName, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                    
                    // Применяем выбранную тему
                    try {
                        ThemeManager themeManager = ThemeManager.getInstance();
                        ThemeManager.Theme[] availableThemes = themeManager.getAvailableThemes();
                        for (ThemeManager.Theme theme : availableThemes) {
                            if (theme.getName().equals(configData.getCurrentTheme())) {
                                themeManager.setTheme(theme);
                                prostovisuals.getInstance().getEventHandler().post(new dev.prostovisuals.client.events.impl.EventThemeChanged(theme));
                                break;
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Не удалось применить тему {}: {}", configData.getCurrentTheme(), e.getMessage());
                    }
                    
                    // Применяем положение HUD элементов
                    if (configData.getHudPositions() != null) {
                        try {
                            for (Map.Entry<String, HudPositionData> entry : configData.getHudPositions().entrySet()) {
                                String hudName = entry.getKey();
                                HudPositionData hudData = entry.getValue();
                                
                                HudElement hudElement = prostovisuals.getInstance().getHudManager().getHudElements().stream()
                                        .filter(element -> element.getName().equals(hudName))
                                        .findFirst()
                                        .orElse(null);
                                
                                if (hudElement != null) {
                                    hudElement.getPosition().getValue().setX(hudData.getX());
                                    hudElement.getPosition().getValue().setY(hudData.getY());
                                    if (hudData.isEnabled() != hudElement.isToggled()) {
                                        hudElement.setToggled(hudData.isEnabled());
                                    }
                                    // Reflect enabled state back into HudManager.elements list for UI consistency
                                    try {
                                        dev.prostovisuals.modules.settings.impl.ListSetting elementsList = prostovisuals.getInstance().getHudManager().getElements();
                                        dev.prostovisuals.modules.settings.impl.BooleanSetting bs = elementsList.getName(hudName);
                                        if (bs != null && bs.getValue() != hudData.isEnabled()) {
                                            bs.setValue(hudData.isEnabled());
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Не удалось применить позиции HUD: {}", e.getMessage());
                        }
                    }
                    
                    // Применяем настройки HUD-элементов
                    if (configData.getHudSettings() != null) {
                        for (Map.Entry<String, Map<String, Object>> entry : configData.getHudSettings().entrySet()) {
                            String hudName = entry.getKey();
                            Map<String, Object> settings = entry.getValue();
                            HudElement hudElement = prostovisuals.getInstance().getHudManager().getHudElements().stream()
                                    .filter(element -> element.getName().equals(hudName))
                                    .findFirst()
                                    .orElse(null);
                            if (hudElement != null) {
                                for (Map.Entry<String, Object> settingEntry : settings.entrySet()) {
                                    String settingName = settingEntry.getKey();
                                    Object value = settingEntry.getValue();
                                    Setting<?> setting = hudElement.getSettings().stream()
                                            .filter(s -> s.getName().equals(settingName))
                                            .findFirst()
                                            .orElse(null);
                                    if (setting != null) {
                                        try {
                                            setSettingValue(setting, value);
                                        } catch (Exception e) {
                                            LOGGER.warn("Не удалось применить настройку {} для HUD {}: {}", settingName, hudName, e.getMessage());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Кэшируем конфигурацию
                    configCache.put(configName, configData);
                    LOGGER.info("Конфигурация '{}' успешно загружена", configName);
                    result.complete(true);
                } catch (Exception e) {
                    LOGGER.error("Ошибка при применении конфигурации '{}': {}", configName, e.getMessage());
                    result.complete(false);
                }
            });
            return result;
        });
    }
    
    /**
     * Получает список всех доступных конфигураций
     */
    public String[] getConfigList() {
        File[] files = configsDir.listFiles((dir, name) -> name.endsWith(".simple"));
        if (files == null) return new String[0];
        
        String[] configs = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            configs[i] = files[i].getName().replace(".simple", "");
        }
        return configs;
    }
    
    /**
     * Получает путь к директории конфигураций
     */
    public String getConfigsDirectory() {
        return configsDir.getAbsolutePath();
    }
    
    /**
     * Удаляет конфигурацию
     */
    public boolean deleteConfig(String configName) {
        File configFile = new File(configsDir, configName + ".simple");
        if (configFile.exists()) {
            boolean deleted = configFile.delete();
            if (deleted) {
                configCache.remove(configName);
                LOGGER.info("Конфигурация '{}' удалена", configName);
            }
            return deleted;
        }
        return false;
    }
    
    /**
     * Проверяет существование конфигурации
     */
    public boolean configExists(String configName) {
        return new File(configsDir, configName + ".simple").exists();
    }
    
    /**
     * Безопасно устанавливает значение настройки
     */
    @SuppressWarnings("unchecked")
    private void setSettingValue(Setting<?> setting, Object value) {
        if (setting instanceof BooleanSetting) {
            if (value instanceof Boolean) {
                ((BooleanSetting) setting).setValue((Boolean) value);
            }
        } else if (setting instanceof NumberSetting) {
            if (value instanceof Number) {
                NumberSetting numberSetting = (NumberSetting) setting;
                float floatValue = ((Number) value).floatValue();
                if (floatValue >= numberSetting.getMin() && floatValue <= numberSetting.getMax()) {
                    numberSetting.setValue(floatValue);
                }
            }
        } else if (setting instanceof StringSetting) {
            if (value instanceof String) {
                ((StringSetting) setting).setValue((String) value);
            }
        } else if (setting instanceof EnumSetting) {
            if (value instanceof String) {
                EnumSetting<?> enumSetting = (EnumSetting<?>) setting;
                try {
                    // Используем метод setEnumValue для установки значения по строке
                    enumSetting.setEnumValue((String) value);
                } catch (Exception e) {
                    LOGGER.warn("Неверное значение enum: {}", value);
                }
            }
        } else if (setting instanceof ColorSetting) {
            if (value instanceof String) {
                try {
                    int color = Integer.parseInt((String) value, 16);
                    ((ColorSetting) setting).setValue(color);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Неверный формат цвета: {}", value);
                }
            } else if (value instanceof Number) {
                ((ColorSetting) setting).setValue(((Number) value).intValue());
            }
        } else if (setting instanceof ListSetting) {
            if (value instanceof Map) {
                ListSetting listSetting = (ListSetting) setting;
                @SuppressWarnings("unchecked")
                Map<String, Object> listValues = (Map<String, Object>) value;
                
                for (BooleanSetting boolSetting : listSetting.getValue()) {
                    Object savedValue = listValues.get(boolSetting.getName());
                    if (savedValue instanceof Boolean) {
                        boolSetting.setValue((Boolean) savedValue);
                    }
                }
            }
        } else if (setting instanceof BindSetting) {
            if (value instanceof String) {
                try {
                    String[] parts = ((String) value).split(":");
                    int key = Integer.parseInt(parts[0]);
                    boolean isMouse = parts.length > 1 && Boolean.parseBoolean(parts[1]);
                    dev.prostovisuals.modules.settings.api.Bind.Mode mode = dev.prostovisuals.modules.settings.api.Bind.Mode.TOGGLE;
                    if (parts.length > 2) {
                        try {
                            mode = dev.prostovisuals.modules.settings.api.Bind.Mode.valueOf(parts[2]);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    ((BindSetting) setting).setValue(new dev.prostovisuals.modules.settings.api.Bind(key, isMouse, mode));
                } catch (Exception e) {
                    LOGGER.warn("Неверный формат бинда: {}", value);
                }
            }
        }
    }
    
    /**
     * Классы для сериализации/десериализации
     */
    public static class ConfigData {
        private Map<String, ModuleData> modules = new HashMap<>();
        private String currentTheme;
        private Map<String, HudPositionData> hudPositions = new HashMap<>();
        private Map<String, Map<String, Object>> hudSettings = new HashMap<>();
        private String commandPrefix;
        
        public Map<String, ModuleData> getModules() {
            return modules;
        }
        
        public void setModules(Map<String, ModuleData> modules) {
            this.modules = modules;
        }
        
        public String getCurrentTheme() {
            return currentTheme;
        }
        
        public void setCurrentTheme(String currentTheme) {
            this.currentTheme = currentTheme;
        }
        
        public Map<String, HudPositionData> getHudPositions() {
            return hudPositions;
        }
        
        public void setHudPositions(Map<String, HudPositionData> hudPositions) {
            this.hudPositions = hudPositions;
        }
        
        public Map<String, Map<String, Object>> getHudSettings() {
            return hudSettings;
        }
        
        public void setHudSettings(Map<String, Map<String, Object>> hudSettings) {
            this.hudSettings = hudSettings;
        }

        public String getCommandPrefix() {
            return commandPrefix;
        }

        public void setCommandPrefix(String commandPrefix) {
            this.commandPrefix = commandPrefix;
        }
    }
    
    public static class ModuleData {
        private boolean toggled;
        private dev.prostovisuals.modules.settings.api.Bind bind;
        private Map<String, Object> settings = new HashMap<>();
        
        public boolean isToggled() {
            return toggled;
        }
        
        public void setToggled(boolean toggled) {
            this.toggled = toggled;
        }
        
        public dev.prostovisuals.modules.settings.api.Bind getBind() {
            return bind;
        }
        
        public void setBind(dev.prostovisuals.modules.settings.api.Bind bind) {
            this.bind = bind;
        }
        
        public Map<String, Object> getSettings() {
            return settings;
        }
        
        public void setSettings(Map<String, Object> settings) {
            this.settings = settings;
        }
    }
    
    public static class HudPositionData {
        private float x;
        private float y;
        private boolean enabled;
        
        public float getX() {
            return x;
        }
        
        public void setX(float x) {
            this.x = x;
        }
        
        public float getY() {
            return y;
        }
        
        public void setY(float y) {
            this.y = y;
        }
        
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
} 
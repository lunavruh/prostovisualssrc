package dev.prostovisuals.client.events.impl;

import dev.prostovisuals.client.events.Event;
import dev.prostovisuals.modules.settings.Setting;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class EventSettingChange extends Event {
    private final Setting<?> setting;
}
package dev.prostovisuals.client.events.impl;

import dev.prostovisuals.client.events.Event;
import dev.prostovisuals.client.managers.ThemeManager;

public class EventThemeChanged extends Event {
    private final ThemeManager.Theme theme;
    
    public EventThemeChanged(ThemeManager.Theme theme) {
        this.theme = theme;
    }
    
    public ThemeManager.Theme getTheme() {
        return theme;
    }
} 
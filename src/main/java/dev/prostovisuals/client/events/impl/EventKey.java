package dev.prostovisuals.client.events.impl;

import dev.prostovisuals.client.events.Event;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public class EventKey extends Event {
    private int key, action, modifiers;
}
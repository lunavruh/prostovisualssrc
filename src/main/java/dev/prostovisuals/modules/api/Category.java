package dev.prostovisuals.modules.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor @Getter
public enum Category {
    Theme("I"),
    Events("E"),
    Render("H"),
    Combat("C"),
    Utility("I"),
    Hud("LOL");

    private final String icon;
}

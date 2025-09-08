package com.daynightwarfare;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

public enum TeamType {
    APOSTLE_OF_LIGHT("빛의 사도", Style.style(NamedTextColor.YELLOW)),
    APOSTLE_OF_MOON("달의 사도", Style.style(NamedTextColor.AQUA));

    private final String displayName;
    private final Style displayStyle;

    TeamType(String displayName, Style displayStyle) {
        this.displayName = displayName;
        this.displayStyle = displayStyle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Style getDisplayStyle() {
        return displayStyle;
    }

    public Component getStyledDisplayName() {
        return Component.text(this.displayName).style(this.displayStyle);
    }
}

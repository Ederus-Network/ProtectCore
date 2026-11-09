/*
 * Decompiled with CFR 0.152.
 */
package com.lucidaps.web;

import org.bukkit.configuration.ConfigurationSection;

public record EditorTheme(String accentLight, String accentDark, String headerColor, String filterLabelColor, String tabBgColor, String tabTextColor, String tabActiveBgColor, String tabActiveTextColor, String entryLabelColor) {
    private static final String DEF_ACCENT_LIGHT = "#3b82f6";
    private static final String DEF_ACCENT_DARK = "#6366f1";
    private static final String DEF_HEADER_COLOR = "#60a5fa";
    private static final String DEF_FILTER_LABEL_COLOR = "#94a3b8";
    private static final String DEF_TAB_BG_COLOR = "#1e293b";
    private static final String DEF_TAB_TEXT_COLOR = "#94a3b8";
    private static final String DEF_TAB_ACTIVE_BG_COLOR = "#2563eb";
    private static final String DEF_TAB_ACTIVE_TEXT = "#ffffff";
    private static final String DEF_ENTRY_LABEL_COLOR = "#38bdf8";

    public static EditorTheme fromConfig(ConfigurationSection configurationSection) {
        return new EditorTheme(
            configurationSection != null ? configurationSection.getString("accentColorLight", DEF_ACCENT_LIGHT) : DEF_ACCENT_LIGHT,
            configurationSection != null ? configurationSection.getString("accentColorDark", DEF_ACCENT_DARK) : DEF_ACCENT_DARK,
            configurationSection != null ? configurationSection.getString("headerColor", DEF_HEADER_COLOR) : DEF_HEADER_COLOR,
            configurationSection != null ? configurationSection.getString("filterLabelColor", DEF_FILTER_LABEL_COLOR) : DEF_FILTER_LABEL_COLOR,
            configurationSection != null ? configurationSection.getString("tabBgColor", DEF_TAB_BG_COLOR) : DEF_TAB_BG_COLOR,
            configurationSection != null ? configurationSection.getString("tabTextColor", DEF_TAB_TEXT_COLOR) : DEF_TAB_TEXT_COLOR,
            configurationSection != null ? configurationSection.getString("tabActiveBgColor", DEF_TAB_ACTIVE_BG_COLOR) : DEF_TAB_ACTIVE_BG_COLOR,
            configurationSection != null ? configurationSection.getString("tabActiveTextColor", DEF_TAB_ACTIVE_TEXT) : DEF_TAB_ACTIVE_TEXT,
            configurationSection != null ? configurationSection.getString("entryLabelColor", DEF_ENTRY_LABEL_COLOR) : DEF_ENTRY_LABEL_COLOR
        );
    }
}

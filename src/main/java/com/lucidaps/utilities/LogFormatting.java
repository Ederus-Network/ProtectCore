package com.lucidaps.utilities;

import com.lucidaps.db.ActionLog;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;

public class LogFormatting {

    public static String[] parseItemQuantityAndMaterial(String input) {
        if (input == null || input.isBlank()) {
            return new String[]{"1", "air"};
        }
        String s = input.trim();
        if (s.startsWith("added ")) s = s.substring(6).trim();
        else if (s.startsWith("removed ")) s = s.substring(8).trim();
        else if (s.startsWith("Dropped ")) s = s.substring(8).trim();
        else if (s.startsWith("Picked up ")) s = s.substring(10).trim();

        String amount = "1";
        if (s.startsWith("x") || s.startsWith("X")) {
            int spaceIdx = s.indexOf(' ');
            if (spaceIdx > 1) {
                String countPart = s.substring(1, spaceIdx);
                if (countPart.matches("\\d+")) {
                    amount = countPart;
                    s = s.substring(spaceIdx + 1).trim();
                }
            }
        }
        if (s.startsWith("minecraft:")) {
            s = s.substring(10);
        }
        s = s.replace(' ', '_').toLowerCase(Locale.ROOT);
        return new String[]{amount, s};
    }

    public static String cleanMaterial(String input) {
        if (input == null) return "air";
        String s = input.trim();
        if (s.startsWith("minecraft:")) {
            s = s.substring(10);
        }
        return s.replace(' ', '_').toLowerCase(Locale.ROOT);
    }

    public enum LogFormatter {
        BLOCK_PLACE("&a+", "placed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = string;
                if (s.startsWith("placed ")) s = s.substring(7);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        ENTITY_PLACE("&a+", "placed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = string.startsWith("placed ") ? string.substring(7) : string;
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        BLOCK_BREAK("&c-", "broke") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = string;
                if (s.startsWith("broken ")) s = s.substring(7);
                else if (s.startsWith("broke ")) s = s.substring(6);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        INTERACT("&7-", "clicked") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                if (s.startsWith("Interacted with ")) s = s.substring(16);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        ITEM_PICKUP("&a+", "picked up") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String[] parsed = parseItemQuantityAndMaterial(string);
                String countPrefix = !"1".equals(parsed[0]) ? "&fx" + parsed[0] + " " : "";
                return new FormattedLogParts(this.symbol, this.actionVerb, countPrefix + "&3" + parsed[1]);
            }
        },
        ITEM_DROP("&c-", "dropped") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String[] parsed = parseItemQuantityAndMaterial(string);
                String countPrefix = !"1".equals(parsed[0]) ? "&fx" + parsed[0] + " " : "";
                return new FormattedLogParts(this.symbol, this.actionVerb, countPrefix + "&3" + parsed[1]);
            }
        },
        CONTAINER_ADD("&a+", "added") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String[] parsed = parseItemQuantityAndMaterial(string);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&fx" + parsed[0] + " &3" + parsed[1]);
            }
        },
        CONTAINER_TAKE("&c-", "removed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String[] parsed = parseItemQuantityAndMaterial(string);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&fx" + parsed[0] + " &3" + parsed[1]);
            }
        },
        BLOCK_EXPLODE("&c!", "exploded") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        ENTITY_EXPLODE("&c!", "exploded") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                if (s.startsWith("Entity explosion triggered by ")) s = s.substring(30);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        BLOCK_BURN("&c-", "burned") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = string.replace("block ", "").replace(" is burning", "").replace(" burned", "").trim();
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        STRUCTURE_GROW("&a+", "grew") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String s = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                if (s.startsWith("Structure ")) s = s.substring(10);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(s));
            }
        },
        LIGHTNING_STRIKE("&e!", "struck") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        PORTAL_CREATE("&b~", "created portal") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        LEAVES_DECAY("&6-", "decayed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        BLOCK_FORM("&c-", "formed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        PISTON_EXTEND("&a->", "extended") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        PISTON_RETRACT("&c<-", "retracted") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        ENTITY_DEATH("&c-", "killed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String details = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                if (details.contains(" by ")) {
                    String[] parts = details.split(" by ", 2);
                    String victim = cleanMaterial(parts[0]);
                    String killer = parts[1].trim();
                    return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + victim, killer, null, false);
                }
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(details));
            }
        },
        LIQUID_FLOW("&a+", "placed") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        HOPPER_TRANSFER("&e>", "transferred") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String[] parsed = parseItemQuantityAndMaterial(string);
                return new FormattedLogParts(this.symbol, this.actionVerb, "&fx" + parsed[0] + " &3" + parsed[1]);
            }
        },
        BUCKET_EMPTY("&c-", "emptied") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&3" + cleanMaterial(string));
            }
        },
        PLAYER_MESSAGE("&b💬", "chat") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String msg = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                return new FormattedLogParts(this.symbol, this.actionVerb, "&f" + msg);
            }
        },
        PLAYER_COMMAND("&d⚡", "command") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String cmd = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                return new FormattedLogParts(this.symbol, this.actionVerb, "&e" + cmd);
            }
        },
        DEFAULT_ACTION("?", "action") {
            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String verb = actionLog.getAction().toLowerCase(Locale.ROOT).replace('_', ' ');
                return new FormattedLogParts(this.symbol, verb, "&3" + string);
            }
        };

        protected final String symbol;
        protected final String actionVerb;
        private static final Map<String, LogFormatter> actionMap = new HashMap<>();

        LogFormatter(String symbol, String actionVerb) {
            this.symbol = symbol;
            this.actionVerb = actionVerb;
        }

        public abstract FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl);

        public static LogFormatter getFormatter(String action) {
            if (action == null) return DEFAULT_ACTION;
            return actionMap.getOrDefault(action.toUpperCase(Locale.ROOT), DEFAULT_ACTION);
        }

        static {
            for (LogFormatter formatter : LogFormatter.values()) {
                if (formatter == DEFAULT_ACTION) continue;
                actionMap.put(formatter.name().toUpperCase(Locale.ROOT), formatter);
            }
        }
    }

    public record FormattedLogParts(String symbol, String actionVerb, String detailSuffix, String usernameOverride, Component overrideComponent, boolean detailContainsVerb) {
        public FormattedLogParts(String symbol, String actionVerb, String detailSuffix) {
            this(symbol, actionVerb, detailSuffix, null, null, false);
        }

        public FormattedLogParts(String symbol, String actionVerb, String detailSuffix, String usernameOverride, Component overrideComponent, boolean detailContainsVerb) {
            this.symbol = symbol;
            this.actionVerb = actionVerb;
            this.detailSuffix = detailSuffix;
            this.usernameOverride = usernameOverride;
            this.overrideComponent = overrideComponent;
            this.detailContainsVerb = detailContainsVerb;
        }
    }
}

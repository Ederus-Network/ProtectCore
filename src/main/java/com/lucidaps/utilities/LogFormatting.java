/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 */
package com.lucidaps.utilities;

import com.lucidaps.db.ActionLog;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;

public class LogFormatting {

    public static enum LogFormatter {
        BLOCK_PLACE("&a+", "placed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = string;
                String string4 = "placed ";
                if (string3.startsWith(string4)) {
                    string3 = string3.substring(string4.length());
                }
                string3 = string3.replace(' ', '_');
                String string5 = "&d" + string3;
                return new FormattedLogParts(this.symbol, this.actionVerb, string5);
            }
        }
        ,
        ENTITY_PLACE("&a+", "placed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = string.startsWith("placed ") ? string.substring("placed ".length()) : string;
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string3.replace(' ', '_'));
            }
        }
        ,
        BLOCK_BREAK("&c-", "broke"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = string;
                String string4 = "broken ";
                if (string3.startsWith(string4)) {
                    string3 = string3.substring(string4.length());
                }
                string3 = string3.replace(' ', '_');
                String string5 = "&d" + string3;
                return new FormattedLogParts(this.symbol, this.actionVerb, string5);
            }
        }
        ,
        INTERACT("&7-", "clicked"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Interacted with ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        ITEM_PICKUP("&a+", "picked up"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Picked up ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        ITEM_DROP("&c-", "dropped"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Dropped ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        CONTAINER_ADD("&a+", "added"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "added ";
                String string4 = string.startsWith(string3) ? "&d" + string.substring(string3.length()) : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        CONTAINER_TAKE("&c-", "removed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "removed ";
                String string4 = string.startsWith(string3) ? "&d" + string.substring(string3.length()) : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        BLOCK_EXPLODE("&c!", "exploded"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        ENTITY_EXPLODE("&c!", "exploded"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Entity explosion triggered by ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        BLOCK_BURN("&c-", "burned"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = string.replace("block ", "").replace(" is burning", "").replace(" burned", "").trim();
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string3);
            }
        }
        ,
        STRUCTURE_GROW("&a+", "grew"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Structure ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        LIGHTNING_STRIKE("&e!", "struck"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Lightning struck at ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        PORTAL_CREATE("&b~", "created portal"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        LEAVES_DECAY("&6-", "decayed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Leaves decayed at ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        BLOCK_FORM("&c-", "formed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "Formed block ";
                String string4 = actionLog.getDetails().startsWith(string3) ? "&d" + actionLog.getDetails().substring(string3.length()).toLowerCase() : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string4);
            }
        }
        ,
        PISTON_EXTEND("&a->", "extended"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        PISTON_RETRACT("&c<-", "retracted"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        ENTITY_DEATH("&c-", "killed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        LIQUID_FLOW("&a+", "placed"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        HOPPER_TRANSFER("&e>", "transfer"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String[] stringArray = string.split(" ");
                String string3 = stringArray.length >= 2 ? "&d" + stringArray[0] + " &f" + this.actionVerb + " &d" + stringArray[1] : "&d" + string;
                return new FormattedLogParts(this.symbol, this.actionVerb, string3, null, null, true);
            }
        }
        ,
        BUCKET_EMPTY("&c-", "emptied"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                return new FormattedLogParts(this.symbol, this.actionVerb, "&d" + string);
            }
        }
        ,
        PLAYER_MESSAGE("&b💬", "chat"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String msg = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                return new FormattedLogParts(this.symbol, this.actionVerb, "&7\"&f" + msg + "&7\"");
            }
        }
        ,
        PLAYER_COMMAND("&d⚡", "cmd"){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String cmd = actionLog.getDetails() != null ? actionLog.getDetails() : string;
                return new FormattedLogParts(this.symbol, this.actionVerb, "&e" + cmd);
            }
        }
        ,
        DEFAULT_ACTION("", ""){

            @Override
            public FormattedLogParts format(ActionLog actionLog, String string, String string2, boolean bl) {
                String string3 = "?";
                String string4 = actionLog.getAction().toLowerCase();
                String string5 = "&d" + string;
                return new FormattedLogParts(string3, string4, string5);
            }
        };

        protected final String symbol;
        protected final String actionVerb;
        private static final Map<String, LogFormatter> actionMap;

        private LogFormatter(String string2, String string3) {
            this.symbol = string2;
            this.actionVerb = string3;
        }

        public abstract FormattedLogParts format(ActionLog var1, String var2, String var3, boolean var4);

        public static LogFormatter getFormatter(String string) {
            return actionMap.getOrDefault(string.toUpperCase(), DEFAULT_ACTION);
        }

        static {
            actionMap = new HashMap<String, LogFormatter>();
            for (LogFormatter logFormatter : LogFormatter.values()) {
                if (logFormatter == DEFAULT_ACTION) continue;
                actionMap.put(logFormatter.name().toUpperCase(), logFormatter);
            }
        }
    }

    public record FormattedLogParts(String symbol, String actionVerb, String detailSuffix, String usernameOverride, Component overrideComponent, boolean detailContainsVerb) {
        public FormattedLogParts(String string, String string2, String string3) {
            this(string, string2, string3, null, null, false);
        }

        public FormattedLogParts(String string, String string2, String string3, String string4) {
            this(string, string2, string3, string4, null, false);
        }

        public FormattedLogParts(Component component) {
            this(null, null, null, null, component, false);
        }
    }
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.ConsoleCommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 */
package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.DbPurger;
import com.lucidaps.db.PurgeStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class PurgeSubCommand
implements SubCommand {
    private final ProtectCore plugin;
    private final DbPurger purger;

    public PurgeSubCommand(ProtectCore protectCore, DbPurger dbPurger) {
        this.plugin = protectCore;
        this.purger = dbPurger;
    }

    @Override
    public String getName() {
        return "purge";
    }

    private Consumer<String> makeProgressSink(CommandSender commandSender) {
        UUID uUID;
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            uUID = player.getUniqueId();
        } else {
            uUID = null;
        }
        UUID uUID2 = uUID;
        boolean bl = commandSender instanceof ConsoleCommandSender;
        return string -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            String string2 = ChatColor.stripColor((String)string);
            this.plugin.getLogger().info(string2);
            if (uUID2 != null) {
                Player player = Bukkit.getPlayer((UUID)uUID2);
                if (player != null && player.isOnline()) {
                    player.sendMessage(string);
                }
            } else if (!bl) {
                commandSender.sendMessage(string2);
            }
        });
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        int n;
        String string2;
        if (!commandSender.hasPermission("protectcore.admin.purge")) {
            commandSender.sendMessage("\u00a7cNo permission.");
            return true;
        }
        boolean bl = string.equalsIgnoreCase("purge");
        String string3 = string2 = bl ? "/" + string : "/" + string + " purge";
        if (PurgeSubCommand.hasToken(stringArray, "status")) {
            this.sendStatus(commandSender, string2);
            return true;
        }
        if (PurgeSubCommand.hasToken(stringArray, "plan")) {
            int n2;
            String string4 = PurgeSubCommand.firstIntToken(stringArray);
            if (string4 == null) {
                commandSender.sendMessage("\u00a7eUsage: \u00a7f" + string2 + " plan <days>");
                return true;
            }
            try {
                n2 = Integer.parseInt(string4);
            }
            catch (NumberFormatException numberFormatException) {
                commandSender.sendMessage("\u00a7cDays must be a number greater than zero.");
                return true;
            }
            if (n2 <= 0) {
                commandSender.sendMessage("\u00a7cPlan days must be greater than zero.");
                return true;
            }
            commandSender.sendMessage("\u00a7eCalculating purge plan\u2026 \u00a77No data will be changed.");
            this.purger.planKeepDaysAsync(n2, null).whenComplete((purgeResult, throwable) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                if (throwable != null) {
                    commandSender.sendMessage("\u00a7cCould not calculate purge plan: " + throwable.getMessage());
                    return;
                }
                commandSender.sendMessage("\u00a7aPurge plan for keeping \u00a7f" + n2 + "\u00a7a day(s):");
                commandSender.sendMessage("\u00a77Actions eligible: \u00a7f" + purgeResult.actionsAffected);
                commandSender.sendMessage("\u00a77Orphan snapshots eligible: \u00a7f" + purgeResult.orphanSnapshotsAffected);
            }));
            return true;
        }
        if (PurgeSubCommand.hasToken(stringArray, "stop") || PurgeSubCommand.hasToken(stringArray, "cancel")) {
            boolean bl2 = PurgeSubCommand.hasToken(stringArray, "confirm");
            if (!bl2) {
                commandSender.sendMessage("\u00a7cThis will stop the running purge.");
                commandSender.sendMessage("\u00a77Write: \u00a7f" + string2 + " stop confirm");
                return true;
            }
            boolean bl3 = this.purger.requestStop();
            if (bl3) {
                commandSender.sendMessage("\u00a7eStop requested. \u00a77It will stop after the current DB operation finishes.");
            } else {
                commandSender.sendMessage("\u00a77No purge is currently running.");
            }
            return true;
        }
        String string5 = PurgeSubCommand.firstIntToken(stringArray);
        if (string5 == null) {
            commandSender.sendMessage("\u00a7eUsage:");
            commandSender.sendMessage("\u00a77Start: \u00a7f" + string2 + " <daysToKeep> confirm");
            commandSender.sendMessage("\u00a77Plan: \u00a7f" + string2 + " plan <daysToKeep>");
            commandSender.sendMessage("\u00a77Status: \u00a7f" + string2 + " status");
            commandSender.sendMessage("\u00a77Stop: \u00a7f" + string2 + " stop confirm");
            return true;
        }
        try {
            n = Integer.parseInt(string5);
        }
        catch (NumberFormatException numberFormatException) {
            commandSender.sendMessage("\u00a7cDays must be a number. Example: \u00a7f" + string2 + " 60 confirm");
            return true;
        }
        if (n < 0) {
            commandSender.sendMessage("\u00a7cDays must be >= 0. Example: \u00a7f" + string2 + " 60 confirm");
            commandSender.sendMessage("\u00a77Tip: \u00a7f" + string2 + " 0 confirm \u00a77to delete ALL ProtectCore data.");
            return true;
        }
        boolean bl4 = PurgeSubCommand.hasToken(stringArray, "confirm");
        if (n == 0) {
            if (!bl4) {
                commandSender.sendMessage("\u00a7c\u00a7lDANGER: This will DELETE ALL ProtectCore data (actions + snapshots).");
                commandSender.sendMessage("\u00a77This is meant to reclaim disk space if you want to remove the plugin data.");
                commandSender.sendMessage("\u00a77Write: \u00a7f" + string2 + " 0 confirm");
                return true;
            }
        } else if (!bl4) {
            commandSender.sendMessage("\u00a7cAre you sure you want to purge and keep only the last \u00a7f" + n + "\u00a7c day(s)?");
            commandSender.sendMessage("\u00a77Write: \u00a7f" + string2 + " " + n + " confirm");
            return true;
        }
        PurgeStatus purgeStatus = this.purger.getStatus();
        if (purgeStatus.running) {
            commandSender.sendMessage("\u00a7cA purge is already running.");
            this.sendStatus(commandSender, string2);
            commandSender.sendMessage("\u00a77To stop it: \u00a7f" + string2 + " stop confirm");
            return true;
        }
        Consumer<String> consumer = this.makeProgressSink(commandSender);
        if (n == 0) {
            commandSender.sendMessage("\u00a7eStarting reset\u2026 \u00a7cThis will delete ALL ProtectCore data.");
        } else {
            commandSender.sendMessage("\u00a7eStarting purge\u2026 \u00a77Keeping last \u00a7f" + n + "\u00a77 days.");
        }
        commandSender.sendMessage("\u00a77Tip: \u00a7f" + string2 + " status \u00a77to monitor after relog.");
        this.purger.purgeKeepDaysAsync(n, commandSender.getName(), consumer).whenComplete((purgeResult, throwable) -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            if (throwable != null) {
                if (throwable instanceof CancellationException) {
                    commandSender.sendMessage("\u00a7cPurge was cancelled.");
                    return;
                }
                commandSender.sendMessage("\u00a7cPurge failed: " + (throwable.getMessage() == null ? throwable.toString() : throwable.getMessage()));
                this.plugin.getLogger().warning("Purge failed: " + String.valueOf(throwable));
                return;
            }
            if (n == 0) {
                commandSender.sendMessage("\u00a7aReset complete. \u00a77All ProtectCore data removed.");
            } else {
                String string = new Date(purgeResult.cutoffMillis).toString();
                commandSender.sendMessage("\u00a7aPurge complete. \u00a77Cutoff: \u00a7f" + string);
            }
            commandSender.sendMessage("\u00a77Actions deleted: \u00a7f" + purgeResult.actionsAffected);
            commandSender.sendMessage("\u00a77Orphan snapshots deleted: \u00a7f" + purgeResult.orphanSnapshotsAffected);
            commandSender.sendMessage("\u00a77Took: \u00a7f" + purgeResult.elapsedMs + "ms");
        }));
        return true;
    }

    private void sendStatus(CommandSender commandSender, String string) {
        PurgeStatus purgeStatus = this.purger.getStatus();
        if (!purgeStatus.running && purgeStatus.stage == PurgeStatus.Stage.IDLE) {
            commandSender.sendMessage("\u00a77No purge running.");
            return;
        }
        long l = System.currentTimeMillis();
        long l2 = purgeStatus.startedAtMs > 0L ? (purgeStatus.endedAtMs > 0L ? purgeStatus.endedAtMs : l) - purgeStatus.startedAtMs : 0L;
        double d = Math.max(1.0, (double)l2 / 1000.0);
        double d2 = (double)purgeStatus.actionsDeleted / d;
        commandSender.sendMessage("\u00a7ePurge status:");
        commandSender.sendMessage("\u00a77Running: \u00a7f" + purgeStatus.running + " \u00a77| Stage: \u00a7f" + String.valueOf((Object)purgeStatus.stage));
        if (purgeStatus.startedBy != null) {
            commandSender.sendMessage("\u00a77Started by: \u00a7f" + purgeStatus.startedBy);
        }
        commandSender.sendMessage("\u00a77Keep days: \u00a7f" + purgeStatus.keepDays + " \u00a77| Batch: \u00a7f" + purgeStatus.batchSize);
        commandSender.sendMessage("\u00a77Maintenance mode: \u00a7f" + purgeStatus.maintenanceMode);
        if (purgeStatus.cutoffMillis > 0L) {
            commandSender.sendMessage("\u00a77Cutoff: \u00a7f" + String.valueOf(new Date(purgeStatus.cutoffMillis)));
        }
        commandSender.sendMessage("\u00a77Elapsed: \u00a7f" + PurgeSubCommand.formatDuration(l2));
        commandSender.sendMessage("\u00a77Actions deleted: \u00a7f" + purgeStatus.actionsDeleted + " \u00a77(" + String.format(Locale.US, "%.1f", d2) + "/s)");
        commandSender.sendMessage("\u00a77Orphan snapshots deleted: \u00a7f" + purgeStatus.orphanSnapshotsDeleted);
        if (purgeStatus.snapshotsByAgeDeleted > 0L) {
            commandSender.sendMessage("\u00a77Snapshots-by-age deleted: \u00a7f" + purgeStatus.snapshotsByAgeDeleted);
        }
        if (purgeStatus.lastMessage != null) {
            commandSender.sendMessage("\u00a77Last: \u00a7f" + purgeStatus.lastMessage);
        }
        if (purgeStatus.lastError != null) {
            commandSender.sendMessage("\u00a7cError: " + purgeStatus.lastError);
        }
        if (purgeStatus.running) {
            commandSender.sendMessage("\u00a77Stop: \u00a7f" + string + " stop confirm");
        }
    }

    private static boolean hasToken(String[] stringArray, String string) {
        for (String string2 : stringArray) {
            if (string2 == null || !string2.equalsIgnoreCase(string)) continue;
            return true;
        }
        return false;
    }

    private static String firstIntToken(String[] stringArray) {
        for (String string : stringArray) {
            if (string == null || !string.matches("-?\\d+")) continue;
            return string;
        }
        return null;
    }

    private static String formatDuration(long l) {
        long l2 = l / 1000L;
        long l3 = l2 / 3600L;
        long l4 = (l2 %= 3600L) / 60L;
        l2 %= 60L;
        if (l3 > 0L) {
            return l3 + "h " + l4 + "m " + l2 + "s";
        }
        if (l4 > 0L) {
            return l4 + "m " + l2 + "s";
        }
        return l2 + "s";
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (stringArray.length == 2) {
            return new ArrayList<String>(Arrays.asList("0", "30", "60", "90", "180", "plan", "status", "stop"));
        }
        if (stringArray.length == 3) {
            if ("stop".equalsIgnoreCase(stringArray[1])) {
                return List.of("confirm");
            }
            if ("plan".equalsIgnoreCase(stringArray[1])) {
                return List.of("30", "60", "90", "180");
            }
            if (stringArray[1].matches("\\d+")) {
                return List.of("confirm");
            }
        }
        return List.of();
    }
}


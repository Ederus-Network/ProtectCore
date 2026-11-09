/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 */
package com.lucidaps.commands.subcommands;

import com.lucidaps.ProtectCore;
import com.lucidaps.commands.SubCommand;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.web.EditorCommand;
import java.util.List;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public class EditorSubCommand
implements SubCommand {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final EditorCommand editorCommand;

    public EditorSubCommand(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
        this.editorCommand = new EditorCommand(protectCore);
    }

    @Override
    public String getName() {
        return "editor";
    }

    @Override
    public boolean execute(CommandSender commandSender, Command command, String string, String[] stringArray) {
        if (!commandSender.hasPermission("protectcore.editor")) {
            commandSender.sendMessage(this.plugin.getLang().get("messages.noPermission", Map.of()));
            return true;
        }
        return this.editorCommand.onCommand(commandSender, command, string, stringArray);
    }

    @Override
    public List<String> tabComplete(CommandSender commandSender, Command command, String string, String[] stringArray) {
        return this.editorCommand.onTabComplete(commandSender, command, string, stringArray);
    }
}


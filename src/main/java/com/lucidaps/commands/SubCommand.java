/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 */
package com.lucidaps.commands;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public interface SubCommand {
    public boolean execute(CommandSender var1, Command var2, String var3, String[] var4);

    public List<String> tabComplete(CommandSender var1, Command var2, String var3, String[] var4);

    public String getName();
}


/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.block.Block
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.EquipmentSlot
 */
package com.lucidaps.listeners;

import com.lucidaps.ProtectCore;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class InspectModeListener
implements Listener {
    private final ProtectCore plugin;

    public InspectModeListener(ProtectCore protectCore) {
        this.plugin = protectCore;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent playerInteractEvent) {
        if (playerInteractEvent.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = playerInteractEvent.getPlayer();
        if (!this.plugin.isInspectModeEnabled(player)) {
            return;
        }
        Block block = playerInteractEvent.getClickedBlock();
        if (block == null) {
            return;
        }
        playerInteractEvent.setCancelled(true);
        int n = block.getX();
        int n2 = block.getY();
        int n3 = block.getZ();
        String string = "";
        if (playerInteractEvent.getAction() == Action.LEFT_CLICK_BLOCK) {
            string = "left";
        } else if (playerInteractEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
            string = "right";
        }
        player.performCommand("po inspect " + string + " x:" + n + " y:" + n2 + " z:" + n3 + " page:1");
    }
}


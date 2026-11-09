package com.lucidaps.listeners;

import com.lucidaps.ProtectCore;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;

public class InspectModeListener implements Listener {
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
        if (playerInteractEvent.getAction() == Action.LEFT_CLICK_BLOCK) {
            player.performCommand("po inspect left x:" + block.getX() + " y:" + block.getY() + " z:" + block.getZ() + " page:1");
        } else if (playerInteractEvent.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (block.getState() instanceof InventoryHolder) {
                player.performCommand("po inspect right x:" + block.getX() + " y:" + block.getY() + " z:" + block.getZ() + " page:1");
            } else if (playerInteractEvent.getBlockFace() != null) {
                Block relative = block.getRelative(playerInteractEvent.getBlockFace());
                player.performCommand("po inspect left x:" + relative.getX() + " y:" + relative.getY() + " z:" + relative.getZ() + " page:1");
            } else {
                player.performCommand("po inspect left x:" + block.getX() + " y:" + block.getY() + " z:" + block.getZ() + " page:1");
            }
        }
    }
}

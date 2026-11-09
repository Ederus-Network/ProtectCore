/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.sk89q.worldedit.EditSession$Stage
 *  com.sk89q.worldedit.WorldEdit
 *  com.sk89q.worldedit.entity.Player
 *  com.sk89q.worldedit.event.extent.EditSessionEvent
 *  com.sk89q.worldedit.extension.platform.Actor
 *  com.sk89q.worldedit.extent.Extent
 *  com.sk89q.worldedit.util.eventbus.EventBus
 *  com.sk89q.worldedit.util.eventbus.Subscribe
 *  com.sk89q.worldedit.world.World
 *  org.bukkit.Bukkit
 *  org.bukkit.plugin.Plugin
 */
package com.lucidaps.integrations.worldedit;

import com.lucidaps.ProtectCore;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.integrations.worldedit.WELoggingExtent;
import com.lucidaps.utilities.ProtectIds;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.util.eventbus.Subscribe;
import com.sk89q.worldedit.world.World;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class WorldEditBridge {
    private final ProtectCore plugin;
    private final DatabaseMariadb db;
    private boolean registered = false;
    private volatile boolean wrappedExtentsCreated = false;

    public WorldEditBridge(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.db = databaseMariadb;
    }

    public boolean register() {
        if (this.registered) {
            return true;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("WorldEdit");
        if (plugin == null || !plugin.isEnabled()) {
            this.plugin.getLogger().info("WorldEdit not present; WE integration disabled.");
            return false;
        }
        if (!this.isClassAvailable("com.sk89q.worldedit.extent.AbstractDelegateExtent")) {
            this.plugin.getLogger().warning("WorldEdit API class AbstractDelegateExtent is not visible; WE integration disabled.");
            return false;
        }
        try {
            EventBus eventBus = WorldEdit.getInstance().getEventBus();
            eventBus.register((Object)this);
            this.registered = true;
            this.plugin.getLogger().info("WorldEdit integration enabled (listening for EditSessionEvent)");
            return true;
        }
        catch (Throwable throwable) {
            this.registered = false;
            this.plugin.getLogger().log(Level.WARNING, "WorldEdit integration unavailable; WE logging disabled.", throwable);
            return false;
        }
    }

    public void unregister() {
        if (!this.registered) {
            return;
        }
        try {
            WorldEdit.getInstance().getEventBus().unregister((Object)this);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.FINE, "WorldEdit bridge unregister skipped", throwable);
        }
        finally {
            this.registered = false;
        }
    }

    public void shutdown() {
        this.unregister();
        this.flushAllQueuesDirect();
    }

    public CompletableFuture<Void> drainAllAsync() {
        if (!this.wrappedExtentsCreated) {
            return CompletableFuture.completedFuture(null);
        }
        try {
            return WELoggingExtent.drainAllAsync(this.plugin);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.WARNING, "WE queue drain skipped because WorldEdit integration is unavailable.", throwable);
            return CompletableFuture.completedFuture(null);
        }
    }

    private void flushAllQueuesDirect() {
        if (!this.wrappedExtentsCreated) {
            return;
        }
        try {
            WELoggingExtent.flushAllQueuesDirect();
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.WARNING, "WE direct drain during shutdown threw", throwable);
        }
    }

    private boolean isClassAvailable(String string) {
        try {
            Class.forName(string, false, this.getClass().getClassLoader());
            return true;
        }
        catch (Throwable throwable) {
            return false;
        }
    }

    @Subscribe
    public void onEditSession(EditSessionEvent editSessionEvent) {
        try {
            this.handleEditSession(editSessionEvent);
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().log(Level.WARNING, "WorldEdit bridge failed while wrapping an edit session; disabling WE logging.", throwable);
            this.unregister();
        }
    }

    private void handleEditSession(EditSessionEvent editSessionEvent) {
        Object object;
        Object object2;
        Player player;
        if (!this.plugin.isWorldEditEnabled()) {
            return;
        }
        EditSession.Stage stage = editSessionEvent.getStage();
        if (stage != EditSession.Stage.BEFORE_CHANGE && stage != EditSession.Stage.BEFORE_HISTORY) {
            return;
        }
        if (editSessionEvent.getExtent() instanceof WELoggingExtent) {
            return;
        }
        Actor actor = editSessionEvent.getActor();
        String string = actor != null ? actor.getName() : "WORLD_EDIT";
        Object object3 = ProtectIds.SYSTEM_ACTOR;
        if (actor instanceof Player) {
            player = (Player)actor;
            try {
                object2 = player.getUniqueId();
                if (object2 != null) {
                    object3 = object2;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        Object object4 = object2 = (player = editSessionEvent.getWorld()) != null ? player.getName() : null;
        if (object2 == null && actor instanceof Player) {
            object = (Player)actor;
            try {
                World world = object.getWorld();
                if (world != null) {
                    object2 = world.getName();
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (object2 == null) {
            object2 = "unknown_world";
        }
        object = new WELoggingExtent(this.plugin, this.db, (String)object2, (UUID)object3, string, editSessionEvent.getExtent());
        this.wrappedExtentsCreated = true;
        editSessionEvent.setExtent((Extent)object);
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.lambda$handleEditSession$0((WELoggingExtent)((Object)object)), 5L);
    }

    private /* synthetic */ void lambda$handleEditSession$0(WELoggingExtent wELoggingExtent) {
        wELoggingExtent.flushAll(this.plugin);
    }
}


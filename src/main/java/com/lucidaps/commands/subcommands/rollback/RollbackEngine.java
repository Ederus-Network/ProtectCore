/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.JsonObject
 *  com.google.gson.JsonParser
 *  io.papermc.paper.threadedregions.scheduler.ScheduledTask
 *  javax.annotation.Nullable
 *  org.bukkit.Art
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Rotation
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.BlockState
 *  org.bukkit.block.Chest
 *  org.bukkit.block.CreatureSpawner
 *  org.bukkit.block.data.Bisected
 *  org.bukkit.block.data.Bisected$Half
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Directional
 *  org.bukkit.block.data.Levelled
 *  org.bukkit.block.data.Waterlogged
 *  org.bukkit.block.data.type.Bed
 *  org.bukkit.block.data.type.Chest
 *  org.bukkit.block.data.type.Chest$Type
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.ArmorStand
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Hanging
 *  org.bukkit.entity.ItemFrame
 *  org.bukkit.entity.Painting
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.plugin.Plugin
 */
package com.lucidaps.commands.subcommands.rollback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lucidaps.ProtectCore;
import com.lucidaps.commands.subcommands.rollback.RollbackModel;
import com.lucidaps.db.ActionLog;
import com.lucidaps.db.DatabaseMariadb;
import com.lucidaps.utilities.BinaryInventoryUtil;
import com.lucidaps.utilities.Debug;
import com.lucidaps.utilities.EntityMetadataUtil;
import com.lucidaps.utilities.ItemMetadataUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.bukkit.Art;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Painting;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public class RollbackEngine {
    private final ProtectCore plugin;
    private final DatabaseMariadb databaseMariadb;
    private final AtomicBoolean rollbackInProgress = new AtomicBoolean(false);
    private static final int MAX_FLUID_DRAIN_BLOCKS = 5000;
    private final AtomicInteger frameTaskEpoch = new AtomicInteger(0);
    private static final UUID SYSTEM_ACTOR = new UUID(0L, 0L);
    private static final String SYSTEM_NAME = "SYSTEM";
    private volatile long currentRollbackId = 0L;
    private final AtomicInteger journaledThisRun = new AtomicInteger(0);
    private static final ThreadLocal<DecimalFormat> DF2 = ThreadLocal.withInitial(() -> new DecimalFormat("#.##"));
    private static final int[][] OFFS = new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private final Map<Integer, JsonObject> metaCache = new ConcurrentHashMap<Integer, JsonObject>();
    private final Map<String, Material> matCache = new ConcurrentHashMap<String, Material>(512);
    private final Map<String, BlockData> bdCache = new ConcurrentHashMap<String, BlockData>(512);
    @Nullable
    private int[] tracePos;

    public RollbackEngine(ProtectCore protectCore, DatabaseMariadb databaseMariadb) {
        this.plugin = protectCore;
        this.databaseMariadb = databaseMariadb;
    }

    public void startRollback(CommandSender commandSender, RollbackModel.ParsedArgs parsedArgs) {
        if (!this.rollbackInProgress.compareAndSet(false, true)) {
            commandSender.sendMessage(String.valueOf(ChatColor.RED) + "A rollback is already in progress. Please wait for it to complete.");
            return;
        }
        try {
            if (!parsedArgs.invalidInclude().isEmpty() || !parsedArgs.invalidExclude().isEmpty()) {
                ArrayList<CallSite> arrayList = new ArrayList<CallSite>();
                if (!parsedArgs.invalidInclude().isEmpty()) {
                    arrayList.add((CallSite)((Object)("include: " + String.join((CharSequence)", ", parsedArgs.invalidInclude()))));
                }
                if (!parsedArgs.invalidExclude().isEmpty()) {
                    arrayList.add((CallSite)((Object)("exclude: " + String.join((CharSequence)", ", parsedArgs.invalidExclude()))));
                }
                commandSender.sendMessage(String.valueOf(ChatColor.YELLOW) + "Unknown material(s) ignored: " + String.join((CharSequence)" | ", arrayList));
            }
            commandSender.sendMessage(this.plugin.getLang().get("messages.rollback.collectingLogs", Map.of("time", RollbackModel.ParsedArgs.formatMillis(parsedArgs.periodMillis()), "radius", parsedArgs.radius() < 0 ? "global" : String.valueOf(parsedArgs.radius()))));
            this.plugin.drainWorldEditQueuesAsync().whenComplete((void_, throwable) -> {
                boolean bl;
                if (throwable != null) {
                    this.plugin.getLogger().log(Level.SEVERE, "Could not drain WorldEdit logs before rollback", (Throwable)throwable);
                    Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> {
                        commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Rollback could not start because pending WorldEdit logs could not be drained.");
                        this.rollbackInProgress.set(false);
                    });
                    return;
                }
                long l = this.plugin.getConfig().getLong("rollback.flushMaxWaitMs", 4000L);
                boolean bl2 = bl = this.plugin.getConfig().getBoolean("rollback.skipFlushIfPeriodOverMinutes", true) && parsedArgs.periodMillis() >= this.plugin.getConfig().getLong("rollback.longWindowMs", 600000L);
                if (bl) {
                    this.applyRollback(commandSender, parsedArgs);
                } else {
                    this.databaseMariadb.flushThroughAsync(l, () -> this.applyRollback(commandSender, parsedArgs));
                }
            });
        }
        catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "Error initiating rollback: " + exception.getMessage(), exception);
            commandSender.sendMessage(String.valueOf(ChatColor.RED) + this.plugin.getLang().get("messages.rollback.errorInitiating", Map.of()));
            this.rollbackInProgress.set(false);
        }
    }

    private void applyRollback(CommandSender commandSender, RollbackModel.ParsedArgs parsedArgs) {
        String string = this.plugin.getConfig().getString("debug.rollback.tracePos", "");
        if (string != null && string.matches("\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*")) {
            String[] stringArray = string.split(",");
            this.tracePos = new int[]{Integer.parseInt(stringArray[0].trim()), Integer.parseInt(stringArray[1].trim()), Integer.parseInt(stringArray[2].trim())};
        } else {
            this.tracePos = null;
        }
        long l = System.currentTimeMillis();
        World world = Bukkit.getWorld((String)parsedArgs.worldName());
        if (world == null) {
            commandSender.sendMessage(String.valueOf(ChatColor.RED) + "Error: World '" + parsedArgs.worldName() + "' is not loaded or does not exist.");
            this.rollbackInProgress.set(false);
            return;
        }
        String string2 = world.getName();
        long l2 = (System.currentTimeMillis() - parsedArgs.periodMillis()) / 1000L;
        this.currentRollbackId = System.currentTimeMillis();
        this.journaledThisRun.set(0);
        this.databaseMariadb.findRollbackChunks(string2, parsedArgs.originX(), parsedArgs.originZ(), parsedArgs.radius(), parsedArgs.targetUuid(), l2, parsedArgs.actionFilter(), parsedArgs.itemFilter(), parsedArgs.includeMaterials(), parsedArgs.excludeMaterials(), list -> this.applyRollbackFromManifest(commandSender, world, l, l2 * 1000L, parsedArgs, (List<DatabaseMariadb.ChunkHit>)list));
    }

    private void applyRollbackFromManifest(final CommandSender commandSender, final World world, final long l, final long l2, final RollbackModel.ParsedArgs parsedArgs, List<DatabaseMariadb.ChunkHit> list) {
        int n;
        if (list.isEmpty()) {
            this.finishAndReport(commandSender, l, l2, parsedArgs, 0, 0, 0);
            return;
        }
        final boolean bl = this.plugin.getConfig().getBoolean("debug.waterlogged", true);
        ArrayList<DatabaseMariadb.ChunkHit> arrayList = new ArrayList<DatabaseMariadb.ChunkHit>(list);
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        for (int i = arrayList.size() - 1; i > 0; --i) {
            n = threadLocalRandom.nextInt(i + 1);
            DatabaseMariadb.ChunkHit chunkHit2 = (DatabaseMariadb.ChunkHit)arrayList.get(n);
            arrayList.set(n, (DatabaseMariadb.ChunkHit)arrayList.get(i));
            arrayList.set(i, chunkHit2);
        }
        final ArrayDeque<DatabaseMariadb.ChunkHit> arrayDeque = new ArrayDeque<DatabaseMariadb.ChunkHit>(arrayList);
        n = Math.max(1, Math.min(4, this.plugin.getConfig().getInt("rollback.global.parallelChunks", 4)));
        final boolean bl2 = parsedArgs.radius() < 0 && this.plugin.getConfig().getBoolean("rollback.global.unloadAfter", true);
        final int n2 = this.frameTaskEpoch.incrementAndGet();
        Debug.Ctx ctx = Debug.ctx(this.plugin).with("rb", n2).with("world", world.getName());
        Debug.log(this.plugin, "rollback.manifest", String.valueOf(ctx) + " got manifest: chunks=" + list.size() + " parallel=" + n, new Object[0]);
        list.stream().limit(5L).forEach(chunkHit -> Debug.log(this.plugin, "rollback.manifest", "  top: (" + chunkHit.cx() + "," + chunkHit.cz() + ") hits=" + chunkHit.hits(), new Object[0]));
        final AtomicInteger atomicInteger = new AtomicInteger(0);
        final AtomicInteger atomicInteger2 = new AtomicInteger(0);
        final AtomicInteger atomicInteger3 = new AtomicInteger(0);
        final AtomicInteger atomicInteger4 = new AtomicInteger(0);
        final AtomicInteger atomicInteger5 = new AtomicInteger(0);
        final AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        Runnable runnable = new Runnable(){

            @Override
            public void run() {
                DatabaseMariadb.ChunkHit chunkHit;
                if (atomicBoolean.get()) {
                    return;
                }
                while (atomicInteger.get() < n && (chunkHit = (DatabaseMariadb.ChunkHit)arrayDeque.pollFirst()) != null) {
                    int n3 = chunkHit.cx();
                    int n22 = chunkHit.cz();
                    Debug.Ctx ctx = Debug.ctx(RollbackEngine.this.plugin).with("rb", n2).with("world", world.getName()).with("cx", n3).with("cz", n22);
                    atomicInteger.incrementAndGet();
                    RollbackEngine.this.databaseMariadb.queryActionsForRollbackChunk(world.getName(), n3, n22, parsedArgs.targetUuid(), l2 / 1000L, parsedArgs.actionFilter(), parsedArgs.itemFilter(), list -> {
                        int n3 = list.size();
                        RollbackEngine.this.applyIncludeExcludeFilters((List<ActionLog>)list, parsedArgs.includeMaterials(), parsedArgs.excludeMaterials());
                        if (n3 > 0 && list.isEmpty()) {
                            Debug.log(RollbackEngine.this.plugin, "rollback.perChunk", String.valueOf(ctx) + " chunk had " + n3 + " logs but all were filtered out; skipping.", new Object[0]);
                        }
                        if (list.isEmpty()) {
                            atomicInteger.decrementAndGet();
                            atomicInteger2.incrementAndGet();
                            Bukkit.getGlobalRegionScheduler().run((Plugin)RollbackEngine.this.plugin, scheduledTask -> this.run());
                            return;
                        }
                        RollbackEngine.this.withChunkLoaded(world, n3, n22, () -> RollbackEngine.this.runRollbackTasksForLogsInThisChunk(world, l2, parsedArgs, (List<ActionLog>)list, bl, ctx, (n3, n4) -> {
                            atomicInteger3.addAndGet((int)n3);
                            atomicInteger4.addAndGet((int)n4);
                            atomicInteger2.incrementAndGet();
                            atomicInteger.decrementAndGet();
                            if (bl2) {
                                world.unloadChunkRequest(n3, n22);
                            }
                            Bukkit.getGlobalRegionScheduler().run((Plugin)RollbackEngine.this.plugin, scheduledTask -> this.run());
                        }), throwable -> {
                            atomicInteger5.incrementAndGet();
                            atomicInteger.decrementAndGet();
                            RollbackEngine.this.plugin.getLogger().log(Level.WARNING, "[Rollback] Failed to load chunk " + n3 + "," + n22 + " in " + world.getName(), (Throwable)throwable);
                            Bukkit.getGlobalRegionScheduler().run((Plugin)RollbackEngine.this.plugin, scheduledTask -> this.run());
                        });
                    });
                }
                if (arrayDeque.isEmpty() && atomicInteger.get() == 0 && atomicBoolean.compareAndSet(false, true)) {
                    if (atomicInteger5.get() > 0) {
                        commandSender.sendMessage(String.valueOf(ChatColor.YELLOW) + "Rollback skipped " + atomicInteger5.get() + " chunk(s) that could not be loaded. See the console for details.");
                    }
                    RollbackEngine.this.finishAndReport(commandSender, l, l2, parsedArgs, atomicInteger3.get(), atomicInteger4.get(), atomicInteger2.get());
                }
            }
        };
        Bukkit.getGlobalRegionScheduler().run((Plugin)this.plugin, scheduledTask -> runnable.run());
    }

    private void runRollbackTasksForLogsInThisChunk(World world, long l, RollbackModel.ParsedArgs parsedArgs, List<ActionLog> list, boolean bl, Debug.Ctx ctx, BiConsumer<Integer, Integer> biConsumer) {
        Object object5;
        boolean bl2;
        Object object2;
        Object object3;
        Object object4;
        if (parsedArgs.radius() >= 0) {
            int n = parsedArgs.originX();
            int n2 = parsedArgs.originY();
            int n3 = parsedArgs.originZ();
            long l2 = (long)parsedArgs.radius() * (long)parsedArgs.radius();
            list.removeIf(actionLog -> {
                long l2;
                long l3;
                long l4 = actionLog.getX() - n;
                return l4 * l4 + (l3 = (long)(actionLog.getY() - n2)) * l3 + (l2 = (long)(actionLog.getZ() - n3)) * l2 > l2;
            });
            if (list.isEmpty()) {
                biConsumer.accept(0, 0);
                return;
            }
        }
        ChunkCounters chunkCounters = new ChunkCounters();
        AtomicInteger atomicInteger = new AtomicInteger(0);
        ArrayList<Integer> arrayList = new ArrayList<Integer>();
        ArrayList arrayList2 = new ArrayList();
        UUID uUID = SYSTEM_ACTOR;
        list.sort(Comparator.comparingLong(RollbackEngine::tsMillis).thenComparingInt(RollbackEngine::eventPriority));
        Debug.log(this.plugin, "rollback.perChunk", String.valueOf(ctx) + " planning chunk logs=" + list.size(), new Object[0]);
        long l3 = Debug.tic();
        int n = Math.max(16, list.size() + (list.size() >>> 1));
        HashMap<Long, ActionLog> hashMap = new HashMap<Long, ActionLog>();
        HashMap<Long, ActionLog> hashMap2 = new HashMap<Long, ActionLog>(n, 0.5f);
        HashMap<Long, ActionLog> hashMap3 = new HashMap<Long, ActionLog>(Math.max(16, list.size() / 8), 0.5f);
        HashMap<Long, ActionLog> hashMap4 = new HashMap<Long, ActionLog>(n, 0.5f);
        HashMap<Long, ActionLog> hashMap5 = new HashMap<Long, ActionLog>(n, 0.5f);
        HashMap<DecorKey, ActionLog> hashMap6 = new HashMap<DecorKey, ActionLog>(n, 0.5f);
        HashMap<DecorKey, ActionLog> hashMap7 = new HashMap<DecorKey, ActionLog>(n, 0.5f);
        LinkedHashMap<DecorKey, ActionLog> linkedHashMap = new LinkedHashMap<DecorKey, ActionLog>(list.size() / 8 + 16, 0.5f);
        LinkedHashMap<DecorKey, ActionLog> linkedHashMap2 = new LinkedHashMap<DecorKey, ActionLog>(list.size() / 8 + 16, 0.5f);
        HashMap<Integer, Material> hashMap8 = new HashMap<Integer, Material>(list.size());
        HashMap<Long, ActionLog> hashMap9 = new HashMap<Long, ActionLog>(n, 0.5f);
        for (ActionLog object62 : list) {
            boolean bl3;
            hashMap8.put(object62.getId(), this.extractMaterialFromLog(object62));
            long arrayDeque2 = RollbackEngine.tsMillis(object62);
            object4 = RollbackEngine.act(object62);
            long atomicInteger2 = RollbackEngine.posKey(object62);
            object3 = this.metaOf(object62);
            boolean entry = ("ENTITY_PLACE".equals(object4) || ((String)object4).startsWith("BLOCK_")) && object3 != null && object3.has("entity");
            boolean actionLog2 = this.isRealBlock(object62) && !entry;
            Timestamp timestamp = object62.getRolledBackTimestamp();
            boolean bl4 = bl3 = timestamp != null && timestamp.getTime() > l;
            if (bl3 && actionLog2 && arrayDeque2 <= l) {
                hashMap9.put(atomicInteger2, object62);
            }
            if (arrayDeque2 > l && actionLog2) {
                hashMap4.put(atomicInteger2, object62);
                if ("BLOCK_PLACE".equals(object4)) {
                    hashMap.putIfAbsent(atomicInteger2, object62);
                }
                if ("BLOCK_BREAK".equals(object4) || "BLOCK_BURN".equals(object4)) {
                    hashMap5.put(atomicInteger2, object62);
                }
            }
            object2 = object62.getDetails() == null ? "" : object62.getDetails().toLowerCase(Locale.ROOT);
            boolean bl5 = bl2 = ((String)object4).startsWith("CONTAINER_") && (object3 != null && object3.has("entity") && ("ITEM_FRAME".equalsIgnoreCase(object3.get("entity").getAsString()) || "GLOW_ITEM_FRAME".equalsIgnoreCase(object3.get("entity").getAsString())) || ((String)object2).contains("item frame") || ((String)object2).contains("item_frame"));
            if (bl2) {
                object5 = this.decorKeyOf(object62);
                if (arrayDeque2 <= l) {
                    linkedHashMap.put((DecorKey)object5, object62);
                    continue;
                }
                linkedHashMap2.put((DecorKey)object5, object62);
                continue;
            }
            if (entry) {
                object5 = this.decorKeyOf(object62);
                if (arrayDeque2 <= l) {
                    hashMap6.put((DecorKey)object5, object62);
                    continue;
                }
                hashMap7.putIfAbsent((DecorKey)object5, object62);
                continue;
            }
            if (arrayDeque2 <= l) {
                if (actionLog2) {
                    hashMap2.put(atomicInteger2, object62);
                    continue;
                }
                if (!((String)object4).equals("CONTAINER_SNAPSHOT")) continue;
                hashMap3.put(atomicInteger2, object62);
                continue;
            }
            if (!actionLog2) continue;
            hashMap4.put(atomicInteger2, object62);
        }
        if (!linkedHashMap2.isEmpty()) {
            linkedHashMap.keySet().removeAll(linkedHashMap2.keySet());
        }
        Debug.log(this.plugin, "rollback.perChunk", String.valueOf(ctx) + " plan: blocks(after)=" + hashMap4.size() + ", blocks(before)=" + hashMap2.size() + ", rolled=" + hashMap9.size() + "  [build took " + Debug.toc(l3) + "]", new Object[0]);
        ArrayDeque arrayDeque = new ArrayDeque();
        ArrayDeque<Object> arrayDeque2 = new ArrayDeque<Object>(hashMap6.size() + hashMap7.size());
        ArrayDeque<ActionLog> arrayDeque3 = new ArrayDeque<ActionLog>(linkedHashMap.size() + linkedHashMap2.size());
        ArrayDeque arrayDeque4 = new ArrayDeque(hashMap3.size());
        object4 = new AtomicInteger();
        AtomicInteger atomicInteger2 = new AtomicInteger();
        ArrayDeque arrayDeque5 = new ArrayDeque(hashMap4.size());
        for (Map.Entry arrayList3 : hashMap9.entrySet()) {
            ActionLog hashSet = (ActionLog)arrayList3.getValue();
            hashMap4.remove(arrayList3.getKey());
            hashMap2.remove(arrayList3.getKey());
            arrayDeque.add(hashSet);
        }
        object3 = new ArrayList();
        ArrayList<ActionLog> arrayList3 = new ArrayList<ActionLog>();
        for (ActionLog actionLog2 : hashMap4.values()) {
            String string = RollbackEngine.act(actionLog2);
            if ("BUCKET_EMPTY".equals(string) || "LIQUID_FLOW".equals(string)) {
                object3.add(actionLog2);
                continue;
            }
            arrayList3.add(actionLog2);
        }
        object3.sort(Comparator.comparingLong(RollbackEngine::tsMillis));
        arrayList3.sort(Comparator.comparingLong(RollbackEngine::tsMillis));
        arrayDeque5.addAll(object3);
        arrayDeque5.addAll(arrayList3);
        for (Map.Entry entry : hashMap5.entrySet()) {
            ActionLog actionLog3 = (ActionLog)hashMap4.get(entry.getKey());
            if (actionLog3 == null || "BLOCK_BREAK".equals(object2 = RollbackEngine.act(actionLog3)) || "BLOCK_BURN".equals(object2)) continue;
            arrayDeque5.add((ActionLog)entry.getValue());
        }
        HashSet hashSet = new HashSet(hashMap4.keySet());
        hashSet.addAll(hashMap3.keySet());
        for (ActionLog actionLog4 : list) {
            object2 = actionLog4.getRolledBackTimestamp();
            if (object2 == null || ((Timestamp)object2).getTime() <= l || !this.isRealBlock(actionLog4)) continue;
            hashSet.add(RollbackEngine.posKey(actionLog4));
        }
        for (Map.Entry entry : hashMap2.entrySet()) {
            if (!hashMap4.containsKey(entry.getKey()) || this.matchesLoggedState((Block)(object2 = world.getBlockAt(((ActionLog)entry.getValue()).getX(), ((ActionLog)entry.getValue()).getY(), ((ActionLog)entry.getValue()).getZ())), (ActionLog)entry.getValue())) continue;
            hashSet.add((Long)entry.getKey());
        }
        for (Map.Entry entry : hashMap2.entrySet()) {
            boolean bl6;
            if (!hashSet.contains(entry.getKey())) continue;
            object2 = RollbackEngine.act((ActionLog)entry.getValue());
            bl2 = "BLOCK_BREAK".equals(object2) || "BLOCK_BURN".equals(object2);
            object5 = ((ActionLog)entry.getValue()).getRolledBackTimestamp();
            boolean bl7 = bl6 = object5 != null && ((Timestamp)object5).getTime() > l;
            if (!bl2 || hashMap4.containsKey(entry.getKey())) {
                arrayDeque.add((ActionLog)entry.getValue());
                continue;
            }
            if (!bl6) continue;
            this.maybeDecision(ctx, "skip pre-cutoff break/burn with recentRollback", (ActionLog)entry.getValue());
        }
        arrayDeque4.addAll(hashMap3.values());
        HashSet<DecorKey> hashSet2 = new HashSet<DecorKey>();
        HashSet<DecorKey> hashSet3 = new HashSet<DecorKey>();
        object2 = new HashSet<DecorKey>();
        for (Object object5 : hashMap7.values()) {
            DecorKey decorKey = this.decorKeyOf((ActionLog)object5);
            if (!hashSet3.add(decorKey)) continue;
            arrayDeque2.add(object5);
            if (!"ENTITY_PLACE".equalsIgnoreCase(RollbackEngine.act((ActionLog)object5)) || hashMap6.containsKey(decorKey)) continue;
            hashSet2.add(decorKey);
        }
        for (Object object5 : hashMap6.values()) {
            if (!object2.add(this.decorKeyOf((ActionLog)object5))) continue;
            arrayDeque2.add(object5);
        }
        ArrayList arrayList4 = new ArrayList(linkedHashMap2.values());
        arrayList4.sort(Comparator.comparingLong(actionLog -> actionLog.getEventTimestamp().getTime()).reversed());
        object5 = arrayList4.iterator();
        while (object5.hasNext()) {
            ActionLog actionLog5 = (ActionLog)object5.next();
            if (hashSet2.contains(this.decorKeyOf(actionLog5))) continue;
            arrayDeque3.add(actionLog5);
        }
        for (ActionLog actionLog6 : linkedHashMap2.values()) {
            if (!hashSet2.contains(this.decorKeyOf(actionLog6))) continue;
            arrayList.add(actionLog6.getId());
        }
        for (ActionLog actionLog7 : linkedHashMap.values()) {
            DecorKey decorKey = this.decorKeyOf(actionLog7);
            arrayDeque3.add(actionLog7);
            if (hashMap6.containsKey(decorKey) || hashMap7.containsKey(decorKey) || !object2.add(decorKey)) continue;
            arrayDeque2.add(actionLog7);
        }
        int n2 = Math.max(1, Math.min(500, this.plugin.getConfig().getInt("rollback.workPerTick", 250)));
        int n3 = list.get(0).getX() >> 4;
        int n4 = list.get(0).getZ() >> 4;
        State state = new State();
        Bukkit.getRegionScheduler().runAtFixedRate((Plugin)this.plugin, world, n3, n4, arg_0 -> this.lambda$runRollbackTasksForLogsInThisChunk$9(state, arrayDeque5, n2, world, bl, ctx, chunkCounters, hashMap2, arrayList, (AtomicInteger)object4, arrayDeque, arrayList2, hashMap4, hashMap, arrayDeque2, hashMap6, hashMap8, l, atomicInteger, arrayDeque3, atomicInteger2, arrayDeque4, uUID, biConsumer, arg_0), 1L, 1L);
    }

    private boolean traceHit(ActionLog actionLog) {
        if (this.tracePos == null) {
            return false;
        }
        return actionLog.getX() == this.tracePos[0] && actionLog.getY() == this.tracePos[1] && actionLog.getZ() == this.tracePos[2];
    }

    private void maybeDecision(Debug.Ctx ctx, String string, ActionLog actionLog) {
        if (Debug.on(this.plugin, "rollback.decisions") || this.traceHit(actionLog)) {
            Debug.log(this.plugin, "rollback.decisions", String.valueOf(ctx) + " " + string + " @ " + actionLog.getX() + "," + actionLog.getY() + "," + actionLog.getZ() + " act=" + RollbackEngine.act(actionLog) + " id=" + actionLog.getId(), new Object[0]);
        }
    }

    private int forceClearAt(World world, int n, int n2, int n3, @Nullable ActionLog actionLog, Debug.Ctx ctx, ChunkCounters chunkCounters) {
        Chest chest;
        Chest.Type type;
        Block block = world.getBlockAt(n, n2, n3);
        BlockData blockData = block.getBlockData();
        Material material = block.getType();
        int n4 = 0;
        if (blockData instanceof Chest && (type = (chest = (Chest)blockData).getType()) != Chest.Type.SINGLE) {
            BlockData blockData2;
            Block block2;
            BlockFace blockFace;
            BlockFace blockFace2 = chest.getFacing();
            switch (blockFace2) {
                case NORTH: {
                    BlockFace blockFace3;
                    if (type == Chest.Type.LEFT) {
                        blockFace3 = BlockFace.WEST;
                        break;
                    }
                    blockFace3 = BlockFace.EAST;
                    break;
                }
                case SOUTH: {
                    BlockFace blockFace3;
                    if (type == Chest.Type.LEFT) {
                        blockFace3 = BlockFace.EAST;
                        break;
                    }
                    blockFace3 = BlockFace.WEST;
                    break;
                }
                case WEST: {
                    BlockFace blockFace3;
                    if (type == Chest.Type.LEFT) {
                        blockFace3 = BlockFace.SOUTH;
                        break;
                    }
                    blockFace3 = BlockFace.NORTH;
                    break;
                }
                case EAST: {
                    BlockFace blockFace3;
                    if (type == Chest.Type.LEFT) {
                        blockFace3 = BlockFace.NORTH;
                        break;
                    }
                    blockFace3 = BlockFace.SOUTH;
                    break;
                }
                default: {
                    BlockFace blockFace3 = blockFace = null;
                }
            }
            if (blockFace != null && (block2 = block.getRelative(blockFace)).getType() == Material.CHEST && (blockData2 = block2.getBlockData()) instanceof Chest) {
                Chest chest2 = (Chest)blockData2;
                chest2.setType(Chest.Type.SINGLE);
                block2.setBlockData((BlockData)chest2, false);
            }
        }
        if (blockData instanceof Bisected && !(type = block.getRelative((chest = (Bisected)blockData).getHalf() == Bisected.Half.TOP ? BlockFace.DOWN : BlockFace.UP)).getType().isAir()) {
            type.setType(Material.AIR, false);
            ++n4;
        }
        if (blockData instanceof Bed && !(type = block.getRelative((chest = (Bed)blockData).getFacing())).getType().isAir()) {
            type.setType(Material.AIR, false);
            ++n4;
        }
        if (!material.isAir()) {
            block.setType(Material.AIR, false);
            ++n4;
        }
        this.purgeDecorEntities(new Location(world, (double)n, (double)n2, (double)n3));
        if (actionLog != null && ((chest = this.extractMaterialFromLog(actionLog)) == Material.WATER || chest == Material.LAVA)) {
            int n5 = this.drainFlowingCluster(new Location(world, (double)n, (double)n2, (double)n3), (Material)chest);
            if (n5 > 0) {
                chunkCounters.waterDrains.addAndGet(n5);
            }
            n4 += n5;
        }
        return n4;
    }

    private int respawnPainting(Location location, JsonObject jsonObject) {
        World world = location.getWorld();
        if (world == null || jsonObject == null) {
            return 0;
        }
        try {
            Art art = Art.valueOf((String)jsonObject.get("art").getAsString().toUpperCase(Locale.ROOT));
            BlockFace blockFace = EntityMetadataUtil.face(jsonObject, "facing", BlockFace.SOUTH);
            Block block = location.getBlock().getRelative(blockFace);
            boolean bl = world.getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0).stream().filter(entity -> {
                Painting painting;
                return entity instanceof Painting && (painting = (Painting)entity).getArt() == art && painting.getFacing() == blockFace;
            }).anyMatch(entity -> {
                Painting painting = (Painting)entity;
                return RollbackEngine.sameAnchor(painting.getLocation().getBlock().getRelative(painting.getAttachedFace()), location);
            });
            if (bl) {
                return 0;
            }
            Painting painting2 = (Painting)world.spawn(block.getLocation().add(0.5, 0.5, 0.5), Painting.class, painting -> {
                painting.setFacingDirection(blockFace, true);
                painting.setArt(art, true);
                painting.setPersistent(true);
            });
            return painting2 != null ? 1 : 0;
        }
        catch (Exception exception) {
            return 0;
        }
    }

    private void applyIncludeExcludeFilters(List<ActionLog> list, Set<String> set, Set<String> set2) {
        if ((set == null || set.isEmpty()) && (set2 == null || set2.isEmpty())) {
            return;
        }
        list.removeIf(arg_0 -> this.lambda$applyIncludeExcludeFilters$13(set, set2, arg_0));
    }

    boolean isRealBlock(ActionLog actionLog) {
        if (this.isSyntheticRollback(actionLog)) {
            return true;
        }
        String string = RollbackEngine.act(actionLog);
        return string.startsWith("BLOCK_") || "LIQUID_FLOW".equals(string) || "BUCKET_EMPTY".equals(string);
    }

    private void finishAndReport(CommandSender commandSender, long l, long l2, RollbackModel.ParsedArgs parsedArgs, int n, int n2, int n3) {
        this.frameTaskEpoch.incrementAndGet();
        this.metaCache.clear();
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            this.sendCompletionMessage(player, new RollbackStatsContext(parsedArgs.worldName(), l, parsedArgs.periodMillis(), parsedArgs.radius(), n2, n, n3));
        } else {
            commandSender.sendMessage(this.plugin.getLang().get("messages.rollback.completedHeader", Map.of()));
        }
        this.rollbackInProgress.set(false);
        int n4 = n + n2;
        int n5 = this.journaledThisRun.get();
        Debug.ctx(this.plugin).with("rb", this.currentRollbackId).log("rollback.trace", "Applied=%d, Journaled=%d", n4, n5);
        if (commandSender instanceof Player) {
            Player player = (Player)commandSender;
            player.sendMessage(String.valueOf(ChatColor.GRAY) + String.format("applied=%d, journaled=%d", n4, n5));
        }
        if (parsedArgs.periodMillis() <= 10000L) {
            long l3 = (System.currentTimeMillis() - 10000L) / 1000L;
            this.databaseMariadb.fetchRecentBySource(l3, map -> {
                String string = String.valueOf(map.getOrDefault("player", 0));
                String string2 = String.valueOf(map.getOrDefault("rollback", 0));
                String string3 = String.format("recent_by_source(last~10s) {player:%s, rollback:%s}", string, string2);
                this.plugin.getLogger().info("[Rollback] " + string3);
                if (this.plugin.getConfig().getBoolean("rollback.debug.showRecentBySourceToPlayers", false) && commandSender instanceof Player) {
                    Player player = (Player)commandSender;
                    player.sendMessage(String.valueOf(ChatColor.GRAY) + string3);
                }
            });
        }
    }

    private void sendCompletionMessage(Player player, RollbackStatsContext rollbackStatsContext) {
        double d = (double)(System.currentTimeMillis() - rollbackStatsContext.rollbackStart()) / 1000.0;
        player.sendMessage(this.plugin.getLang().get("messages.rollback.completedHeader", Map.of()));
        player.sendMessage(this.plugin.getLang().get("messages.rollback.completedSummary", Map.of("world", rollbackStatsContext.worldName())));
        String string = rollbackStatsContext.radius() < 0 ? "global" : String.valueOf(rollbackStatsContext.radius());
        player.sendMessage(this.plugin.getLang().get("messages.rollback.completedDetails", Map.of("time", RollbackModel.ParsedArgs.formatMillis(rollbackStatsContext.timeRangeMillis()), "radius", string, "items", String.valueOf(rollbackStatsContext.itemsModified()), "blocks", String.valueOf(rollbackStatsContext.blocksModified()), "chunks", String.valueOf(rollbackStatsContext.totalChunks()), "timetaken", DF2.get().format(d))));
    }

    private int countValidItems(ItemStack[] itemStackArray) {
        int n = 0;
        for (ItemStack itemStack : itemStackArray) {
            if (itemStack == null || itemStack.getType() == Material.AIR) continue;
            ++n;
        }
        return n;
    }

    @Nullable
    private ItemFrame getExactFrame(World world, Location location, @Nullable BlockFace blockFace, @Nullable EntityType entityType) {
        if (Debug.on(this.plugin, "rollback.frames")) {
            Debug.log(this.plugin, "rollback.frames", "findFrame anchor=" + String.valueOf(location.toVector()) + (String)(blockFace != null ? " facing=" + String.valueOf(blockFace) : "") + (String)(entityType != null ? " type=" + String.valueOf(entityType) : ""), new Object[0]);
        }
        Block block = location.getBlock();
        Collection collection = world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), 1.25, 1.25, 1.25, entity -> {
            if (!(entity instanceof ItemFrame)) {
                return false;
            }
            ItemFrame itemFrame = (ItemFrame)entity;
            if (entityType != null && itemFrame.getType() != entityType) {
                return false;
            }
            if (blockFace != null && itemFrame.getFacing() != blockFace) {
                return false;
            }
            Block block2 = itemFrame.getLocation().getBlock();
            Block block3 = block2.getRelative(itemFrame.getAttachedFace());
            return block3.equals((Object)block) || block2.equals((Object)block);
        });
        return collection.stream().map(entity -> (ItemFrame)entity).min(Comparator.comparingDouble(itemFrame -> itemFrame.getLocation().distanceSquared(location))).orElse(null);
    }

    private boolean isPainting(ActionLog actionLog) {
        JsonObject jsonObject = this.metaOf(actionLog);
        return jsonObject != null && jsonObject.has("entity") && "PAINTING".equalsIgnoreCase(jsonObject.get("entity").getAsString());
    }

    private int purgePlacedEntity(Location location, ActionLog actionLog) {
        EntityType entityType;
        World world = location.getWorld();
        JsonObject jsonObject = this.metaOf(actionLog);
        if (world == null || jsonObject == null || !jsonObject.has("entity")) {
            return 0;
        }
        try {
            entityType = EntityType.valueOf((String)jsonObject.get("entity").getAsString().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return this.purgeDecorEntities(location);
        }
        UUID uUID = null;
        if (jsonObject.has("entityUuid")) {
            try {
                uUID = UUID.fromString(jsonObject.get("entityUuid").getAsString());
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        Entity entity = null;
        double d = Double.MAX_VALUE;
        for (Entity entity2 : world.getNearbyEntities(location, 3.0, 3.0, 3.0)) {
            if (entity2.getType() != entityType) continue;
            if (uUID != null && entity2.getUniqueId().equals(uUID)) {
                entity = entity2;
                break;
            }
            double d2 = entity2.getLocation().distanceSquared(location);
            if (!(d2 < d)) continue;
            d = d2;
            entity = entity2;
        }
        if (entity != null) {
            entity.remove();
            return 1;
        }
        Class clazz = entityType.getEntityClass();
        if (clazz != null && Hanging.class.isAssignableFrom(clazz) || entityType == EntityType.ARMOR_STAND) {
            return this.purgeDecorEntities(location);
        }
        return 0;
    }

    private int purgeDecorEntities(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return 0;
        }
        Block block = location.getBlock();
        ArrayList arrayList = new ArrayList(world.getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 2.0, 2.0, 2.0));
        int n = 0;
        for (Entity entity : arrayList) {
            if (entity instanceof Hanging) {
                ItemFrame itemFrame;
                ItemStack itemStack;
                Hanging hanging = (Hanging)entity;
                Block block2 = hanging.getLocation().getBlock();
                Block block3 = block2.getRelative(hanging.getAttachedFace());
                if (!block3.equals((Object)block) && !block2.equals((Object)block)) continue;
                if (hanging instanceof ItemFrame && (itemStack = (itemFrame = (ItemFrame)hanging).getItem()) != null && !itemStack.getType().isAir()) {
                    JsonObject jsonObject = new JsonObject();
                    jsonObject.addProperty("origin", "rollback");
                    jsonObject.addProperty("reversing", Boolean.valueOf(false));
                    jsonObject.addProperty("timestamp", (Number)System.currentTimeMillis());
                    String string = RollbackEngine.toItemJsonSafe(itemStack);
                    if (string != null) {
                        jsonObject.add("item", JsonParser.parseString((String)string));
                    }
                    jsonObject.addProperty("entity", itemFrame.getType().name());
                    jsonObject.addProperty("facing", itemFrame.getFacing().name());
                    jsonObject.addProperty("rotation", itemFrame.getRotation().name());
                    jsonObject.addProperty("invisible", Boolean.valueOf(itemFrame.isInvisible()));
                    jsonObject.addProperty("fixed", Boolean.valueOf(itemFrame.isFixed()));
                    String string2 = "removed " + (String)(itemStack.getAmount() > 1 ? "x" + itemStack.getAmount() + " " : "") + itemStack.getType().name().toLowerCase() + " from " + itemFrame.getType().name().toLowerCase() + " (by rollback)";
                    this.databaseMariadb.logActionAsync(SYSTEM_ACTOR, SYSTEM_NAME, "CONTAINER_TAKE", string2, jsonObject.toString(), location);
                }
                entity.remove();
                ++n;
                continue;
            }
            if (!(entity instanceof ArmorStand) || !entity.getLocation().getBlock().equals((Object)block) && !entity.getLocation().getBlock().getRelative(BlockFace.DOWN).equals((Object)block)) continue;
            entity.remove();
            ++n;
        }
        return n;
    }

    private static boolean sameAnchor(Block block, Location location) {
        if (block.getWorld() == null || location.getWorld() == null) {
            return false;
        }
        return block.getWorld().equals((Object)location.getWorld()) && Math.abs(block.getX() - location.getBlockX()) <= 1 && Math.abs(block.getY() - location.getBlockY()) <= 1 && Math.abs(block.getZ() - location.getBlockZ()) <= 1;
    }

    private int purgeDecorEntitiesPaintingAware(Location location, JsonObject jsonObject) {
        BlockFace blockFace;
        Art art;
        World world = location.getWorld();
        if (world == null) {
            return 0;
        }
        try {
            if (jsonObject == null || !jsonObject.has("art") || !jsonObject.has("facing")) {
                this.plugin.getLogger().warning("[Rollback] Insufficient metadata for painting purge at " + String.valueOf(location));
                return 0;
            }
            art = Art.valueOf((String)jsonObject.get("art").getAsString().toUpperCase(Locale.ROOT));
            blockFace = EntityMetadataUtil.face(jsonObject, "facing", BlockFace.SOUTH);
        }
        catch (Exception exception) {
            this.plugin.getLogger().warning("[Rollback] Failed to parse metadata for painting purge at " + String.valueOf(location) + ": " + exception.getMessage());
            return 0;
        }
        double d = 2.0;
        Collection collection = world.getNearbyEntities(location, d, d, d);
        int n = 0;
        for (Entity entity : collection) {
            Block block;
            Painting painting;
            if (!(entity instanceof Painting) || (painting = (Painting)entity).getArt() != art || painting.getFacing() != blockFace || !RollbackEngine.sameAnchor(block = painting.getLocation().getBlock().getRelative(painting.getAttachedFace()), location)) continue;
            painting.remove();
            ++n;
            break;
        }
        if (n == 0 && this.plugin.getLogger().isLoggable(Level.FINE)) {
            this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback] No matching painting found to purge for Art=%s, Facing=%s, LoggedAnchor=%s (search radius %s)", art, blockFace, location.toVector(), d));
        }
        if (this.plugin.getLogger().isLoggable(Level.FINE)) {
            this.plugin.getLogger().fine("[RB-TRACE]  purgeDecor removed=" + n);
        }
        return n;
    }

    public int respawnDecor(Material material, Location location, ActionLog actionLog, AtomicInteger atomicInteger) {
        EntityType entityType;
        World world = location.getWorld();
        if (world == null) {
            return 0;
        }
        JsonObject jsonObject = this.metaOf(actionLog);
        if (jsonObject == null) {
            this.plugin.getLogger().warning("[ProtectCore] [DBG] respawnDecor: Metadata missing or unparseable for log at " + String.valueOf(location) + ". Cannot respawn decor.");
            return 0;
        }
        EntityType entityType2 = null;
        if (jsonObject.has("entity")) {
            try {
                entityType2 = EntityType.valueOf((String)jsonObject.get("entity").getAsString().toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        if (entityType2 == null) {
            switch (material != null ? material : Material.AIR) {
                case ITEM_FRAME: {
                    EntityType entityType3 = EntityType.ITEM_FRAME;
                    break;
                }
                case GLOW_ITEM_FRAME: {
                    EntityType entityType3 = EntityType.GLOW_ITEM_FRAME;
                    break;
                }
                case PAINTING: {
                    EntityType entityType3 = EntityType.PAINTING;
                    break;
                }
                case ARMOR_STAND: {
                    EntityType entityType3 = EntityType.ARMOR_STAND;
                    break;
                }
                default: {
                    EntityType entityType3 = entityType2 = null;
                }
            }
        }
        if ((entityType = entityType2) == null) {
            return 0;
        }
        if (this.plugin.getLogger().isLoggable(Level.FINE)) {
            this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback-Frames] respawnDecor called for %s at %s. LogID: %s", entityType, location.toVector(), actionLog.getId()));
        }
        if (entityType == EntityType.PAINTING) {
            return this.respawnPainting(location, jsonObject);
        }
        if (entityType == EntityType.ITEM_FRAME || entityType == EntityType.GLOW_ITEM_FRAME) {
            BlockFace blockFace = EntityMetadataUtil.face(jsonObject, "facing", BlockFace.SOUTH);
            Location location2 = location.getBlock().getRelative(blockFace).getLocation().add(0.5, 0.5, 0.5);
            int n = atomicInteger.incrementAndGet();
            if (this.plugin.getLogger().isLoggable(Level.FINE)) {
                this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback-Frames] Scheduling spawn task for %s at %s with delay %d ticks. LogID: %s", entityType, location.toVector(), n, actionLog.getId()));
            }
            int n2 = location.getBlockX() >> 4;
            int n3 = location.getBlockZ() >> 4;
            Bukkit.getRegionScheduler().runDelayed((Plugin)this.plugin, world, n2, n3, scheduledTask -> {
                boolean bl = world.getNearbyEntities(location2, 1.0, 1.0, 1.0).stream().anyMatch(entity -> {
                    if (entity.getType() != entityType || !(entity instanceof ItemFrame)) {
                        return false;
                    }
                    ItemFrame itemFrame = (ItemFrame)entity;
                    if (itemFrame.getFacing() != blockFace) {
                        return false;
                    }
                    Block block = itemFrame.getLocation().getBlock();
                    Block block2 = block.getRelative(itemFrame.getAttachedFace());
                    return block.equals((Object)location.getBlock()) || block2.equals((Object)location.getBlock());
                });
                if (this.plugin.getLogger().isLoggable(Level.FINE)) {
                    this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback-Frames] Spawn task running for %s at %s. Frame already exists: %b. LogID: %s", entityType, location.toVector(), bl, actionLog.getId()));
                }
                if (bl) {
                    return;
                }
                world.spawn(location2, entityType.getEntityClass(), hanging -> {
                    hanging.setFacingDirection(blockFace, true);
                    if (hanging instanceof ItemFrame) {
                        ItemStack itemStack;
                        ItemFrame itemFrame = (ItemFrame)hanging;
                        if (jsonObject.has("rotation")) {
                            try {
                                itemFrame.setRotation(Rotation.valueOf((String)jsonObject.get("rotation").getAsString().toUpperCase(Locale.ROOT)));
                            }
                            catch (IllegalArgumentException illegalArgumentException) {
                                // empty catch block
                            }
                        }
                        if (jsonObject.has("item") && (itemStack = ItemMetadataUtil.jsonToItem(jsonObject.get("item").toString())) != null) {
                            itemFrame.setItem(itemStack, false);
                        }
                        if (jsonObject.has("invisible") && jsonObject.get("invisible").getAsBoolean()) {
                            itemFrame.setInvisible(true);
                        }
                        if (jsonObject.has("fixed") && jsonObject.get("fixed").getAsBoolean()) {
                            itemFrame.setFixed(true);
                        }
                    }
                    hanging.setPersistent(true);
                });
            }, (long)n);
            return 1;
        }
        if (entityType == EntityType.ARMOR_STAND) {
            BlockFace blockFace = EntityMetadataUtil.face(jsonObject, "facing", BlockFace.SOUTH);
            Location location3 = location.clone().add(0.5, 0.0, 0.5);
            if (jsonObject.has("yaw")) {
                location3.setYaw(jsonObject.get("yaw").getAsFloat());
            } else {
                location3.setYaw(EntityMetadataUtil.yawFromFacing(blockFace));
            }
            int n = atomicInteger.incrementAndGet();
            int n4 = location.getBlockX() >> 4;
            int n5 = location.getBlockZ() >> 4;
            Bukkit.getRegionScheduler().runDelayed((Plugin)this.plugin, world, n4, n5, scheduledTask -> {
                if (world.getNearbyEntities(location3, 0.5, 1.0, 0.5).stream().anyMatch(entity -> entity.getType() == entityType && entity.getLocation().distanceSquared(location3) < 0.25)) {
                    return;
                }
                world.spawn(location3, ArmorStand.class, armorStand -> {
                    EntityMetadataUtil.applyFullMeta(armorStand, jsonObject);
                    armorStand.setPersistent(true);
                });
            }, (long)n);
            return 1;
        }
        return 0;
    }

    private void withChunkLoaded(World world, int n, int n2, Runnable runnable, Consumer<Throwable> consumer) {
        try {
            if (world.isChunkLoaded(n, n2)) {
                Bukkit.getRegionScheduler().execute((Plugin)this.plugin, world, n, n2, runnable);
                return;
            }
            boolean bl = this.plugin.getConfig().getBoolean("rollback.loadChunks.generate", false);
            world.getChunkAtAsync(n, n2, bl).whenComplete((chunk, throwable) -> {
                if (throwable != null) {
                    consumer.accept((Throwable)throwable);
                    return;
                }
                try {
                    Bukkit.getRegionScheduler().execute((Plugin)this.plugin, world, n, n2, runnable);
                }
                catch (Throwable throwable2) {
                    consumer.accept(throwable2);
                }
            });
        }
        catch (Throwable throwable2) {
            consumer.accept(throwable2);
        }
    }

    private BlockData cachedBlockData(String string) {
        if (string == null) {
            throw new IllegalArgumentException("Cannot create BlockData from null string");
        }
        String string2 = RollbackEngine.sanitizeBlockDataString(string);
        BlockData blockData = this.bdCache.get(string2);
        if (blockData == null) {
            try {
                blockData = Bukkit.createBlockData((String)string2);
                BlockData blockData2 = this.bdCache.putIfAbsent(string2, blockData);
                if (blockData2 != null) {
                    blockData = blockData2;
                }
            }
            catch (IllegalArgumentException illegalArgumentException) {
                String string3 = string2.contains("[") ? string2.substring(0, string2.indexOf(91)) : string2;
                Material material = RollbackModel.ParsedArgs.matchMatCompat(string3, true);
                if (material != null) {
                    blockData = Bukkit.createBlockData((Material)material);
                    BlockData blockData3 = this.bdCache.putIfAbsent(string2, blockData);
                    if (blockData3 != null) {
                        blockData = blockData3;
                    }
                }
                this.plugin.getLogger().fine("[Rollback] BlockData parse failed for: " + string);
                throw illegalArgumentException;
            }
        }
        return blockData.clone();
    }

    public static String sanitizeBlockDataString(String string) {
        if (string == null) {
            return null;
        }
        int n = string.indexOf(123);
        if (n >= 0) {
            string = string.substring(0, n);
        }
        if ((string = string.trim().toLowerCase(Locale.ROOT)).startsWith("minecraft:") ? string.startsWith("minecraft:cave_air") || string.startsWith("minecraft:void_air") : string.equals("cave_air") || string.equals("void_air")) {
            return "minecraft:air";
        }
        return string;
    }

    @Nullable
    private String extractBlockDataString(ActionLog actionLog) {
        JsonObject jsonObject = this.metaOf(actionLog);
        if (jsonObject != null && jsonObject.has("blockData")) {
            return RollbackEngine.sanitizeBlockDataString(jsonObject.get("blockData").getAsString());
        }
        String string = actionLog.getMetadata();
        if (string != null && string.startsWith("minecraft:")) {
            return RollbackEngine.sanitizeBlockDataString(string);
        }
        return null;
    }

    private void journalChangeIfAny(int n, Location location, ActionLog actionLog, Material material, BlockData blockData, Block block) {
        if (n > 0) {
            this.databaseMariadb.logRollbackBlockChange(this.currentRollbackId, actionLog.getId(), location, material, blockData, block.getType(), block.getBlockData());
            this.journaledThisRun.incrementAndGet();
        }
    }

    private int applyBlockLogForRealBlocks(World world, ActionLog actionLog, boolean bl, Debug.Ctx ctx, ChunkCounters chunkCounters) {
        Location location = new Location(world, (double)actionLog.getX(), (double)actionLog.getY(), (double)actionLog.getZ());
        String string = RollbackEngine.act(actionLog);
        Material material = this.extractMaterialFromLog(actionLog);
        Block block = location.getBlock();
        String string2 = this.extractBlockDataString(actionLog);
        Material material2 = block.getType();
        BlockData blockData = block.getBlockData();
        switch (string) {
            case "BLOCK_PLACE": {
                JsonObject jsonObject;
                if (this.isRealBlock(actionLog) && material != null && material.isBlock()) {
                    boolean bl2;
                    boolean bl3 = bl2 = block.getType() == material;
                    if (bl2 && string2 != null) {
                        try {
                            bl2 = block.getBlockData().matches(this.cachedBlockData(string2));
                        }
                        catch (IllegalArgumentException illegalArgumentException) {
                            bl2 = false;
                        }
                    }
                    if (!bl2) {
                        this.maybeDecision(ctx, "apply place: target=" + String.valueOf(material) + " bd=" + string2, actionLog);
                        block.setType(material, false);
                        if (string2 != null) {
                            try {
                                block.setBlockData(this.cachedBlockData(string2), false);
                            }
                            catch (IllegalArgumentException illegalArgumentException) {
                                // empty catch block
                            }
                        }
                        chunkCounters.blocksApplied.incrementAndGet();
                        this.journalChangeIfAny(1, location, actionLog, material2, blockData, block);
                        return 1;
                    }
                    this.maybeDecision(ctx, "no-op place: live already matches", actionLog);
                }
                if (material == Material.SPAWNER && (jsonObject = this.metaOf(actionLog)) != null && jsonObject.has("spawnerType")) {
                    try {
                        BlockState blockState = block.getState(false);
                        if (blockState instanceof CreatureSpawner) {
                            CreatureSpawner creatureSpawner = (CreatureSpawner)blockState;
                            creatureSpawner.setSpawnedType(EntityType.valueOf((String)jsonObject.get("spawnerType").getAsString().toUpperCase(Locale.ROOT)));
                            creatureSpawner.update(true, false);
                        }
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                return 0;
            }
            case "BUCKET_EMPTY": 
            case "LIQUID_FLOW": {
                Waterlogged waterlogged;
                if (string2 != null) {
                    try {
                        BlockData blockData2 = this.cachedBlockData(string2);
                        block.setBlockData(blockData2, false);
                        chunkCounters.blocksApplied.incrementAndGet();
                        this.journalChangeIfAny(1, location, actionLog, material2, blockData, block);
                        return 1;
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                        // empty catch block
                    }
                }
                BlockData blockData3 = block.getBlockData();
                if (material == Material.WATER && blockData3 instanceof Waterlogged && (waterlogged = (Waterlogged)blockData3).isWaterlogged()) {
                    waterlogged.setWaterlogged(false);
                    block.setBlockData(blockData3, false);
                    chunkCounters.waterlogClears.incrementAndGet();
                    int n = this.drainFlowingCluster(location, Material.WATER);
                    if (n > 0) {
                        chunkCounters.waterDrains.addAndGet(n);
                    }
                    chunkCounters.blocksApplied.incrementAndGet();
                    this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                    return 1 + n;
                }
                if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    waterlogged = block.getType();
                    if (material == Material.WATER || material == Material.LAVA || material == waterlogged || material == null) {
                        block.setType(Material.AIR, false);
                        int n = this.drainFlowingCluster(location, (Material)waterlogged);
                        if (n > 0) {
                            chunkCounters.waterDrains.addAndGet(n);
                        }
                        chunkCounters.blocksApplied.addAndGet(1 + n);
                        this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                        return 1 + n;
                    }
                }
                return 0;
            }
            case "BLOCK_BREAK": 
            case "BLOCK_BURN": {
                Waterlogged waterlogged;
                Material material3;
                if (!this.isRealBlock(actionLog) || material == null || !material.isBlock()) {
                    return 0;
                }
                int n = 0;
                BlockData blockData4 = block.getBlockData();
                if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    material3 = block.getType();
                    block.setType(Material.AIR, false);
                    int n2 = this.drainFlowingCluster(location, material3);
                    if (n2 > 0) {
                        chunkCounters.waterDrains.addAndGet(n2);
                    }
                    n += 1 + n2;
                } else if (blockData4 instanceof Waterlogged && (waterlogged = (Waterlogged)blockData4).isWaterlogged()) {
                    waterlogged.setWaterlogged(false);
                    block.setBlockData(blockData4, false);
                    chunkCounters.waterlogClears.incrementAndGet();
                    int n3 = this.drainFlowingCluster(location, Material.WATER);
                    if (n3 > 0) {
                        chunkCounters.waterDrains.addAndGet(n3);
                    }
                    n += 1 + n3;
                }
                if (material == Material.SPAWNER && (waterlogged = this.metaOf(actionLog)) != null && waterlogged.has("spawnerType")) {
                    try {
                        material3 = block.getState(false);
                        if (material3 instanceof CreatureSpawner) {
                            CreatureSpawner creatureSpawner = (CreatureSpawner)material3;
                            creatureSpawner.setSpawnedType(EntityType.valueOf((String)waterlogged.get("spawnerType").getAsString().toUpperCase(Locale.ROOT)));
                            creatureSpawner.update(true, false);
                        }
                    }
                    catch (Throwable throwable) {
                        // empty catch block
                    }
                }
                if (!block.getType().isAir()) {
                    n += this.forceClearAt(world, actionLog.getX(), actionLog.getY(), actionLog.getZ(), null, ctx, chunkCounters);
                }
                if (n > 0) {
                    chunkCounters.blocksReversed.addAndGet(n);
                    this.journalChangeIfAny(n, location, actionLog, material2, blockData, block);
                }
                return n;
            }
        }
        return 0;
    }

    private int reverseBlockLogForRealBlocks(World world, ActionLog actionLog, boolean bl, Debug.Ctx ctx, ChunkCounters chunkCounters) {
        Location location = new Location(world, (double)actionLog.getX(), (double)actionLog.getY(), (double)actionLog.getZ());
        String string = RollbackEngine.act(actionLog);
        Material material = this.extractMaterialFromLog(actionLog);
        Block block = location.getBlock();
        String string2 = this.extractBlockDataString(actionLog);
        Material material2 = block.getType();
        BlockData blockData = block.getBlockData();
        if (this.isSyntheticRollback(actionLog)) {
            Block block2 = location.getBlock();
            String[] stringArray = this.readFromTo(actionLog, true);
            Material material3 = this.parseMat(stringArray[0]);
            BlockData blockData2 = this.parseBd(stringArray[1]);
            int n = 0;
            if (material3 == null || material3.isAir()) {
                if (!block2.getType().isAir()) {
                    block2.setType(Material.AIR, false);
                    ++n;
                }
            } else {
                if (block2.getType() != material3) {
                    block2.setType(material3, false);
                    ++n;
                }
                if (blockData2 != null) {
                    try {
                        block2.setBlockData(blockData2, false);
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                        // empty catch block
                    }
                }
            }
            if (n > 0) {
                chunkCounters.blocksReversed.addAndGet(n);
                this.databaseMariadb.logRollbackBlockChange(this.currentRollbackId, actionLog.getId(), location, material2, blockData, block2.getType(), block2.getBlockData());
                this.journaledThisRun.incrementAndGet();
            }
            return n;
        }
        switch (string) {
            case "BLOCK_PLACE": {
                if (!this.isRealBlock(actionLog)) {
                    return 0;
                }
                int n = this.forceClearAt(world, actionLog.getX(), actionLog.getY(), actionLog.getZ(), actionLog, ctx, chunkCounters);
                if (n > 0) {
                    chunkCounters.forceClears.addAndGet(n);
                    chunkCounters.blocksReversed.addAndGet(n);
                    this.journalChangeIfAny(n, location, actionLog, material2, blockData, block);
                }
                return n;
            }
            case "BUCKET_EMPTY": 
            case "LIQUID_FLOW": {
                BlockData blockData3;
                BlockData blockData4;
                String string3 = this.extractBlockDataString(actionLog);
                if (string3 != null) {
                    try {
                        Waterlogged waterlogged;
                        blockData4 = this.cachedBlockData(string3);
                        blockData3 = block.getBlockData();
                        if (blockData4 instanceof Waterlogged && (waterlogged = (Waterlogged)blockData4).isWaterlogged()) {
                            Waterlogged waterlogged2;
                            if (blockData3 instanceof Waterlogged && (waterlogged2 = (Waterlogged)blockData3).isWaterlogged()) {
                                waterlogged2.setWaterlogged(false);
                                block.setBlockData(blockData3, false);
                                chunkCounters.waterlogClears.incrementAndGet();
                                int n = this.drainFlowingCluster(location, Material.WATER);
                                if (n > 0) {
                                    chunkCounters.waterDrains.addAndGet(n);
                                }
                                chunkCounters.blocksReversed.addAndGet(1 + n);
                                this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                                return 1 + n;
                            }
                            if (blockData3.getMaterial() == Material.WATER || blockData3.getMaterial() == Material.LAVA) {
                                block.setType(Material.AIR, false);
                                int n = this.drainFlowingCluster(location, blockData3.getMaterial());
                                if (n > 0) {
                                    chunkCounters.waterDrains.addAndGet(n);
                                }
                                chunkCounters.blocksReversed.addAndGet(1 + n);
                                this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                                return 1 + n;
                            }
                            return 0;
                        }
                        if ((blockData4.getMaterial() == Material.WATER || blockData4.getMaterial() == Material.LAVA) && block.getType() == blockData4.getMaterial()) {
                            String string4;
                            boolean bl2 = false;
                            JsonObject jsonObject = this.metaOf(actionLog);
                            String string5 = string4 = jsonObject != null && jsonObject.has("beforeBlockData") ? RollbackEngine.sanitizeBlockDataString(jsonObject.get("beforeBlockData").getAsString()) : null;
                            if (string4 != null) {
                                try {
                                    BlockData blockData5 = this.cachedBlockData(string4);
                                    if (blockData5.getMaterial() == blockData4.getMaterial()) {
                                        Waterlogged waterlogged3;
                                        if (blockData5 instanceof Levelled) {
                                            Levelled levelled = (Levelled)blockData5;
                                            bl2 = levelled.getLevel() == 0;
                                        } else if (blockData5 instanceof Waterlogged && (waterlogged3 = (Waterlogged)blockData5).isWaterlogged()) {
                                            bl2 = true;
                                        }
                                    }
                                }
                                catch (IllegalArgumentException illegalArgumentException) {
                                    // empty catch block
                                }
                            }
                            if (!bl2) {
                                block.setType(Material.AIR, false);
                                int n = this.drainFlowingCluster(location, blockData4.getMaterial());
                                if (n > 0) {
                                    chunkCounters.waterDrains.addAndGet(n);
                                }
                                chunkCounters.blocksReversed.addAndGet(1 + n);
                                this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                                return 1 + n;
                            }
                        }
                        if (block.getBlockData().matches(blockData4)) {
                            block.setType(Material.AIR, false);
                            chunkCounters.blocksReversed.incrementAndGet();
                            this.journalChangeIfAny(1, location, actionLog, material2, blockData, block);
                            return 1;
                        }
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                        // empty catch block
                    }
                }
                if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    blockData4 = block.getType();
                    block.setType(Material.AIR, false);
                    int n = this.drainFlowingCluster(location, (Material)blockData4);
                    if (n > 0) {
                        chunkCounters.waterDrains.addAndGet(n);
                    }
                    chunkCounters.blocksReversed.addAndGet(1 + n);
                    this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                    return 1 + n;
                }
                blockData4 = block.getBlockData();
                if (blockData4 instanceof Waterlogged && (blockData3 = (Waterlogged)blockData4).isWaterlogged()) {
                    blockData3.setWaterlogged(false);
                    block.setBlockData(blockData4, false);
                    chunkCounters.waterlogClears.incrementAndGet();
                    int n = this.drainFlowingCluster(location, Material.WATER);
                    if (n > 0) {
                        chunkCounters.waterDrains.addAndGet(n);
                    }
                    chunkCounters.blocksReversed.addAndGet(1 + n);
                    this.journalChangeIfAny(1 + n, location, actionLog, material2, blockData, block);
                    return 1 + n;
                }
                return 0;
            }
            case "BLOCK_BREAK": 
            case "BLOCK_BURN": {
                Waterlogged waterlogged;
                if (!this.isRealBlock(actionLog) || material == null || !material.isBlock()) {
                    return 0;
                }
                BlockData blockData6 = block.getBlockData();
                int n = 0;
                if (block.getType() == Material.WATER || block.getType() == Material.LAVA) {
                    Material material4 = block.getType();
                    block.setType(Material.AIR, false);
                    int n2 = this.drainFlowingCluster(location, material4);
                    if (n2 > 0) {
                        chunkCounters.waterDrains.addAndGet(n2);
                    }
                    n += n2;
                } else if (blockData6 instanceof Waterlogged && (waterlogged = (Waterlogged)blockData6).isWaterlogged()) {
                    waterlogged.setWaterlogged(false);
                    block.setBlockData(blockData6, false);
                    chunkCounters.waterlogClears.incrementAndGet();
                    int n3 = this.drainFlowingCluster(location, Material.WATER);
                    if (n3 > 0) {
                        chunkCounters.waterDrains.addAndGet(n3);
                    }
                    n += n3;
                }
                if (block.getType() != material) {
                    block.setType(material, false);
                    ++n;
                }
                if (string2 != null) {
                    try {
                        block.setBlockData(this.cachedBlockData(string2), false);
                    }
                    catch (IllegalArgumentException illegalArgumentException) {
                        // empty catch block
                    }
                }
                if (n > 0) {
                    chunkCounters.blocksReversed.addAndGet(n);
                    this.journalChangeIfAny(n, location, actionLog, material2, blockData, block);
                }
                return n;
            }
        }
        return 0;
    }

    private Runnable createItemFrameRollbackAction(World world, ActionLog actionLog, Location location, String string, AtomicInteger atomicInteger, boolean bl, int n, Debug.Ctx ctx) {
        boolean bl2;
        Object object;
        ItemStack itemStack;
        EntityType entityType;
        BlockFace blockFace;
        JsonObject jsonObject;
        block28: {
            ItemStack itemStack2;
            jsonObject = this.metaOf(actionLog);
            blockFace = jsonObject != null && jsonObject.has("facing") ? EntityMetadataUtil.face(jsonObject, "facing", BlockFace.SOUTH) : null;
            EntityType entityType2 = null;
            if (jsonObject != null && jsonObject.has("entity")) {
                try {
                    entityType2 = EntityType.valueOf((String)jsonObject.get("entity").getAsString().toUpperCase());
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            entityType = entityType2;
            String string2 = jsonObject != null && jsonObject.has("item") ? jsonObject.get("item").toString() : null;
            itemStack = itemStack2 = ItemMetadataUtil.jsonToItem(string2);
            try {
                Object object2;
                Object object3;
                if ((itemStack == null || itemStack.getType().isAir()) && jsonObject != null) {
                    Object object4;
                    object = null;
                    if (jsonObject.has("item")) {
                        object4 = jsonObject.get("item");
                        if (object4.isJsonObject()) {
                            object = object4.getAsJsonObject();
                        } else if (object4.isJsonPrimitive() && object4.getAsJsonPrimitive().isString()) {
                            try {
                                object3 = JsonParser.parseString((String)object4.getAsString());
                                if (object3.isJsonObject()) {
                                    object = object3.getAsJsonObject();
                                }
                            }
                            catch (Throwable throwable) {
                                // empty catch block
                            }
                        }
                    }
                    object4 = null;
                    object3 = null;
                    if (object != null) {
                        if (object.has("material")) {
                            object4 = object.get("material").getAsString();
                        } else if (object.has("id")) {
                            object4 = object.get("id").getAsString();
                        } else if (object.has("type")) {
                            object4 = object.get("type").getAsString();
                        }
                        if (object.has("amount")) {
                            object3 = Math.max(1, object.get("amount").getAsInt());
                        }
                    }
                    if (object4 == null && jsonObject.has("material")) {
                        object4 = jsonObject.get("material").getAsString();
                    }
                    if (object4 != null && object4.startsWith("minecraft:")) {
                        object4 = object4.substring("minecraft:".length());
                    }
                    if (object4 != null && (object2 = RollbackModel.ParsedArgs.matchMatCompat((String)object4, true)) != null && object2 != Material.AIR) {
                        itemStack = new ItemStack((Material)object2, object3 != null ? (Integer)object3 : 1);
                    }
                }
                if (itemStack != null && !itemStack.getType().isAir() || (object = this.extractMaterialFromLog(actionLog)) == null || object == Material.AIR) break block28;
                int n2 = 1;
                object3 = actionLog.getDetails() == null ? "" : actionLog.getDetails().toLowerCase(Locale.ROOT);
                object2 = Pattern.compile("\\bx(\\d+)\\b").matcher((CharSequence)object3);
                if (((Matcher)object2).find()) {
                    try {
                        n2 = Math.max(1, Integer.parseInt(((Matcher)object2).group(1)));
                    }
                    catch (NumberFormatException numberFormatException) {
                        // empty catch block
                    }
                }
                itemStack = new ItemStack(object, n2);
            }
            catch (Throwable throwable) {
                this.plugin.getLogger().log(Level.FINE, "[Rollback-Frames] item resolve failure for LogID " + actionLog.getId(), throwable);
            }
        }
        object = string == null ? "" : string.trim().toUpperCase(Locale.ROOT);
        boolean bl3 = "CONTAINER_ADD".equals(object);
        boolean bl4 = "CONTAINER_TAKE".equals(object);
        boolean bl5 = bl2 = bl ? bl4 : bl3;
        if ((itemStack == null || itemStack.getType().isAir()) && bl2) {
            this.plugin.getLogger().warning(String.format("[Rollback-Frames] LogID %d at %s wants an item but none could be reconstructed from metadata: %s", actionLog.getId(), location.toVector(), jsonObject));
        }
        ItemStack itemStack3 = itemStack;
        if (this.plugin.getLogger().isLoggable(Level.FINE)) {
            this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback-Frames] Creating item action for LogID: %s at %s. Action: %s, actUC: %s, Reversing: %b, Item: %s", actionLog.getId(), location.toVector(), string, object, bl, itemStack3 != null ? itemStack3.getType() : "NULL"));
        }
        return () -> {
            int n2 = location.getBlockX() >> 4;
            int n3 = location.getBlockZ() >> 4;
            int n4 = 40;
            int n5 = 2;
            int[] nArray = new int[]{0};
            Bukkit.getRegionScheduler().runAtFixedRate((Plugin)this.plugin, world, n2, n3, scheduledTask -> {
                if (n != this.frameTaskEpoch.get()) {
                    scheduledTask.cancel();
                    return;
                }
                nArray[0] = nArray[0] + 1;
                if (nArray[0] >= 40) {
                    this.plugin.getLogger().warning(String.format("[ProtectCore] [Rollback-Frames] FAILED to find item frame at %s after %d tries for LogID %s. Aborting item restoration.", location.toVector(), 40, actionLog.getId()));
                    scheduledTask.cancel();
                    return;
                }
                ItemFrame itemFrame = this.getExactFrame(world, location, blockFace, entityType);
                if (itemFrame == null) {
                    if (this.plugin.getLogger().isLoggable(Level.FINE)) {
                        this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback-Frames] [Try %d/%d] Frame not found at %s for LogID %s. Retrying...", nArray[0], 40, location.toVector(), actionLog.getId()));
                    }
                    return;
                }
                if (this.plugin.getLogger().isLoggable(Level.FINE)) {
                    this.plugin.getLogger().fine(String.format("[ProtectCore] [Rollback-Frames] [Try %d/%d] Frame found at %s for LogID %s! Current item: %s.", nArray[0], 40, location.toVector(), actionLog.getId(), itemFrame.getItem().getType()));
                }
                if (bl2) {
                    boolean bl2;
                    ItemStack itemStack2 = itemFrame.getItem();
                    boolean bl3 = bl2 = itemStack2 == null || itemStack2.getType().isAir();
                    if (!bl2 && itemStack3 != null) {
                        boolean bl4 = bl2 = !itemStack2.isSimilar(itemStack3);
                    }
                    if (!bl2) {
                        Debug.log(this.plugin, "rollback.frames", String.valueOf(ctx) + " frame item already correct @%s id=%d", location.toVector(), actionLog.getId());
                    }
                    if (bl2 && itemStack3 != null && !itemStack3.getType().isAir()) {
                        Debug.log(this.plugin, "rollback.frames", String.valueOf(ctx) + " frame item replaced %s->%s @%s id=%d", itemStack2.getType(), itemStack3.getType(), location.toVector(), actionLog.getId());
                        itemFrame.setItem(itemStack3, false);
                        atomicInteger.incrementAndGet();
                        this.databaseMariadb.markRolledBackBatchAsync(Collections.singletonList(actionLog.getId()), SYSTEM_ACTOR);
                        try {
                            String string = RollbackEngine.toItemJsonSafe(itemStack3);
                            JsonObject jsonObject2 = new JsonObject();
                            jsonObject2.addProperty("origin", "rollback");
                            jsonObject2.addProperty("reversing", Boolean.valueOf(true));
                            jsonObject2.addProperty("timestamp", (Number)System.currentTimeMillis());
                            if (string != null) {
                                jsonObject2.add("item", JsonParser.parseString((String)string));
                            }
                            jsonObject2.addProperty("entity", itemFrame.getType().name());
                            jsonObject2.addProperty("facing", itemFrame.getFacing().name());
                            jsonObject2.addProperty("rotation", itemFrame.getRotation().name());
                            jsonObject2.addProperty("invisible", Boolean.valueOf(itemFrame.isInvisible()));
                            jsonObject2.addProperty("fixed", Boolean.valueOf(itemFrame.isFixed()));
                            this.databaseMariadb.logActionAsync(SYSTEM_ACTOR, SYSTEM_NAME, "CONTAINER_ADD", "set frame item (by rollback)", jsonObject2.toString(), location);
                        }
                        catch (Exception exception) {
                            this.plugin.getLogger().log(Level.FINE, "[Rollback] journaling ADD failed", exception);
                        }
                    }
                } else {
                    ItemStack itemStack3 = itemFrame.getItem();
                    if (itemStack3 != null && !itemStack3.getType().isAir()) {
                        itemFrame.setItem(null, false);
                        atomicInteger.incrementAndGet();
                        this.databaseMariadb.markRolledBackBatchAsync(Collections.singletonList(actionLog.getId()), SYSTEM_ACTOR);
                        try {
                            String string = RollbackEngine.toItemJsonSafe(itemStack3);
                            JsonObject jsonObject3 = new JsonObject();
                            jsonObject3.addProperty("origin", "rollback");
                            jsonObject3.addProperty("reversing", Boolean.valueOf(true));
                            jsonObject3.addProperty("timestamp", (Number)System.currentTimeMillis());
                            if (string != null) {
                                jsonObject3.add("item", JsonParser.parseString((String)string));
                            }
                            jsonObject3.addProperty("entity", itemFrame.getType().name());
                            jsonObject3.addProperty("facing", itemFrame.getFacing().name());
                            jsonObject3.addProperty("rotation", itemFrame.getRotation().name());
                            jsonObject3.addProperty("invisible", Boolean.valueOf(itemFrame.isInvisible()));
                            jsonObject3.addProperty("fixed", Boolean.valueOf(itemFrame.isFixed()));
                            this.databaseMariadb.logActionAsync(SYSTEM_ACTOR, SYSTEM_NAME, "CONTAINER_TAKE", "cleared frame item (by rollback)", jsonObject3.toString(), location);
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                    }
                }
                if (bl2 && (itemStack3 == null || itemStack3.getType().isAir())) {
                    this.plugin.getLogger().warning("[ProtectCore][Rollback-Frames] Could not reconstruct item for LogID " + actionLog.getId() + " @ " + String.valueOf(location.toVector()) + " meta=" + String.valueOf(jsonObject));
                }
                scheduledTask.cancel();
            }, 1L, 2L);
        };
    }

    @Nullable
    private Runnable createContainerSnapshotRestoreAction(World world, ActionLog actionLog, Location location, AtomicInteger atomicInteger, Debug.Ctx ctx) {
        Object object;
        byte[] byArray;
        JsonObject jsonObject;
        byte[] byArray2 = actionLog.getSnapshotBytes();
        if (Debug.on(this.plugin, "rollback.containers")) {
            Debug.log(this.plugin, "rollback.containers", String.valueOf(ctx) + " restore snapshot logId=" + actionLog.getId() + " @ " + String.valueOf(location) + " invSize=" + (byArray2 != null ? "bin" : "b64") + " bytes=" + (byArray2 != null ? byArray2.length : -1), new Object[0]);
        }
        if ((jsonObject = this.metaOf(actionLog)) == null) {
            this.plugin.getLogger().warning("[ProtectCore RB] Missing/malformed metadata for SNAPSHOT @ " + String.valueOf(location) + ", LogID: " + actionLog.getId());
            return null;
        }
        if (byArray2 != null) {
            byArray = byArray2;
        } else {
            Object object2 = jsonObject.has("snapshot_base64") ? jsonObject.get("snapshot_base64").getAsString() : (object = jsonObject.has("snapshot") ? jsonObject.get("snapshot").getAsString() : null);
            if (object == null) {
                this.plugin.getLogger().warning("[RB] Missing snapshot payload for LogID " + actionLog.getId());
                return null;
            }
            try {
                byArray = Base64.getDecoder().decode((String)object);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                this.plugin.getLogger().warning("[RB] Bad Base64 for LogID " + actionLog.getId());
                return null;
            }
        }
        try {
            object = BinaryInventoryUtil.deserializeCompressed(byArray);
        }
        catch (Exception exception) {
            this.plugin.getLogger().log(Level.SEVERE, "[ProtectCore RB] Bad snapshot payload at " + String.valueOf(location) + ", LogID: " + actionLog.getId(), exception);
            return null;
        }
        return () -> this.lambda$createContainerSnapshotRestoreAction$27(location, jsonObject, actionLog, world, (ItemStack[])object, ctx, atomicInteger);
    }

    @Nullable
    private Material extractMaterialFromLog(ActionLog actionLog) {
        Material material;
        String string = actionLog.getMetadata();
        String string2 = actionLog.getDetails();
        if (string != null && string.startsWith("{")) {
            try {
                String string3;
                Material material2;
                material = this.metaOf(actionLog);
                if (material == null) {
                    return null;
                }
                if (material.has("material") && (material2 = this.materialByAnyName(string3 = material.get("material").getAsString())) != null) {
                    return material2;
                }
                if (material.has("materialPretty") && (material2 = this.materialByAnyName(string3 = material.get("materialPretty").getAsString())) != null) {
                    return material2;
                }
                if (material.has("entity")) {
                    return switch (material.get("entity").getAsString().toUpperCase(Locale.ROOT)) {
                        case "ITEM_FRAME" -> Material.ITEM_FRAME;
                        case "GLOW_ITEM_FRAME" -> Material.GLOW_ITEM_FRAME;
                        case "PAINTING" -> Material.PAINTING;
                        case "ARMOR_STAND" -> Material.ARMOR_STAND;
                        default -> null;
                    };
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
        if (string != null && string.startsWith("minecraft:") && (material = this.materialByAnyName(string.split("\\[", 2)[0])) != null) {
            return material;
        }
        if (string2 != null && !string2.isBlank()) {
            for (Material material3 : string2.split("\\s+")) {
                Material material4 = this.materialByAnyName(material3.trim());
                if (material4 == null) continue;
                return material4;
            }
        }
        return null;
    }

    private Material materialByAnyName(String string2) {
        if (string2 == null) {
            return null;
        }
        String string3 = string2.toLowerCase(Locale.ROOT);
        return this.matCache.computeIfAbsent(string3, string -> {
            String string2 = string.startsWith("minecraft:") ? string.substring(10) : string;
            Material material = RollbackModel.ParsedArgs.matchMatCompat(string2, true);
            if (material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                return Material.AIR;
            }
            if (material != null) {
                return material;
            }
            material = RollbackModel.ParsedArgs.matchMatCompat(string, true);
            if (material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                return Material.AIR;
            }
            return material;
        });
    }

    @Nullable
    private JsonObject metaOf(ActionLog actionLog) {
        if (actionLog.getMetadata() == null || !actionLog.getMetadata().startsWith("{")) {
            return null;
        }
        return this.metaCache.computeIfAbsent(actionLog.getId(), n -> {
            try {
                return JsonParser.parseString((String)actionLog.getMetadata()).getAsJsonObject();
            }
            catch (Exception exception) {
                return null;
            }
        });
    }

    private boolean isSyntheticRollback(ActionLog actionLog) {
        JsonObject jsonObject = this.metaOf(actionLog);
        return jsonObject != null && jsonObject.has("origin") && "rollback".equalsIgnoreCase(jsonObject.get("origin").getAsString());
    }

    private String[] readFromTo(ActionLog actionLog, boolean bl) {
        JsonObject jsonObject = this.metaOf(actionLog);
        if (jsonObject == null) {
            return new String[]{null, null};
        }
        String string = bl ? "from_block" : "to_block";
        String string2 = bl ? "from_data" : "to_data";
        String string3 = jsonObject.has(string) ? jsonObject.get(string).getAsString() : null;
        String string4 = jsonObject.has(string2) ? RollbackEngine.sanitizeBlockDataString(jsonObject.get(string2).getAsString()) : null;
        return new String[]{string3, string4};
    }

    private Material parseMat(@Nullable String string) {
        return string == null ? null : RollbackModel.ParsedArgs.matchMatCompat(string, true);
    }

    private BlockData parseBd(@Nullable String string) {
        if (string == null) {
            return null;
        }
        try {
            return this.cachedBlockData(string);
        }
        catch (Exception exception) {
            return null;
        }
    }

    private static String safeLower(String string) {
        return string == null ? "" : string.toLowerCase(Locale.ROOT);
    }

    private static boolean isNonSourceFluid(Block block, Material material) {
        if (block.getType() != material) {
            return false;
        }
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Levelled) {
            Levelled levelled = (Levelled)blockData;
            return levelled.getLevel() != 0;
        }
        return true;
    }

    private int drainFlowingCluster(Location location, Material material) {
        Block block = location.getBlock();
        HashSet<Block> hashSet = new HashSet<Block>();
        ArrayDeque<Block> arrayDeque = new ArrayDeque<Block>();
        if (RollbackEngine.isNonSourceFluid(block, material)) {
            hashSet.add(block);
            arrayDeque.add(block);
        } else {
            for (int[] object : OFFS) {
                Block block2 = block.getRelative(object[0], object[1], object[2]);
                if (!RollbackEngine.isNonSourceFluid(block2, material) || !hashSet.add(block2)) continue;
                arrayDeque.add(block2);
            }
        }
        int n = 0;
        int n2 = 0;
        while (!arrayDeque.isEmpty() && ++n2 <= 5000) {
            Block block3 = (Block)arrayDeque.poll();
            if (!RollbackEngine.isNonSourceFluid(block3, material)) continue;
            block3.setType(Material.AIR, false);
            ++n;
            for (int[] nArray : OFFS) {
                Block block4 = block3.getRelative(nArray[0], nArray[1], nArray[2]);
                if (hashSet.contains(block4) || !RollbackEngine.isNonSourceFluid(block4, material)) continue;
                hashSet.add(block4);
                arrayDeque.add(block4);
            }
        }
        return n;
    }

    private DecorKey decorKeyOf(ActionLog actionLog) {
        JsonObject jsonObject = this.metaOf(actionLog);
        return new DecorKey(actionLog.getX(), actionLog.getY(), actionLog.getZ(), RollbackEngine.faceOf(jsonObject), RollbackEngine.kindOf(jsonObject));
    }

    private static byte kindOf(@Nullable JsonObject jsonObject) {
        if (jsonObject == null || !jsonObject.has("entity")) {
            return 0;
        }
        String string = jsonObject.get("entity").getAsString().toUpperCase(Locale.ROOT);
        if (string.equals("BOAT") || string.endsWith("_BOAT") || string.endsWith("_RAFT")) {
            return 5;
        }
        return (byte)(switch (string) {
            case "ITEM_FRAME" -> 1;
            case "GLOW_ITEM_FRAME" -> 2;
            case "PAINTING" -> 3;
            case "ARMOR_STAND" -> 4;
            default -> 0;
        });
    }

    private static byte faceOf(@Nullable JsonObject jsonObject) {
        return (byte)EntityMetadataUtil.face(jsonObject, "facing", BlockFace.SOUTH).ordinal();
    }

    private boolean matchesLoggedState(Block block, ActionLog actionLog) {
        String string = RollbackEngine.act(actionLog);
        if ("BLOCK_BREAK".equals(string) || "BLOCK_BURN".equals(string)) {
            return block.getType().isAir();
        }
        Material material = this.extractMaterialFromLog(actionLog);
        String string2 = this.extractBlockDataString(actionLog);
        if (material == null) {
            return false;
        }
        if (block.getType() != material) {
            return false;
        }
        if (string2 != null) {
            try {
                return block.getBlockData().matches(this.cachedBlockData(string2));
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        return true;
    }

    private static String toItemJsonSafe(ItemStack itemStack) {
        String string;
        if (itemStack == null || itemStack.getType().isAir()) {
            return null;
        }
        try {
            string = ItemMetadataUtil.extractItemMetadata(itemStack);
            if (string != null && !string.isBlank()) {
                return string;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        string = new JsonObject();
        string.addProperty("material", itemStack.getType().name());
        string.addProperty("amount", (Number)itemStack.getAmount());
        return string.toString();
    }

    private static long posKey(int n, int n2, int n3) {
        return ((long)n & 0x3FFFFFFL) << 38 | ((long)n3 & 0x3FFFFFFL) << 12 | (long)n2 & 0xFFFL;
    }

    private static long posKey(ActionLog actionLog) {
        return RollbackEngine.posKey(actionLog.getX(), actionLog.getY(), actionLog.getZ());
    }

    private static long tsMillis(ActionLog actionLog) {
        Timestamp timestamp = actionLog.getEventTimestamp();
        if (timestamp != null) {
            return timestamp.getTime();
        }
        Timestamp timestamp2 = actionLog.getTimestamp();
        return timestamp2 != null ? timestamp2.getTime() : 0L;
    }

    private static int eventPriority(ActionLog actionLog) {
        return switch (RollbackEngine.act(actionLog)) {
            case "BLOCK_PLACE" -> 3;
            case "BUCKET_EMPTY", "LIQUID_FLOW" -> 2;
            case "BLOCK_BREAK", "BLOCK_BURN" -> 1;
            default -> 0;
        };
    }

    private static String act(ActionLog actionLog) {
        String string = actionLog.getAction();
        return string == null ? "" : string.toUpperCase(Locale.ROOT);
    }

    private /* synthetic */ void lambda$createContainerSnapshotRestoreAction$27(Location location, JsonObject jsonObject, ActionLog actionLog, World world, ItemStack[] itemStackArray, Debug.Ctx ctx, AtomicInteger atomicInteger) {
        InventoryHolder inventoryHolder;
        org.bukkit.block.Chest chest;
        Block block;
        InventoryHolder inventoryHolder2;
        block58: {
            InventoryHolder inventoryHolder3;
            Chest chest2;
            BlockData blockData;
            Block block2;
            String string;
            InventoryHolder inventoryHolder4;
            Block block3 = location.getBlock();
            BlockState blockState = block3.getState(false);
            inventoryHolder2 = blockState instanceof InventoryHolder ? (inventoryHolder4 = (InventoryHolder)blockState) : null;
            inventoryHolder4 = null;
            if (jsonObject.has("blockMaterial")) {
                inventoryHolder4 = this.materialByAnyName(jsonObject.get("blockMaterial").getAsString());
            }
            if (inventoryHolder4 == null) {
                inventoryHolder4 = this.extractMaterialFromLog(actionLog);
            }
            if (inventoryHolder4 == null) {
                String string2 = string = jsonObject.has("containerStructure") ? jsonObject.get("containerStructure").getAsString() : "";
                inventoryHolder4 = "SHULKER_BOX".equalsIgnoreCase(string) ? Material.SHULKER_BOX : ("BARREL".equalsIgnoreCase(string) ? Material.BARREL : Material.CHEST);
            }
            string = null;
            if (jsonObject.has("blockData")) {
                try {
                    string = this.cachedBlockData(jsonObject.get("blockData").getAsString());
                }
                catch (IllegalArgumentException illegalArgumentException) {
                    // empty catch block
                }
            }
            String string3 = jsonObject.has("containerStructure") ? jsonObject.get("containerStructure").getAsString() : "SINGLE";
            boolean bl = "DOUBLE".equalsIgnoreCase(string3);
            if (inventoryHolder4 == Material.CHEST) {
                block = location.getBlock();
                chest = null;
                inventoryHolder = null;
                if (jsonObject.has("facing")) {
                    chest = BlockFace.valueOf((String)jsonObject.get("facing").getAsString().toUpperCase(Locale.ROOT));
                }
                if (jsonObject.has("chestType")) {
                    inventoryHolder = Chest.Type.valueOf((String)jsonObject.get("chestType").getAsString().toUpperCase(Locale.ROOT));
                }
                block2 = null;
                if (bl) {
                    if (jsonObject.has("partnerX") && jsonObject.has("partnerY") && jsonObject.has("partnerZ")) {
                        block2 = world.getBlockAt(jsonObject.get("partnerX").getAsInt(), jsonObject.get("partnerY").getAsInt(), jsonObject.get("partnerZ").getAsInt());
                    } else if (chest != null && inventoryHolder != null) {
                        blockData = switch (chest) {
                            case NORTH -> {
                                if (inventoryHolder == Chest.Type.LEFT) {
                                    yield BlockFace.WEST;
                                }
                                yield BlockFace.EAST;
                            }
                            case SOUTH -> {
                                if (inventoryHolder == Chest.Type.LEFT) {
                                    yield BlockFace.EAST;
                                }
                                yield BlockFace.WEST;
                            }
                            case WEST -> {
                                if (inventoryHolder == Chest.Type.LEFT) {
                                    yield BlockFace.SOUTH;
                                }
                                yield BlockFace.NORTH;
                            }
                            case EAST -> {
                                if (inventoryHolder == Chest.Type.LEFT) {
                                    yield BlockFace.NORTH;
                                }
                                yield BlockFace.SOUTH;
                            }
                            default -> BlockFace.EAST;
                        };
                        block2 = block.getRelative((BlockFace)blockData);
                    }
                }
                try {
                    if (bl && block2 != null) {
                        Chest chest3;
                        block.setType((Material)inventoryHolder4, false);
                        block2.setType((Material)inventoryHolder4, false);
                        if (string instanceof Chest) {
                            chest2 = (Chest)string;
                            chest3 = (Chest)chest2.clone();
                        } else {
                            chest3 = (Chest)Bukkit.createBlockData((Material)Material.CHEST);
                        }
                        blockData = chest3;
                        chest2 = (Chest)blockData.clone();
                        if (chest != null) {
                            blockData.setFacing((BlockFace)chest);
                            chest2.setFacing((BlockFace)chest);
                        }
                        if (inventoryHolder != null) {
                            blockData.setType((Chest.Type)inventoryHolder);
                            chest2.setType(inventoryHolder == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT);
                        }
                        block.setBlockData(blockData, true);
                        block2.setBlockData((BlockData)chest2, true);
                        blockState = block.getState(true);
                        inventoryHolder2 = blockState instanceof InventoryHolder ? (inventoryHolder3 = (InventoryHolder)blockState) : null;
                    } else if (!bl) {
                        block.setType((Material)inventoryHolder4, false);
                        BlockData blockData2 = blockData = string != null ? string.clone() : Bukkit.createBlockData((Material)Material.CHEST);
                        if (blockData instanceof Chest) {
                            chest2 = (Chest)blockData;
                            chest2.setType(Chest.Type.SINGLE);
                        }
                        block.setBlockData(blockData, true);
                        blockState = block.getState(true);
                        inventoryHolder2 = blockState instanceof InventoryHolder ? (chest2 = (InventoryHolder)blockState) : null;
                    }
                }
                catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.WARNING, "[RB] Failed enforcing chest shape for snapshot at " + String.valueOf(location) + " (LogID " + actionLog.getId() + ")", throwable);
                }
            }
            if (inventoryHolder2 == null) {
                try {
                    if ("DOUBLE".equalsIgnoreCase(string3) && jsonObject.has("partnerX") && jsonObject.has("partnerY") && jsonObject.has("partnerZ")) {
                        block = new Location(world, (double)jsonObject.get("partnerX").getAsInt(), (double)jsonObject.get("partnerY").getAsInt(), (double)jsonObject.get("partnerZ").getAsInt());
                        chest = block.getBlock();
                        block3.setType((Material)inventoryHolder4, false);
                        chest.setType((Material)inventoryHolder4, false);
                        if (string != null && jsonObject.has("chestType")) {
                            try {
                                inventoryHolder = jsonObject.get("chestType").getAsString().toUpperCase(Locale.ROOT);
                                block2 = Chest.Type.valueOf((String)inventoryHolder);
                                blockData = block2 == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT;
                                chest2 = (Chest)string.clone();
                                inventoryHolder3 = (Chest)string.clone();
                                chest2.setType((Chest.Type)block2);
                                inventoryHolder3.setType((Chest.Type)blockData);
                                block3.setBlockData((BlockData)chest2, true);
                                chest.setBlockData((BlockData)inventoryHolder3, true);
                            }
                            catch (Exception exception) {}
                        } else if (jsonObject.has("facing") && jsonObject.has("chestType")) {
                            try {
                                inventoryHolder = BlockFace.valueOf((String)jsonObject.get("facing").getAsString().toUpperCase(Locale.ROOT));
                                block2 = Chest.Type.valueOf((String)jsonObject.get("chestType").getAsString().toUpperCase(Locale.ROOT));
                                blockData = (Chest)block3.getBlockData();
                                chest2 = (Chest)chest.getBlockData();
                                blockData.setFacing((BlockFace)inventoryHolder);
                                blockData.setType((Chest.Type)block2);
                                chest2.setFacing((BlockFace)inventoryHolder);
                                chest2.setType(block2 == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT);
                                block3.setBlockData(blockData, true);
                                chest.setBlockData((BlockData)chest2, true);
                            }
                            catch (Exception exception) {
                                // empty catch block
                            }
                        }
                        inventoryHolder2 = (blockState = block3.getState(true)) instanceof InventoryHolder ? (inventoryHolder = (InventoryHolder)blockState) : null;
                        break block58;
                    }
                    block3.setType((Material)inventoryHolder4, true);
                    if (string != null) {
                        block3.setBlockData((BlockData)string, true);
                    } else {
                        chest = block3.getBlockData();
                        if (chest instanceof Directional) {
                            block = (Directional)chest;
                            if (jsonObject.has("facing")) {
                                try {
                                    block.setFacing(BlockFace.valueOf((String)jsonObject.get("facing").getAsString().toUpperCase(Locale.ROOT)));
                                    block3.setBlockData((BlockData)block, true);
                                }
                                catch (IllegalArgumentException illegalArgumentException) {
                                    // empty catch block
                                }
                            }
                        }
                    }
                    blockState = block3.getState(true);
                    inventoryHolder2 = blockState instanceof InventoryHolder ? (block = (InventoryHolder)blockState) : null;
                }
                catch (Throwable throwable) {
                    this.plugin.getLogger().log(Level.WARNING, "[RB] Failed to materialize container block(s) for snapshot at " + String.valueOf(location) + " (LogID " + actionLog.getId() + ")", throwable);
                }
            }
        }
        if (inventoryHolder2 == null) {
            this.plugin.getLogger().warning("[RB] Could not create/find container at " + String.valueOf(location) + " to apply snapshot (LogID " + actionLog.getId() + ")");
            return;
        }
        if (inventoryHolder2 instanceof org.bukkit.block.Chest) {
            chest = (org.bukkit.block.Chest)inventoryHolder2;
            block = chest.getInventory();
        } else {
            block = inventoryHolder2.getInventory();
        }
        int n = this.countValidItems(block.getContents());
        if (block.getSize() != itemStackArray.length) {
            Debug.warn(this.plugin, "rollback.containers", String.valueOf(ctx) + " snapshot size mismatch snap=" + itemStackArray.length + " inv=" + block.getSize() + " @" + String.valueOf(location), new Object[0]);
        }
        if (block.getSize() == itemStackArray.length) {
            block.clear();
            block.setContents(itemStackArray);
            atomicInteger.addAndGet(this.countValidItems(itemStackArray) - n);
        } else if (itemStackArray.length == 54 && block.getSize() == 27) {
            inventoryHolder = Arrays.copyOfRange(itemStackArray, 0, 27);
            block.clear();
            block.setContents((ItemStack[])inventoryHolder);
            atomicInteger.addAndGet(this.countValidItems((ItemStack[])inventoryHolder) - n);
        } else {
            inventoryHolder = Arrays.copyOf(itemStackArray, Math.min(block.getSize(), itemStackArray.length));
            block.clear();
            block.setContents((ItemStack[])inventoryHolder);
            atomicInteger.addAndGet(this.countValidItems((ItemStack[])inventoryHolder) - n);
        }
    }

    private boolean lambda$applyIncludeExcludeFilters$13(Set<String> include, Set<String> exclude, ActionLog log) {
        Material mat = this.extractMaterialFromLog(log);
        String matName = mat == null ? "" : mat.name().toLowerCase(Locale.ROOT);
        String details = RollbackEngine.safeLower(log.getDetails());
        boolean includePassed = true;
        if (include != null && !include.isEmpty()) {
            includePassed = include.contains(matName) || include.stream().anyMatch(inc -> details.contains(inc));
        }
        boolean excludeMatched = false;
        if (exclude != null && !exclude.isEmpty()) {
            excludeMatched = exclude.contains(matName) || exclude.stream().anyMatch(exc -> details.contains(exc));
        }
        return !includePassed || excludeMatched;
    }

    private /* synthetic */ void lambda$runRollbackTasksForLogsInThisChunk$9(State state, Deque deque, int n, World world, boolean bl, Debug.Ctx ctx, ChunkCounters chunkCounters, Map map, List list, AtomicInteger atomicInteger, Deque deque2, List list2, Map map2, Map map3, Deque deque3, Map map4, Map map5, long l, AtomicInteger atomicInteger2, Deque deque4, AtomicInteger atomicInteger3, Deque deque5, UUID uUID, BiConsumer biConsumer, ScheduledTask scheduledTask) {
        if (state.waitTicks > 0) {
            --state.waitTicks;
            return;
        }
        int n2 = 0;
        switch (state.phase) {
            case BLOCKS: {
                String string;
                BlockData blockData;
                Object object;
                Iterator iterator;
                while (!deque.isEmpty() && n2++ < n) {
                    iterator = (ActionLog)deque.poll();
                    try {
                        int n3;
                        if (this.isSyntheticRollback((ActionLog)((Object)iterator))) {
                            n3 = this.reverseBlockLogForRealBlocks(world, (ActionLog)((Object)iterator), bl, ctx, chunkCounters);
                        } else if (!map.containsKey(RollbackEngine.posKey((ActionLog)((Object)iterator))) && "BLOCK_PLACE".equals(RollbackEngine.act((ActionLog)((Object)iterator)))) {
                            object = new Location(world, (double)((ActionLog)((Object)iterator)).getX(), (double)((ActionLog)((Object)iterator)).getY(), (double)((ActionLog)((Object)iterator)).getZ());
                            blockData = world.getBlockAt(((ActionLog)((Object)iterator)).getX(), ((ActionLog)((Object)iterator)).getY(), ((ActionLog)((Object)iterator)).getZ());
                            Material material = blockData.getType();
                            string = blockData.getBlockData();
                            int n4 = this.forceClearAt(world, ((ActionLog)((Object)iterator)).getX(), ((ActionLog)((Object)iterator)).getY(), ((ActionLog)((Object)iterator)).getZ(), (ActionLog)((Object)iterator), ctx, chunkCounters);
                            if (n4 > 0) {
                                chunkCounters.forceClears.addAndGet(n4);
                                this.journalChangeIfAny(n4, (Location)object, (ActionLog)((Object)iterator), material, (BlockData)string, world.getBlockAt(((ActionLog)((Object)iterator)).getX(), ((ActionLog)((Object)iterator)).getY(), ((ActionLog)((Object)iterator)).getZ()));
                            }
                            n3 = n4;
                        } else {
                            n3 = this.reverseBlockLogForRealBlocks(world, (ActionLog)((Object)iterator), bl, ctx, chunkCounters);
                        }
                        if (n3 <= 0) continue;
                        list.add(((ActionLog)((Object)iterator)).getId());
                        atomicInteger.addAndGet(n3);
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().log(Level.WARNING, "[Rollback] block-reverse task error", throwable);
                    }
                }
                while (!deque2.isEmpty() && n2++ < n) {
                    iterator = (ActionLog)deque2.poll();
                    try {
                        int n5 = this.applyBlockLogForRealBlocks(world, (ActionLog)((Object)iterator), bl, ctx, chunkCounters);
                        if (n5 <= 0) continue;
                        list2.add(((ActionLog)((Object)iterator)).getId());
                        atomicInteger.addAndGet(n5);
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().log(Level.WARNING, "[Rollback] block-apply task error", throwable);
                    }
                }
                if (deque.isEmpty() && deque2.isEmpty() && state.afterOnlyToClear == null) {
                    state.afterOnlyToClear = new ArrayDeque<ActionLog>();
                    for (ActionLog actionLog : map2.values()) {
                        if (map.containsKey(RollbackEngine.posKey(actionLog)) || !"BLOCK_PLACE".equals(object = RollbackEngine.act(actionLog)) && !"BUCKET_EMPTY".equals(object) && !"LIQUID_FLOW".equals(object)) continue;
                        state.afterOnlyToClear.add(actionLog);
                    }
                    for (ActionLog actionLog : map3.values()) {
                        if (map.containsKey(RollbackEngine.posKey(actionLog))) continue;
                        state.afterOnlyToClear.add(actionLog);
                    }
                }
                while (state.afterOnlyToClear != null && !state.afterOnlyToClear.isEmpty() && n2++ < n) {
                    int n6;
                    iterator = state.afterOnlyToClear.poll();
                    Block block = world.getBlockAt(((ActionLog)((Object)iterator)).getX(), ((ActionLog)((Object)iterator)).getY(), ((ActionLog)((Object)iterator)).getZ());
                    object = block.getType();
                    blockData = block.getBlockData();
                    boolean bl2 = false;
                    string = this.extractBlockDataString((ActionLog)((Object)iterator));
                    if (string != null) {
                        try {
                            bl2 = block.getBlockData().matches(this.cachedBlockData(string));
                        }
                        catch (IllegalArgumentException illegalArgumentException) {
                            // empty catch block
                        }
                    }
                    if (!(bl2 || block.getType() != Material.WATER && block.getType() != Material.LAVA)) {
                        bl2 = true;
                    }
                    if (!bl2 || block.getType().isAir() || (n6 = this.forceClearAt(world, ((ActionLog)((Object)iterator)).getX(), ((ActionLog)((Object)iterator)).getY(), ((ActionLog)((Object)iterator)).getZ(), (ActionLog)((Object)iterator), ctx, chunkCounters)) <= 0) continue;
                    chunkCounters.forceClears.addAndGet(n6);
                    atomicInteger.addAndGet(n6);
                    list.add(((ActionLog)((Object)iterator)).getId());
                    this.journalChangeIfAny(n6, new Location(world, (double)((ActionLog)((Object)iterator)).getX(), (double)((ActionLog)((Object)iterator)).getY(), (double)((ActionLog)((Object)iterator)).getZ()), (ActionLog)((Object)iterator), (Material)object, blockData, world.getBlockAt(((ActionLog)((Object)iterator)).getX(), ((ActionLog)((Object)iterator)).getY(), ((ActionLog)((Object)iterator)).getZ()));
                }
                if (state.afterOnlyToClear != null && state.afterOnlyToClear.isEmpty()) {
                    state.afterOnlyToClear = null;
                    state.phase = RollbackModel.Phase.ENTITIES;
                    Debug.log(this.plugin, "rollback.phases", String.valueOf(ctx) + " \u2192 ENTITIES", new Object[0]);
                }
                return;
            }
            case ENTITIES: {
                while (!deque3.isEmpty() && n2++ < n) {
                    ActionLog actionLog = (ActionLog)deque3.poll();
                    try {
                        int n7;
                        DecorKey decorKey = this.decorKeyOf(actionLog);
                        boolean bl3 = map4.containsKey(decorKey);
                        Material material = (Material)map5.get(actionLog.getId());
                        if (actionLog.getEventTimestamp().getTime() <= l) {
                            int n8 = this.respawnDecor(material, new Location(world, (double)actionLog.getX(), (double)actionLog.getY(), (double)actionLog.getZ()), actionLog, atomicInteger2);
                            if (n8 <= 0) continue;
                            list2.add(actionLog.getId());
                            continue;
                        }
                        String string = RollbackEngine.act(actionLog);
                        if ("BLOCK_BREAK".equals(string) || "ENTITY_PLACE".equals(string) && bl3) {
                            n7 = this.respawnDecor(material, new Location(world, (double)actionLog.getX(), (double)actionLog.getY(), (double)actionLog.getZ()), actionLog, atomicInteger2);
                        } else {
                            Location location = new Location(world, (double)actionLog.getX(), (double)actionLog.getY(), (double)actionLog.getZ());
                            int n9 = "ENTITY_PLACE".equals(string) ? this.purgePlacedEntity(location, actionLog) : (n7 = this.isPainting(actionLog) ? this.purgeDecorEntitiesPaintingAware(location, this.metaOf(actionLog)) : this.purgeDecorEntities(location));
                        }
                        if (n7 <= 0) continue;
                        list.add(actionLog.getId());
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().log(Level.WARNING, "[Rollback] decor task error", throwable);
                    }
                }
                if (deque3.isEmpty()) {
                    state.waitTicks = Math.max(0, atomicInteger2.getAndSet(0) + 2);
                    state.phase = RollbackModel.Phase.ENTITY_ITEMS;
                    Debug.log(this.plugin, "rollback.phases", String.valueOf(ctx) + " \u2192 ENTITY_ITEMS", new Object[0]);
                }
                return;
            }
            case ENTITY_ITEMS: {
                while (!deque4.isEmpty() && n2++ < n) {
                    ActionLog actionLog = (ActionLog)deque4.poll();
                    try {
                        boolean bl4 = actionLog.getEventTimestamp().getTime() > l;
                        this.createItemFrameRollbackAction(world, actionLog, new Location(world, (double)actionLog.getX(), (double)actionLog.getY(), (double)actionLog.getZ()), RollbackEngine.act(actionLog), atomicInteger3, bl4, this.frameTaskEpoch.get(), ctx).run();
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().log(Level.WARNING, "[Rollback] frame-item task error", throwable);
                    }
                }
                if (deque4.isEmpty()) {
                    state.phase = RollbackModel.Phase.CONTAINERS;
                    Debug.log(this.plugin, "rollback.phases", String.valueOf(ctx) + " \u2192 CONTAINERS", new Object[0]);
                }
                return;
            }
            case CONTAINERS: {
                Object object;
                while (!deque5.isEmpty() && n2++ < n) {
                    object = (ActionLog)deque5.poll();
                    try {
                        Runnable runnable = this.createContainerSnapshotRestoreAction(world, (ActionLog)object, new Location(world, (double)((ActionLog)object).getX(), (double)((ActionLog)object).getY(), (double)((ActionLog)object).getZ()), atomicInteger3, ctx);
                        if (runnable == null) continue;
                        runnable.run();
                    }
                    catch (Throwable throwable) {
                        this.plugin.getLogger().log(Level.WARNING, "[Rollback] container task error", throwable);
                    }
                }
                if (!deque5.isEmpty()) break;
                object = Debug.scope(this.plugin, "rollback.counters", ctx, "chunk summary blocksApplied=%d blocksReversed=%d forceClears=%d waterDrains=%d waterlogClears=%d", chunkCounters.blocksApplied.get(), chunkCounters.blocksReversed.get(), chunkCounters.forceClears.get(), chunkCounters.waterDrains.get(), chunkCounters.waterlogClears.get());
                try {
                    ((Debug.Scope)object).add("blocksApplied", chunkCounters.blocksApplied.get()).add("blocksReversed", chunkCounters.blocksReversed.get()).add("forceClears", chunkCounters.forceClears.get()).add("waterDrains", chunkCounters.waterDrains.get()).add("waterlogClears", chunkCounters.waterlogClears.get());
                }
                finally {
                    if (object != null) {
                        ((Debug.Scope)object).close();
                    }
                }
                Debug.log(this.plugin, "rollback.perChunk", String.valueOf(ctx) + " done: blocks+" + atomicInteger.get() + " items+" + atomicInteger3.get(), new Object[0]);
                scheduledTask.cancel();
                if (!list.isEmpty()) {
                    this.databaseMariadb.markRolledBackBatchAsync(list, uUID);
                }
                if (!list2.isEmpty()) {
                    this.databaseMariadb.unmarkRolledBackBatchAsync(list2);
                }
                biConsumer.accept(atomicInteger.get(), atomicInteger3.get());
            }
        }
    }

    private static class ChunkCounters {
        final AtomicInteger blocksReversed = new AtomicInteger();
        final AtomicInteger blocksApplied = new AtomicInteger();
        final AtomicInteger forceClears = new AtomicInteger();
        final AtomicInteger waterDrains = new AtomicInteger();
        final AtomicInteger waterlogClears = new AtomicInteger();

        private ChunkCounters() {
        }
    }

    private static final class DecorKey {
        final int x;
        final int y;
        final int z;
        final byte face;
        final byte kind;

        DecorKey(int n, int n2, int n3, byte by, byte by2) {
            this.x = n;
            this.y = n2;
            this.z = n3;
            this.face = by;
            this.kind = by2;
        }

        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof DecorKey)) {
                return false;
            }
            DecorKey decorKey = (DecorKey)object;
            return this.x == decorKey.x && this.y == decorKey.y && this.z == decorKey.z && this.face == decorKey.face && this.kind == decorKey.kind;
        }

        public int hashCode() {
            int n = this.x * 0x700001 ^ this.y * 4099 ^ this.z;
            n = n * 31 + this.face;
            return n * 31 + this.kind;
        }
    }

    private static class State {
        RollbackModel.Phase phase = RollbackModel.Phase.BLOCKS;
        int waitTicks = 0;
        Deque<ActionLog> afterOnlyToClear = null;

        private State() {
        }
    }

    record RollbackStatsContext(String worldName, long rollbackStart, long timeRangeMillis, int radius, int itemsModified, int blocksModified, int totalChunks) {
    }
}


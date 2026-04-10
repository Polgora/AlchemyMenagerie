package me.Polgora;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.entity.item.PreventItemMerging;
import com.hypixel.hytale.server.core.modules.entity.item.PreventPickup;
import com.hypixel.hytale.server.core.modules.physics.component.PhysicsValues;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Tracks which Menagerie Nest blocks have eggs in them, manages display entities,
 * and schedules hatch timers.
 *
 * <p>All state mutations (display entity spawn/removal, disk writes) happen on the
 * game thread. The scheduler only queues a key into {@link #PENDING_HATCHES}; the
 * actual clean-up is deferred to the next time any game-thread code calls
 * {@link #processPendingHatches}.
 */
public class NestManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── NestState ─────────────────────────────────────────────────────────────

    public static class NestState {
        public final String eggItemId;
        public final long   insertTimeMs;
        public final long   hatchDurationMs;
        public final UUID   worldUuid;
        public final int    x, y, z;
        /** UUID of the player who placed the egg; may be null for pre-ownership nests. */
        public final UUID   ownerUuid;
        /** Username of the player who placed the egg; may be null for pre-ownership nests. */
        public final String ownerName;
        public volatile UUID              displayEntityUuid; // UUID of the floating egg entity
        public ScheduledFuture<?>         hatchFuture;

        public NestState(String eggItemId, long insertTimeMs, long hatchDurationMs,
                         UUID worldUuid, int x, int y, int z,
                         UUID ownerUuid, String ownerName) {
            this.eggItemId       = eggItemId;
            this.insertTimeMs    = insertTimeMs;
            this.hatchDurationMs = hatchDurationMs;
            this.worldUuid       = worldUuid;
            this.x = x; this.y = y; this.z = z;
            this.ownerUuid       = ownerUuid;
            this.ownerName       = ownerName;
        }

        public long remainingMs() {
            return Math.max(0L, insertTimeMs + hatchDurationMs - System.currentTimeMillis());
        }

        public boolean hasElapsed() {
            return System.currentTimeMillis() >= insertTimeMs + hatchDurationMs;
        }

        /** Human-readable remaining time, e.g. "4 min 32 sec" or "Ready!". */
        public String formatRemaining() {
            long ms = remainingMs();
            if (ms <= 0) return "Ready!";
            long secs = ms / 1000;
            long mins = secs / 60;
            secs %= 60;
            return mins > 0 ? mins + " min " + secs + " sec" : secs + " sec";
        }
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private static final Map<String, NestState>    NESTS          = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService  SCHEDULER      = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "NestManager-Hatch");
        t.setDaemon(true);
        return t;
    });
    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadAndReschedule();
        // Periodically flush remaining times to disk so that even a crash loses
        // at most ~60 seconds of server-uptime progress.
        SCHEDULER.scheduleAtFixedRate(NestManager::saveAllToDisk, 60L, 60L, TimeUnit.SECONDS);
    }

    // ── Key & hatch-time helpers ──────────────────────────────────────────────

    public static String nestKey(UUID worldUuid, int x, int y, int z) {
        return worldUuid + "_" + x + "_" + y + "_" + z;
    }

    public static int getHatchSeconds(String rarity) {
        return switch (rarity) {
            case "Uncommon"  -> 600;
            case "Rare"      -> 900;
            case "Epic"      -> 1200;
            case "Legendary" -> 1800;
            default          -> 300; // Common
        };
    }

    // ── Insert (game thread) ──────────────────────────────────────────────────

    /**
     * Records a new egg in a nest, spawns the display entity, and schedules the hatch timer.
     * Must be called from the game thread.
     */
    public static NestState insertEgg(Store<EntityStore> store,
                                      String eggItemId, String rarity,
                                      UUID worldUuid, int x, int y, int z,
                                      UUID ownerUuid, String ownerName) {
        String key            = nestKey(worldUuid, x, y, z);
        long   hatchDurationMs = (long) getHatchSeconds(rarity) * 1000L;
        NestState state       = new NestState(eggItemId, System.currentTimeMillis(), hatchDurationMs, worldUuid, x, y, z, ownerUuid, ownerName);
        NESTS.put(key, state);

        state.displayEntityUuid = spawnDisplayEntity(store, eggItemId, x, y, z);
        state.hatchFuture = SCHEDULER.schedule(() -> executeHatch(key), state.remainingMs(), TimeUnit.MILLISECONDS);

        saveToDisk(key, state);
        return state;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    public static NestState getState(String key) {
        return NESTS.get(key);
    }

    // ── Remove (game thread) ──────────────────────────────────────────────────

    /**
     * Removes a nest entry (e.g. block broken), cancels the timer, and deletes the save file.
     * Does NOT remove the display entity — the caller must do that via
     * {@link #removeDisplayEntityViaCmd} (preferred, from ECS event handlers) or
     * {@link #removeDisplayEntityViaStore} (fallback, from UI pages).
     * Returns the removed state, or null if none existed.
     */
    public static NestState removeNest(String key) {
        NestState state = NESTS.remove(key);
        if (state == null) return null;
        if (state.hatchFuture != null) state.hatchFuture.cancel(false);
        deleteFromDisk(key);
        return state;
    }

    /**
     * Removes the floating display entity via the ECS command buffer.
     * Must be called from within an {@link com.hypixel.hytale.component.system.EntityEventSystem}
     * handler so that the removal is properly networked to clients.
     */
    public static void removeDisplayEntityViaCmd(NestState state, CommandBuffer<EntityStore> cmd) {
        if (state == null || state.displayEntityUuid == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: removeDisplayEntityViaCmd — no UUID to remove");
            return;
        }
        World world = Universe.get().getWorld(state.worldUuid);
        if (world == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: removeDisplayEntityViaCmd — world %s not found", state.worldUuid);
            return;
        }
        Ref<EntityStore> ref = world.getEntityRef(state.displayEntityUuid);
        if (ref == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: removeDisplayEntityViaCmd — no ref for UUID %s", state.displayEntityUuid);
            state.displayEntityUuid = null;
            return;
        }
        cmd.tryRemoveEntity(ref, RemoveReason.REMOVE);
        LOGGER.at(java.util.logging.Level.INFO).log("NestManager: queued removal of display entity %s via cmd", state.displayEntityUuid);
        state.displayEntityUuid = null;
    }

    /**
     * Removes the floating display entity directly via the store.
     * Use this only from non-ECS contexts (e.g. UI page handlers) where no command buffer
     * is available. For ECS event handlers, prefer {@link #removeDisplayEntityViaCmd}.
     */
    public static void removeDisplayEntityViaStore(NestState state, Store<EntityStore> store) {
        removeDisplayEntityFallback(state);
    }

    // ── Auto-hatch (scheduler thread → world thread) ──────────────────────────

    /**
     * Executes a hatch triggered by the scheduler. Runs on the scheduler thread;
     * all world-state mutations are deferred to the world's game thread via
     * {@link World#execute}.
     */
    private static void executeHatch(String key) {
        NestState state = NESTS.remove(key);
        if (state == null) return;
        deleteFromDisk(key);

        World world = Universe.get().getWorld(state.worldUuid);
        if (world == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "NestManager: world %s not found for auto-hatch of %s", state.worldUuid, key);
            return;
        }

        world.execute(() -> {
            removeDisplayEntityFallback(state);
            spawnCreature(state);
        });
    }

    // ── Creature spawning ─────────────────────────────────────────────────────

    /**
     * Derives the NPC role name from the egg item ID (strips the "Egg_" prefix)
     * and spawns the creature above the nest block.
     */
    private static void spawnCreature(NestState state) {
        if (!state.eggItemId.startsWith("Egg_")) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: cannot derive NPC role from egg ID '%s'", state.eggItemId);
            return;
        }
        String npcRole = state.eggItemId.substring(4); // "Egg_Verdant_Sprout" -> "Verdant_Sprout"

        World world = Universe.get().getWorld(state.worldUuid);
        if (world == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: world %s not found, cannot spawn %s", state.worldUuid, npcRole);
            return;
        }

        // Defer to the world's task queue so the spawn runs after the current ECS tick,
        // avoiding the "Store is currently processing" error when called from a system.
        Vector3d spawnPos = new Vector3d(state.x + 0.5, state.y + 1.0, state.z + 0.5);
        world.execute(() -> {
            try {
                var entityStore = world.getEntityStore();
                var store       = entityStore.getStore();
                var spawnResult = NPCPlugin.get().spawnNPC(store, npcRole, npcRole, spawnPos, Vector3f.ZERO);
                LOGGER.at(java.util.logging.Level.INFO).log("NestManager: spawned %s at %s", npcRole, spawnPos);

                // The spawned NPC persists in the world with a stable entity UUID across restarts.
                // Store entity UUID → owner directly so lookups survive without re-spawning.
                if (spawnResult != null && spawnResult.first() != null) {
                    UUIDComponent uuidComp = store.getComponent(spawnResult.first(), UUIDComponent.getComponentType());
                    if (uuidComp != null && uuidComp.getUuid() != null) {
                        CreatureRegistry.registerExternal(uuidComp.getUuid(), state.ownerUuid, state.ownerName);
                        LOGGER.at(java.util.logging.Level.INFO).log(
                                "NestManager: registered owner %s for creature %s (%s)",
                                state.ownerName, uuidComp.getUuid(), npcRole);
                    }
                }
            } catch (Exception e) {
                LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: failed to spawn %s — %s", npcRole, e.getMessage());
            }
        });
    }

    // ── Display-entity restore after restart (game thread) ───────────────────

    /**
     * If the server restarted and the nest's display entity was lost, re-spawns it.
     * Call when a nest block is first interacted with after restart.
     */
    public static void ensureDisplayEntity(String key, Store<EntityStore> store) {
        NestState state = NESTS.get(key);
        if (state == null || state.hasElapsed()) return;

        // If we have a UUID, verify the entity still exists in the world.
        // After a restart the world re-loads the item entity with the same UUID,
        // so this check prevents spawning a duplicate on top of it.
        if (state.displayEntityUuid != null) {
            var ref = store.getExternalData().getRefFromUUID(state.displayEntityUuid);
            if (ref != null) return; // entity is alive — nothing to do
        }

        // Entity is absent (first run, or lost) — spawn a fresh one and persist the new UUID.
        state.displayEntityUuid = spawnDisplayEntity(store, state.eggItemId, state.x, state.y, state.z);
        saveToDisk(key, state);
    }

    // ── Display entity internals ──────────────────────────────────────────────

    /** Spawns the floating egg entity; returns its UUID for later removal, or null on failure. */
    private static UUID spawnDisplayEntity(Store<EntityStore> store,
                                           String eggItemId, int x, int y, int z) {
        ItemStack eggStack = new ItemStack(eggItemId, 1);
        Vector3d  pos      = new Vector3d(x + 0.5, y + 0.5, z + 0.5);

        Holder<EntityStore> holder = ItemComponent.generateItemDrop(
                store, eggStack, pos, Vector3f.ZERO, Float.MAX_VALUE, Float.MAX_VALUE, 0f);
        if (holder == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: generateItemDrop returned null for %s", eggItemId);
            return null;
        }

        // Remove Velocity and PhysicsValues so neither ItemPhysicsSystem nor ItemPrePhysicsSystem
        // processes this entity — it stays frozen in place without using the deprecated ItemPhysicsComponent.
        holder.tryRemoveComponent(Velocity.getComponentType());
        holder.tryRemoveComponent(PhysicsValues.getComponentType());
        holder.tryRemoveComponent(DespawnComponent.getComponentType());
        holder.addComponent(PreventPickup.getComponentType(),      PreventPickup.INSTANCE);
        holder.addComponent(PreventItemMerging.getComponentType(), PreventItemMerging.INSTANCE);

        Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
        if (ref == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: addEntity returned null for %s", eggItemId);
            return null;
        }

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null || uuidComp.getUuid() == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: no UUID on display entity for %s", eggItemId);
            return null;
        }
        UUID entityUuid = uuidComp.getUuid();
        LOGGER.at(java.util.logging.Level.INFO).log("NestManager: spawned display entity %s for %s", entityUuid, eggItemId);
        return entityUuid;
    }

    /** Remove display entity by UUID lookup via EntityStore. */
    private static void removeDisplayEntityFallback(NestState state) {
        if (state.displayEntityUuid == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: removeDisplayEntity called but displayEntityUuid is null");
            return;
        }
        World world = Universe.get().getWorld(state.worldUuid);
        if (world == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: world %s not found during entity removal", state.worldUuid);
            return;
        }
        Ref<EntityStore> ref = world.getEntityStore().getRefFromUUID(state.displayEntityUuid);
        if (ref == null) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: no entity found for UUID %s", state.displayEntityUuid);
            state.displayEntityUuid = null;
            return;
        }
        try {
            world.getEntityStore().getStore().removeEntity(ref, RemoveReason.REMOVE);
            LOGGER.at(java.util.logging.Level.INFO).log("NestManager: removed display entity %s", state.displayEntityUuid);
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log("NestManager: exception removing display entity: %s", e.getMessage());
        }
        state.displayEntityUuid = null;
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    /**
     * Called when the server is shutting down. Flushes all remaining times to disk
     * and stops the scheduler.
     */
    public static void shutdown() {
        SCHEDULER.shutdownNow();
        saveAllToDisk();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveAllToDisk() {
        for (Map.Entry<String, NestState> entry : NESTS.entrySet()) {
            saveToDisk(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Saves the nest state to disk.
     *
     * <p>Line 0 is a "v2" format marker. Line 1 stores only the <em>remaining
     * milliseconds at write time</em> — NOT an absolute deadline. On load, this
     * value is used directly as the initial countdown so that offline time is
     * never counted toward the hatch timer.
     */
    private static void saveToDisk(String key, NestState state) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(fileNameFor(key)),
                    "v2\n"
                    + state.eggItemId + "\n"
                    + state.remainingMs() + "\n"
                    + state.worldUuid + "\n"
                    + state.x + "\n"
                    + state.y + "\n"
                    + state.z + "\n"
                    + (state.ownerUuid != null ? state.ownerUuid : "null") + "\n"
                    + (state.ownerName != null ? state.ownerName : "") + "\n"
                    + (state.displayEntityUuid != null ? state.displayEntityUuid : "null"));
        } catch (IOException ignored) {}
    }

    private static void deleteFromDisk(String key) {
        if (dataDir == null) return;
        try { Files.deleteIfExists(dataDir.resolve(fileNameFor(key))); } catch (IOException ignored) {}
    }

    private static String fileNameFor(String key) {
        return key.replace(':', '_') + ".txt";
    }

    private static void loadAndReschedule() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(NestManager::loadNestFile);
        } catch (IOException ignored) {}
    }

    private static void loadNestFile(Path file) {
        try {
            String[] raw   = Files.readString(file).trim().split("\n");
            String[] lines = java.util.Arrays.stream(raw).map(String::trim).toArray(String[]::new);

            // Both v2 and legacy formats share the same layout from line 3 onward:
            //   line 3 = worldUuid, 4 = x, 5 = y, 6 = z, 7 = ownerUuid, 8 = ownerName, 9 = displayUuid
            //
            // They differ only in how lines 0-2 are interpreted:
            //   v2 (line 0 == "v2"):  line 1 = eggItemId, line 2 = remainingMs (pure duration)
            //   legacy:               line 0 = eggItemId, line 1 = insertTimeMs, line 2 = hatchDurationMs

            if (lines.length < 7) return;

            boolean v2         = "v2".equals(lines[0]);
            String  eggItemId  = v2 ? lines[1] : lines[0];
            long    remainingMs;

            if (v2) {
                // Remaining time stored directly — offline time is not counted on load.
                remainingMs = Long.parseLong(lines[2]);
            } else {
                // Legacy: compute from absolute deadline (still affected by offline time,
                // but only for this one load; the file is rewritten as v2 on next save).
                long insertTimeMs    = Long.parseLong(lines[1]);
                long hatchDurationMs = Long.parseLong(lines[2]);
                remainingMs = Math.max(0L, insertTimeMs + hatchDurationMs - System.currentTimeMillis());
            }

            UUID   worldUuid = UUID.fromString(lines[3]);
            int    x         = Integer.parseInt(lines[4]);
            int    y         = Integer.parseInt(lines[5]);
            int    z         = Integer.parseInt(lines[6]);

            UUID   ownerUuid        = null;
            String ownerName        = null;
            UUID   displayEntityUuid = null;
            if (lines.length >= 9) {
                ownerUuid = "null".equals(lines[7]) ? null : UUID.fromString(lines[7]);
                ownerName = lines[8].isEmpty() ? null : lines[8];
            }
            if (lines.length >= 10) {
                displayEntityUuid = "null".equals(lines[9]) ? null : UUID.fromString(lines[9]);
            }

            // Set insertTimeMs = now and hatchDurationMs = remainingMs so that
            // remainingMs() counts down from `remainingMs` starting right now —
            // no offline time is ever subtracted.
            String    key   = nestKey(worldUuid, x, y, z);
            NestState state = new NestState(eggItemId, System.currentTimeMillis(), remainingMs,
                    worldUuid, x, y, z, ownerUuid, ownerName);
            state.displayEntityUuid = displayEntityUuid;
            NESTS.put(key, state);

            if (remainingMs <= 0) {
                SCHEDULER.schedule(() -> executeHatch(key), 10L, TimeUnit.SECONDS);
            } else {
                state.hatchFuture = SCHEDULER.schedule(
                        () -> executeHatch(key), remainingMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {}
    }
}
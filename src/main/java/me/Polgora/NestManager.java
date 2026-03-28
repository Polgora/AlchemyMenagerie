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
        public volatile UUID              displayEntityUuid; // UUID of the floating egg entity
        public ScheduledFuture<?>         hatchFuture;

        public NestState(String eggItemId, long insertTimeMs, long hatchDurationMs,
                         UUID worldUuid, int x, int y, int z) {
            this.eggItemId       = eggItemId;
            this.insertTimeMs    = insertTimeMs;
            this.hatchDurationMs = hatchDurationMs;
            this.worldUuid       = worldUuid;
            this.x = x; this.y = y; this.z = z;
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
    private static final ConcurrentLinkedQueue<String> PENDING_HATCHES = new ConcurrentLinkedQueue<>();
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
                                      UUID worldUuid, int x, int y, int z) {
        String key            = nestKey(worldUuid, x, y, z);
        long   hatchDurationMs = (long) getHatchSeconds(rarity) * 1000L;
        NestState state       = new NestState(eggItemId, System.currentTimeMillis(), hatchDurationMs, worldUuid, x, y, z);
        NESTS.put(key, state);

        state.displayEntityUuid = spawnDisplayEntity(store, eggItemId, x, y, z);
        state.hatchFuture = SCHEDULER.schedule(() -> PENDING_HATCHES.add(key), state.remainingMs(), TimeUnit.MILLISECONDS);

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

    // ── Pending hatch processing (game thread) ────────────────────────────────

    /**
     * Drains the pending-hatches queue and cleans up each elapsed nest.
     * Call this overload from ECS event handlers (has CommandBuffer) for proper client sync.
     */
    public static void processPendingHatches(Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        String key;
        while ((key = PENDING_HATCHES.poll()) != null) {
            NestState state = NESTS.remove(key);
            if (state == null) continue;
            removeDisplayEntityViaCmd(state, cmd);
            deleteFromDisk(key);
            // TODO: spawn creature entity at (state.x + 0.5, state.y + 1, state.z + 0.5)
        }
    }

    /**
     * Drains the pending-hatches queue and cleans up each elapsed nest.
     * Use this overload from non-ECS contexts (e.g. UI page handlers).
     */
    public static void processPendingHatches(Store<EntityStore> store) {
        String key;
        while ((key = PENDING_HATCHES.poll()) != null) {
            NestState state = NESTS.remove(key);
            if (state == null) continue;
            removeDisplayEntityFallback(state);
            deleteFromDisk(key);
            // TODO: spawn creature entity at (state.x + 0.5, state.y + 1, state.z + 0.5)
        }
    }

    // ── Display-entity restore after restart (game thread) ───────────────────

    /**
     * If the server restarted and the nest's display entity was lost, re-spawns it.
     * Call when a nest block is first interacted with after restart.
     */
    public static void ensureDisplayEntity(String key, Store<EntityStore> store) {
        NestState state = NESTS.get(key);
        if (state != null && state.displayEntityUuid == null && !state.hasElapsed()) {
            state.displayEntityUuid = spawnDisplayEntity(store, state.eggItemId, state.x, state.y, state.z);
        }
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

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(String key, NestState state) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(fileNameFor(key)),
                    state.eggItemId + "\n"
                    + state.insertTimeMs + "\n"
                    + state.hatchDurationMs + "\n"
                    + state.worldUuid + "\n"
                    + state.x + "\n"
                    + state.y + "\n"
                    + state.z);
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
            String[] lines = Files.readString(file).trim().split("\n");
            if (lines.length < 7) return;
            String eggItemId      = lines[0].trim();
            long   insertTimeMs   = Long.parseLong(lines[1].trim());
            long   hatchDurationMs = Long.parseLong(lines[2].trim());
            UUID   worldUuid      = UUID.fromString(lines[3].trim());
            int    x              = Integer.parseInt(lines[4].trim());
            int    y              = Integer.parseInt(lines[5].trim());
            int    z              = Integer.parseInt(lines[6].trim());

            String    key   = nestKey(worldUuid, x, y, z);
            NestState state = new NestState(eggItemId, insertTimeMs, hatchDurationMs, worldUuid, x, y, z);
            NESTS.put(key, state);

            long remainingMs = state.remainingMs();
            if (remainingMs <= 0) {
                PENDING_HATCHES.add(key); // will be processed on next nest interaction
            } else {
                state.hatchFuture = SCHEDULER.schedule(
                        () -> PENDING_HATCHES.add(key), remainingMs, TimeUnit.MILLISECONDS);
            }
        } catch (Exception ignored) {}
    }
}
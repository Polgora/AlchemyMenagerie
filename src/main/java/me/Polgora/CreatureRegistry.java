package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.events.AllWorldsLoadedEvent;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistent registry for all Menagerie creatures.
 *
 * <p>Each creature gets a stable {@code creatureId} (generated once at hatch/spawn
 * time) that survives server restarts. At startup, all recorded creatures are
 * re-spawned after worlds finish loading, and their new runtime entity UUIDs are
 * mapped back to the stable records.
 *
 * <p>There are two kinds of records:
 * <ul>
 *   <li><b>Managed</b> — nest-hatched creatures whose position and role are stored
 *       here; re-spawned automatically by this registry on each server start.
 *   <li><b>External</b> — creatively-placed creatures whose entity UUID is stable
 *       (the world handles re-loading them); only owner metadata is stored.
 * </ul>
 *
 * <p>Disk format for a managed record ({@code creatures/<creatureId>.txt}):
 * <pre>
 * managed
 * roleName
 * worldUuid
 * x
 * y
 * z
 * ownerUuid   (or "null")
 * ownerName   (or "")
 * </pre>
 *
 * <p>Disk format for an external record ({@code creatures/<entityUuid>.txt}):
 * <pre>
 * external
 * ownerUuid   (or "null")
 * ownerName   (or "")
 * roleName    (or "" — absent in legacy records; backfilled at world load)
 * </pre>
 */
public class CreatureRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Record types ──────────────────────────────────────────────────────────

    public static class CreatureRecord {
        /** Stable ID — never changes for this creature's lifetime. */
        public final UUID   creatureId;
        public final String roleName;
        public final UUID   worldUuid;
        public final double x, y, z;
        /** May be null if no owner was recorded. */
        public final UUID   ownerUuid;
        public final String ownerName;
        /** Current runtime entity UUID — updated after every spawn. */
        volatile UUID entityUuid;

        CreatureRecord(UUID creatureId, String roleName, UUID worldUuid,
                       double x, double y, double z,
                       UUID ownerUuid, String ownerName) {
            this.creatureId = creatureId;
            this.roleName   = roleName;
            this.worldUuid  = worldUuid;
            this.x = x; this.y = y; this.z = z;
            this.ownerUuid  = ownerUuid;
            this.ownerName  = ownerName;
        }
    }

    // ── Internal state ────────────────────────────────────────────────────────

    /** Stable creature ID → managed record. */
    private static final Map<UUID, CreatureRecord> MANAGED = new ConcurrentHashMap<>();

    /** Runtime entity UUID → stable creature ID (rebuilt each start). */
    private static final Map<UUID, UUID> ENTITY_TO_CREATURE = new ConcurrentHashMap<>();

    /**
     * External (creative-spawn) entity UUID → (ownerUuid, ownerName, roleName).
     * Entity UUIDs are stable for world-static NPCs.
     * {@code EXTERNAL_ROLE_NAME} may be absent for legacy records; it is backfilled
     * from live NPC entities in {@link #onWorldsLoaded}.
     */
    private static final Map<UUID, UUID>   EXTERNAL_OWNER_UUID = new ConcurrentHashMap<>();
    private static final Map<UUID, String> EXTERNAL_OWNER_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, String> EXTERNAL_ROLE_NAME  = new ConcurrentHashMap<>();

    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Loads all persisted records into memory. Call once from {@code Main.setup()}
     * before worlds load. Re-spawning happens later via {@link #onWorldsLoaded}.
     */
    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadAll();
    }

    // ── Managed creature API (nest-hatched) ───────────────────────────────────

    /**
     * Creates a new managed creature record and saves it to disk.
     * Returns the stable {@code creatureId}.
     */
    public static UUID create(String roleName, UUID worldUuid,
                              double x, double y, double z,
                              UUID ownerUuid, String ownerName) {
        UUID          id     = UUID.randomUUID();
        CreatureRecord record = new CreatureRecord(id, roleName, worldUuid, x, y, z, ownerUuid, ownerName);
        MANAGED.put(id, record);
        saveManagedToDisk(record);
        return id;
    }

    /**
     * Links a freshly-spawned runtime entity UUID to a managed creature record.
     * Call immediately after the NPC entity is added to the world.
     */
    public static void linkEntityUuid(UUID creatureId, UUID entityUuid) {
        CreatureRecord record = MANAGED.get(creatureId);
        if (record == null) return;
        record.entityUuid = entityUuid;
        ENTITY_TO_CREATURE.put(entityUuid, creatureId);
    }

    // ── External creature API (creative-spawn) ────────────────────────────────

    /**
     * Persists ownership for a creatively-placed creature whose entity UUID
     * is stable across restarts (the world re-loads it automatically).
     *
     * @param roleName the NPC role name (e.g. {@code "Verdant_Sprout"}); may be null
     *                 for legacy callers but should be provided when available.
     */
    public static void registerExternal(UUID entityUuid, UUID ownerUuid, String ownerName, String roleName) {
        EXTERNAL_OWNER_UUID.put(entityUuid, ownerUuid);
        EXTERNAL_OWNER_NAME.put(entityUuid, ownerName);
        if (roleName != null && !roleName.isBlank()) EXTERNAL_ROLE_NAME.put(entityUuid, roleName);
        saveExternalToDisk(entityUuid, ownerUuid, ownerName, roleName);
    }

    // ── Whistle / ownership queries ───────────────────────────────────────────

    /** Stable creature identity used by the Runebound Whistle UI. */
    public record OwnedCreature(UUID stableId, String roleName) {}

    /**
     * Scans the live entity store for any external creatures owned by the given player
     * that are missing a stored role name, and backfills them in memory and on disk.
     *
     * <p>Call this once from a context where the store is available (e.g. a UI page's
     * {@code build()} method). This handles creatures whose disk records pre-date role
     * tracking — typically nest-hatched creatures from older sessions.
     */
    public static void backfillExternalRoles(UUID ownerUuid,
                                             com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store) {
        for (Map.Entry<UUID, UUID> entry : EXTERNAL_OWNER_UUID.entrySet()) {
            if (!ownerUuid.equals(entry.getValue())) continue;
            UUID entityUuid = entry.getKey();
            if (EXTERNAL_ROLE_NAME.containsKey(entityUuid)) continue;

            var ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref == null) continue;
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc == null || npc.getRoleName() == null) continue;

            String roleName  = npc.getRoleName();
            String ownerName = EXTERNAL_OWNER_NAME.getOrDefault(entityUuid, "");
            EXTERNAL_ROLE_NAME.put(entityUuid, roleName);
            saveExternalToDisk(entityUuid, ownerUuid, ownerName, roleName);
            LOGGER.at(java.util.logging.Level.INFO).log(
                    "CreatureRegistry: backfilled role %s for external creature %s", roleName, entityUuid);
        }
    }

    /**
     * Returns the distinct species (role names) owned by the given player,
     * sorted alphabetically. Covers both managed (nest-hatched) and external
     * (creative-spawn) creatures; external creatures without a known role name
     * are excluded until their role is backfilled via {@link #onWorldsLoaded}.
     */
    public static List<String> getOwnedSpecies(UUID ownerUuid) {
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (CreatureRecord r : MANAGED.values()) {
            if (ownerUuid.equals(r.ownerUuid) && r.roleName != null && seen.add(r.roleName)) {
                result.add(r.roleName);
            }
        }
        for (Map.Entry<UUID, UUID> e : EXTERNAL_OWNER_UUID.entrySet()) {
            if (ownerUuid.equals(e.getValue())) {
                String role = EXTERNAL_ROLE_NAME.get(e.getKey());
                if (role != null && seen.add(role)) result.add(role);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Returns all creatures of the given species owned by the player (both managed
     * and external), sorted by stable creature ID for consistent ordering.
     */
    public static List<OwnedCreature> getOwnedBySpecies(UUID ownerUuid, String roleName) {
        List<OwnedCreature> result = new ArrayList<>();
        for (CreatureRecord r : MANAGED.values()) {
            if (ownerUuid.equals(r.ownerUuid) && roleName.equals(r.roleName)) {
                result.add(new OwnedCreature(r.creatureId, r.roleName));
            }
        }
        for (Map.Entry<UUID, UUID> e : EXTERNAL_OWNER_UUID.entrySet()) {
            if (ownerUuid.equals(e.getValue()) && roleName.equals(EXTERNAL_ROLE_NAME.get(e.getKey()))) {
                result.add(new OwnedCreature(e.getKey(), roleName));
            }
        }
        result.sort((a, b) -> a.stableId().compareTo(b.stableId()));
        return result;
    }

    /**
     * Returns the runtime entity UUID for a creature given its stable ID, or {@code null}.
     * For managed creatures, this is the current session's entity UUID (may be null if not yet spawned).
     * For external creatures, the stable ID <em>is</em> the entity UUID.
     */
    public static UUID getEntityUuidForCreature(UUID stableCreatureId) {
        CreatureRecord r = MANAGED.get(stableCreatureId);
        if (r != null) return r.entityUuid;
        // External: stableId == entityUuid
        if (EXTERNAL_OWNER_UUID.containsKey(stableCreatureId)) return stableCreatureId;
        return null;
    }

    // ── Owner lookups (used by CreatureInfoPage) ──────────────────────────────

    /** Returns the owner display name for any creature, or {@code null} if unknown. */
    public static String getOwnerName(UUID entityUuid) {
        // Managed (nest-hatched)
        UUID creatureId = ENTITY_TO_CREATURE.get(entityUuid);
        if (creatureId != null) {
            CreatureRecord record = MANAGED.get(creatureId);
            if (record != null) return record.ownerName;
        }
        // External (creative-spawn)
        return EXTERNAL_OWNER_NAME.get(entityUuid);
    }

    /** Returns the owner UUID for any creature, or {@code null} if unknown. */
    public static UUID getOwnerUuid(UUID entityUuid) {
        UUID creatureId = ENTITY_TO_CREATURE.get(entityUuid);
        if (creatureId != null) {
            CreatureRecord record = MANAGED.get(creatureId);
            if (record != null) return record.ownerUuid;
        }
        return EXTERNAL_OWNER_UUID.get(entityUuid);
    }

    // ── World-load re-spawning ────────────────────────────────────────────────

    /**
     * Re-spawns all managed creatures and backfills role names for external creatures
     * loaded from disk before role tracking was added.
     *
     * <p>Register with:
     * {@code getEventRegistry().registerGlobal(AllWorldsLoadedEvent.class, CreatureRegistry::onWorldsLoaded)}
     */
    public static void onWorldsLoaded(AllWorldsLoadedEvent event) {
        // Re-spawn managed (nest-hatched) creatures
        for (CreatureRecord record : MANAGED.values()) {
            World world = Universe.get().getWorld(record.worldUuid);
            if (world == null) {
                LOGGER.at(java.util.logging.Level.WARNING).log(
                        "CreatureRegistry: world %s not found for creature %s (%s)",
                        record.worldUuid, record.creatureId, record.roleName);
                continue;
            }
            Vector3d pos = new Vector3d(record.x, record.y, record.z);
            world.execute(() -> {
                try {
                    var store  = world.getEntityStore().getStore();
                    var result = NPCPlugin.get().spawnNPC(store, record.roleName, record.roleName, pos, Vector3f.ZERO);
                    if (result != null && result.first() != null) {
                        UUIDComponent uuidComp = store.getComponent(result.first(), UUIDComponent.getComponentType());
                        if (uuidComp != null && uuidComp.getUuid() != null) {
                            linkEntityUuid(record.creatureId, uuidComp.getUuid());
                            LOGGER.at(java.util.logging.Level.INFO).log(
                                    "CreatureRegistry: re-spawned %s (owner: %s) as entity %s",
                                    record.roleName, record.ownerName, uuidComp.getUuid());
                        }
                    }
                } catch (Exception e) {
                    LOGGER.at(java.util.logging.Level.WARNING).log(
                            "CreatureRegistry: failed to re-spawn %s — %s", record.roleName, e.getMessage());
                }
            });
        }

    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveManagedToDisk(CreatureRecord r) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(r.creatureId + ".txt"),
                    "managed\n"
                    + r.roleName + "\n"
                    + r.worldUuid + "\n"
                    + r.x + "\n"
                    + r.y + "\n"
                    + r.z + "\n"
                    + (r.ownerUuid != null ? r.ownerUuid : "null") + "\n"
                    + (r.ownerName != null ? r.ownerName : ""));
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureRegistry: failed to save managed %s — %s", r.creatureId, e.getMessage());
        }
    }

    private static void saveExternalToDisk(UUID entityUuid, UUID ownerUuid, String ownerName, String roleName) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(entityUuid + ".txt"),
                    "external\n"
                    + (ownerUuid != null ? ownerUuid : "null") + "\n"
                    + (ownerName != null ? ownerName : "") + "\n"
                    + (roleName  != null ? roleName  : ""));
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureRegistry: failed to save external %s — %s", entityUuid, e.getMessage());
        }
    }

    private static void loadAll() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(CreatureRegistry::loadFile);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureRegistry: failed to list data dir — %s", e.getMessage());
        }
    }

    private static void loadFile(Path file) {
        try {
            String[] lines = Files.readString(file).split("\n", -1);
            if (lines.length < 1) return;
            String type = lines[0].trim();

            UUID fileUuid = UUID.fromString(file.getFileName().toString().replace(".txt", ""));

            if ("managed".equals(type)) {
                if (lines.length < 8) return;
                String roleName       = lines[1].trim();
                UUID   worldUuid      = UUID.fromString(lines[2].trim());
                double x              = Double.parseDouble(lines[3].trim());
                double y              = Double.parseDouble(lines[4].trim());
                double z              = Double.parseDouble(lines[5].trim());
                String ownerUuidStr   = lines[6].trim();
                UUID   ownerUuid      = "null".equals(ownerUuidStr) ? null : UUID.fromString(ownerUuidStr);
                String ownerName      = lines[7].trim().isEmpty() ? null : lines[7].trim();
                MANAGED.put(fileUuid, new CreatureRecord(fileUuid, roleName, worldUuid, x, y, z, ownerUuid, ownerName));

            } else if ("external".equals(type)) {
                if (lines.length < 3) return;
                String ownerUuidStr = lines[1].trim();
                UUID   ownerUuid    = "null".equals(ownerUuidStr) ? null : UUID.fromString(ownerUuidStr);
                String ownerName    = lines[2].trim().isEmpty() ? null : lines[2].trim();
                // Line 4 (index 3) added in v0.0.31 — absent in legacy records
                String roleName     = (lines.length >= 4 && !lines[3].trim().isEmpty())
                                      ? lines[3].trim() : null;
                if (ownerUuid != null) {
                    EXTERNAL_OWNER_UUID.put(fileUuid, ownerUuid);
                    EXTERNAL_OWNER_NAME.put(fileUuid, ownerName != null ? ownerName : "");
                    if (roleName != null) EXTERNAL_ROLE_NAME.put(fileUuid, roleName);
                }
            }
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureRegistry: failed to load %s — %s", file.getFileName(), e.getMessage());
        }
    }
}
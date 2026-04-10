package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps creature entity UUIDs to the player who owns them, with disk persistence
 * so ownership survives server restarts.
 *
 * <p>One file per creature is written to the data directory:
 * <pre>
 * line 0 — ownerUuid
 * line 1 — ownerName
 * </pre>
 *
 * <p>Call {@link #init(Path)} once on plugin startup before any registrations.
 */
public class CreatureOwnerRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private record OwnerInfo(UUID ownerUuid, String ownerName) {}

    private static final Map<UUID, OwnerInfo> OWNERS = new ConcurrentHashMap<>();
    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadAll();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Associates a creature with its owner and persists the mapping to disk. */
    public static void register(UUID creatureUuid, UUID ownerUuid, String ownerName) {
        OWNERS.put(creatureUuid, new OwnerInfo(ownerUuid, ownerName));
        saveToDisk(creatureUuid, ownerUuid, ownerName);
    }

    /** Returns the display name of the creature's owner, or {@code null} if unknown. */
    public static String getOwnerName(UUID creatureUuid) {
        OwnerInfo info = OWNERS.get(creatureUuid);
        return info != null ? info.ownerName : null;
    }

    /** Returns the UUID of the creature's owner, or {@code null} if unknown. */
    public static UUID getOwnerUuid(UUID creatureUuid) {
        OwnerInfo info = OWNERS.get(creatureUuid);
        return info != null ? info.ownerUuid : null;
    }

    /** Removes ownership data for a creature and deletes its file from disk. */
    public static void remove(UUID creatureUuid) {
        OWNERS.remove(creatureUuid);
        deleteFromDisk(creatureUuid);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(UUID creatureUuid, UUID ownerUuid, String ownerName) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(creatureUuid + ".txt"),
                    ownerUuid + "\n" + ownerName);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureOwnerRegistry: failed to save %s — %s", creatureUuid, e.getMessage());
        }
    }

    private static void deleteFromDisk(UUID creatureUuid) {
        if (dataDir == null) return;
        try { Files.deleteIfExists(dataDir.resolve(creatureUuid + ".txt")); } catch (IOException ignored) {}
    }

    private static void loadAll() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(CreatureOwnerRegistry::loadFile);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureOwnerRegistry: failed to list data dir — %s", e.getMessage());
        }
    }

    private static void loadFile(Path file) {
        try {
            String[] lines = Files.readString(file).trim().split("\n");
            if (lines.length < 2) return;
            UUID   creatureUuid = UUID.fromString(file.getFileName().toString().replace(".txt", ""));
            UUID   ownerUuid    = UUID.fromString(lines[0].trim());
            String ownerName    = lines[1].trim();
            OWNERS.put(creatureUuid, new OwnerInfo(ownerUuid, ownerName));
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureOwnerRegistry: failed to load %s — %s", file.getFileName(), e.getMessage());
        }
    }
}
package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists creature-to-anchor assignments so the Runebound Whistle's assignments
 * survive server restarts.
 *
 * <p>Maps a stable creature ID (from {@link CreatureRegistry}) to the block
 * position of the {@link AnchorManager.AnchorPos} it is assigned to.
 *
 * <p>Disk format ({@code creature_anchors/<stableCreatureId>.txt}):
 * <pre>
 * worldUuid
 * x
 * y
 * z
 * </pre>
 */
public class CreatureAnchorRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Lightweight anchor identity — position only, no owner/name data. */
    public record AnchorKey(UUID worldUuid, int x, int y, int z) {}

    private static final Map<UUID, AnchorKey> ASSIGNMENTS = new ConcurrentHashMap<>();
    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadAll();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Assigns the creature to the anchor and persists the mapping to disk.
     *
     * @param stableCreatureId the stable creature ID from {@link CreatureRegistry}
     * @param anchor           the anchor to assign to
     */
    public static void assign(UUID stableCreatureId, AnchorManager.AnchorPos anchor) {
        AnchorKey key = new AnchorKey(anchor.worldUuid, anchor.x, anchor.y, anchor.z);
        ASSIGNMENTS.put(stableCreatureId, key);
        saveToDisk(stableCreatureId, key);
    }

    /**
     * Returns the assigned {@link AnchorManager.AnchorPos} for the creature,
     * or {@code null} if unassigned or if the anchor no longer exists.
     */
    public static AnchorManager.AnchorPos getAnchorPos(UUID stableCreatureId) {
        AnchorKey key = ASSIGNMENTS.get(stableCreatureId);
        if (key == null) return null;
        return AnchorManager.getAnchorAt(key.worldUuid(), key.x(), key.y(), key.z());
    }

    /**
     * Returns the display name of the assigned anchor, or {@code "Unassigned"} if
     * no assignment exists or the anchor has since been broken.
     */
    public static String getAnchorName(UUID stableCreatureId) {
        AnchorManager.AnchorPos pos = getAnchorPos(stableCreatureId);
        return pos != null ? pos.name : "Unassigned";
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(UUID stableId, AnchorKey key) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(stableId + ".txt"),
                    key.worldUuid() + "\n"
                    + key.x()       + "\n"
                    + key.y()       + "\n"
                    + key.z());
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureAnchorRegistry: failed to save %s — %s", stableId, e.getMessage());
        }
    }

    private static void loadAll() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(CreatureAnchorRegistry::loadFile);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureAnchorRegistry: failed to list data dir — %s", e.getMessage());
        }
    }

    private static void loadFile(Path file) {
        try {
            UUID     stableId = UUID.fromString(file.getFileName().toString().replace(".txt", ""));
            String[] lines    = Files.readString(file).split("\n", -1);
            if (lines.length < 4) return;
            UUID worldUuid = UUID.fromString(lines[0].trim());
            int  x         = Integer.parseInt(lines[1].trim());
            int  y         = Integer.parseInt(lines[2].trim());
            int  z         = Integer.parseInt(lines[3].trim());
            ASSIGNMENTS.put(stableId, new AnchorKey(worldUuid, x, y, z));
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureAnchorRegistry: failed to load %s — %s", file.getFileName(), e.getMessage());
        }
    }
}
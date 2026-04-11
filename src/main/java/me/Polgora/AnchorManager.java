package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all placed Menagerie Anchor blocks and provides radius checks.
 *
 * <p>Anchors are persisted to disk so they survive server restarts.
 * Multiple anchors can exist per world; a position is considered "in the Menagerie"
 * if it falls within {@link #RADIUS} blocks of any anchor.
 *
 * <p>Each anchor records its owner (UUID + username at placement time) and a
 * player-supplied display name (default {@code "Anchor"}).
 *
 * <p>Disk format ({@code anchors/<key>.txt}):
 * <pre>
 * worldUuid
 * x
 * y
 * z
 * ownerUuid   (or "null")
 * ownerName   (or "")
 * name
 * </pre>
 * Files written before owner/name tracking (lines 5–7 absent) load with
 * {@code ownerUuid = null}, {@code ownerName = ""}, {@code name = "Anchor"}.
 */
public class AnchorManager {

    public static final double RADIUS = 8.0;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── AnchorPos ─────────────────────────────────────────────────────────────

    public static class AnchorPos {
        public final UUID   worldUuid;
        public final int    x, y, z;
        /** UUID of the player who placed this anchor. May be null for legacy records. */
        public final UUID   ownerUuid;
        /** Username of the player who placed this anchor (snapshot at placement time). */
        public final String ownerName;
        /** Player-supplied display name for this anchor. Mutable; persisted on change. */
        public volatile String name;

        public AnchorPos(UUID worldUuid, int x, int y, int z,
                         UUID ownerUuid, String ownerName, String name) {
            this.worldUuid = worldUuid;
            this.x         = x;
            this.y         = y;
            this.z         = z;
            this.ownerUuid = ownerUuid;
            this.ownerName = ownerName != null ? ownerName : "";
            this.name      = (name != null && !name.isBlank()) ? name : "Anchor";
        }

        /** Whether the given world position falls within this anchor's radius. */
        public boolean contains(UUID world, double px, double py, double pz) {
            if (!worldUuid.equals(world)) return false;
            double dx = px - (x + 0.5);
            double dy = py - (y + 0.5);
            double dz = pz - (z + 0.5);
            return Math.sqrt(dx * dx + dy * dy + dz * dz) <= RADIUS;
        }

        /** Distance (in blocks) from this anchor to the given world position. */
        public double distanceTo(UUID world, double px, double py, double pz) {
            if (!worldUuid.equals(world)) return Double.MAX_VALUE;
            double dx = px - (x + 0.5);
            double dy = py - (y + 0.5);
            double dz = pz - (z + 0.5);
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof AnchorPos a)) return false;
            return x == a.x && y == a.y && z == a.z && worldUuid.equals(a.worldUuid);
        }

        @Override
        public int hashCode() {
            return worldUuid.hashCode() * 31 * 31 * 31 + x * 31 * 31 + y * 31 + z;
        }

        @Override
        public String toString() {
            return worldUuid + "_" + x + "_" + y + "_" + z;
        }
    }

    // ── Internal state ────────────────────────────────────────────────────────

    private static final Set<AnchorPos> ANCHORS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadFromDisk();
    }

    // ── Add / Remove ──────────────────────────────────────────────────────────

    public static void addAnchor(UUID worldUuid, int x, int y, int z,
                                  UUID ownerUuid, String ownerName) {
        AnchorPos pos = new AnchorPos(worldUuid, x, y, z, ownerUuid, ownerName, "Anchor");
        ANCHORS.add(pos);
        saveToDisk(pos);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "AnchorManager: added anchor at %s (owner: %s)", pos, ownerName);
    }

    public static void removeAnchor(UUID worldUuid, int x, int y, int z) {
        AnchorPos pos = new AnchorPos(worldUuid, x, y, z, null, null, null);
        ANCHORS.remove(pos);
        deleteFromDisk(pos);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "AnchorManager: removed anchor at %s", pos);
    }

    // ── Name update ───────────────────────────────────────────────────────────

    /**
     * Updates the display name of the anchor at the given position and persists the change.
     * No-op if no anchor exists at that position.
     */
    public static void setName(UUID worldUuid, int x, int y, int z, String name) {
        AnchorPos anchor = getAnchorAt(worldUuid, x, y, z);
        if (anchor == null) return;
        anchor.name = (name != null && !name.isBlank()) ? name.trim() : "Anchor";
        saveToDisk(anchor);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Returns the anchor at exactly the given block position, or {@code null}. */
    public static AnchorPos getAnchorAt(UUID worldUuid, int x, int y, int z) {
        for (AnchorPos anchor : ANCHORS) {
            if (anchor.worldUuid.equals(worldUuid)
                    && anchor.x == x && anchor.y == y && anchor.z == z) {
                return anchor;
            }
        }
        return null;
    }

    /** Returns all anchors owned by the given player. */
    public static List<AnchorPos> getAnchorsOwnedBy(UUID ownerUuid) {
        List<AnchorPos> result = new ArrayList<>();
        for (AnchorPos anchor : ANCHORS) {
            if (ownerUuid.equals(anchor.ownerUuid)) result.add(anchor);
        }
        return result;
    }

    /**
     * Returns all anchors owned by the given player that are within {@code range} blocks
     * of the given world position.
     */
    public static List<AnchorPos> getAnchorsOwnedByWithinRange(UUID ownerUuid,
                                                                UUID worldUuid,
                                                                double px, double py, double pz,
                                                                double range) {
        List<AnchorPos> result = new ArrayList<>();
        for (AnchorPos anchor : ANCHORS) {
            if (ownerUuid.equals(anchor.ownerUuid)
                    && anchor.distanceTo(worldUuid, px, py, pz) <= range) {
                result.add(anchor);
            }
        }
        return result;
    }

    /**
     * Returns true if the given world position is within the radius of any placed anchor.
     */
    public static boolean isWithinAnyAnchor(UUID worldUuid, double x, double y, double z) {
        for (AnchorPos anchor : ANCHORS) {
            if (anchor.contains(worldUuid, x, y, z)) return true;
        }
        return false;
    }

    /** Returns a read-only view of all tracked anchors. */
    public static Set<AnchorPos> getAnchors() {
        return Collections.unmodifiableSet(ANCHORS);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(AnchorPos pos) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(fileNameFor(pos)),
                    pos.worldUuid + "\n"
                    + pos.x + "\n"
                    + pos.y + "\n"
                    + pos.z + "\n"
                    + (pos.ownerUuid != null ? pos.ownerUuid : "null") + "\n"
                    + pos.ownerName + "\n"
                    + pos.name);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "AnchorManager: failed to save %s — %s", pos, e.getMessage());
        }
    }

    private static void deleteFromDisk(AnchorPos pos) {
        if (dataDir == null) return;
        try { Files.deleteIfExists(dataDir.resolve(fileNameFor(pos))); } catch (IOException ignored) {}
    }

    private static void loadFromDisk() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(AnchorManager::loadFile);
        } catch (IOException ignored) {}
    }

    private static void loadFile(Path file) {
        try {
            String[] lines = Files.readString(file).split("\n", -1);
            if (lines.length < 4) return;

            UUID worldUuid = UUID.fromString(lines[0].trim());
            int  x         = Integer.parseInt(lines[1].trim());
            int  y         = Integer.parseInt(lines[2].trim());
            int  z         = Integer.parseInt(lines[3].trim());

            // Lines 5–7 are optional for backwards compatibility
            UUID   ownerUuid  = null;
            String ownerName  = "";
            String name       = "Anchor";

            if (lines.length >= 5) {
                String ownerStr = lines[4].trim();
                ownerUuid = "null".equals(ownerStr) ? null : UUID.fromString(ownerStr);
            }
            if (lines.length >= 6) {
                ownerName = lines[5].trim();
            }
            if (lines.length >= 7) {
                String n = lines[6].trim();
                name = n.isEmpty() ? "Anchor" : n;
            }

            ANCHORS.add(new AnchorPos(worldUuid, x, y, z, ownerUuid, ownerName, name));
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "AnchorManager: failed to load %s — %s", file.getFileName(), e.getMessage());
        }
    }

    private static String fileNameFor(AnchorPos pos) {
        return pos.toString().replace(':', '_') + ".txt";
    }
}
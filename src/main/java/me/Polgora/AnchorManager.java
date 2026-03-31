package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks all placed Menagerie Anchor blocks and provides radius checks.
 *
 * <p>Anchors are persisted to disk so they survive server restarts.
 * Multiple anchors can exist per world; a position is considered "in the Menagerie"
 * if it falls within {@link #RADIUS} blocks of any anchor.
 */
public class AnchorManager {

    public static final double RADIUS = 16.0;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── AnchorPos ─────────────────────────────────────────────────────────────

    public static class AnchorPos {
        public final UUID worldUuid;
        public final int  x, y, z;

        public AnchorPos(UUID worldUuid, int x, int y, int z) {
            this.worldUuid = worldUuid;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /** Whether the given world position falls within this anchor's radius. */
        public boolean contains(UUID world, double px, double py, double pz) {
            if (!worldUuid.equals(world)) return false;
            double dx = px - (x + 0.5);
            double dy = py - (y + 0.5);
            double dz = pz - (z + 0.5);
            return Math.sqrt(dx * dx + dy * dy + dz * dz) <= RADIUS;
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

    // ── Add / Remove (game thread) ────────────────────────────────────────────

    public static void addAnchor(UUID worldUuid, int x, int y, int z) {
        AnchorPos pos = new AnchorPos(worldUuid, x, y, z);
        ANCHORS.add(pos);
        saveToDisk(pos);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "AnchorManager: added anchor at %s", pos);
    }

    public static void removeAnchor(UUID worldUuid, int x, int y, int z) {
        AnchorPos pos = new AnchorPos(worldUuid, x, y, z);
        ANCHORS.remove(pos);
        deleteFromDisk(pos);
        LOGGER.at(java.util.logging.Level.INFO).log(
                "AnchorManager: removed anchor at %s", pos);
    }

    // ── Radius query ──────────────────────────────────────────────────────────

    /**
     * Returns true if the given world position is within the radius of any placed anchor.
     */
    public static boolean isWithinAnyAnchor(UUID worldUuid, double x, double y, double z) {
        for (AnchorPos anchor : ANCHORS) {
            if (anchor.contains(worldUuid, x, y, z)) return true;
        }
        return false;
    }

    /**
     * Returns a read-only view of all tracked anchors.
     */
    public static Set<AnchorPos> getAnchors() {
        return Collections.unmodifiableSet(ANCHORS);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(AnchorPos pos) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(fileNameFor(pos)),
                    pos.worldUuid + "\n" + pos.x + "\n" + pos.y + "\n" + pos.z);
        } catch (IOException ignored) {}
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
            String[] lines = Files.readString(file).trim().split("\n");
            if (lines.length < 4) return;
            UUID worldUuid = UUID.fromString(lines[0].trim());
            int  x         = Integer.parseInt(lines[1].trim());
            int  y         = Integer.parseInt(lines[2].trim());
            int  z         = Integer.parseInt(lines[3].trim());
            ANCHORS.add(new AnchorPos(worldUuid, x, y, z));
        } catch (Exception ignored) {}
    }

    private static String fileNameFor(AnchorPos pos) {
        return pos.toString().replace(':', '_') + ".txt";
    }
}
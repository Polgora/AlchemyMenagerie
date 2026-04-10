package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-creature bond points with disk persistence.
 *
 * <p>Bond advances through four tiers as the creature is fed:
 * <ul>
 *   <li>Wild:   0 – 99 pts
 *   <li>Tame:   100 – 299 pts
 *   <li>Bonded: 300 – 699 pts
 *   <li>Devoted: 700+ pts (ceiling)
 * </ul>
 *
 * <p>Each successful feeding adds {@link #POINTS_PER_FEED} points.
 * Points never drop below the floor of the creature's current tier
 * (i.e. a Tame creature will never erode back into Wild).
 *
 * <p>Erosion runs via {@link me.Polgora.events.CreatureBondErosionSystem}:
 * after {@link #GRACE_PERIOD_MS} of no feeding, 1 point is deducted
 * every {@link #EROSION_INTERVAL_MS} until the tier floor is reached.
 *
 * <p>Disk format ({@code bonds/<entityUuid>.txt}):
 * <pre>
 * points
 * lastFedMs
 * </pre>
 */
public class CreatureBondManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    // ── Bond tier ─────────────────────────────────────────────────────────────

    public enum BondTier {
        WILD(   0, "Wild",    0,   99),
        TAME(   1, "Tame",  100,  299),
        BONDED( 2, "Bonded",300,  699),
        DEVOTED(3, "Devoted",700, Integer.MAX_VALUE);

        public final int    level;
        public final String displayName;
        public final int    floor;
        public final int    ceiling;

        BondTier(int level, String displayName, int floor, int ceiling) {
            this.level       = level;
            this.displayName = displayName;
            this.floor       = floor;
            this.ceiling     = ceiling;
        }

        /** Returns the tier that contains {@code points}. */
        public static BondTier fromPoints(int points) {
            for (BondTier t : values()) {
                if (points <= t.ceiling) return t;
            }
            return DEVOTED;
        }

        /** Returns the minimum points required to reach the tier above this one,
         *  or {@code -1} if this is Devoted (no tier above). */
        public int nextThreshold() {
            BondTier[] all = values();
            if (this.level + 1 >= all.length) return -1;
            return all[this.level + 1].floor;
        }
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Points awarded per successful feeding. */
    public static final int POINTS_PER_FEED = 5;

    /** Milliseconds without feeding before erosion begins (1 hour of online time). */
    public static final long GRACE_PERIOD_MS = 60L * 60L * 1000L;

    /** How often the erosion system ticks per creature (2 minutes). */
    public static final long EROSION_INTERVAL_MS = 2L * 60L * 1000L;

    // ── Internal state ────────────────────────────────────────────────────────

    private static class BondData {
        int  points;
        long lastFedMs; // epoch ms; 0 means never fed

        BondData(int points, long lastFedMs) {
            this.points    = points;
            this.lastFedMs = lastFedMs;
        }
    }

    private static final Map<UUID, BondData> BONDS = new ConcurrentHashMap<>();
    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadAll();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the creature's current bond tier. */
    public static BondTier getTier(UUID creatureUuid) {
        return BondTier.fromPoints(getOrCreate(creatureUuid).points);
    }

    /** Returns the creature's raw bond point total. */
    public static int getPoints(UUID creatureUuid) {
        return getOrCreate(creatureUuid).points;
    }

    /**
     * Awards {@link #POINTS_PER_FEED} bond points to the creature and
     * records the current time as the last-fed timestamp.
     * Must be called after {@link CreatureFeedManager#recordFeed}.
     */
    public static void recordFeed(UUID creatureUuid) {
        BondData data = getOrCreate(creatureUuid);
        data.points    = Math.min(data.points + POINTS_PER_FEED, Integer.MAX_VALUE);
        data.lastFedMs = System.currentTimeMillis();
        saveToDisk(creatureUuid, data);
    }

    /**
     * Sets the creature's bond points directly to the floor of the given tier.
     * Intended for admin use via {@link me.Polgora.commands.BondSetCommand}.
     */
    public static void setTier(UUID creatureUuid, BondTier tier) {
        BondData data = getOrCreate(creatureUuid);
        data.points    = tier.floor;
        data.lastFedMs = System.currentTimeMillis();
        saveToDisk(creatureUuid, data);
    }

    /**
     * Attempts to deduct 1 bond point due to erosion.
     * Called by {@link me.Polgora.events.CreatureBondErosionSystem} on a fixed interval.
     * No-op if the grace period hasn't elapsed or the creature is already at the tier floor.
     */
    public static void tryErode(UUID creatureUuid) {
        BondData data = BONDS.get(creatureUuid);
        if (data == null) return; // creature has never been fed — nothing to erode

        // Grace period: don't erode until 1 hour after the last feeding
        if (data.lastFedMs == 0L) return;
        long now = System.currentTimeMillis();
        if (now - data.lastFedMs < GRACE_PERIOD_MS) return;

        // Stop at the floor of the current tier
        int floor = BondTier.fromPoints(data.points).floor;
        if (data.points <= floor) return;

        data.points--;
        saveToDisk(creatureUuid, data);
    }

    /**
     * Returns a human-readable bond status string for display in the UI, e.g.:
     * {@code "Bond: Tame (175 / 300 pts)"} or {@code "Bond: Devoted (Max)"}.
     */
    public static String formatBondStatus(UUID creatureUuid) {
        BondData data  = getOrCreate(creatureUuid);
        BondTier tier  = BondTier.fromPoints(data.points);
        int      next  = tier.nextThreshold();
        if (next < 0) return "Bond: Devoted (Max)";
        return "Bond: " + tier.displayName + " (" + data.points + " / " + next + " pts)";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BondData getOrCreate(UUID uuid) {
        return BONDS.computeIfAbsent(uuid, k -> new BondData(0, 0L));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(UUID uuid, BondData data) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(uuid + ".txt"),
                    data.points + "\n" + data.lastFedMs);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureBondManager: failed to save %s — %s", uuid, e.getMessage());
        }
    }

    private static void loadAll() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(CreatureBondManager::loadFile);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureBondManager: failed to list data dir — %s", e.getMessage());
        }
    }

    private static void loadFile(Path file) {
        try {
            UUID     uuid      = UUID.fromString(file.getFileName().toString().replace(".txt", ""));
            String[] lines     = Files.readString(file).split("\n", -1);
            if (lines.length < 2) return;
            int  points    = Integer.parseInt(lines[0].trim());
            long lastFedMs = Long.parseLong(lines[1].trim());
            BONDS.put(uuid, new BondData(points, lastFedMs));
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureBondManager: failed to load %s — %s", file.getFileName(), e.getMessage());
        }
    }
}
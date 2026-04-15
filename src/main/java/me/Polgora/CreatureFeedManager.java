package me.Polgora;

import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-creature feeding cooldowns with disk persistence.
 *
 * <p>Each creature's last-fed timestamp is written to {@code feeds/<entityUuid>.txt}
 * so cooldowns survive server restarts. Wall-clock time is used intentionally —
 * real time counts toward the cooldown even while the server is offline.
 */
public class CreatureFeedManager {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** How long a player must wait between feedings of the same creature. */
    public static final long FEED_COOLDOWN_MS = 10L * 60L * 1000L; // 10 minutes

    /** Maps creature entity UUID → timestamp of the last successful feeding. */
    private static final Map<UUID, Long> LAST_FED = new ConcurrentHashMap<>();

    private static Path dataDir;

    // ── Init ──────────────────────────────────────────────────────────────────

    public static void init(Path dir) {
        dataDir = dir;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        loadAll();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns {@code true} if the creature has never been fed or the cooldown has elapsed. */
    public static boolean canFeed(UUID creatureUuid) {
        Long last = LAST_FED.get(creatureUuid);
        return last == null || System.currentTimeMillis() - last >= FEED_COOLDOWN_MS;
    }

    /** Records a successful feeding at the current time and persists it to disk. */
    public static void recordFeed(UUID creatureUuid) {
        long now = System.currentTimeMillis();
        LAST_FED.put(creatureUuid, now);
        saveToDisk(creatureUuid, now);
    }

    /** Returns the milliseconds remaining on the cooldown, or 0 if the creature can be fed now. */
    public static long getRemainingMs(UUID creatureUuid) {
        Long last = LAST_FED.get(creatureUuid);
        if (last == null) return 0L;
        return Math.max(0L, FEED_COOLDOWN_MS - (System.currentTimeMillis() - last));
    }

    /**
     * Returns a human-readable cooldown string, e.g.
     * {@code "Ready to feed!"} or {@code "Next feed in: 4 min 32 sec"}.
     */
    public static String formatCooldown(UUID creatureUuid) {
        long ms = getRemainingMs(creatureUuid);
        if (ms <= 0) return "Ready to feed!";
        long secs = ms / 1000;
        long mins = secs / 60;
        secs %= 60;
        return "Next feed in: " + (mins > 0 ? mins + " min " : "") + secs + " sec";
    }

    /**
     * Removes feed cooldown data for a creature and deletes its file from disk.
     * Called when the creature dies.
     */
    public static void remove(UUID creatureUuid) {
        LAST_FED.remove(creatureUuid);
        if (dataDir == null) return;
        try { Files.deleteIfExists(dataDir.resolve(creatureUuid + ".txt")); } catch (IOException ignored) {}
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private static void saveToDisk(UUID creatureUuid, long lastFedMs) {
        if (dataDir == null) return;
        try {
            Files.writeString(dataDir.resolve(creatureUuid + ".txt"), String.valueOf(lastFedMs));
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureFeedManager: failed to save %s — %s", creatureUuid, e.getMessage());
        }
    }

    private static void loadAll() {
        if (dataDir == null || !Files.exists(dataDir)) return;
        try {
            Files.list(dataDir)
                 .filter(p -> p.getFileName().toString().endsWith(".txt"))
                 .forEach(CreatureFeedManager::loadFile);
        } catch (IOException e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureFeedManager: failed to list data dir — %s", e.getMessage());
        }
    }

    private static void loadFile(Path file) {
        try {
            UUID uuid      = UUID.fromString(file.getFileName().toString().replace(".txt", ""));
            long lastFedMs = Long.parseLong(Files.readString(file).trim());
            // Only keep the record if the cooldown hasn't already fully elapsed —
            // expired entries are left out so they get cleaned up on next feed.
            if (System.currentTimeMillis() - lastFedMs < FEED_COOLDOWN_MS) {
                LAST_FED.put(uuid, lastFedMs);
            } else {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            LOGGER.at(java.util.logging.Level.WARNING).log(
                    "CreatureFeedManager: failed to load %s — %s", file.getFileName(), e.getMessage());
        }
    }
}
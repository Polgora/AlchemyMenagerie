package me.Polgora;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-creature feeding cooldowns.
 *
 * <p>State is in-memory only; cooldowns reset on server restart, which is acceptable
 * since 10 minutes is shorter than any typical restart window.
 */
public class CreatureFeedManager {

    /** How long a player must wait between feedings of the same creature. */
    public static final long FEED_COOLDOWN_MS = 10L * 60L * 1000L; // 10 minutes

    /** Maps creature entity UUID → timestamp of the last successful feeding. */
    private static final Map<UUID, Long> LAST_FED = new ConcurrentHashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns {@code true} if the creature has never been fed or the cooldown has elapsed. */
    public static boolean canFeed(UUID creatureUuid) {
        Long last = LAST_FED.get(creatureUuid);
        return last == null || System.currentTimeMillis() - last >= FEED_COOLDOWN_MS;
    }

    /** Records a successful feeding at the current time. */
    public static void recordFeed(UUID creatureUuid) {
        LAST_FED.put(creatureUuid, System.currentTimeMillis());
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
}
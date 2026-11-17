package me.Polgora;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Remembers which player threw which item, so a creature that picks a thrown item up can tell
 * whose food it is. Item entities carry no thrower, so this is a best-effort match by item ID
 * (most recent throw first); records expire after {@link #MAX_AGE_MS}.
 */
public final class ThrownFoodTracker {

    private static final long MAX_AGE_MS = 90_000L;

    private static final class Throw {
        final UUID thrower;
        final String itemId;
        final long timeMs;
        int remaining;
        Throw(UUID thrower, String itemId, int quantity) {
            this.thrower = thrower;
            this.itemId = itemId;
            this.timeMs = System.currentTimeMillis();
            this.remaining = quantity;
        }
    }

    private static final List<Throw> THROWS = new ArrayList<>();

    private ThrownFoodTracker() {}

    public static synchronized void record(UUID thrower, String itemId, int quantity) {
        prune();
        THROWS.add(new Throw(thrower, itemId, Math.max(1, quantity)));
    }

    /** Claims one thrown item of this ID; returns the thrower, or null if no recent throw is known. */
    public static synchronized UUID consume(String itemId) {
        prune();
        for (int i = THROWS.size() - 1; i >= 0; i--) {
            Throw t = THROWS.get(i);
            if (!t.itemId.equals(itemId)) continue;
            if (--t.remaining <= 0) THROWS.remove(i);
            return t.thrower;
        }
        return null;
    }

    private static void prune() {
        long now = System.currentTimeMillis();
        THROWS.removeIf(t -> now - t.timeMs > MAX_AGE_MS);
    }
}

package me.Polgora;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Food items a creature refused and spat out. Each stays locked (pickup delay) for
 * {@link #LOCK_SECONDS} so creatures can't re-eat it, but {@code SpatFoodUnlockSystem}
 * unlocks it the moment a player comes near so players can grab it right away.
 */
public final class SpatFoodRegistry {

    public static final float LOCK_SECONDS = 420f; // 7 minutes

    private static final Map<UUID, Long> EXPIRY_MS = new ConcurrentHashMap<>();

    private SpatFoodRegistry() {}

    public static void add(UUID itemEntityUuid) {
        EXPIRY_MS.put(itemEntityUuid, System.currentTimeMillis() + (long) (LOCK_SECONDS * 1000));
    }

    public static boolean isEmpty() { return EXPIRY_MS.isEmpty(); }

    public static boolean contains(UUID itemEntityUuid) { return EXPIRY_MS.containsKey(itemEntityUuid); }

    public static void remove(UUID itemEntityUuid) { EXPIRY_MS.remove(itemEntityUuid); }

    /** Drops expired entries (the item's own delay has run out by then). */
    public static void prune() {
        long now = System.currentTimeMillis();
        EXPIRY_MS.values().removeIf(t -> t < now);
    }
}

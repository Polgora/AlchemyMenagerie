package me.Polgora;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Facade for creature custom-name storage.
 *
 * <p>Names are stored inside the existing creature record files in the
 * {@code creatures/} directory via {@link CreatureRegistry}, so no
 * separate data directory is needed.
 */
public class CreatureNameManager {

    private CreatureNameManager() {}

    /** No-op — name data is loaded as part of {@link CreatureRegistry#init}. */
    public static void init(Path dir) {}

    /** Returns the custom name for the creature, or {@code null} if none has been set. */
    public static String getName(UUID creatureUuid) {
        return CreatureRegistry.getCreatureName(creatureUuid);
    }

    /** Sets and persists the custom name for the creature. */
    public static void setName(UUID creatureUuid, String name) {
        CreatureRegistry.setCreatureName(creatureUuid, name);
    }

    /**
     * No-op — the name is embedded in the creature record file, which is
     * deleted in full by {@link CreatureRegistry#removeByEntityUuid}.
     */
    public static void remove(UUID creatureUuid) {}
}
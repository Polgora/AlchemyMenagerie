package me.Polgora.events;

import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import me.Polgora.CreatureAnchorRegistry;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureFeedManager;
import me.Polgora.CreatureNameManager;
import me.Polgora.CreatureRegistry;

import java.util.UUID;

/**
 * Listens for {@link EntityRemoveEvent} and cleans up all persistent data for
 * a Menagerie creature when it dies.
 *
 * <p>Deleted on creature death:
 * <ul>
 *   <li>{@code creatures/<id>.txt} — via {@link CreatureRegistry#removeByEntityUuid}
 *   <li>{@code bonds/<entityUuid>.txt} — via {@link CreatureBondManager#remove}
 *   <li>{@code feeds/<entityUuid>.txt} — via {@link CreatureFeedManager#remove}
 *   <li>{@code creature_anchors/<stableId>.txt} — via {@link CreatureAnchorRegistry#remove}
 * </ul>
 *
 * <p>During server shutdown {@link EntityRemoveEvent} fires for every entity as
 * worlds are torn down. {@link CreatureRegistry#isShuttingDown()} guards against
 * treating those removals as deaths — call {@link CreatureRegistry#markShuttingDown()}
 * from {@code Main.shutdown()} before the world is unloaded.
 *
 * <p>Register via:
 * {@code getEventRegistry().registerGlobal(EntityRemoveEvent.class, CreatureDeathEvent::onEntityRemove)}
 */
public class CreatureDeathEvent {

    @SuppressWarnings("removal")
    public static void onEntityRemove(EntityRemoveEvent event) {
        // Ignore mass-removal during server shutdown
        if (CreatureRegistry.isShuttingDown()) return;

        UUID entityUuid = event.getEntity().getUuid();
        if (entityUuid == null) return;

        // Get the stable ID before removing (removeByEntityUuid clears the mapping)
        UUID stableId = CreatureRegistry.getStableId(entityUuid);

        // Not a tracked Menagerie creature — nothing to do
        if (stableId == null) return;

        CreatureRegistry.removeByEntityUuid(entityUuid);
        CreatureBondManager.remove(entityUuid);
        CreatureFeedManager.remove(entityUuid);
        CreatureNameManager.remove(entityUuid);
        CreatureAnchorRegistry.remove(stableId);
    }
}
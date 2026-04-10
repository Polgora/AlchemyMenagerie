package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.CreatureBondManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically erodes bond points for Menagerie creatures that haven't been fed.
 *
 * <p>Runs as an ECS ticking system. Each creature is checked independently;
 * the actual erosion logic in {@link CreatureBondManager#tryErode} fires once
 * every {@link CreatureBondManager#EROSION_INTERVAL_MS} per creature, and only
 * after the {@link CreatureBondManager#GRACE_PERIOD_MS} grace period has elapsed
 * since the last feeding.
 *
 * <p>Because this system only runs while the server is online, offline time does
 * not count toward the grace period or erosion rate.
 */
public class CreatureBondErosionSystem extends EntityTickingSystem<EntityStore> {

    /** Per-creature timestamp of the last erosion tick. In-memory only. */
    private final Map<UUID, Long> lastErosionTick = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {

        // Only process NPC entities (creatures)
        NPCEntity npc = chunk.getComponent(index, NPCEntity.getComponentType());
        if (npc == null) return;

        // Resolve entity UUID
        Ref<EntityStore> ref      = chunk.getReferenceTo(index);
        UUIDComponent    uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID entityUuid = uuidComp.getUuid();
        if (entityUuid == null) return;

        // Time-gate: erode at most once per EROSION_INTERVAL_MS per creature
        long now  = System.currentTimeMillis();
        Long last = lastErosionTick.get(entityUuid);
        if (last != null && now - last < CreatureBondManager.EROSION_INTERVAL_MS) return;
        lastErosionTick.put(entityUuid, now);

        // Delegate erosion logic to the bond manager
        CreatureBondManager.tryErode(entityUuid);
    }
}
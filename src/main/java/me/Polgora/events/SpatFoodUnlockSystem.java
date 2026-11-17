package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.SpatFoodRegistry;
import org.joml.Vector3d;

import java.util.UUID;

/** Unlocks creature-spat food for pickup as soon as any player is close to it. */
public class SpatFoodUnlockSystem extends EntityTickingSystem<EntityStore> {

    private static final double UNLOCK_RANGE_SQ = 3.0 * 3.0;
    private long lastPruneMs = 0;

    @Override
    public Query<EntityStore> getQuery() { return Query.any(); }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {
        if (SpatFoodRegistry.isEmpty()) return;

        ItemComponent item = chunk.getComponent(index, ItemComponent.getComponentType());
        if (item == null) return;
        UUIDComponent uuidComp = chunk.getComponent(index, UUIDComponent.getComponentType());
        if (uuidComp == null) return;
        UUID id = uuidComp.getUuid();
        if (!SpatFoodRegistry.contains(id)) return;

        TransformComponent tc = chunk.getComponent(index, TransformComponent.getComponentType());
        if (tc == null) return;
        Vector3d pos = tc.getPosition();

        for (PlayerRef p : store.getExternalData().getWorld().getPlayerRefs()) {
            var t = p == null ? null : p.getTransform();
            if (t == null || t.getPosition() == null) continue;
            if (t.getPosition().distanceSquared(pos) <= UNLOCK_RANGE_SQ) {
                item.setPickupDelay(0f);
                SpatFoodRegistry.remove(id);
                return;
            }
        }

        long now = System.currentTimeMillis();
        if (now - lastPruneMs > 60_000) {
            lastPruneMs = now;
            SpatFoodRegistry.prune();
        }
    }
}

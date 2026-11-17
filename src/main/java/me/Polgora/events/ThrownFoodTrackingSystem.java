package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.ThrownFoodTracker;

/** Records which player dropped/threw which item, for {@link ThrownFoodTracker}. */
public class ThrownFoodTrackingSystem extends EntityEventSystem<EntityStore, DropItemEvent.Drop> {

    public ThrownFoodTrackingSystem() {
        super(DropItemEvent.Drop.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, DropItemEvent.Drop event) {
        PlayerRef playerRef = chunk.getComponent(i, PlayerRef.getComponentType());
        if (playerRef == null) return;
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) return;
        ThrownFoodTracker.record(playerRef.getUuid(), stack.getItemId(), stack.getQuantity());
    }
}

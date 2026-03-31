package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.AnchorManager;

public class MenagerieAnchorPlaceEvent extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public MenagerieAnchorPlaceEvent() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, PlaceBlockEvent event) {
        var heldItem = event.getItemInHand();
        if (heldItem == null || !"Menagerie_Anchor".equals(heldItem.getItemId())) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);

        PlayerRef playerRef = cmd.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        var pos = event.getTargetBlock();
        AnchorManager.addAnchor(playerRef.getWorldUuid(), pos.x, pos.y, pos.z);
    }
}
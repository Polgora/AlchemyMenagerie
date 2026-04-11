package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.AnchorManager;
import me.Polgora.pages.AnchorInfoPage;

/**
 * Opens {@link AnchorInfoPage} when a player presses F on a Menagerie Anchor block.
 */
public class MenagerieAnchorUseEvent extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    public MenagerieAnchorUseEvent() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, UseBlockEvent.Pre event) {
        if (!"Menagerie_Anchor".equals(event.getBlockType().getId())) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if (player.getPageManager().getCustomPage() != null) return;

        PlayerRef playerRef = cmd.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        var pos = event.getTargetBlock();
        AnchorManager.AnchorPos anchor = AnchorManager.getAnchorAt(
                playerRef.getWorldUuid(), pos.x, pos.y, pos.z);
        if (anchor == null) return;

        player.getPageManager().openCustomPage(ref, store,
                new AnchorInfoPage(playerRef, anchor));
        event.setCancelled(true);
    }
}
package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.NestManager;
import me.Polgora.NestManager.NestState;

public class MenagerieNestBreakEvent extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public MenagerieNestBreakEvent() {
        super(BreakBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, BreakBlockEvent event) {
        if (!"Menagerie_Nest".equals(event.getBlockType().getId())) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        PlayerRef playerRef = cmd.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        var    pos = event.getTargetBlock();
        String key = NestManager.nestKey(playerRef.getWorldUuid(), pos.x, pos.y, pos.z);

        // Remove the nest record and cancel timer; then remove the display entity via cmd
        // so the removal is properly networked to clients.
        NestState state = NestManager.removeNest(key);
        if (state == null) return;
        NestManager.removeDisplayEntityViaCmd(state, cmd);

        // Return the egg to the player's inventory
        var addResult = player.getInventory()
                .getCombinedArmorHotbarUtilityStorage()
                .addItemStack(new ItemStack(state.eggItemId, 1));

        if (!addResult.succeeded()) {
            player.sendMessage(Message.raw("[Menagerie] Inventory full \u2014 egg dropped!"));
            // TODO: spawn a dropped egg entity at the nest position as a fallback
        }
        // The nest block item itself is returned by the game's normal block-break loot system.
    }
}
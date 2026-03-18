package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.CraftRecipeEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.PouchManager;

/**
 * Cancels Alchemy_Flask crafting before ingredients are consumed if the player
 * has no Flask Pouch, or if all of their Flask Pouches are full.
 */
public class FlaskCraftPreventionSystem extends EntityEventSystem<EntityStore, CraftRecipeEvent.Pre> {

    public FlaskCraftPreventionSystem() {
        super(CraftRecipeEvent.Pre.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    @SuppressWarnings("removal")
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, CraftRecipeEvent.Pre event) {
        CraftingRecipe recipe = event.getCraftedRecipe();
        if (recipe == null) return;

        var primary = recipe.getPrimaryOutput();
        if (primary == null || !primary.getItemId().equals("Alchemy_Flask")) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getUuid() == null) return;

        ItemContainer inventory = player.getInventory().getCombinedEverything();
        short capacity = inventory.getCapacity();
        boolean hasPouch = false;
        boolean hasRoom = false;

        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
            hasPouch = true;
            if (!PouchManager.isFull(stack)) {
                hasRoom = true;
                break;
            }
        }

        if (!hasPouch) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("You need a Flask Pouch to craft Alchemy Flasks."));
        } else if (!hasRoom) {
            event.setCancelled(true);
            player.sendMessage(Message.raw("Flask Pouch full! Make room in your pouch before crafting more flasks."));
        }
    }
}
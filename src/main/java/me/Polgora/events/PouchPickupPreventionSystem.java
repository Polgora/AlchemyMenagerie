package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.PouchManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents a player from picking up a Flask Pouch from the ground when they
 * already have one in their inventory. The item simply stays on the ground.
 *
 * <p>An "in-flight" set tracks players who have a pouch pickup already en route
 * to their inventory, so that if two pouches are walked over simultaneously the
 * second event (which fires before the first pouch reaches the inventory) is
 * still blocked. {@link InventorySwapEvent} clears the flag once the pouch
 * actually arrives.
 */
public class PouchPickupPreventionSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    /** Players who have a Flask Pouch pickup in progress but not yet in inventory. */
    static final Set<String> pouchPickupInFlight = ConcurrentHashMap.newKeySet();

    public PouchPickupPreventionSystem() {
        super(InteractivelyPickupItemEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, InteractivelyPickupItemEvent event) {
        if (!event.getItemStack().getItemId().equals(PouchManager.POUCH_ITEM_ID)) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || playerRef.getUuid() == null) return;
        String playerId = playerRef.getUuid().toString();

        // Block if the player already has a pouch in inventory OR one is already
        // on its way to their inventory (race condition with simultaneous pickups).
        if (pouchPickupInFlight.contains(playerId) || hasPouch(player)) {
            event.setCancelled(true);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(), Message.raw("You can only carry one Flask Pouch at a time."), NotificationStyle.Warning);
        } else {
            // Mark that a pouch is now en route; cleared by InventorySwapEvent on arrival.
            pouchPickupInFlight.add(playerId);
        }
    }

    private static boolean hasPouch(Player player) {
        ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) return true;
        }
        return false;
    }
}
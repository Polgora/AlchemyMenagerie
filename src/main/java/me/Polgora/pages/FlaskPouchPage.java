package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.PouchManager;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class FlaskPouchPage extends InteractiveCustomUIPage<FlaskPouchPage.PouchAction> {

    public static class PouchAction {
        public static final BuilderCodec<PouchAction> CODEC = BuilderCodec.builder(PouchAction.class, PouchAction::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot)
                .build();
        public String action;
        public String slot;
    }

    public FlaskPouchPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, PouchAction.CODEC);
    }

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        String playerId = playerRef.getUuid().toString();
        Player player = store.getComponent(ref, Player.getComponentType());

        commandBuilder.append("FlaskPouchUI.ui");

        List<ItemStack> pouchContents = PouchManager.getContents(playerId);
        populatePouchSlots(commandBuilder, eventBuilder, pouchContents);

        if (player != null) {
            List<ItemStack> inventoryFlasks = findFlasksInInventory(player);
            populateFlaskList(commandBuilder, eventBuilder, inventoryFlasks);
        }
    }

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PouchAction data) {
        if (data.action == null) return;
        String playerId = playerRef.getUuid().toString();

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        if ("take".equals(data.action) && data.slot != null) {
            int slotIndex;
            try {
                slotIndex = Integer.parseInt(data.slot);
            } catch (NumberFormatException e) {
                return;
            }
            ItemStack removed = PouchManager.removeFlask(playerId, slotIndex);
            if (removed != null) {
                player.getInventory().getCombinedEverything().addItemStack(removed);
            }
        } else if ("store".equals(data.action) && data.slot != null) {
            if (PouchManager.isFull(playerId)) return;
            // slot field holds the item ID to store
            String itemId = data.slot;
            ItemContainer inventory = player.getInventory().getCombinedEverything();
            short capacity = inventory.getCapacity();
            for (short s = 0; s < capacity; s++) {
                ItemStack stack = inventory.getItemStack(s);
                if (stack == null || stack.isEmpty()) continue;
                if (!stack.getItemId().equals(itemId)) continue;
                inventory.removeItemStackFromSlot(s, 1);
                PouchManager.addFlask(playerId, new ItemStack(itemId, 1));
                break;
            }
        }

        // Rebuild full UI state
        List<ItemStack> pouchContents = PouchManager.getContents(playerId);
        List<ItemStack> inventoryFlasks = findFlasksInInventory(player);

        UICommandBuilder update = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populatePouchSlots(update, events, pouchContents);

        update.clear("#FlaskList");
        populateFlaskList(update, events, inventoryFlasks);

        sendUpdate(update, events, false);
    }

    private static void populatePouchSlots(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, List<ItemStack> contents) {
        for (int i = 0; i < PouchManager.MAX_SLOTS; i++) {
            String selector = "#PouchSlot" + i;
            ItemGridSlot slot = new ItemGridSlot();
            if (i < contents.size()) {
                slot.setItemStack(contents.get(i));
                eventBuilder.addEventBinding(CustomUIEventBindingType.SlotClicking, selector,
                        EventData.of("Action", "take").append("Slot", String.valueOf(i)));
            }
            commandBuilder.set(selector + ".Slots", List.of(slot));
        }
    }

    private static void populateFlaskList(UICommandBuilder commandBuilder, UIEventBuilder eventBuilder, List<ItemStack> flasks) {
        for (int i = 0; i < flasks.size(); i++) {
            ItemStack flask = flasks.get(i);
            String entry = "#FlaskList[" + i + "]";

            commandBuilder.append("#FlaskList", "FlaskSlot.ui");
            commandBuilder.set(entry + " #FlaskIcon.ItemId", flask.getItemId());
            int qty = flask.getQuantity();
            commandBuilder.set(entry + " #QuantityLabel.Text", qty > 1 ? String.valueOf(qty) : "");
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, entry + " #FlaskButton",
                    EventData.of("Action", "store").append("Slot", flask.getItemId()));
        }
    }

    private static List<ItemStack> findFlasksInInventory(Player player) {
        ItemContainer inventory = player.getInventory().getCombinedEverything();
        short capacity = inventory.getCapacity();
        List<ItemStack> flasks = new ArrayList<>();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getItemId().startsWith(PouchManager.FLASK_ITEM_PREFIX)) {
                flasks.add(stack);
            }
        }
        return flasks;
    }
}
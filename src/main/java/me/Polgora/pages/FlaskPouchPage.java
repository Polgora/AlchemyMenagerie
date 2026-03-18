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

    /** The unique ID of the specific Flask Pouch this UI was opened for. */
    private final String pouchId;

    public FlaskPouchPage(PlayerRef playerRef, String pouchId) {
        super(playerRef, CustomPageLifetime.CanDismiss, PouchAction.CODEC);
        this.pouchId = pouchId;
    }

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());

        commandBuilder.append("FlaskPouchUI.ui");

        if (player != null) {
            ItemContainer inventory = player.getInventory().getCombinedEverything();
            ItemStack pouchStack = findPouch(inventory, pouchId);
            List<ItemStack> pouchContents = pouchStack != null ? PouchManager.getFlasks(pouchStack) : List.of();
            populatePouchSlots(commandBuilder, pouchContents);
            populateFlaskList(commandBuilder, eventBuilder, findFlasksInInventory(inventory));
        }
    }

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PouchAction data) {
        if (data.action == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemContainer inventory = player.getInventory().getCombinedEverything();

        if ("store".equals(data.action) && data.slot != null) {
            short pouchSlot = findPouchSlot(inventory, pouchId);
            if (pouchSlot < 0) return;
            ItemStack pouchStack = inventory.getItemStack(pouchSlot);
            if (PouchManager.isFull(pouchStack)) return;

            String itemId = data.slot;
            short capacity = inventory.getCapacity();
            for (short s = 0; s < capacity; s++) {
                ItemStack stack = inventory.getItemStack(s);
                if (stack == null || stack.isEmpty()) continue;
                if (!stack.getItemId().equals(itemId)) continue;
                inventory.removeItemStackFromSlot(s, 1);
                inventory.setItemStackForSlot(pouchSlot, PouchManager.withFlaskAdded(pouchStack, new ItemStack(itemId, 1)));
                break;
            }
        }

        // Rebuild UI state
        ItemStack updatedPouch = findPouch(inventory, pouchId);
        List<ItemStack> pouchContents = updatedPouch != null ? PouchManager.getFlasks(updatedPouch) : List.of();

        UICommandBuilder update = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populatePouchSlots(update, pouchContents);
        update.clear("#FlaskList");
        populateFlaskList(update, events, findFlasksInInventory(inventory));
        sendUpdate(update, events, false);
    }

    private static void populatePouchSlots(UICommandBuilder commandBuilder, List<ItemStack> contents) {
        for (int i = 0; i < PouchManager.MAX_SLOTS; i++) {
            String selector = "#PouchSlot" + i;
            ItemGridSlot slot = new ItemGridSlot();
            if (i < contents.size()) {
                slot.setItemStack(contents.get(i));
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

    /** Finds the Flask Pouch with the given pouchId in the inventory, or null. */
    private static ItemStack findPouch(ItemContainer inventory, String pouchId) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
            if (pouchId.equals(PouchManager.getPouchId(stack))) return stack;
        }
        return null;
    }

    /** Returns the slot index of the Flask Pouch with the given pouchId, or -1. */
    private static short findPouchSlot(ItemContainer inventory, String pouchId) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (!stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) continue;
            if (pouchId.equals(PouchManager.getPouchId(stack))) return s;
        }
        return -1;
    }

    private static List<ItemStack> findFlasksInInventory(ItemContainer inventory) {
        short capacity = inventory.getCapacity();
        List<ItemStack> flasks = new ArrayList<>();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack == null || stack.isEmpty()) continue;
            if (stack.getItemId().startsWith(PouchManager.FLASK_ITEM_PREFIX)) flasks.add(stack);
        }
        return flasks;
    }
}
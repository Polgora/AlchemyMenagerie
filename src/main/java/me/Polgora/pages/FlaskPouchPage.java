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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.PouchManager;
import me.Polgora.PouchManager.FlaskState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class FlaskPouchPage extends InteractiveCustomUIPage<FlaskPouchPage.PouchAction> {

    public static class PouchAction {
        public static final BuilderCodec<PouchAction> CODEC = BuilderCodec.builder(PouchAction.class, PouchAction::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                .append(new KeyedCodec<>("Slot", Codec.STRING), (d, v) -> d.slot = v, d -> d.slot).add()
                .build();
        public String action;
        public String slot;
    }

    /** The unique ID of the specific Flask Pouch this UI was opened for. */
    private final String pouchId;

    /** Which pouch slot the player has selected; -1 until they click one. */
    private int selectedFlask = -1;

    public FlaskPouchPage(PlayerRef playerRef, String pouchId) {
        super(playerRef, CustomPageLifetime.CanDismiss, PouchAction.CODEC);
        this.pouchId = pouchId;
    }

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());

        cmd.append("FlaskPouchUI.ui");

        if (player != null) {
            ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
            ItemStack pouchStack = findPouch(inventory, pouchId);
            List<FlaskState> pouchContents = pouchStack != null ? PouchManager.getFlaskStates(pouchStack) : List.of();
            populatePouchSlots(cmd, evt, pouchContents);
            populateDetailPanel(cmd, pouchContents);
            populateFlaskList(cmd, evt, findFlasksInInventory(inventory));
        }
    }

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PouchAction data) {
        if (data.action == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();

        switch (data.action) {
            case "select_flask" -> {
                if (data.slot == null) return;
                try {
                    int idx = Integer.parseInt(data.slot);
                    // Toggle off if clicking the already-selected flask
                    selectedFlask = (selectedFlask == idx) ? -1 : idx;
                } catch (NumberFormatException ignored) { return; }
                sendFullUpdate(ref, store);
            }
            case "store" -> {
                if (data.slot == null) return;
                short pouchSlot = findPouchSlot(inventory, pouchId);
                if (pouchSlot < 0) { sendFullUpdate(ref, store); return; }
                ItemStack pouchStack = inventory.getItemStack(pouchSlot);
                if (PouchManager.isFull(pouchStack)) { sendFullUpdate(ref, store); return; }

                String itemId = data.slot;
                short capacity = inventory.getCapacity();
                for (short s = 0; s < capacity; s++) {
                    ItemStack stack = inventory.getItemStack(s);
                    if (stack == null || stack.isEmpty()) continue;
                    if (!stack.getItemId().equals(itemId)) continue;
                    inventory.removeItemStackFromSlot(s, 1);
                    inventory.setItemStackForSlot(pouchSlot, PouchManager.withEmptyFlaskAdded(pouchStack));
                    break;
                }
                sendFullUpdate(ref, store);
            }
        }
    }

    // ── Section builders ─────────────────────────────────────────────────────

    private void populatePouchSlots(UICommandBuilder cmd, UIEventBuilder evt, List<FlaskState> contents) {
        for (int i = 0; i < PouchManager.MAX_SLOTS; i++) {
            boolean selected = i == selectedFlask;
            cmd.set("#PouchHighlight" + i + ".Background", selected ? "#c9a050" : "#00000000");
            if (i < contents.size()) {
                cmd.set("#PouchIcon" + i + ".ItemId", PouchManager.FLASK_ITEM_PREFIX);
                cmd.set("#PouchFill" + i + ".Text", contents.get(i).fillLabel());
            } else {
                cmd.set("#PouchFill" + i + ".Text", "");
            }
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#PouchButton" + i,
                    EventData.of("Action", "select_flask").append("Slot", String.valueOf(i)));
        }
    }

    private void populateDetailPanel(UICommandBuilder cmd, List<FlaskState> contents) {
        if (selectedFlask < 0 || selectedFlask >= contents.size()) {
            cmd.set("#FlaskDetailHeader.Text", "");
            cmd.set("#FlaskDetailContents.Text", "");
            return;
        }
        FlaskState flask = contents.get(selectedFlask);
        cmd.set("#FlaskDetailHeader.Text", "Flask " + (selectedFlask + 1) + " \u2014 " + flask.fill + "% full");
        if (flask.isEmpty()) {
            cmd.set("#FlaskDetailContents.Text", "Empty");
        } else {
            StringBuilder sb = new StringBuilder();
            for (FlaskState.ElementEntry e : flask.elements) {
                if (sb.length() > 0) sb.append("  |  ");
                sb.append(e.name).append(": ").append(e.amount).append("%");
            }
            cmd.set("#FlaskDetailContents.Text", sb.toString());
        }
    }

    private static void populateFlaskList(UICommandBuilder cmd, UIEventBuilder evt, List<ItemStack> flasks) {
        for (int i = 0; i < flasks.size(); i++) {
            ItemStack flask = flasks.get(i);
            String entry = "#FlaskList[" + i + "]";
            cmd.append("#FlaskList", "FlaskSlot.ui");
            cmd.set(entry + " #FlaskIcon.ItemId", flask.getItemId());
            int qty = flask.getQuantity();
            cmd.set(entry + " #QuantityLabel.Text", qty > 1 ? String.valueOf(qty) : "");
            evt.addEventBinding(CustomUIEventBindingType.Activating, entry + " #FlaskButton",
                    EventData.of("Action", "store").append("Slot", flask.getItemId()));
        }
    }

    // ── Full update helper ────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void sendFullUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        ItemContainer inventory = player != null ? player.getInventory().getCombinedArmorHotbarUtilityStorage() : null;
        ItemStack pouchStack = inventory != null ? findPouch(inventory, pouchId) : null;
        List<FlaskState> contents = pouchStack != null ? PouchManager.getFlaskStates(pouchStack) : List.of();
        List<ItemStack> flasks = inventory != null ? findFlasksInInventory(inventory) : List.of();

        UICommandBuilder update = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        populatePouchSlots(update, events, contents);
        populateDetailPanel(update, contents);
        update.clear("#FlaskList");
        populateFlaskList(update, events, flasks);
        sendUpdate(update, events, false);
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

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
package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.CreatureRecipeManager;
import me.Polgora.NestManager;
import me.Polgora.NestManager.NestState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MenagerieNestPage extends InteractiveCustomUIPage<MenagerieNestPage.NestAction>
        implements Refreshable {

    private static final int MAX_EGG_SLOTS = 4;

    // ── Action codec ──────────────────────────────────────────────────────────

    public static class NestAction {
        public static final BuilderCodec<NestAction> CODEC =
                BuilderCodec.builder(NestAction.class, NestAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                        .append(new KeyedCodec<>("Slot",   Codec.STRING), (d, v) -> d.slot   = v, d -> d.slot).add()
                        .build();
        public String action;
        public String slot;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final UUID worldUuid;
    private final int  bx, by, bz;
    /** Index into the egg-slot list of the currently selected egg; -1 = none. */
    private int pendingSlot = -1;

    public MenagerieNestPage(PlayerRef playerRef, UUID worldUuid, int bx, int by, int bz) {
        super(playerRef, CustomPageLifetime.CanDismiss, NestAction.CODEC);
        this.worldUuid = worldUuid;
        this.bx = bx; this.by = by; this.bz = bz;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("MenagerieNestUI.ui");

        String    key   = NestManager.nestKey(worldUuid, bx, by, bz);
        NestState state = NestManager.getState(key);

        if (state != null) {
            buildOccupied(cmd, state);
        } else {
            Player player = store.getComponent(ref, Player.getComponentType());
            buildEmpty(cmd, evt, player);
        }
    }

    // ── Occupied state ────────────────────────────────────────────────────────

    private void buildOccupied(UICommandBuilder cmd, NestState state) {
        // Clear egg-selection slots
        for (int i = 0; i < MAX_EGG_SLOTS; i++) {
            cmd.set("#NestEggHighlight" + i + ".Background", "#00000000");
            cmd.set("#NestEggIcon"      + i + ".ItemId",     "");
            cmd.set("#NestEggQty"       + i + ".Text",       "");
        }

        // Occupied display
        String displayName = CreatureRecipeManager.getDisplayNameByEggId(state.eggItemId);
        String timerText   = state.hasElapsed() ? "Ready!" : "Hatching in " + state.formatRemaining();

        cmd.set("#NestStatusLabel.Text",       state.hasElapsed() ? "The egg is ready to hatch!" : "Currently incubating\u2026");
        cmd.set("#NestOccupiedEggSlot.ItemId", state.eggItemId);
        cmd.set("#NestOccupiedEggName.Text",   displayName);
        cmd.set("#NestHatchTimer.Text",        timerText);
        cmd.set("#NestInsertLabel.Text",       state.hasElapsed() ? "Egg has hatched!" : "Incubating\u2026");
    }

    // ── Empty state ───────────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void buildEmpty(UICommandBuilder cmd, UIEventBuilder evt, Player player) {
        cmd.set("#NestStatusLabel.Text", "Select an egg to begin hatching.");
        cmd.set("#NestHatchTimer.Text",  "");

        List<EggEntry> eggs = findEggsInInventory(player);

        for (int i = 0; i < MAX_EGG_SLOTS; i++) {
            boolean selected = (i == pendingSlot);
            cmd.set("#NestEggHighlight" + i + ".Background", selected ? "#c9a050" : "#00000000");

            if (i < eggs.size()) {
                EggEntry entry = eggs.get(i);
                cmd.set("#NestEggIcon" + i + ".ItemId", entry.stack.getItemId());
                cmd.set("#NestEggQty"  + i + ".Text",
                        entry.stack.getQuantity() > 1 ? "x" + entry.stack.getQuantity() : "");
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#NestEggButton" + i,
                        EventData.of("Action", "select_egg").append("Slot", String.valueOf(i)));
            } else {
                cmd.set("#NestEggIcon" + i + ".ItemId", "");
                cmd.set("#NestEggQty"  + i + ".Text",  "");
            }
        }

        // Central preview: show selected egg if any
        if (pendingSlot >= 0 && pendingSlot < eggs.size()) {
            EggEntry entry = eggs.get(pendingSlot);
            cmd.set("#NestOccupiedEggSlot.ItemId", entry.stack.getItemId());
            cmd.set("#NestOccupiedEggName.Text",
                    CreatureRecipeManager.getDisplayNameByEggId(entry.stack.getItemId()));
        } else {
            cmd.set("#NestOccupiedEggSlot.ItemId", "");
            cmd.set("#NestOccupiedEggName.Text",   "");
        }

        boolean ready = pendingSlot >= 0 && pendingSlot < eggs.size();
        cmd.set("#NestInsertLabel.Text", ready ? "Insert Egg" : "Select an Egg First");
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NestInsertButton",
                EventData.of("Action", "insert"));
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull NestAction data) {
        if (data.action == null) return;

        String key = NestManager.nestKey(worldUuid, bx, by, bz);

        switch (data.action) {
            case "select_egg" -> {
                if (data.slot == null) return;
                try {
                    int idx = Integer.parseInt(data.slot);
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        List<EggEntry> eggs = findEggsInInventory(player);
                        // Toggle: clicking the already-selected slot deselects it
                        pendingSlot = (pendingSlot == idx || idx >= eggs.size()) ? -1 : idx;
                    }
                } catch (NumberFormatException ignored) {}
                sendFullUpdate(ref, store);
            }
            case "insert" -> {
                if (pendingSlot < 0) { sendFullUpdate(ref, store); return; }
                doInsert(ref, store, key);
            }
        }
    }

    // ── Insert logic ──────────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void doInsert(Ref<EntityStore> ref, Store<EntityStore> store, String key) {
        // Guard: nest may have been filled by another player between open and insert
        if (NestManager.getState(key) != null) {
            pendingSlot = -1;
            sendFullUpdate(ref, store);
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemContainer  inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        List<EggEntry> eggs      = findEggsInInventory(player);

        if (pendingSlot < 0 || pendingSlot >= eggs.size()) {
            pendingSlot = -1;
            sendFullUpdate(ref, store);
            return;
        }

        EggEntry  entry   = eggs.get(pendingSlot);
        ItemStack eggStack = inventory.getItemStack(entry.inventorySlot);
        if (eggStack == null || eggStack.isEmpty()) {
            pendingSlot = -1;
            sendFullUpdate(ref, store);
            return;
        }

        String eggItemId  = eggStack.getItemId();
        String rarity     = CreatureRecipeManager.getRarityByEggId(eggItemId);
        String displayName = CreatureRecipeManager.getDisplayNameByEggId(eggItemId);

        // Consume one egg from inventory
        inventory.removeItemStackFromSlot(entry.inventorySlot, 1);
        pendingSlot = -1;

        // Register in the nest
        NestManager.insertEgg(store, eggItemId, rarity, worldUuid, bx, by, bz);

        int mins = NestManager.getHatchSeconds(rarity) / 60;
        player.sendMessage(Message.raw("[Menagerie] " + displayName + " egg placed. It will hatch in " + mins + " minute" + (mins != 1 ? "s" : "") + "."));

        sendFullUpdate(ref, store);
    }

    // ── Refreshable ───────────────────────────────────────────────────────────

    /**
     * Pushes only the hatch timer text to the client.
     * Called once per second by {@link me.Polgora.events.PageRefreshSystem}.
     * No-op when the nest is empty or the egg has already hatched.
     */
    @Override
    public void refresh() {
        String key = NestManager.nestKey(worldUuid, bx, by, bz);
        NestState state = NestManager.getState(key);
        if (state == null || state.hasElapsed()) return; // nothing to tick
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#NestHatchTimer.Text", "Hatching in " + state.formatRemaining());
        sendUpdate(cmd, false);
    }

    // ── Full-update helper ────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void sendFullUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder update = new UICommandBuilder();
        UIEventBuilder   events = new UIEventBuilder();

        String    key   = NestManager.nestKey(worldUuid, bx, by, bz);
        NestState state = NestManager.getState(key);

        if (state != null) {
            buildOccupied(update, state);
        } else {
            Player player = store.getComponent(ref, Player.getComponentType());
            buildEmpty(update, events, player);
        }
        sendUpdate(update, events, false);
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private static class EggEntry {
        final short     inventorySlot;
        final ItemStack stack;
        EggEntry(short inventorySlot, ItemStack stack) {
            this.inventorySlot = inventorySlot;
            this.stack         = stack;
        }
    }

    private List<EggEntry> findEggsInInventory(Player player) {
        List<EggEntry> result = new ArrayList<>();
        if (player == null) return result;
        ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        short         capacity  = inventory.getCapacity();
        List<String>  knownIds  = CreatureRecipeManager.getKnownEggIds();
        for (short s = 0; s < capacity && result.size() < MAX_EGG_SLOTS; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && knownIds.contains(stack.getItemId())) {
                result.add(new EggEntry(s, stack));
            }
        }
        return result;
    }
}
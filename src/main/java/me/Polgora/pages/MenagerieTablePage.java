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
import me.Polgora.CreatureRecipeManager.CreatureRecipe;
import me.Polgora.JournalManager;
import me.Polgora.PouchManager;
import me.Polgora.PouchManager.FlaskState;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Random;

public class MenagerieTablePage extends InteractiveCustomUIPage<MenagerieTablePage.MenagerieAction> {

    private static final String LIFE_ESSENCE_ID = "Ingredient_Life_Essence";
    private static final Random RANDOM = new Random();

    public static class MenagerieAction {
        public static final BuilderCodec<MenagerieAction> CODEC =
                BuilderCodec.builder(MenagerieAction.class, MenagerieAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                        .append(new KeyedCodec<>("Flask",  Codec.STRING), (d, v) -> d.flask  = v, d -> d.flask).add()
                        .build();
        public String action;
        public String flask;
    }

    /** Which pouch-flask slot the player has selected; -1 until they click a full flask. */
    private int pendingFlask = -1;

    public MenagerieTablePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, MenagerieAction.CODEC);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        cmd.append("MenagerieTableUI.ui");
        if (player != null) {
            ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
            List<FlaskState> flasks = getFlaskStates(inventory);
            boolean hasEssence = findItemSlot(inventory, LIFE_ESSENCE_ID) >= 0;
            populateFlaskSlots(cmd, evt, flasks);
            populateDetailPanel(cmd, flasks);
            populateRequirements(cmd, hasEssence);
            populateCraftButton(cmd, evt, flasks);
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull MenagerieAction data) {
        if (data.action == null) return;
        switch (data.action) {
            case "select_flask" -> {
                if (data.flask == null) return;
                try {
                    int idx = Integer.parseInt(data.flask);
                    Player player = store.getComponent(ref, Player.getComponentType());
                    if (player != null) {
                        List<FlaskState> flasks = getFlaskStates(player.getInventory().getCombinedArmorHotbarUtilityStorage());
                        // Only allow selecting full flasks
                        if (idx >= 0 && idx < flasks.size() && flasks.get(idx).isFull()) {
                            pendingFlask = (pendingFlask == idx) ? -1 : idx;
                        }
                    }
                } catch (NumberFormatException ignored) { return; }
                sendFullUpdate(ref, store);
            }
            case "craft" -> {
                if (pendingFlask < 0) { sendFullUpdate(ref, store); return; }
                doCraft(ref, store);
            }
        }
    }

    // ── Section builders ──────────────────────────────────────────────────────

    private void populateFlaskSlots(UICommandBuilder cmd, UIEventBuilder evt, List<FlaskState> flasks) {
        for (int i = 0; i < PouchManager.MAX_SLOTS; i++) {
            boolean selected = i == pendingFlask;
            cmd.set("#MenFlaskHighlight" + i + ".Background", selected ? "#c9a050" : "#00000000");
            if (i < flasks.size()) {
                FlaskState fs = flasks.get(i);
                cmd.set("#MenFlaskIcon" + i + ".ItemId", PouchManager.FLASK_ITEM_PREFIX);
                cmd.set("#MenFlaskFill" + i + ".Text", fs.fillLabel());
                // Dim non-full flasks to indicate they are not selectable
                cmd.set("#MenFlaskFill" + i + ".Style.TextColor", fs.isFull() ? "#c9a050" : "#4a5a6a");
            } else {
                cmd.set("#MenFlaskFill" + i + ".Text", "");
            }
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#MenFlaskButton" + i,
                    EventData.of("Action", "select_flask").append("Flask", String.valueOf(i)));
        }
    }

    private void populateDetailPanel(UICommandBuilder cmd, List<FlaskState> flasks) {
        if (pendingFlask < 0 || pendingFlask >= flasks.size()) {
            cmd.set("#FlaskDetailHeader.Text", "");
            cmd.set("#FlaskDetailContents.Text", "");
            return;
        }
        FlaskState flask = flasks.get(pendingFlask);
        cmd.set("#FlaskDetailHeader.Text", "Flask " + (pendingFlask + 1) + " \u2014 Full");
        StringBuilder sb = new StringBuilder();
        for (FlaskState.ElementEntry e : flask.elements) {
            if (sb.length() > 0) sb.append("  |  ");
            sb.append(e.name).append(": ").append(e.amount).append("%");
        }
        cmd.set("#FlaskDetailContents.Text", sb.toString());
    }

    private void populateRequirements(UICommandBuilder cmd, boolean hasEssence) {
        cmd.set("#LifeEssenceIcon.ItemId", LIFE_ESSENCE_ID);
        cmd.set("#LifeEssenceLabel.Style.TextColor", hasEssence ? "#70c080" : "#c05050");
    }

    private void populateCraftButton(UICommandBuilder cmd, UIEventBuilder evt, List<FlaskState> flasks) {
        boolean ready = pendingFlask >= 0 && pendingFlask < flasks.size();
        cmd.set("#CraftLabel.Text", ready ? "Transmute" : "Select a Flask First");
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CraftButton",
                EventData.of("Action", "craft"));
    }

    // ── Full update helper ────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void sendFullUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        UICommandBuilder update = new UICommandBuilder();
        UIEventBuilder   events = new UIEventBuilder();
        if (player != null) {
            ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
            List<FlaskState> flasks = getFlaskStates(inventory);
            boolean hasEssence = findItemSlot(inventory, LIFE_ESSENCE_ID) >= 0;
            populateFlaskSlots(update, events, flasks);
            populateDetailPanel(update, flasks);
            populateRequirements(update, hasEssence);
            populateCraftButton(update, events, flasks);
        }
        sendUpdate(update, events, false);
    }

    // ── Crafting ──────────────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void doCraft(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemContainer inventory = player.getInventory().getCombinedArmorHotbarUtilityStorage();
        String playerId = playerRef.getUuid().toString();

        // Validate pouch and flask
        short pouchSlot = findPouchSlot(inventory);
        if (pouchSlot < 0) {
            player.sendMessage(Message.raw("[Menagerie] No Flask Pouch found."));
            pendingFlask = -1;
            sendFullUpdate(ref, store);
            return;
        }
        ItemStack pouchStack = inventory.getItemStack(pouchSlot);
        List<FlaskState> flasks = PouchManager.getFlaskStates(pouchStack);

        if (pendingFlask < 0 || pendingFlask >= flasks.size() || !flasks.get(pendingFlask).isFull()) {
            player.sendMessage(Message.raw("[Menagerie] Selected flask is no longer valid."));
            pendingFlask = -1;
            sendFullUpdate(ref, store);
            return;
        }
        FlaskState flask = flasks.get(pendingFlask);

        // Validate Life Essence
        short essenceSlot = findItemSlot(inventory, LIFE_ESSENCE_ID);
        if (essenceSlot < 0) {
            player.sendMessage(Message.raw("[Menagerie] Transmutation requires 1 " + LIFE_ESSENCE_ID + "."));
            sendFullUpdate(ref, store);
            return;
        }

        // Always consume Life Essence; flask is handled per outcome below
        int flaskIdx = pendingFlask;
        inventory.removeItemStackFromSlot(essenceSlot, 1);
        pendingFlask = -1;

        // Match recipes
        List<CreatureRecipe> matches = CreatureRecipeManager.findMatches(flask);

        if (!matches.isEmpty()) {
            // Success — consume the flask and hold the egg in the result panel
            inventory.setItemStackForSlot(pouchSlot, PouchManager.withFlaskRemoved(pouchStack, flaskIdx));

            CreatureRecipe winner = CreatureRecipeManager.rollWinner(matches, RANDOM);

            // Record success in journal
            JournalManager.addCreatureAttempt(playerId,
                    "SUCCESS|" + winner.eggItemId + "|" + winner.displayName + " \u2014 " + winner.formatElements());

            player.sendMessage(Message.raw("[Menagerie] Transmutation successful! Collect your " + winner.displayName + " egg."));
            player.getPageManager().openCustomPage(ref, store,
                    new MenagerieResultPage(playerRef, winner.eggItemId, winner.displayName));

        } else {
            // Failure — return the flask empty so the player can refill and try again
            inventory.setItemStackForSlot(pouchSlot,
                    PouchManager.withFlaskStateUpdated(pouchStack, flaskIdx, new PouchManager.FlaskState()));

            CreatureRecipe closest = CreatureRecipeManager.findClosest(flask);
            String failMessage;

            if (closest != null && closest.maxDeviation(flask) <= CreatureRecipeManager.CLOSE_THRESHOLD) {
                failMessage = closest.getHint(flask);
                JournalManager.addCreatureAttempt(playerId,
                        "FAILURE|" + closest.eggItemId + "|" + failMessage);
            } else {
                failMessage = "The essence did not respond. No creature matches this composition.";
                JournalManager.addCreatureAttempt(playerId,
                        "FAILURE|(none)|" + failMessage);
            }

            player.sendMessage(Message.raw("[Menagerie] " + failMessage));
            close();
        }
    }

    // ── Inventory helpers ─────────────────────────────────────────────────────

    private List<FlaskState> getFlaskStates(ItemContainer inventory) {
        ItemStack pouch = findPouch(inventory);
        return pouch != null ? PouchManager.getFlaskStates(pouch) : List.of();
    }

    private static ItemStack findPouch(ItemContainer inventory) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(PouchManager.POUCH_ITEM_ID))
                return stack;
        }
        return null;
    }

    private static short findPouchSlot(ItemContainer inventory) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(PouchManager.POUCH_ITEM_ID))
                return s;
        }
        return -1;
    }

    private static short findItemSlot(ItemContainer inventory, String itemId) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(itemId))
                return s;
        }
        return -1;
    }
}
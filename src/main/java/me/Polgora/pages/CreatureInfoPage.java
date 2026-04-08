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
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureDietRegistry.DietType;
import me.Polgora.CreatureFeedManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * UI page opened when a player right-clicks a Menagerie creature empty-handed.
 *
 * <p>Shows the creature's name, diet type, and current feeding cooldown.
 * The Feed button consumes the player's active hotbar item if it matches
 * the creature's diet and the cooldown has elapsed.
 */
public class CreatureInfoPage extends InteractiveCustomUIPage<CreatureInfoPage.FeedAction>
        implements Refreshable {

    // ── Action codec ──────────────────────────────────────────────────────────

    public static class FeedAction {
        public static final BuilderCodec<FeedAction> CODEC =
                BuilderCodec.builder(FeedAction.class, FeedAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (d, v) -> d.action = v, d -> d.action).add()
                        .build();
        public String action;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final UUID   creatureUuid;
    private final String roleName;
    private final String displayName;
    private final DietType diet;

    public CreatureInfoPage(PlayerRef playerRef, UUID creatureUuid, String roleName, String displayName) {
        super(playerRef, CustomPageLifetime.CanDismiss, FeedAction.CODEC);
        this.creatureUuid = creatureUuid;
        this.roleName     = roleName;
        this.displayName  = displayName;
        this.diet         = CreatureDietRegistry.getDiet(roleName);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("CreatureInfoUI.ui");
        populateFields(cmd);
        addFeedBinding(evt);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull FeedAction data) {
        if (!"feed".equals(data.action)) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Cooldown check
        if (!CreatureFeedManager.canFeed(creatureUuid)) {
            player.sendMessage(Message.raw(
                    "[Menagerie] " + displayName + " isn't hungry yet. "
                    + CreatureFeedManager.formatCooldown(creatureUuid)));
            sendUpdate(ref, store);
            return;
        }

        // Item check
        ItemStack heldItem = player.getInventory().getActiveHotbarItem();
        if (heldItem == null || heldItem.isEmpty()) {
            player.sendMessage(Message.raw(
                    "[Menagerie] Hold a food item in your active slot to feed " + displayName + "."));
            sendUpdate(ref, store);
            return;
        }

        String itemId = heldItem.getItemId();

        // Diet check
        if (!CreatureDietRegistry.isAcceptedFood(diet, itemId)) {
            String dietName = CreatureDietRegistry.getDietDisplayName(diet);
            player.sendMessage(Message.raw(
                    "[Menagerie] " + displayName + " won't eat that. "
                    + displayName + " is a " + dietName + " and needs appropriate food."));
            sendUpdate(ref, store);
            return;
        }

        // Consume one item from the active hotbar slot
        byte slot = player.getInventory().getActiveHotbarSlot();
        player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1);

        CreatureFeedManager.recordFeed(creatureUuid);

        player.sendMessage(Message.raw(
                "[Menagerie] You fed " + displayName + " a " + formatItemName(itemId) + "."));

        sendUpdate(ref, store);
    }

    // ── Refreshable ───────────────────────────────────────────────────────────

    /**
     * Pushes only the time-sensitive fields (countdown label and button text)
     * to the client. Called once per second by {@link me.Polgora.events.PageRefreshSystem}.
     * No-op once the cooldown has expired.
     */
    @Override
    public void refresh() {
        if (CreatureFeedManager.canFeed(creatureUuid)) return; // already ready — nothing to tick
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#CooldownLabel.Text",      CreatureFeedManager.formatCooldown(creatureUuid));
        cmd.set("#CooldownLabel.Style.TextColor", "#c09050");
        cmd.set("#FeedButtonLabel.Text",    "Not Hungry");
        sendUpdate(cmd, false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void sendUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder   evt = new UIEventBuilder();
        populateFields(cmd);
        addFeedBinding(evt);
        sendUpdate(cmd, evt, false);
    }

    private void populateFields(UICommandBuilder cmd) {
        boolean ready = CreatureFeedManager.canFeed(creatureUuid);

        cmd.set("#CreatureName.Text", displayName);
        cmd.set("#CreatureDiet.Text", "Diet: " + CreatureDietRegistry.getDietDisplayName(diet));
        cmd.set("#CooldownLabel.Text", CreatureFeedManager.formatCooldown(creatureUuid));
        cmd.set("#CooldownLabel.Style.TextColor", ready ? "#70c080" : "#c09050");
        cmd.set("#FeedButtonLabel.Text", ready ? "Feed" : "Not Hungry");
    }

    @SuppressWarnings("removal")
    private void addFeedBinding(UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FeedButton",
                EventData.of("Action", "feed"));
    }

    /** Turns e.g. "Food_Beef_Raw" into "Beef Raw" for chat messages. */
    private static String formatItemName(String itemId) {
        String stripped = itemId.startsWith("Food_") ? itemId.substring(5) : itemId;
        return stripped.replace("_", " ");
    }
}
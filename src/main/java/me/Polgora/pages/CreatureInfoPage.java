package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
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
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureDietRegistry.DietType;
import me.Polgora.CreatureFeedManager;
import me.Polgora.CreatureRegistry;

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

    private final PlayerRef playerRef;
    private final UUID      creatureUuid;
    /** Tracks whether the last refresh push reflected a "ready to feed" state,
     *  so we only push a full update once on the cooldown-expiry transition. */
    private boolean lastSentReady = true;
    /** Last bond status string sent to the client; used to detect external changes (e.g. /bondset). */
    private String lastSentBondStatus = null;
    private final String    roleName;
    private final String    displayName;
    private final DietType  diet;

    public CreatureInfoPage(PlayerRef playerRef, UUID creatureUuid, String roleName, String displayName) {
        super(playerRef, CustomPageLifetime.CanDismiss, FeedAction.CODEC);
        this.playerRef    = playerRef;
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

        // Owner check — only the creature's owner may feed it
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        if (ownerUuid != null && !ownerUuid.equals(playerRef.getUuid())) {
            player.sendMessage(Message.raw(
                    "[Menagerie] Only " + displayName + "'s owner can feed them."));
            return;
        }

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
        CreatureBondManager.recordFeed(creatureUuid);

        spawnFeedParticles(store);

        player.sendMessage(Message.raw(
                "[Menagerie] You fed " + displayName + " a " + formatItemName(itemId) + "."));

        sendUpdate(ref, store);
    }

    // ── Refreshable ───────────────────────────────────────────────────────────

    /**
     * Called once per second by {@link me.Polgora.events.PageRefreshSystem}.
     *
     * <p>While the cooldown is active: pushes the live countdown text every second.
     * When the cooldown expires: pushes one full update (all fields, including bond level
     * and the re-enabled Feed button) so the player doesn't need to reopen the page.
     * After that transition is delivered, no further updates are sent until the next feed.
     */
    @Override
    @SuppressWarnings("removal")
    public void refresh() {
        boolean ready      = CreatureFeedManager.canFeed(creatureUuid);
        String  bondStatus = CreatureBondManager.formatBondStatus(creatureUuid);
        boolean bondChanged = !bondStatus.equals(lastSentBondStatus);

        if (ready) {
            if (!lastSentReady || bondChanged) {
                // Cooldown just expired, or bond was changed externally (e.g. /bondset) — full update
                lastSentReady      = true;
                lastSentBondStatus = bondStatus;
                UICommandBuilder cmd = new UICommandBuilder();
                UIEventBuilder   evt = new UIEventBuilder();
                populateFields(cmd);
                addFeedBinding(evt);
                sendUpdate(cmd, evt, false);
            }
            return;
        }

        // Cooldown still active — push time-sensitive fields, plus bond if it changed
        lastSentReady = false;
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#CooldownLabel.Text",           CreatureFeedManager.formatCooldown(creatureUuid));
        cmd.set("#CooldownLabel.Style.TextColor", "#c09050");
        cmd.set("#FeedButtonLabel.Text",          "Not Hungry");
        if (bondChanged) {
            lastSentBondStatus = bondStatus;
            cmd.set("#BondLevel.Text", bondStatus);
        }
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

        String ownerName = CreatureRegistry.getOwnerName(creatureUuid);
        cmd.set("#CreatureOwner.Text", "Owner: " + (ownerName != null ? ownerName : "Unknown"));
        cmd.set("#BondLevel.Text", CreatureBondManager.formatBondStatus(creatureUuid));

        cmd.set("#CooldownLabel.Text", CreatureFeedManager.formatCooldown(creatureUuid));
        cmd.set("#CooldownLabel.Style.TextColor", ready ? "#70c080" : "#c09050");
        cmd.set("#FeedButtonLabel.Text", ready ? "Feed" : "Not Hungry");
    }

    @SuppressWarnings("removal")
    private void addFeedBinding(UIEventBuilder evt) {
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FeedButton",
                EventData.of("Action", "feed"));
    }

    /** Spawns the attractive-item particle effect at the creature's position. */
    private void spawnFeedParticles(Store<EntityStore> store) {
        var entityStore = store.getExternalData();
        var creatureRef = entityStore.getRefFromUUID(creatureUuid);
        if (creatureRef == null) return;
        NPCEntity npc = store.getComponent(creatureRef, NPCEntity.getComponentType());
        if (npc == null) return;
        Vector3d pos = npc.getOldPosition();
        if (pos == null) return;
        // Offset 1 block up, matching the role's ParticleOffset
        ParticleUtil.spawnParticleEffect("Hearts_Subtle",
                new Vector3d(pos.x, pos.y + 1.0, pos.z), store);
    }

    /** Turns e.g. "Food_Beef_Raw" into "Beef Raw" for chat messages. */
    private static String formatItemName(String itemId) {
        String stripped = itemId.startsWith("Food_") ? itemId.substring(5) : itemId;
        return stripped.replace("_", " ");
    }
}
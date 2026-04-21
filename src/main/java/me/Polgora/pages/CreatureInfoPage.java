package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
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
import me.Polgora.FoodIngredientValueRegistry;
import me.Polgora.CreatureDietRegistry.DietType;
import me.Polgora.CreatureFeedManager;
import me.Polgora.CreatureNameManager;
import me.Polgora.CreatureRegistry;
import me.Polgora.CreatureRenameManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * UI page opened when a player right-clicks a Menagerie creature empty-handed.
 *
 * <p>Shows the creature's name, diet type, and current feeding cooldown.
 * The Feed button consumes the player's active hotbar item if it matches
 * the creature's diet and the cooldown has elapsed.
 *
 * <p>The Rename button is shown only to the creature's owner once it has reached
 * Tame or higher. Clicking it closes the page and enters chat-based rename mode
 * via {@link CreatureRenameManager}.
 */
public class CreatureInfoPage extends InteractiveCustomUIPage<CreatureInfoPage.PageAction>
        implements Refreshable {

    // ── Action codec ──────────────────────────────────────────────────────────

    public static class PageAction {
        public static final BuilderCodec<PageAction> CODEC =
                BuilderCodec.builder(PageAction.class, PageAction::new)
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
    /** Species display name — used as fallback when no custom name has been set. */
    private final String    displayName;
    private final DietType  diet;

    public CreatureInfoPage(PlayerRef playerRef, UUID creatureUuid, String roleName, String displayName) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageAction.CODEC);
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
        addRenameBinding(evt);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull PageAction data) {
        if ("feed".equals(data.action)) {
            handleFeed(ref, store);
        } else if ("rename".equals(data.action)) {
            handleRename(ref, store);
        }
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
    public void refresh(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
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
                addRenameBinding(evt);
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

    // ── Action handlers ───────────────────────────────────────────────────────

    private void handleFeed(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        String creatureName = resolveCreatureName();

        // Owner check — only the creature's owner may feed it
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        if (ownerUuid != null && !ownerUuid.equals(playerRef.getUuid())) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("Only " + creatureName + "'s owner can feed them."), NotificationStyle.Warning);
            sendUpdate(ref, store);
            return;
        }

        // Cooldown check
        if (!CreatureFeedManager.canFeed(creatureUuid)) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("" + creatureName + " isn't hungry yet. "
                    + CreatureFeedManager.formatCooldown(creatureUuid)), NotificationStyle.Warning);
            sendUpdate(ref, store);
            return;
        }

        // Item check
        ItemStack heldItem = player.getInventory().getActiveHotbarItem();
        if (heldItem == null || heldItem.isEmpty()) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("Hold a food item in your active slot to feed " + creatureName + "."), NotificationStyle.Warning);
            sendUpdate(ref, store);
            return;
        }

        String itemId = heldItem.getItemId();

        // Diet check
        if (!CreatureDietRegistry.isAcceptedFood(diet, itemId)) {
            String dietName = CreatureDietRegistry.getDietDisplayName(diet);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("" + creatureName + " won't eat that. "
                    + creatureName + " is a " + dietName + " and needs appropriate food."), NotificationStyle.Warning);
            sendUpdate(ref, store);
            return;
        }

        // Consume one item from the active hotbar slot
        byte slot = player.getInventory().getActiveHotbarSlot();
        player.getInventory().getHotbar().removeItemStackFromSlot(slot, 1);

        int ingredientCount = FoodIngredientValueRegistry.getCount(itemId);
        int bondPoints      = ingredientCount * CreatureBondManager.POINTS_PER_INGREDIENT;

        CreatureFeedManager.recordFeed(creatureUuid);
        CreatureBondManager.recordFeed(creatureUuid, bondPoints);

        // First feed on an ownerless creature — claim ownership
        if (ownerUuid == null) {
            CreatureRegistry.registerExternal(creatureUuid, playerRef.getUuid(), playerRef.getUsername(), roleName);
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw(creatureName + " has no owner. You are now their owner!"), NotificationStyle.Success);
        }

        spawnFeedParticles(store);

        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                Message.raw("You fed " + creatureName + " a " + formatItemName(itemId)
                + ". (+" + bondPoints + " bond)"), NotificationStyle.Success);

        sendUpdate(ref, store);
    }

    @SuppressWarnings("removal")
    private void handleRename(Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Owner check
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        if (ownerUuid == null || !ownerUuid.equals(playerRef.getUuid())) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("Only this creature's owner can rename them."), NotificationStyle.Warning);
            return;
        }

        // Bond check — must be at least Tame
        CreatureBondManager.BondTier tier = CreatureBondManager.getTier(creatureUuid);
        if (tier.level < CreatureBondManager.BondTier.TAME.level) {
            NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                    Message.raw("You can only name a creature once it trusts you (Tame or higher)."), NotificationStyle.Warning);
            return;
        }

        player.getPageManager().setPage(ref, store, Page.None);
        CreatureRenameManager.awaitRename(playerRef.getUuid(), creatureUuid);
        playerRef.getPacketHandler().write(new com.hypixel.hytale.protocol.packets.interface_.OpenChatWithCommand(""));
        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                Message.raw("Type a name for your creature in chat (max "
                + CreatureRenameManager.MAX_NAME_LENGTH + " characters)."), NotificationStyle.Default);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void sendUpdate(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder   evt = new UIEventBuilder();
        populateFields(cmd);
        addFeedBinding(evt);
        addRenameBinding(evt);
        sendUpdate(cmd, evt, false);
    }

    private void populateFields(UICommandBuilder cmd) {
        boolean ready = CreatureFeedManager.canFeed(creatureUuid);

        cmd.set("#CreatureName.Text", resolveCreatureName());
        cmd.set("#CreatureDiet.Text", "Diet: " + CreatureDietRegistry.getDietDisplayName(diet));

        String ownerName = CreatureRegistry.getOwnerName(creatureUuid);
        cmd.set("#CreatureOwner.Text", "Owner: " + (ownerName != null ? ownerName : "Unknown"));
        cmd.set("#BondLevel.Text", CreatureBondManager.formatBondStatus(creatureUuid));

        // Rename button label — grayed out when not eligible
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        boolean isOwner   = ownerUuid != null && ownerUuid.equals(playerRef.getUuid());
        boolean canRename = isOwner
                && CreatureBondManager.getTier(creatureUuid).level
                   >= CreatureBondManager.BondTier.TAME.level;
        if (!isOwner) {
            cmd.set("#RenameButtonLabel.Text",              "Not Your Creature");
            cmd.set("#RenameButtonLabel.Style.TextColor",   "#3a4a5a");
        } else if (!canRename) {
            cmd.set("#RenameButtonLabel.Text",              "Tame to Rename");
            cmd.set("#RenameButtonLabel.Style.TextColor",   "#5a4a3a");
        }

        cmd.set("#CooldownLabel.Text", CreatureFeedManager.formatCooldown(creatureUuid));
        cmd.set("#CooldownLabel.Style.TextColor", ready ? "#70c080" : "#c09050");
        cmd.set("#FeedButtonLabel.Text", ready ? "Feed" : "Not Hungry");
    }

    @SuppressWarnings("removal")
    private void addFeedBinding(UIEventBuilder evt) {
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        boolean isOwner = ownerUuid == null || ownerUuid.equals(playerRef.getUuid());
        if (!isOwner) return;
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FeedButton",
                EventData.of("Action", "feed"));
    }

    @SuppressWarnings("removal")
    private void addRenameBinding(UIEventBuilder evt) {
        UUID ownerUuid = CreatureRegistry.getOwnerUuid(creatureUuid);
        boolean isOwner = ownerUuid != null && ownerUuid.equals(playerRef.getUuid());
        if (isOwner && CreatureBondManager.getTier(creatureUuid).level
                >= CreatureBondManager.BondTier.TAME.level) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#RenameButton",
                    EventData.of("Action", "rename"));
        }
    }

    /** Returns the custom name if set, otherwise the species display name. */
    private String resolveCreatureName() {
        String custom = CreatureNameManager.getName(creatureUuid);
        return (custom != null) ? custom : displayName;
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
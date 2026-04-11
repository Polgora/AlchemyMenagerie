package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.AnchorManager;
import me.Polgora.AnchorRenameManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * UI page opened when a player presses F on a Menagerie Anchor block.
 *
 * <p>Displays the anchor's current name and owner. The Rename button closes
 * the page and enters chat-based rename mode via {@link AnchorRenameManager}.
 */
public class AnchorInfoPage extends InteractiveCustomUIPage<AnchorInfoPage.AnchorAction> {

    // ── Action codec ──────────────────────────────────────────────────────────

    public static class AnchorAction {
        public static final BuilderCodec<AnchorAction> CODEC =
                BuilderCodec.builder(AnchorAction.class, AnchorAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (d, v) -> d.action = v, d -> d.action).add()
                        .build();
        public String action;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final PlayerRef               playerRef;
    private final AnchorManager.AnchorPos anchor;

    public AnchorInfoPage(PlayerRef playerRef, AnchorManager.AnchorPos anchor) {
        super(playerRef, CustomPageLifetime.CanDismiss, AnchorAction.CODEC);
        this.playerRef = playerRef;
        this.anchor    = anchor;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("AnchorInfoUI.ui");
        populateFields(cmd);

        // Only the owner may rename
        if (playerRef.getUuid().equals(anchor.ownerUuid)) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#RenameButton",
                    EventData.of("Action", "rename"));
        } else {
            cmd.set("#RenameButtonLabel.Text", "Not Your Anchor");
            cmd.set("#RenameButtonLabel.Style.TextColor", "#3a5a3a");
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull AnchorAction data) {
        if (!"rename".equals(data.action)) return;

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        // Must be the owner
        if (!playerRef.getUuid().equals(anchor.ownerUuid)) {
            player.sendMessage(Message.raw("[Menagerie] Only this anchor's owner can rename it."));
            return;
        }

        // Close the page first, then prompt in chat
        player.getPageManager().setPage(ref, store, Page.None);
        AnchorRenameManager.awaitRename(playerRef.getUuid(), anchor);
        player.sendMessage(Message.raw(
                "[Menagerie] Type a new name for this anchor in chat (max "
                + AnchorRenameManager.MAX_NAME_LENGTH + " characters)."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void populateFields(UICommandBuilder cmd) {
        cmd.set("#AnchorName.Text", anchor.name);

        String ownerDisplay = anchor.ownerName.isBlank() ? "Unknown" : anchor.ownerName;
        cmd.set("#AnchorOwner.Text", "Owner: " + ownerDisplay);
    }
}
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

import javax.annotation.Nonnull;

public class MenagerieResultPage extends InteractiveCustomUIPage<MenagerieResultPage.ResultAction> {

    public static class ResultAction {
        public static final BuilderCodec<ResultAction> CODEC =
                BuilderCodec.builder(ResultAction.class, ResultAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action).add()
                        .build();
        public String action;
    }

    private final String eggItemId;
    private final String eggDisplayName;
    /** Guard against giving the egg twice (collect button + onDismiss). */
    private boolean given = false;

    public MenagerieResultPage(PlayerRef playerRef, String eggItemId, String eggDisplayName) {
        super(playerRef, CustomPageLifetime.CanDismiss, ResultAction.CODEC);
        this.eggItemId      = eggItemId;
        this.eggDisplayName = eggDisplayName;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("MenagerieResultUI.ui");
        cmd.set("#ResultEggSlot.ItemId", eggItemId);
        cmd.set("#ResultEggName.Text",   eggDisplayName);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CollectButton",
                EventData.of("Action", "collect"));
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull ResultAction data) {
        if ("collect".equals(data.action)) {
            giveEgg(ref, store);
            close();
        }
    }

    /** Safety net: give the egg if the player presses back or ESC instead of Collect. */
    @Override
    @SuppressWarnings("removal")
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        giveEgg(ref, store);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void giveEgg(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (given) return;
        given = true;
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;
        var addResult = player.getInventory()
                .getCombinedArmorHotbarUtilityStorage()
                .addItemStack(new ItemStack(eggItemId, 1));
        if (!addResult.succeeded()) {
            player.sendMessage(Message.raw("[Menagerie] Inventory full — " + eggDisplayName + " egg was lost!"));
        }
    }
}
package me.Polgora;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.Polgora.commands.ExampleCommand;
import me.Polgora.commands.JournalClearCommand;
import me.Polgora.commands.JournalCommand;
import me.Polgora.events.AlchemyBenchCategorySetup;
import me.Polgora.events.ArcanoscopePacketWatcher;
import me.Polgora.events.ExampleEvent;
import me.Polgora.events.InventorySwapEvent;
import me.Polgora.events.PouchPickupPreventionSystem;
import me.Polgora.events.JournalEvent;
import me.Polgora.interactions.ArcanoscopeParticleInteraction;
import me.Polgora.pages.FlaskPouchPage;

import javax.annotation.Nonnull;

public class Main extends JavaPlugin {

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        JournalManager.init(getFile().getParent().resolve("AlchemyMenagerie").resolve("journal"), getFile());

        Interaction.CODEC.register("ArcanoscopeParticle", ArcanoscopeParticleInteraction.class, ArcanoscopeParticleInteraction.CODEC);

        // Open the specific Flask Pouch that was right-clicked, identified by its pouchId metadata.
        OpenCustomUIInteraction.registerCustomPageSupplier(this, FlaskPouchPage.class, "FlaskPouchSupplier",
                (ref, accessor, playerRef, ctx) -> {
                    if (ctx == null) return null;
                    var heldItem = ctx.getHeldItem();
                    if (heldItem == null || heldItem.isEmpty()) return null;
                    String pouchId = PouchManager.getPouchId(heldItem);
                    if (pouchId == null) {
                        // Pouch arrived without an ID — assign one now
                        var withId = PouchManager.withNewPouchId(heldItem);
                        ctx.getHeldItemContainer().setItemStackForSlot((short) ctx.getHeldItemSlot(), withId);
                        pouchId = PouchManager.getPouchId(withId);
                    }
                    return new FlaskPouchPage(playerRef, pouchId);
                });

        this.getCommandRegistry().registerCommand(new ExampleCommand("example", "An example command"));
        this.getCommandRegistry().registerCommand(new JournalCommand());
        this.getCommandRegistry().registerCommand(new JournalClearCommand());

        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExampleEvent::onPlayerReady);
        this.getEventRegistry().registerGlobal(PlayerConnectEvent.class, JournalEvent::onPlayerConnect);
        this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, JournalEvent::onPlayerDisconnect);
        this.getEventRegistry().registerGlobal(LivingEntityInventoryChangeEvent.class, InventorySwapEvent::onInventoryChange);

        this.getEntityStoreRegistry().registerSystem(new PouchPickupPreventionSystem());

        PacketAdapters.registerInbound(new ArcanoscopePacketWatcher());

        this.getEventRegistry().registerGlobal(EventPriority.LAST, LoadAssetEvent.class, AlchemyBenchCategorySetup::onLoadAsset);
    }
}
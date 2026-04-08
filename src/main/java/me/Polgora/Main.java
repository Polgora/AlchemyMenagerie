package me.Polgora;

import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.LoadAssetEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.server.OpenCustomUIInteraction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.npc.NPCPlugin;
import me.Polgora.AnchorManager;
import me.Polgora.CreatureDropManager;
import me.Polgora.NestManager;
import me.Polgora.commands.ExampleCommand;
import me.Polgora.commands.JournalClearCommand;
import me.Polgora.commands.JournalCommand;
import me.Polgora.events.AlchemyBenchCategorySetup;
import me.Polgora.events.ArcanoscopePacketWatcher;
import me.Polgora.events.ChestLootEvent;
import me.Polgora.events.ExampleEvent;
import me.Polgora.events.InventorySwapEvent;
import me.Polgora.events.AnchorResourceSystem;
import me.Polgora.events.MenagerieAnchorBreakEvent;
import me.Polgora.npc.BuilderActionOpenCreatureInfo;
import me.Polgora.events.MenagerieAnchorPlaceEvent;
import me.Polgora.events.MenagerieNestBreakEvent;
import me.Polgora.events.MenagerieNestEvent;
import me.Polgora.events.CreatureLureHintSystem;
import me.Polgora.events.PageRefreshSystem;
import me.Polgora.events.MenagerieTableEvent;
import me.Polgora.events.PouchPickupPreventionSystem;
import me.Polgora.events.JournalEvent;
import me.Polgora.interactions.ArcanoscopeParticleInteraction;
import me.Polgora.pages.ArcanoscopePage;
import me.Polgora.pages.FlaskPouchPage;
import me.Polgora.pages.MenagerieTablePage;

import javax.annotation.Nonnull;

public class Main extends JavaPlugin {

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        JournalManager.init(getFile().getParent().resolve("AlchemyMenagerie").resolve("journal"), getFile());
        ChestLootManager.init(getFile().getParent().resolve("AlchemyMenagerie").resolve("chestloot"));
        NestManager.init(getFile().getParent().resolve("AlchemyMenagerie").resolve("nests"));
        AnchorManager.init(getFile().getParent().resolve("AlchemyMenagerie").resolve("anchors"));
        CreatureDropManager.load("Verdant_Sprout");

        NPCPlugin.get().registerCoreComponentType("OpenCreatureInfo", BuilderActionOpenCreatureInfo::new);

        Interaction.CODEC.register("ArcanoscopeParticle", ArcanoscopeParticleInteraction.class, ArcanoscopeParticleInteraction.CODEC);

        OpenCustomUIInteraction.registerSimple(this, MenagerieTablePage.class, "MenagerieTableSupplier",
                playerRef -> new MenagerieTablePage(playerRef));

        OpenCustomUIInteraction.registerCustomPageSupplier(this, ArcanoscopePage.class, "ArcanoscopeSupplier",
                (ref, accessor, playerRef, ctx) -> {
                    if (ctx == null) return null;

                    var targetBlock = ctx.getTargetBlock();
                    if (targetBlock == null) return null;

                    var world = Universe.get().getWorld(playerRef.getWorldUuid());
                    if (world == null) return null;

                    BlockType blockType = world.getBlockType(targetBlock.x, targetBlock.y, targetBlock.z);
                    if (blockType == null) return null;

                    String blockId = blockType.getId();
                    String playerId = playerRef.getUuid().toString();

                    if (!JournalManager.isKnown(playerId, blockId)) {
                        playerRef.sendMessage(Message.raw("[Arcanoscope] Unknown. Right-click to scan first."));
                        return null;
                    }

                    var elements = ArcanoscopePage.parseElements(blockId);
                    var displayName = ArcanoscopePage.resolveDisplayName(blockId);
                    int harvestPower = ArcanoscopePage.parseHarvestPower();
                    return new ArcanoscopePage(playerRef, displayName, elements, harvestPower,
                            targetBlock.x, targetBlock.y, targetBlock.z);
                });

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
        this.getEntityStoreRegistry().registerSystem(new InventorySwapEvent());
        this.getEntityStoreRegistry().registerSystem(new MenagerieTableEvent());
        this.getEntityStoreRegistry().registerSystem(new MenagerieNestEvent());
        this.getEntityStoreRegistry().registerSystem(new MenagerieNestBreakEvent());
        this.getEntityStoreRegistry().registerSystem(new MenagerieAnchorPlaceEvent());
        this.getEntityStoreRegistry().registerSystem(new MenagerieAnchorBreakEvent());
        this.getEntityStoreRegistry().registerSystem(new AnchorResourceSystem());
        this.getEntityStoreRegistry().registerSystem(new PouchPickupPreventionSystem());
        this.getEntityStoreRegistry().registerSystem(new CreatureLureHintSystem());
        this.getEntityStoreRegistry().registerSystem(new PageRefreshSystem());
        this.getEntityStoreRegistry().registerSystem(new ChestLootEvent.PlacementTracker());
        this.getEntityStoreRegistry().registerSystem(new ChestLootEvent.BreakTracker());
        this.getEntityStoreRegistry().registerSystem(new ChestLootEvent.EnrichmentSystem());

        PacketAdapters.registerInbound(new ArcanoscopePacketWatcher());

        this.getEventRegistry().registerGlobal(EventPriority.LAST, LoadAssetEvent.class, AlchemyBenchCategorySetup::onLoadAsset);
    }
}
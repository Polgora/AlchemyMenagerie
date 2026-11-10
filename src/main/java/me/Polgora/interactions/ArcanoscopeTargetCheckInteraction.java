package me.Polgora.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;
import me.Polgora.WorldBlockUtil;

import java.util.UUID;

/**
 * Server-validated gate placed in front of the Arcanoscope's charging interaction.
 *
 * The client waits for the server's verdict ({@link WaitForDataFrom#Server}) before moving on to the
 * charging step, which is the step that plays the scan particles. If the target block can't be
 * scanned (no block, not scannable, or already in the player's journal) this interaction fails and
 * the charging step — and therefore its particles — never starts.
 */
public class ArcanoscopeTargetCheckInteraction extends SimpleInstantInteraction {

    public static final BuilderCodec<ArcanoscopeTargetCheckInteraction> CODEC =
            BuilderCodec.builder(ArcanoscopeTargetCheckInteraction.class,
                            ArcanoscopeTargetCheckInteraction::new, SimpleInstantInteraction.CODEC)
                    .build();

    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Server;
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        if (!isScannableTarget(context)) {
            context.getState().state = InteractionState.Failed;
        }
    }

    private static boolean isScannableTarget(InteractionContext context) {
        BlockPosition pos = context.getTargetBlock();
        if (pos == null) return false;

        var cmd = context.getCommandBuffer();
        if (cmd == null) return false;

        World world = cmd.getExternalData().getWorld();
        BlockType blockType = WorldBlockUtil.getBlockType(world, pos.x, pos.y, pos.z);
        if (blockType == null) return false;

        // A placed _Known block is the same block for scanning purposes as its vanilla form.
        String blockId = KnownItemSwapper.baseId(blockType.getId());
        if (blockId == null || blockId.isEmpty()) return false;
        if (!JournalManager.isScannableItem(blockId)) return false;

        UUIDComponent uuidComp = cmd.getComponent(context.getEntity(), UUIDComponent.getComponentType());
        if (uuidComp == null) return false;
        UUID playerUuid = uuidComp.getUuid();
        return playerUuid != null && !JournalManager.isKnown(playerUuid.toString(), blockId);
    }
}

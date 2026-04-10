package me.Polgora.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureBondManager.BondTier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin command that sets a creature's bond tier directly.
 *
 * <p>Usage: {@code /bondset <creatureUuid> <tier>}
 *
 * <p>Sets the creature's bond points to the floor of the specified tier
 * (WILD=0, TAME=100, BONDED=300, DEVOTED=700) and resets the erosion timer.
 * Restricted to OP-level players.
 */
public class BondSetCommand extends AbstractCommand {

    private final RequiredArg<UUID>     creatureUuidArg;
    private final RequiredArg<BondTier> tierArg;

    public BondSetCommand() {
        super("bondset", "Sets a creature's bond tier (OP only). Usage: /bondset <creatureUuid> <tier>");
        requirePermission(HytalePermissions.fromCommand("op.add"));
        creatureUuidArg = withRequiredArg("creatureUuid", "The entity UUID of the target creature", ArgTypes.UUID);
        tierArg         = withRequiredArg("tier",         "Bond tier: WILD, TAME, BONDED, or DEVOTED",
                                          ArgTypes.forEnum("tier", BondTier.class));
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        UUID     creatureUuid = context.get(creatureUuidArg);
        BondTier tier         = context.get(tierArg);

        BondTier previous = CreatureBondManager.getTier(creatureUuid);
        CreatureBondManager.setTier(creatureUuid, tier);

        context.sendMessage(Message.raw(
                "[Menagerie] Bond for " + creatureUuid
                + " set from " + previous.displayName
                + " to " + tier.displayName
                + " (" + tier.floor + " pts)."));

        return CompletableFuture.completedFuture(null);
    }
}
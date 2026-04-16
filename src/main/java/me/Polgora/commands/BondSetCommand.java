package me.Polgora.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureBondManager.BondTier;
import me.Polgora.CreatureRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin command that sets a creature's bond tier directly.
 *
 * <p>Usage: {@code /bondset <creatureUuid|creatureName> <tier>}
 *
 * <p>The first argument may be either the creature's runtime entity UUID or its
 * player-assigned custom name (case-insensitive). If a name matches multiple
 * creatures (owned by different players), the command reports ambiguity and asks
 * for the UUID instead.
 *
 * <p>Sets the creature's bond points to the floor of the specified tier
 * (WILD=0, TAME=100, BONDED=300, DEVOTED=700) and resets the erosion timer.
 * Restricted to OP-level players.
 */
public class BondSetCommand extends AbstractCommand {

    private final RequiredArg<String>   creatureArg;
    private final RequiredArg<BondTier> tierArg;

    public BondSetCommand() {
        super("bondset", "Sets a creature's bond tier (OP only). Usage: /bondset <creatureUuid|name> <tier>");
        requirePermission(HytalePermissions.fromCommand("op.add"));
        creatureArg = withRequiredArg("creature", "The entity UUID or custom name of the target creature", ArgTypes.STRING);
        tierArg     = withRequiredArg("tier",     "Bond tier: WILD, TAME, BONDED, or DEVOTED",
                                      ArgTypes.forEnum("tier", BondTier.class));
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        String   creatureArg = context.get(this.creatureArg);
        BondTier tier        = context.get(tierArg);

        UUID creatureUuid = resolveCreature(context, creatureArg);
        if (creatureUuid == null) return CompletableFuture.completedFuture(null);

        BondTier previous = CreatureBondManager.getTier(creatureUuid);
        CreatureBondManager.setTier(creatureUuid, tier);

        context.sendMessage(Message.raw(
                "[Menagerie] Bond for " + creatureArg
                + " set from " + previous.displayName
                + " to " + tier.displayName
                + " (" + tier.floor + " pts)."));

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Resolves the argument to an entity UUID. Tries UUID parse first; falls back
     * to name lookup. Sends an error message and returns {@code null} on failure.
     */
    private static UUID resolveCreature(CommandContext context, String arg) {
        // Try direct UUID parse
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {}

        // Fall back to name search
        List<UUID> matches = CreatureRegistry.findEntityUuidsByName(arg);

        if (matches.isEmpty()) {
            context.sendMessage(Message.raw(
                    "[Menagerie] No creature found with name \"" + arg + "\"."));
            return null;
        }

        if (matches.size() > 1) {
            context.sendMessage(Message.raw(
                    "[Menagerie] Multiple creatures named \"" + arg + "\" exist (different owners). "
                    + "Use the entity UUID instead."));
            return null;
        }

        return matches.get(0);
    }
}
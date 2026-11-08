package me.Polgora.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureBondManager.BondTier;
import me.Polgora.CreatureRegistry;
import me.Polgora.CreatureRegistry.OwnedCreature;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin command that lists every creature the command sender owns, grouped by
 * species, with each creature's entity UUID, custom name (if any), and bond
 * tier — the companion lookup to {@link BondSetCommand}'s species fallback,
 * for picking a *specific* creature among several un-named ones of the same
 * species rather than always landing on the first.
 *
 * <p>Usage: {@code /bondlist}
 */
public class BondListCommand extends AbstractCommand {

    public BondListCommand() {
        super("bondlist", "Lists your creatures with their UUID, name, and bond tier (OP only).");
        requirePermission(HytalePermissions.fromCommand("op.add"));
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("Players only."));
            return CompletableFuture.completedFuture(null);
        }

        UUID ownerUuid = context.sender().getUuid();
        List<String> species = CreatureRegistry.getOwnedSpecies(ownerUuid);

        if (species.isEmpty()) {
            context.sendMessage(Message.raw("You don't own any creatures yet."));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("Your creatures (use " + BondSetCommand.SELECTOR_EXAMPLE
                + " with /bondset instead of the UUID):"));
        for (String role : species) {
            context.sendMessage(Message.raw(role.replace('_', ' ') + ":"));

            List<OwnedCreature> owned = CreatureRegistry.getOwnedBySpecies(ownerUuid, role);
            int index = 1;
            for (OwnedCreature creature : owned) {
                UUID entityUuid = CreatureRegistry.getEntityUuidForCreature(creature.stableId());
                if (entityUuid == null) continue;

                String name = CreatureRegistry.getCreatureNameByStableId(creature.stableId());
                BondTier tier = CreatureBondManager.getTier(entityUuid);

                String line = "  " + role + "#" + index + " — Bond: " + tier.displayName
                        + (name != null ? " — \"" + name + "\"" : "")
                        + " (" + entityUuid + ")";
                context.sendMessage(Message.raw(line));
                index++;
            }
        }

        return CompletableFuture.completedFuture(null);
    }
}

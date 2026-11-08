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
 * <p>The first argument may be the creature's runtime entity UUID, its
 * player-assigned custom name (case-insensitive), a species name (spaces or
 * underscores, case-insensitive) matched against creatures the command
 * sender owns, or a {@code species#index} selector exactly as printed by
 * {@link BondListCommand} (e.g. {@code Verdant_Sprout#2}) — the easiest way
 * to target one specific creature among several un-named ones without typing
 * or copying a UUID. If a name matches multiple creatures owned by different
 * players, or a bare species name matches more than one of the sender's own
 * creatures, the command reports which it targeted and points at
 * {@code /bondlist} for picking a specific one via the selector form.
 *
 * <p>Sets the creature's bond points to the floor of the specified tier
 * (WILD=0, TAME=100, BONDED=300, DEVOTED=700) and resets the erosion timer.
 * Restricted to OP-level players.
 */
public class BondSetCommand extends AbstractCommand {

    /** Shown in /bondlist's hint pointing back at this command's selector syntax. */
    public static final String SELECTOR_EXAMPLE = "Species#Index (e.g. Verdant_Sprout#2)";

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
                "Bond for " + creatureArg
                + " set from " + previous.displayName
                + " to " + tier.displayName
                + " (" + tier.floor + " pts)."));

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Resolves the argument to an entity UUID. Tries UUID parse first, then
     * custom-name lookup, then falls back to matching the argument against a
     * species the command sender owns — this covers Wild creatures, which
     * can't be named yet (naming requires Tame+) but still need to be
     * targetable to reach Tame in the first place. Sends an error message and
     * returns {@code null} on failure.
     */
    private static UUID resolveCreature(CommandContext context, String arg) {
        // A "#" means this is meant as the species#index selector from /bondlist —
        // commit to that path entirely rather than falling through, so a typo in
        // the index gets a specific error instead of a confusing generic one.
        if (context.isPlayer() && arg.contains("#")) {
            return resolveBySelector(context, arg);
        }

        // Try direct UUID parse
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {}

        // Try custom-name search
        List<UUID> matches = CreatureRegistry.findEntityUuidsByName(arg);

        if (matches.size() == 1) return matches.get(0);

        if (matches.size() > 1) {
            context.sendMessage(Message.raw(
                    "Multiple creatures named \"" + arg + "\" exist (different owners). "
                    + "Use the entity UUID instead."));
            return null;
        }

        // Fall back to species lookup among creatures the sender owns.
        if (context.isPlayer()) {
            UUID bySpecies = resolveBySpecies(context, arg);
            if (bySpecies != null) return bySpecies;
        }

        context.sendMessage(Message.raw(
                "No creature found with name \"" + arg + "\". "
                + "If you meant a species or name with multiple words, please use an underscore "
                + "to separate the words."));
        return null;
    }

    /**
     * Resolves a {@code species#index} selector exactly as printed by
     * {@link BondListCommand} (1-based index, species spaces/underscores
     * interchangeable, case-insensitive). Always sends a message and returns
     * {@code null} on any failure — this path never falls through to the
     * other resolution strategies once a "#" is present in the argument.
     */
    private static UUID resolveBySelector(CommandContext context, String arg) {
        int hash = arg.indexOf('#');
        String speciesPart = arg.substring(0, hash).trim().replace(' ', '_');
        String indexPart   = arg.substring(hash + 1).trim();

        int index;
        try {
            index = Integer.parseInt(indexPart);
        } catch (NumberFormatException e) {
            context.sendMessage(Message.raw(
                    "\"" + indexPart + "\" isn't a valid index. Use the selector exactly as "
                    + "/bondlist prints it, e.g. " + SELECTOR_EXAMPLE + "."));
            return null;
        }

        UUID ownerUuid = context.sender().getUuid();
        String species = CreatureRegistry.getOwnedSpecies(ownerUuid).stream()
                .filter(s -> s.equalsIgnoreCase(speciesPart))
                .findFirst().orElse(null);
        if (species == null) {
            context.sendMessage(Message.raw(
                    "You don't own any " + speciesPart.replace('_', ' ') + "."));
            return null;
        }

        List<CreatureRegistry.OwnedCreature> owned = CreatureRegistry.getOwnedBySpecies(ownerUuid, species);
        if (index < 1 || index > owned.size()) {
            context.sendMessage(Message.raw(
                    "Index " + index + " is out of range — you own " + owned.size() + " "
                    + species.replace('_', ' ') + "(s). Run /bondlist to see current indices."));
            return null;
        }

        UUID entityUuid = CreatureRegistry.getEntityUuidForCreature(owned.get(index - 1).stableId());
        if (entityUuid == null) {
            context.sendMessage(Message.raw(
                    "That creature isn't currently spawned in the world. Run /bondlist to refresh."));
        }
        return entityUuid;
    }

    /**
     * Matches {@code arg} (spaces or underscores, case-insensitive) against a
     * species role name the command sender owns, and returns the entity UUID
     * of one such creature. If the sender owns more than one of that species,
     * targets the first (by stable ID) and tells the sender how many exist.
     */
    private static UUID resolveBySpecies(CommandContext context, String arg) {
        UUID ownerUuid = context.sender().getUuid();
        String normalized = arg.trim().replace(' ', '_');

        String species = CreatureRegistry.getOwnedSpecies(ownerUuid).stream()
                .filter(s -> s.equalsIgnoreCase(normalized))
                .findFirst().orElse(null);
        if (species == null) return null;

        List<CreatureRegistry.OwnedCreature> owned = CreatureRegistry.getOwnedBySpecies(ownerUuid, species);
        if (owned.isEmpty()) return null;

        UUID entityUuid = CreatureRegistry.getEntityUuidForCreature(owned.get(0).stableId());
        if (entityUuid == null) return null;

        if (owned.size() > 1) {
            context.sendMessage(Message.raw(
                    "You own " + owned.size() + " " + species.replace('_', ' ')
                    + "(s); targeting the first. Run /bondlist and use the " + SELECTOR_EXAMPLE
                    + " form to target a specific one."));
        }
        return entityUuid;
    }
}
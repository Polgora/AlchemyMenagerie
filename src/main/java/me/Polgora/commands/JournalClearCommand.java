package me.Polgora.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import me.Polgora.JournalManager;
import me.Polgora.KnownItemSwapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

public class JournalClearCommand extends AbstractCommand {

    public JournalClearCommand() {
        super("journalclear", "Erases all entries from your Arcanoscope journal.");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by a player."));
            return CompletableFuture.completedFuture(null);
        }

        String playerId = context.sender().getUuid().toString();

        if (JournalManager.getKnownItems(playerId).isEmpty()) {
            context.sendMessage(Message.raw("Your journal is already empty."));
            return CompletableFuture.completedFuture(null);
        }

        JournalManager.clearPlayer(playerId);
        Player player = (Player) context.sender();
        player.getWorld().execute(() -> KnownItemSwapper.swapToVanilla(player));
        context.sendMessage(Message.raw("Your journal has been cleared."));
        return CompletableFuture.completedFuture(null);
    }
}
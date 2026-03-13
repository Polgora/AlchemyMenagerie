package me.Polgora.commands;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import me.Polgora.JournalManager;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class JournalCommand extends AbstractCommand {

    public JournalCommand() {
        super("journal", "Lists all items you have scanned with the Arcanoscope.");
    }

    @Nullable
    @Override
    protected CompletableFuture<Void> execute(@Nonnull CommandContext context) {
        if (!context.isPlayer()) {
            context.sendMessage(Message.raw("This command can only be used by a player."));
            return CompletableFuture.completedFuture(null);
        }

        String playerId = context.sender().getUuid().toString();
        Set<String> knownItems = JournalManager.getKnownItems(playerId);

        if (knownItems.isEmpty()) {
            context.sendMessage(Message.raw("Your journal is empty. Use the Arcanoscope to scan blocks!"));
            return CompletableFuture.completedFuture(null);
        }

        context.sendMessage(Message.raw("=== Arcanoscope Journal (" + knownItems.size() + " entries) ==="));
        for (String itemId : knownItems) {
            context.sendMessage(Message.raw("- " + resolveDisplayName(itemId)));
        }

        return CompletableFuture.completedFuture(null);
    }

    private String resolveDisplayName(String blockTypeId) {
        String key = "server.items." + blockTypeId + ".name";
        String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        return (name != null && !name.isBlank()) ? name : blockTypeId;
    }
}
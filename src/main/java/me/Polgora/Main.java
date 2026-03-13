package me.Polgora;

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import me.Polgora.commands.ExampleCommand;
import me.Polgora.events.ExampleEvent;
import me.Polgora.interactions.ArcanoscopeParticleInteraction;

import javax.annotation.Nonnull;

public class Main extends JavaPlugin {

    public Main(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        Interaction.CODEC.register("ArcanoscopeParticle", ArcanoscopeParticleInteraction.class, ArcanoscopeParticleInteraction.CODEC);
        this.getCommandRegistry().registerCommand(new ExampleCommand("example", "An example command"));
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, ExampleEvent::onPlayerReady);
    }
}
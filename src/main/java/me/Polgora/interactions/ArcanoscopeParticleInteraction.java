package me.Polgora.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;

public class ArcanoscopeParticleInteraction extends ChargingInteraction {

    public static final BuilderCodec<ArcanoscopeParticleInteraction> CODEC =
            BuilderCodec.builder(ArcanoscopeParticleInteraction.class, ArcanoscopeParticleInteraction::new, ChargingInteraction.ABSTRACT_CODEC)
                    .build();

    public ArcanoscopeParticleInteraction() {
        this.allowIndefiniteHold = true;
    }
}
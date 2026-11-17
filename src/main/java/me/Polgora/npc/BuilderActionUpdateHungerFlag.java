package me.Polgora.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

/**
 * Builder for {@link ActionUpdateHungerFlag}.
 * Registered in {@code Main.setup()} as {@code "UpdateHungerFlag"}.
 */
public class BuilderActionUpdateHungerFlag extends BuilderActionBase {

    @Override
    public Action build(BuilderSupport support) {
        return new ActionUpdateHungerFlag(this, support);
    }

    @Override
    public Builder<Action> readConfig(JsonElement json) {
        return this;
    }

    @Override
    public String getShortDescription() {
        return "Raises the NotHungry flag while an owned creature is on feed cooldown.";
    }

    @Override
    public String getLongDescription() {
        return "Sets the NotHungry role flag when the creature has an owner and is on feed cooldown, so thrown-food instructions can ignore food.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}

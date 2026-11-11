package me.Polgora.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

/**
 * Builder for {@link ActionEatDroppedFood}.
 * Registered in {@code Main.setup()} as {@code "EatDroppedFood"}.
 */
public class BuilderActionEatDroppedFood extends BuilderActionBase {

    @Override
    public Action build(BuilderSupport support) {
        return new ActionEatDroppedFood(this, support);
    }

    @Override
    public Builder<Action> readConfig(JsonElement json) {
        return this;
    }

    @Override
    public String getShortDescription() {
        return "Eats food the creature just picked up from the ground.";
    }

    @Override
    public String getLongDescription() {
        return "Consumes diet-matching food from the NPC's hotbar (put there by PickUpItem), "
                + "plays the Eat animation, and credits feed cooldown / bond points if the creature has an owner.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}

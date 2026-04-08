package me.Polgora.npc;

import com.google.gson.JsonElement;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;

import java.util.EnumSet;

/**
 * Builder for {@link ActionOpenCreatureInfo}.
 * Registered in {@code Main.setup()} as {@code "OpenCreatureInfo"}.
 * Requires use inside an {@code InteractionInstruction} block.
 */
public class BuilderActionOpenCreatureInfo extends BuilderActionBase {

    @Override
    public Action build(BuilderSupport support) {
        return new ActionOpenCreatureInfo(this, support);
    }

    @Override
    public Builder<Action> readConfig(JsonElement json) {
        requireInstructionType(EnumSet.of(InstructionType.Interaction));
        return this;
    }

    @Override
    public String getShortDescription() {
        return "Opens the Menagerie creature info page.";
    }

    @Override
    public String getLongDescription() {
        return "Opens CreatureInfoPage for the player who interacted with this creature, "
                + "showing diet type and the feed action.";
    }

    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
}
package me.Polgora.npc;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.concurrent.TimeUnit;

/**
 * Plays the creature's {@code Eat} animation reliably. The entity keeps "Eat" as its active
 * Status animation after the one-shot finishes, so playing it again is a no-op and the second
 * meal showed nothing. We clear the slot first, then start Eat a moment later so the client
 * sees a real change.
 */
public final class EatAnimation {

    private static final long RESTART_DELAY_MS = 150;

    private EatAnimation() {}

    @SuppressWarnings("removal")
    public static void play(NPCEntity npc, Ref<EntityStore> ref, Store<EntityStore> store) {
        // Clear everything the role/engine may have playing (all slots but Movement, which the
        // locomotion controller owns), then start Eat once the clear has been sent.
        for (AnimationSlot slot : AnimationSlot.VALUES) {
            if (slot != AnimationSlot.Movement) npc.playAnimation(ref, slot, null, store);
        }
        store.getExternalData().getWorld().scheduleAfter(() -> {
            try {
                if (ref.isValid()) npc.playAnimation(ref, AnimationSlot.Status, "Eat", store);
            } catch (Exception ignored) { }
        }, RESTART_DELAY_MS, TimeUnit.MILLISECONDS);
    }
}

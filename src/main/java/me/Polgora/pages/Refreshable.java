package me.Polgora.pages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Implemented by {@link com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage}
 * subclasses that contain time-sensitive content (e.g. countdown timers) that
 * should update in real time while the page is open.
 *
 * <p>The {@link me.Polgora.events.PageRefreshSystem} calls {@link #refresh} on
 * the current page of every player approximately once per second.
 */
public interface Refreshable {

    /**
     * Called once per second with the full ECS context for the owning player.
     * Implementations push live updates (timers, state changes) and may close
     * the page by calling {@code player.getPageManager().setPage(ref, store, Page.None)}.
     */
    void refresh(Ref<EntityStore> ref, Store<EntityStore> store, Player player);
}
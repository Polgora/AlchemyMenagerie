package me.Polgora.pages;

/**
 * Implemented by {@link com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage}
 * subclasses that contain time-sensitive content (e.g. countdown timers) that
 * should update in real time while the page is open.
 *
 * <p>The {@link me.Polgora.events.PageRefreshSystem} calls {@link #refresh()} on
 * the current page of every player approximately once per second.
 */
public interface Refreshable {

    /**
     * Pushes a lightweight update to the client containing only the fields
     * that change over time. Implementations should be a no-op when no live
     * content needs updating (e.g. countdown already finished).
     */
    void refresh();
}
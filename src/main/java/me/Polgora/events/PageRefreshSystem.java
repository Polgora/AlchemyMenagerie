package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.pages.Refreshable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Periodically calls {@link Refreshable#refresh()} on any open custom UI page
 * that implements {@link Refreshable}, enabling live countdown timers without
 * requiring a full page rebuild.
 *
 * <p>Runs once per second per player (time-gated via {@link #REFRESH_INTERVAL_MS}).
 */
public class PageRefreshSystem extends EntityTickingSystem<EntityStore> {

    private static final long REFRESH_INTERVAL_MS = 1_000L;

    private final Map<UUID, Long> lastRefreshTimes = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                     Store<EntityStore> store, CommandBuffer<EntityStore> cmd) {

        Player player = chunk.getComponent(index, Player.getComponentType());
        if (player == null) return;

        CustomUIPage page = player.getPageManager().getCustomPage();
        if (!(page instanceof Refreshable refreshable)) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;
        UUID playerUuid = playerRef.getUuid();

        long now = System.currentTimeMillis();
        Long last = lastRefreshTimes.get(playerUuid);
        if (last != null && now - last < REFRESH_INTERVAL_MS) return;
        lastRefreshTimes.put(playerUuid, now);

        refreshable.refresh(ref, store, player);
    }
}
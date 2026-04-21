package me.Polgora.events;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.AnchorManager;

import java.util.UUID;

public class MenagerieAnchorBreakEvent extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public MenagerieAnchorBreakEvent() {
        super(BreakBlockEvent.class);
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int i, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                       CommandBuffer<EntityStore> cmd, BreakBlockEvent event) {
        if (!"Menagerie_Anchor".equals(event.getBlockType().getId())) return;

        Ref<EntityStore> ref = chunk.getReferenceTo(i);

        PlayerRef playerRef = cmd.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) return;

        var pos = event.getTargetBlock();
        AnchorManager.AnchorPos anchor = AnchorManager.getAnchorAt(playerRef.getWorldUuid(), pos.x, pos.y, pos.z);

        if (anchor != null) {
            UUID playerUuid = playerRef.getUuid();
            boolean isOwner = playerUuid.equals(anchor.ownerUuid);
            boolean isOp    = PermissionsModule.get().hasPermission(playerUuid, HytalePermissions.fromCommand("op.add"));

            if (!isOwner && !isOp) {
                event.setCancelled(true);
                NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                        Message.raw("You can only break your own Menagerie Anchors."), NotificationStyle.Warning);
                return;
            }
        }

        AnchorManager.removeAnchor(playerRef.getWorldUuid(), pos.x, pos.y, pos.z);
    }
}
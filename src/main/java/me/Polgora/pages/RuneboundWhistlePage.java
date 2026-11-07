package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import me.Polgora.AnchorManager;
import me.Polgora.CreatureAnchorRegistry;
import me.Polgora.CreatureRegistry;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Two-phase UI opened when a player right-clicks with the Runebound Whistle.
 *
 * <p>Phase flow:
 * <ol>
 *   <li><b>SELECT</b> — species stepper at top cycles through creature types; creature list
 *       below shows all creatures of the current type with their anchor assignment.
 *       Clicking a creature advances to phase 2.
 *   <li><b>ANCHOR</b> — select a nearby anchor and press Assign to confirm.
 * </ol>
 *
 * <p>All data (owned creatures, nearby anchors) is captured at page-open time so
 * the list does not change while the player is browsing.
 */
public class RuneboundWhistlePage extends InteractiveCustomUIPage<RuneboundWhistlePage.WhistleAction> {

    // ── Action codec ──────────────────────────────────────────────────────────

    public static class WhistleAction {
        public static final BuilderCodec<WhistleAction> CODEC =
                BuilderCodec.builder(WhistleAction.class, WhistleAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (d, v) -> d.action = v, d -> d.action).add()
                        .build();
        public String action;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    /** Number of list slots displayed per page. */
    private static final int SLOTS = 3;

    /** Radius (blocks) within which anchors are listed in the ANCHOR phase. */
    private static final double WHISTLE_RANGE = 5.0;

    // ── Phase ─────────────────────────────────────────────────────────────────

    private enum Phase { SELECT, ANCHOR }

    // ── Mutable state ─────────────────────────────────────────────────────────

    private Phase  phase                    = Phase.SELECT;
    private String selectedSpecies          = null;
    private int    selectedSpeciesIndex     = 0;   // stepper index within speciesList
    private UUID   selectedCreatureStableId = null;
    private int    selectedAnchorIndex      = -1;  // -1 = none selected
    private int    currentPage              = 0;

    /** Creatures of the currently-selected species; kept in sync with selectedSpeciesIndex. */
    private List<CreatureRegistry.OwnedCreature> currentSpeciesCreatures = List.of();

    // ── Immutable data (loaded at open time) ──────────────────────────────────

    private final PlayerRef playerRef;

    /**
     * Distinct species the player owns, sorted alphabetically.
     * Populated in {@link #build} after the lazy backfill runs.
     */
    private List<String> speciesList = List.of();

    /** All creatures per species. Populated in {@link #build}. */
    private Map<String, List<CreatureRegistry.OwnedCreature>> creaturesBySpecies = Map.of();

    /**
     * Anchors owned by the player within {@link #WHISTLE_RANGE} blocks of where
     * they were standing when the page opened.
     */
    private final List<AnchorManager.AnchorPos> nearbyAnchors;

    // ── Constructor ───────────────────────────────────────────────────────────

    public RuneboundWhistlePage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, WhistleAction.CODEC);
        this.playerRef = playerRef;

        // Capture nearby anchors at open time (position available without store)
        Vector3d pos = playerRef.getTransform().getPosition();
        this.nearbyAnchors = AnchorManager.getAnchorsOwnedByWithinRange(
                playerRef.getUuid(), playerRef.getWorldUuid(),
                pos.x, pos.y, pos.z, WHISTLE_RANGE);

        // Species/creatures data is loaded in build() after the store-based backfill runs
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append(uiFileForPhase());
        // Backfill any external creatures missing a stored role name using the live store,
        // then load species/creatures data now that the map is up to date.
        CreatureRegistry.backfillExternalRoles(playerRef.getUuid(), store);
        loadSpeciesData();
        populateUI(cmd, evt);
    }

    private String uiFileForPhase() {
        return switch (phase) {
            case SELECT -> "WhistleSpeciesUI.ui";
            case ANCHOR -> "WhistleAnchorUI.ui";
        };
    }

    // ── Event handling ────────────────────────────────────────────────────────

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull WhistleAction data) {
        if (data.action == null) { sendNoOp(ref, store); return; }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) { sendNoOp(ref, store); return; }

        switch (data.action) {
            case "prev"         -> navigatePage(-1, ref, store);
            case "next"         -> navigatePage(+1, ref, store);
            case "back"         -> handleBack(ref, store, player);
            case "continue"     -> handleContinue(ref, store);
            case "assign"       -> handleAssign(ref, store, player);
            case "species_prev" -> handleSpeciesStep(-1, ref, store);
            case "species_next" -> handleSpeciesStep(+1, ref, store);
            default -> {
                if (data.action.startsWith("slot_")) {
                    int i = Integer.parseInt(data.action.substring(5));
                    handleSlotClick(i, ref, store);
                }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void navigatePage(int delta, Ref<EntityStore> ref, Store<EntityStore> store) {
        int next = currentPage + delta;
        if (next < 0 || next >= pageCount()) { sendNoOp(ref, store); return; }
        currentPage = next;
        sendRefresh(ref, store, false);
    }

    private void handleBack(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        switch (phase) {
            case SELECT -> player.getPageManager().setPage(ref, store, Page.None);
            case ANCHOR -> {
                phase                    = Phase.SELECT;
                selectedCreatureStableId = null;
                selectedAnchorIndex      = -1;
                currentPage              = 0;
                sendRefresh(ref, store, true);
            }
        }
    }

    private void handleContinue(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (phase != Phase.SELECT || selectedCreatureStableId == null) { sendNoOp(ref, store); return; }
        phase               = Phase.ANCHOR;
        currentPage         = 0;
        selectedAnchorIndex = -1;
        sendRefresh(ref, store, true);
    }

    private void handleSpeciesStep(int delta, Ref<EntityStore> ref, Store<EntityStore> store) {
        int next = selectedSpeciesIndex + delta;
        if (next < 0 || next >= speciesList.size()) { sendNoOp(ref, store); return; }
        selectedSpeciesIndex    = next;
        selectedSpecies         = speciesList.get(selectedSpeciesIndex);
        currentSpeciesCreatures = creaturesBySpecies.getOrDefault(selectedSpecies, List.of());
        currentPage             = 0;
        selectedCreatureStableId = null;
        sendRefresh(ref, store, false);
    }

    private void handleSlotClick(int slotIndex, Ref<EntityStore> ref, Store<EntityStore> store) {
        int absIdx = currentPage * SLOTS + slotIndex;

        switch (phase) {
            case SELECT -> {
                if (absIdx >= currentSpeciesCreatures.size()) { sendNoOp(ref, store); return; }
                selectedCreatureStableId = currentSpeciesCreatures.get(absIdx).stableId();
                sendRefresh(ref, store, false);
            }
            case ANCHOR -> {
                if (absIdx >= nearbyAnchors.size()) { sendNoOp(ref, store); return; }
                selectedAnchorIndex = absIdx;
                sendRefresh(ref, store, false);
            }
        }
    }

    private void handleAssign(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        if (phase != Phase.ANCHOR
                || selectedAnchorIndex < 0 || selectedAnchorIndex >= nearbyAnchors.size()
                || selectedCreatureStableId == null) {
            sendNoOp(ref, store);
            return;
        }

        AnchorManager.AnchorPos anchor = nearbyAnchors.get(selectedAnchorIndex);

        // Persist the assignment
        CreatureAnchorRegistry.assign(selectedCreatureStableId, anchor);

        // Apply leash point to the live NPC entity (if currently spawned in the world)
        UUID entityUuid = CreatureRegistry.getEntityUuidForCreature(selectedCreatureStableId);
        if (entityUuid != null) {
            Ref<EntityStore> creatureRef = store.getExternalData().getRefFromUUID(entityUuid);
            if (creatureRef != null) {
                NPCEntity npc = store.getComponent(creatureRef, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.setLeashPoint(new Vector3d(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5));
                }
            }
        }

        String customName    = CreatureRegistry.getCreatureNameByStableId(selectedCreatureStableId);
        String creatureLabel = (customName != null) ? customName : formatSpeciesName(selectedSpecies);
        NotificationUtil.sendNotification(playerRef.getPacketHandler(),
                Message.raw("" + creatureLabel + " assigned to anchor: " + anchor.name), NotificationStyle.Success);
        player.getPageManager().setPage(ref, store, Page.None);
    }

    // ── UI population ─────────────────────────────────────────────────────────

    /** (Re-)loads species and creature data from the registry into instance fields. */
    private void loadSpeciesData() {
        UUID ownerUuid = playerRef.getUuid();
        List<String> owned = CreatureRegistry.getOwnedSpecies(ownerUuid);
        Map<String, List<CreatureRegistry.OwnedCreature>> bySpecies = new LinkedHashMap<>();
        for (String sp : owned) {
            bySpecies.put(sp, CreatureRegistry.getOwnedBySpecies(ownerUuid, sp));
        }
        this.speciesList        = owned;
        this.creaturesBySpecies = bySpecies;

        // Pre-populate creature list for the currently selected species index
        if (!speciesList.isEmpty()) {
            if (selectedSpeciesIndex >= speciesList.size()) selectedSpeciesIndex = 0;
            selectedSpecies         = speciesList.get(selectedSpeciesIndex);
            currentSpeciesCreatures = creaturesBySpecies.getOrDefault(selectedSpecies, List.of());
        }
    }

    private void sendRefresh(Ref<EntityStore> ref, Store<EntityStore> store, boolean fullRebuild) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder   evt = new UIEventBuilder();
        if (fullRebuild) {
            cmd.append(uiFileForPhase());
        }
        populateUI(cmd, evt);
        sendUpdate(cmd, evt, fullRebuild);
    }

    /** Sends an empty incremental update to release the client's Loading state. */
    private void sendNoOp(Ref<EntityStore> ref, Store<EntityStore> store) {
        sendUpdate(new UICommandBuilder(), new UIEventBuilder(), false);
    }

    private void populateUI(UICommandBuilder cmd, UIEventBuilder evt) {
        if (phase == Phase.SELECT) {
            populateSpeciesStepper(cmd, evt);
            boolean continueReady = selectedCreatureStableId != null;
            cmd.set("#ContinueLabel.Style.TextColor", continueReady ? "#60d080" : "#404848");
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ContinueButton", EventData.of("Action", "continue"));
        } else {
            // Anchor phase — show label, help text, and assign button
            cmd.set("#PhaseLabel.Text", "Assign to an Anchor");
            if (nearbyAnchors.isEmpty()) {
                cmd.set("#HelpText.Text", "No anchors in range. Place a Menagerie Anchor within 5 blocks, then reopen the whistle.");
            } else if (selectedAnchorIndex < 0) {
                cmd.set("#HelpText.Text", "Select a nearby anchor from the list, then press Assign.");
            } else {
                cmd.set("#HelpText.Text", "Press Assign to link your creature to this anchor.");
            }
            boolean assignReady = selectedAnchorIndex >= 0;
            cmd.set("#AssignLabel.Style.TextColor", assignReady ? "#60d080" : "#404848");
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#AssignButton", EventData.of("Action", "assign"));
        }

        // Creature/anchor list — present in both UI files
        populateListContent(cmd, evt);

        // Back / cancel button — present in both UI files
        cmd.set("#BackLabel.Text", phase == Phase.SELECT ? "Cancel" : "< Back");
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "back"));
    }

    /** Populates the species stepper row: < Name >. Arrows are always visible; boundary clicks are silently ignored. */
    private void populateSpeciesStepper(UICommandBuilder cmd, UIEventBuilder evt) {
        if (speciesList.isEmpty()) {
            cmd.set("#SpeciesNameLabel.Text", "No creatures found.");
            cmd.set("#HelpText.Text", "You have no creatures yet.");
        } else {
            String sp = speciesList.get(selectedSpeciesIndex);
            cmd.set("#SpeciesNameLabel.Text", formatSpeciesName(sp));
            cmd.set("#HelpText.Text", speciesList.size() > 1
                    ? "Use < > to browse creature types. Select a creature from the list, then press Continue."
                    : "Select a creature from the list, then press Continue.");
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SpeciesPrevButton", EventData.of("Action", "species_prev"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#SpeciesNextButton", EventData.of("Action", "species_next"));
    }

    /** Populates the shared 3-slot list with pagination — used by both phases. */
    private void populateListContent(UICommandBuilder cmd, UIEventBuilder evt) {
        int startIdx = currentPage * SLOTS;
        int listSize = currentListSize();

        for (int i = 0; i < SLOTS; i++) {
            int    absIdx = startIdx + i;
            String text;
            String color;
            String highlight;

            if (listSize == 0 && i == 0) {
                text      = emptyListMessage();
                color     = "#6a7a8a";
                highlight = "#00000000";
            } else if (absIdx < listSize) {
                text      = slotText(absIdx);
                color     = slotColor(absIdx);
                highlight = isSelectedSlot(absIdx) ? "#c9a050" : "#00000000";
            } else {
                text      = "";
                color     = "#6a7a8a";
                highlight = "#00000000";
            }

            cmd.set("#Slot" + i + "Label.Text",            text);
            cmd.set("#Slot" + i + "Label.Style.TextColor", color);
            cmd.set("#Slot" + i + "Highlight.Background",  highlight);
        }

        // Pagination
        int total = pageCount();
        cmd.set("#PageLabel.Text", (currentPage + 1) + " / " + total);
        cmd.set("#PrevLabel.Text", currentPage > 0         ? "< Prev" : "");
        cmd.set("#NextLabel.Text", currentPage < total - 1 ? "Next >"  : "");

        for (int i = 0; i < SLOTS; i++) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#Slot" + i + "Button",
                    EventData.of("Action", "slot_" + i));
        }
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton", EventData.of("Action", "prev"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "next"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int currentListSize() {
        return switch (phase) {
            case SELECT -> currentSpeciesCreatures.size();
            case ANCHOR -> nearbyAnchors.size();
        };
    }

    private int pageCount() {
        int size = currentListSize();
        return size == 0 ? 1 : (size + SLOTS - 1) / SLOTS;
    }

    private String slotText(int absIdx) {
        return switch (phase) {
            case SELECT -> {
                CreatureRegistry.OwnedCreature c = currentSpeciesCreatures.get(absIdx);
                String customName = CreatureRegistry.getCreatureNameByStableId(c.stableId());
                String label      = (customName != null) ? customName : "#" + (absIdx + 1);
                String anchorName = CreatureAnchorRegistry.getAnchorName(c.stableId());
                yield label + "  |  Anchor: " + anchorName;
            }
            case ANCHOR -> nearbyAnchors.get(absIdx).name;
        };
    }

    private boolean isSelectedSlot(int absIdx) {
        return switch (phase) {
            case SELECT -> {
                if (selectedCreatureStableId == null || absIdx >= currentSpeciesCreatures.size()) yield false;
                yield selectedCreatureStableId.equals(currentSpeciesCreatures.get(absIdx).stableId());
            }
            case ANCHOR -> absIdx == selectedAnchorIndex;
        };
    }

    private String slotColor(int absIdx) {
        return "#c890f0";
    }

    private String emptyListMessage() {
        return switch (phase) {
            case SELECT -> speciesList.isEmpty() ? "No creatures found." : "No creatures of this type.";
            case ANCHOR -> "No anchors within 5 blocks.";
        };
    }

    private static String formatSpeciesName(String roleName) {
        if (roleName == null) return "";
        return roleName.replace('_', ' ');
    }
}
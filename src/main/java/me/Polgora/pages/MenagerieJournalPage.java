package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.CreatureBestiaryRegistry;
import me.Polgora.CreatureBondManager;
import me.Polgora.CreatureDietRegistry;
import me.Polgora.CreatureDropManager;
import me.Polgora.CreatureRegistry;
import me.Polgora.JournalManager;
import me.Polgora.MenagerieJournalManager;
import me.Polgora.MenagerieJournalManager.DeceasedEntry;

import javax.annotation.Nonnull;
import java.util.*;

public class MenagerieJournalPage extends InteractiveCustomUIPage<MenagerieJournalPage.JournalAction> {

    // ── Action codec ──────────────────────────────────────────────────────────

    public static class JournalAction {
        public static final BuilderCodec<JournalAction> CODEC =
                BuilderCodec.builder(JournalAction.class, JournalAction::new)
                        .append(new KeyedCodec<>("Action", Codec.STRING),
                                (d, v) -> d.action = v, d -> d.action).add()
                        .build();
        public String action;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final int SLOTS = 4;

    // ── Tab enum ──────────────────────────────────────────────────────────────

    private enum Tab { CREATURES, BESTIARY, DISCOVERIES, RECIPES, GUIDE }
    private enum CreaturesView { LIVING, DECEASED }
    private enum BestiaryPhase { LIST, DETAIL }
    private enum RecipesView { SUCCESSES, FAILURES }
    private enum RecipesPhase { LIST, DETAIL, FAILURE_DETAIL }

    // ── Mutable state ─────────────────────────────────────────────────────────

    private Tab           currentTab              = Tab.CREATURES;
    private CreaturesView creaturesView           = CreaturesView.LIVING;
    private BestiaryPhase bestiaryPhase           = BestiaryPhase.LIST;
    private String        selectedBestiarySpecies = null;
    private RecipesView   recipesView             = RecipesView.SUCCESSES;
    private RecipesPhase  recipesPhase            = RecipesPhase.LIST;
    private String        selectedRecipeName      = null;
    private String        selectedRecipeElements  = null;
    private String        selectedFailureMessage  = null;
    private String        selectedFailureFlask    = null;
    private int currentPage  = 0;
    private int filterIndex  = 0; // index into elementFilters; 0 = "All"

    /** Cached list of unique element names for the Discoveries filter (["All", ...]). */
    private List<String> elementFilters = List.of("All");

    // ── Constructor ───────────────────────────────────────────────────────────

    private final PlayerRef playerRef;

    public MenagerieJournalPage(PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, JournalAction.CODEC);
        this.playerRef = playerRef;
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        CreatureRegistry.backfillExternalRoles(playerRef.getUuid(), store);
        cmd.append(uiFileForTab());
        populateUI(cmd, evt);
    }

    private String uiFileForTab() {
        return switch (currentTab) {
            case CREATURES   -> creaturesView == CreaturesView.DECEASED
                    ? "MenagerieJournalCreaturesDeceasedUI.ui"
                    : "MenagerieJournalCreaturesUI.ui";
            case BESTIARY    -> bestiaryPhase == BestiaryPhase.DETAIL
                    ? "MenagerieJournalBestiaryDetailUI.ui"
                    : "MenagerieJournalBestiaryUI.ui";
            case DISCOVERIES -> "MenagerieJournalDiscoveriesUI.ui";
            case GUIDE       -> "MenagerieJournalGuideUI.ui";
            case RECIPES     -> recipesPhase == RecipesPhase.DETAIL
                    ? "MenagerieJournalRecipesDetailUI.ui"
                    : recipesPhase == RecipesPhase.FAILURE_DETAIL
                            ? "MenagerieJournalRecipesFailedDetailUI.ui"
                            : recipesView == RecipesView.FAILURES
                                    ? "MenagerieJournalRecipesFailedUI.ui"
                                    : "MenagerieJournalRecipesUI.ui";
        };
    }

    // ── Event handling ────────────────────────────────────────────────────────

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                @Nonnull JournalAction data) {
        if (data.action == null) { noOp(); return; }

        switch (data.action) {
            case "tab_creatures"   -> switchTab(Tab.CREATURES, ref, store);
            case "tab_bestiary"    -> switchTab(Tab.BESTIARY, ref, store);
            case "tab_discoveries" -> switchTab(Tab.DISCOVERIES, ref, store);
            case "tab_recipes"     -> switchTab(Tab.RECIPES, ref, store);
            case "tab_guide"       -> switchTab(Tab.GUIDE, ref, store);
            case "prev"            -> navigate(-1);
            case "next"            -> navigate(+1);
            case "filter_prev"      -> shiftFilter(-1);
            case "filter_next"      -> shiftFilter(+1);
            case "view_living" -> {
                creaturesView = CreaturesView.LIVING;
                currentPage   = 0;
                sendRefresh(true);
                return;
            }
            case "view_deceased" -> {
                creaturesView = CreaturesView.DECEASED;
                currentPage   = 0;
                sendRefresh(true);
                return;
            }
            case "view_recipes" -> {
                recipesView  = RecipesView.SUCCESSES;
                recipesPhase = RecipesPhase.LIST;
                currentPage  = 0;
                sendRefresh(true);
                return;
            }
            case "view_failed" -> {
                recipesView  = RecipesView.FAILURES;
                recipesPhase = RecipesPhase.LIST;
                currentPage  = 0;
                sendRefresh(true);
                return;
            }
            case "recipes_back" -> {
                recipesPhase = RecipesPhase.LIST;
                sendRefresh(true);
                return;
            }
            case "bestiary_back" -> {
                bestiaryPhase = BestiaryPhase.LIST;
                sendRefresh(true);
                return;
            }
            case "close"           -> {
                Player p = store.getComponent(ref, Player.getComponentType());
                if (p != null) p.getPageManager().setPage(ref, store, Page.None);
                return;
            }
            default -> {
                if (data.action.startsWith("delete_")) {
                    int i = Integer.parseInt(data.action.substring(7));
                    handleDelete(i);
                } else if (data.action.startsWith("slot_")
                           && currentTab == Tab.BESTIARY
                           && bestiaryPhase == BestiaryPhase.LIST) {
                    int i = Integer.parseInt(data.action.substring(5));
                    handleBestiarySlotClick(i);
                    return;
                } else if (data.action.startsWith("slot_")
                           && currentTab == Tab.RECIPES
                           && recipesView == RecipesView.SUCCESSES
                           && recipesPhase == RecipesPhase.LIST) {
                    int i = Integer.parseInt(data.action.substring(5));
                    handleRecipeSlotClick(i);
                    return;
                } else if (data.action.startsWith("slot_")
                           && currentTab == Tab.RECIPES
                           && recipesView == RecipesView.FAILURES
                           && recipesPhase == RecipesPhase.LIST) {
                    int i = Integer.parseInt(data.action.substring(5));
                    handleFailureSlotClick(i);
                    return;
                }
            }
        }
        sendRefresh(false);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    private void switchTab(Tab tab, Ref<EntityStore> ref, Store<EntityStore> store) {
        CreatureRegistry.backfillExternalRoles(playerRef.getUuid(), store);
        currentTab              = tab;
        currentPage             = 0;
        filterIndex             = 0;
        creaturesView           = CreaturesView.LIVING;
        bestiaryPhase           = BestiaryPhase.LIST;
        selectedBestiarySpecies = null;
        recipesView             = RecipesView.SUCCESSES;
        recipesPhase            = RecipesPhase.LIST;
        selectedRecipeName      = null;
        selectedRecipeElements  = null;
        selectedFailureMessage  = null;
        selectedFailureFlask    = null;
        sendRefresh(true);
    }

    private void navigate(int delta) {
        int next = currentPage + delta;
        if (next >= 0 && next < pageCount()) currentPage = next;
    }

    private void shiftFilter(int delta) {
        buildElementFilters(); // refresh in case scans changed
        int next = filterIndex + delta;
        if (next >= 0 && next < elementFilters.size()) {
            filterIndex = next;
            currentPage = 0;
        }
    }

    private void handleDelete(int slotIndex) {
        if (currentTab != Tab.CREATURES || creaturesView != CreaturesView.DECEASED) return;
        List<CreatureSlotEntry> entries = buildDeceasedEntries();
        int absIdx = currentPage * SLOTS + slotIndex;
        if (absIdx >= entries.size()) return;
        MenagerieJournalManager.deleteDeceasedEntry(playerRef.getUuid().toString(), entries.get(absIdx).stableId);
    }

    // ── UI population ─────────────────────────────────────────────────────────

    private void populateUI(UICommandBuilder cmd, UIEventBuilder evt) {
        // Tab buttons
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabCreaturesButton",   EventData.of("Action", "tab_creatures"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabBestiaryButton",    EventData.of("Action", "tab_bestiary"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabDiscoveriesButton", EventData.of("Action", "tab_discoveries"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabRecipesButton",     EventData.of("Action", "tab_recipes"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#TabGuideButton",       EventData.of("Action", "tab_guide"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",          EventData.of("Action", "close"));

        if (currentTab == Tab.BESTIARY && bestiaryPhase == BestiaryPhase.DETAIL) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "bestiary_back"));
        }
        if (currentTab == Tab.RECIPES
                && (recipesPhase == RecipesPhase.DETAIL || recipesPhase == RecipesPhase.FAILURE_DETAIL)) {
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#BackButton", EventData.of("Action", "recipes_back"));
        }

        switch (currentTab) {
            case CREATURES   -> populateCreatures(cmd, evt);
            case BESTIARY    -> populateBestiary(cmd, evt);
            case DISCOVERIES -> populateDiscoveries(cmd, evt);
            case RECIPES     -> populateRecipes(cmd, evt);
            case GUIDE       -> populateGuide(cmd, evt);
        }
    }

    // ── Creatures tab ─────────────────────────────────────────────────────────

    private record CreatureSlotEntry(UUID stableId, String label, boolean deceased) {}

    private List<CreatureSlotEntry> buildLivingEntries() {
        UUID ownerUuid = playerRef.getUuid();
        List<CreatureSlotEntry> entries = new ArrayList<>();
        for (String species : CreatureRegistry.getOwnedSpecies(ownerUuid)) {
            for (CreatureRegistry.OwnedCreature c : CreatureRegistry.getOwnedBySpecies(ownerUuid, species)) {
                UUID entityUuid = CreatureRegistry.getEntityUuidForCreature(c.stableId());
                String name     = CreatureRegistry.getCreatureNameByStableId(c.stableId());
                String label    = name != null ? name : formatSpecies(species);
                String tier     = entityUuid != null
                        ? CreatureBondManager.getTier(entityUuid).displayName : "Wild";
                entries.add(new CreatureSlotEntry(c.stableId(),
                        label + "  |  " + formatSpecies(species) + "  |  Bond: " + tier, false));
            }
        }
        return entries;
    }

    private List<CreatureSlotEntry> buildDeceasedEntries() {
        List<CreatureSlotEntry> entries = new ArrayList<>();
        for (DeceasedEntry d : MenagerieJournalManager.getDeceased(playerRef.getUuid().toString())) {
            String species = d.species() != null ? formatSpecies(d.species()) : "Unknown";
            entries.add(new CreatureSlotEntry(d.stableId(),
                    d.displayName() + "  |  " + species + "  |  Bond: " + d.bondTier(), true));
        }
        return entries;
    }

    private void populateCreatures(UICommandBuilder cmd, UIEventBuilder evt) {
        boolean viewingLiving = creaturesView == CreaturesView.LIVING;

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewLivingButton",   EventData.of("Action", "view_living"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewDeceasedButton", EventData.of("Action", "view_deceased"));

        List<CreatureSlotEntry> entries = viewingLiving ? buildLivingEntries() : buildDeceasedEntries();
        int start = currentPage * SLOTS;

        for (int i = 0; i < SLOTS; i++) {
            int absIdx = start + i;
            if (absIdx < entries.size()) {
                CreatureSlotEntry e = entries.get(absIdx);
                cmd.set("#Slot" + i + "Label.Text",            e.label());
                cmd.set("#Slot" + i + "Label.Style.TextColor", viewingLiving ? "#c0d0e0" : "#6a7a8a");
                if (!viewingLiving) {
                    cmd.set("#Delete" + i + "Label.Text", "[X]");
                    evt.addEventBinding(CustomUIEventBindingType.Activating, "#Delete" + i + "Button",
                            EventData.of("Action", "delete_" + i));
                }
            } else {
                String empty = absIdx == 0 && entries.isEmpty()
                        ? (viewingLiving ? "No living creatures." : "No deceased creatures.")
                        : "";
                cmd.set("#Slot" + i + "Label.Text",            empty);
                cmd.set("#Slot" + i + "Label.Style.TextColor", "#6a7a8a");
                if (!viewingLiving) cmd.set("#Delete" + i + "Label.Text", "");
            }
        }

        populatePagination(cmd, evt, entries.size());
    }

    // ── Bestiary tab ──────────────────────────────────────────────────────────

    private void handleBestiarySlotClick(int slotIndex) {
        List<String> species = new ArrayList<>(
                MenagerieJournalManager.getBestiary(playerRef.getUuid().toString()));
        Collections.sort(species);
        int absIdx = currentPage * SLOTS + slotIndex;
        if (absIdx >= species.size()) return;
        selectedBestiarySpecies = species.get(absIdx);
        bestiaryPhase = BestiaryPhase.DETAIL;
        sendRefresh(true);
    }

    private void populateBestiary(UICommandBuilder cmd, UIEventBuilder evt) {
        if (bestiaryPhase == BestiaryPhase.DETAIL) {
            populateBestiaryDetail(cmd);
            return;
        }

        List<String> species = new ArrayList<>(
                MenagerieJournalManager.getBestiary(playerRef.getUuid().toString()));
        Collections.sort(species);
        int start = currentPage * SLOTS;

        for (int i = 0; i < SLOTS; i++) {
            int absIdx = start + i;
            if (absIdx < species.size()) {
                cmd.set("#Slot" + i + "Label.Text",            formatSpecies(species.get(absIdx)));
                cmd.set("#Slot" + i + "Label.Style.TextColor", "#c9a050");
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#Slot" + i + "Button",
                        EventData.of("Action", "slot_" + i));
            } else {
                cmd.set("#Slot" + i + "Label.Text",            absIdx == 0 && species.isEmpty()
                        ? "No species discovered yet." : "");
                cmd.set("#Slot" + i + "Label.Style.TextColor", "#6a7a8a");
            }
        }

        populatePagination(cmd, evt, species.size());
    }

    private void populateBestiaryDetail(UICommandBuilder cmd) {
        if (selectedBestiarySpecies == null) return;
        String species = selectedBestiarySpecies;

        CreatureBestiaryRegistry.BestiaryEntry entry = CreatureBestiaryRegistry.get(species);
        if (entry != null) {
            String iconPath = entry.iconPath() != null
                    ? entry.iconPath()
                    : entry.iconItemId() + ".png";
            cmd.set("#CreatureIcon.Background", iconPath);
        }

        cmd.set("#SpeciesNameLabel.Text", formatSpecies(species));

        List<String> lines = entry != null ? entry.descriptionLines() : List.of();
        cmd.set("#DescLine0.Text", String.join(" ", lines));

        CreatureDietRegistry.DietType diet = CreatureDietRegistry.getDiet(species);
        cmd.set("#DietLabel.Text", "Diet: " + CreatureDietRegistry.getDietDisplayName(diet));

        List<CreatureDropManager.DropEntry> drops = CreatureDropManager.getDrops(species);
        for (int i = 0; i < 5; i++) {
            cmd.set("#Drop" + i + "Label.Text", i < drops.size() ? formatDrop(drops.get(i)) : "");
        }
    }

    private static String formatDrop(CreatureDropManager.DropEntry e) {
        String name = ArcanoscopePage.resolveDisplayName(e.itemId);
        String qty  = e.quantityMin == e.quantityMax
                ? String.valueOf(e.quantityMin)
                : e.quantityMin + "-" + e.quantityMax;
        int pct = (int) Math.round(e.chance * 100);
        return name + "  \u00d7" + qty + "  (" + pct + "%)";
    }

    // ── Discoveries tab ───────────────────────────────────────────────────────

    private void buildElementFilters() {
        String playerId  = playerRef.getUuid().toString();
        Set<String> seen = new LinkedHashSet<>();
        seen.add("All");
        for (String blockId : JournalManager.getKnownItems(playerId)) {
            for (String element : ArcanoscopePage.parseElements(blockId)) {
                seen.add(ArcanoscopePage.parseElementName(element));
            }
        }
        List<String> filters = new ArrayList<>(seen);
        elementFilters = filters;
        if (filterIndex >= elementFilters.size()) filterIndex = 0;
    }

    private void populateDiscoveries(UICommandBuilder cmd, UIEventBuilder evt) {
        buildElementFilters();
        String playerId      = playerRef.getUuid().toString();
        String activeFilter  = elementFilters.isEmpty() ? "All" : elementFilters.get(filterIndex);

        // Filter label
        cmd.set("#FilterLabel.Text", "Filter: " + activeFilter);
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterPrevButton", EventData.of("Action", "filter_prev"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#FilterNextButton", EventData.of("Action", "filter_next"));

        // Build filtered block list
        List<String> slotTexts = new ArrayList<>();
        for (String blockId : JournalManager.getKnownItems(playerId)) {
            List<String> elems = ArcanoscopePage.parseElements(blockId);
            if (!"All".equals(activeFilter)) {
                boolean match = elems.stream()
                        .anyMatch(e -> activeFilter.equals(ArcanoscopePage.parseElementName(e)));
                if (!match) continue;
            }
            String displayName = ArcanoscopePage.resolveDisplayName(blockId);
            String elemStr     = String.join("  |  ", elems);
            slotTexts.add(displayName + "  —  " + elemStr);
        }
        Collections.sort(slotTexts);

        int start = currentPage * SLOTS;
        for (int i = 0; i < SLOTS; i++) {
            int absIdx = start + i;
            if (absIdx < slotTexts.size()) {
                cmd.set("#Slot" + i + "Label.Text",            slotTexts.get(absIdx));
                cmd.set("#Slot" + i + "Label.Style.TextColor", "#c0d0e0");
            } else {
                cmd.set("#Slot" + i + "Label.Text", absIdx == 0 && slotTexts.isEmpty()
                        ? "No blocks scanned yet." : "");
                cmd.set("#Slot" + i + "Label.Style.TextColor", "#6a7a8a");
            }
        }

        populatePagination(cmd, evt, slotTexts.size());
    }

    // ── Recipes tab ───────────────────────────────────────────────────────────

    private static final int MAX_FAILURES = 20;

    private record SuccessEntry(String label, String name, String elementsStr) {}
    private record FailureEntry(String label, String message, String flaskComp) {}

    private List<SuccessEntry> buildSuccessEntries() {
        String playerId = playerRef.getUuid().toString();
        List<SuccessEntry> entries = new ArrayList<>();
        Set<String>        seen    = new LinkedHashSet<>();
        for (String line : JournalManager.getCreatureAttempts(playerId)) {
            if (!line.startsWith("SUCCESS|")) continue;
            String rest   = line.substring(8);
            int    pipe   = rest.indexOf('|');
            String eggId  = pipe >= 0 ? rest.substring(0, pipe) : rest;
            String detail = pipe >= 0 ? rest.substring(pipe + 1) : rest;
            if (seen.add(eggId)) {
                int    dash  = detail.indexOf(" \u2014 ");
                String name  = dash >= 0 ? detail.substring(0, dash) : detail;
                String elems = dash >= 0 ? detail.substring(dash + 3) : "";
                entries.add(new SuccessEntry("[+] " + name, name, elems));
            }
        }
        return entries;
    }

    private void handleRecipeSlotClick(int slotIndex) {
        List<SuccessEntry> entries = buildSuccessEntries();
        int absIdx = currentPage * SLOTS + slotIndex;
        if (absIdx >= entries.size()) return;
        SuccessEntry e      = entries.get(absIdx);
        selectedRecipeName     = e.name();
        selectedRecipeElements = e.elementsStr();
        recipesPhase = RecipesPhase.DETAIL;
        sendRefresh(true);
    }

    private void handleFailureSlotClick(int slotIndex) {
        List<FailureEntry> entries = buildFailureEntries();
        int absIdx = currentPage * SLOTS + slotIndex;
        if (absIdx >= entries.size()) return;
        FailureEntry e         = entries.get(absIdx);
        selectedFailureMessage = e.message();
        selectedFailureFlask   = e.flaskComp();
        recipesPhase = RecipesPhase.FAILURE_DETAIL;
        sendRefresh(true);
    }

    private List<FailureEntry> buildFailureEntries() {
        String playerId = playerRef.getUuid().toString();
        List<FailureEntry> entries = new ArrayList<>();
        Set<String>        seen    = new LinkedHashSet<>();
        for (String line : JournalManager.getCreatureAttempts(playerId)) {
            if (!line.startsWith("FAILURE|")) continue;
            String   rest      = line.substring(8);
            String[] parts     = rest.split("\\|", 4);
            String   prefix;
            String   message;
            String   flaskComp;
            try {
                long ts = Long.parseLong(parts[0]);
                prefix    = formatTimestamp(ts) + " - ";
                message   = parts.length >= 3 ? parts[2] : (parts.length >= 2 ? parts[1] : rest);
                flaskComp = parts.length >= 4 ? parts[3] : "";
            } catch (NumberFormatException e) {
                // Legacy entry without timestamp
                prefix    = "- ";
                message   = parts.length >= 2 ? parts[1] : rest;
                flaskComp = parts.length >= 3 ? parts[2] : "";
            }
            if (seen.add(rest)) {
                entries.add(new FailureEntry(prefix + message, message, flaskComp));
            }
        }
        Collections.reverse(entries);
        if (entries.size() > MAX_FAILURES) {
            entries = new ArrayList<>(entries.subList(0, MAX_FAILURES));
        }
        return entries;
    }

    private static String formatTimestamp(long millis) {
        java.time.LocalDate d = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate();
        return String.format("%02d/%02d/%02d", d.getMonthValue(), d.getDayOfMonth(), d.getYear() % 100);
    }

    private void populateRecipes(UICommandBuilder cmd, UIEventBuilder evt) {
        if (recipesPhase == RecipesPhase.DETAIL) {
            populateRecipesDetail(cmd, evt);
            return;
        }
        if (recipesPhase == RecipesPhase.FAILURE_DETAIL) {
            populateFailureDetail(cmd, evt);
            return;
        }

        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewRecipesButton", EventData.of("Action", "view_recipes"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewFailedButton",  EventData.of("Action", "view_failed"));

        boolean viewingSuccess = recipesView == RecipesView.SUCCESSES;
        List<String> entries   = viewingSuccess
                ? buildSuccessEntries().stream().map(SuccessEntry::label).toList()
                : buildFailureEntries().stream().map(FailureEntry::label).toList();
        String emptyMsg        = viewingSuccess ? "No successful recipes yet." : "No failed attempts recorded.";
        String slotColor       = viewingSuccess ? "#60d080" : "#90a0b0";
        int start = currentPage * SLOTS;

        for (int i = 0; i < SLOTS; i++) {
            int absIdx = start + i;
            if (absIdx < entries.size()) {
                cmd.set("#Slot" + i + "Label.Text",            entries.get(absIdx));
                cmd.set("#Slot" + i + "Label.Style.TextColor", slotColor);
                evt.addEventBinding(CustomUIEventBindingType.Activating, "#Slot" + i + "Button",
                        EventData.of("Action", "slot_" + i));
            } else {
                cmd.set("#Slot" + i + "Label.Text",            absIdx == 0 && entries.isEmpty() ? emptyMsg : "");
                cmd.set("#Slot" + i + "Label.Style.TextColor", "#6a7a8a");
            }
        }

        populatePagination(cmd, evt, entries.size());
    }

    private void populateRecipesDetail(UICommandBuilder cmd, UIEventBuilder evt) {
        // BackButton is wired in populateUI; sub-tab buttons still present
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewRecipesButton", EventData.of("Action", "view_recipes"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewFailedButton",  EventData.of("Action", "view_failed"));

        cmd.set("#RecipeNameLabel.Text", selectedRecipeName != null ? selectedRecipeName : "");

        String[] parts = (selectedRecipeElements != null && !selectedRecipeElements.isEmpty())
                ? selectedRecipeElements.split(", ")
                : new String[0];
        for (int i = 0; i < 5; i++) {
            cmd.set("#Element" + i + "Label.Text", i < parts.length ? parts[i] : "");
        }
    }

    private void populateFailureDetail(UICommandBuilder cmd, UIEventBuilder evt) {
        // BackButton is wired in populateUI; sub-tab buttons still present
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewRecipesButton", EventData.of("Action", "view_recipes"));
        evt.addEventBinding(CustomUIEventBindingType.Activating, "#ViewFailedButton",  EventData.of("Action", "view_failed"));

        cmd.set("#FailureMessageLabel.Text", selectedFailureMessage != null ? selectedFailureMessage : "");

        String[] parts = (selectedFailureFlask != null && !selectedFailureFlask.isEmpty())
                ? selectedFailureFlask.split(", ")
                : new String[0];
        for (int i = 0; i < 3; i++) {
            cmd.set("#Element" + i + "Label.Text", i < parts.length ? parts[i] : "");
        }
    }

    // ── Guide tab ─────────────────────────────────────────────────────────────

    private record GuidePage(String title, String icon, String lore, String[] bullets) {}

    private static final GuidePage[] GUIDE_PAGES = {
        new GuidePage(
            "The Alchemist's Path",
            "Menagerie_Journal",
            "Few have mastered the art of Menagerie Alchemy -- the ancient practice of listening to the world's elemental voice and coaxing life from its essence. Your journey begins with a single scan, and ends with a bond unlike any other. Read on, and discover what awaits.",
            new String[]{
                "- Craft your tools and materials at the Alchemy Bench to get started",
                "- Scan blocks with the Arcanoscope to learn their elemental composition",
                "- Fill Alchemy Flasks with elemental essence using the Arcanoscope",
                "- Transmute a filled flask at the Menagerie Table to create an egg",
                "- Hatch, feed, and bond with your very own creature"
            }
        ),
        new GuidePage(
            "The Arcanoscope",
            "Tool_Arcanoscope",
            "This peculiar instrument does not merely look; it listens. Point it at any material, hold your breath, and hold still. In five seconds it will have heard the block's elemental composition and committed it to your journal forever.",
            new String[]{
                "- Right-click a block while holding the Arcanoscope to begin a scan",
                "- Hold completely still for 5 seconds -- any movement cancels the scan",
                "- Successful scans add the block to your Discoveries journal",
                "- Left-click scanned blocks with the Arcanoscope to harvest elemental essences."
            }
        ),
        new GuidePage(
            "The Alchemy Flask & Flask Pouch",
            "Flask_Pouch",
            "Raw knowledge is nothing without a vessel. The Alchemy Flask captures elemental ratios in precise measure. The Flask Pouch keeps them close, organized, and ready for the Menagerie Table. Treat your flasks well -- each one holds the world's secrets, bottled.",
            new String[]{
                "- Craft an Alchemy Flask and a Flask Pouch to get started",
                "- Alchemy Flasks are automatically stored in your Flask Pouch on pickup",
                "- Fill flasks with elemental essence using the Arcanoscope",
                "- A full flask is ready to be transmuted at the Menagerie Table"
            }
        ),
        new GuidePage(
            "The Menagerie Table",
            "Bench_Menagerie",
            "Here is where the real magic happens. Feed the Table a flask brimming with the right essence, sealed with ten drops of Life Essence, and watch the elements answer. The closer your composition matches a creature's recipe, the better your odds. Achieve a perfect match, and the outcome is guaranteed.",
            new String[]{
                "- Place a full Alchemy Flask and 10 Life Essence to attempt transmutation",
                "- Match a creature's elemental recipe to receive its egg",
                "- A perfect composition match guarantees the egg, no chance involved",
                "- Near-matches may still succeed; the closer you are, the better your odds"
            }
        ),
        new GuidePage(
            "The Menagerie Nest",
            "Menagerie_Nest",
            "An egg is not a creature -- not yet. It needs warmth, time, and a Menagerie Nest. Place your egg inside and let the Nest do its work. Common creatures are eager to hatch. Legendary ones make you wait.",
            new String[]{
                "- Place a Menagerie Nest and right-click it to open the Nest UI",
                "- Select your egg from your inventory and confirm to begin incubation",
                "- Hatch time varies by rarity -- rarer creatures require more patience",
                "- The Nest displays live progress"
            }
        ),
        new GuidePage(
            "Creature Care & Anchors",
            "Runebound_Whistle",
            "A creature in the wild does not trust easily. Earn that trust with food, patience, and presence -- and it will reward you tenfold. Give it a home with a Menagerie Anchor, and assign it using the Runebound Whistle. All creatures within anchor range provide resources over time.",
            new String[]{
                "- Feed your creature its preferred food to build your Bond (Wild -> Tame -> Bonded -> Devoted)",
                "- The first player to feed an ownerless creature claims ownership",
                "- Place a Menagerie Anchor and use the Runebound Whistle to assign a home",
                "- All creatures within anchor range drop resources over time"
            }
        )
    };

    private void populateGuide(UICommandBuilder cmd, UIEventBuilder evt) {
        int page = Math.min(currentPage, GUIDE_PAGES.length - 1);
        GuidePage gp = GUIDE_PAGES[page];

        cmd.set("#GuideIconSlot.ItemId",  gp.icon());
        cmd.set("#GuideTitleLabel.Text",  gp.title());
        cmd.set("#GuideLoreLabel.Text",   gp.lore());

        String[] bullets = gp.bullets();
        for (int i = 0; i < 5; i++) {
            cmd.set("#GuideBullet" + i + "Label.Text", i < bullets.length ? bullets[i] : "");
        }

        cmd.set("#PageLabel.Text",  "Page " + (page + 1) + " / " + GUIDE_PAGES.length);
        cmd.set("#PrevLabel.Text",  page > 0                      ? "< Prev" : "");
        cmd.set("#NextLabel.Text",  page < GUIDE_PAGES.length - 1 ? "Next >"  : "");
        if (page > 0)
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton", EventData.of("Action", "prev"));
        if (page < GUIDE_PAGES.length - 1)
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "next"));
    }

    // ── Shared pagination ─────────────────────────────────────────────────────

    private int pageCount() {
        if (currentTab == Tab.GUIDE) return GUIDE_PAGES.length;
        int size = currentListSize();
        return size == 0 ? 1 : (size + SLOTS - 1) / SLOTS;
    }

    private int currentListSize() {
        return switch (currentTab) {
            case CREATURES   -> (creaturesView == CreaturesView.LIVING
                    ? buildLivingEntries() : buildDeceasedEntries()).size();
            case BESTIARY    -> MenagerieJournalManager.getBestiary(playerRef.getUuid().toString()).size();
            case DISCOVERIES -> {
                buildElementFilters();
                String active = elementFilters.isEmpty() ? "All" : elementFilters.get(filterIndex);
                String pid    = playerRef.getUuid().toString();
                if ("All".equals(active)) yield JournalManager.getKnownItems(pid).size();
                yield (int) JournalManager.getKnownItems(pid).stream()
                        .filter(b -> ArcanoscopePage.parseElements(b).stream()
                                .anyMatch(e -> active.equals(ArcanoscopePage.parseElementName(e))))
                        .count();
            }
            case RECIPES -> (recipesPhase == RecipesPhase.DETAIL || recipesPhase == RecipesPhase.FAILURE_DETAIL) ? 0
                    : recipesView == RecipesView.SUCCESSES
                            ? buildSuccessEntries().size()
                            : buildFailureEntries().size();
            case GUIDE   -> GUIDE_PAGES.length;
        };
    }

    private void populatePagination(UICommandBuilder cmd, UIEventBuilder evt, int listSize) {
        int total = listSize == 0 ? 1 : (listSize + SLOTS - 1) / SLOTS;
        cmd.set("#PageLabel.Text", (currentPage + 1) + " / " + total);
        cmd.set("#PrevLabel.Text", currentPage > 0         ? "< Prev" : "");
        cmd.set("#NextLabel.Text", currentPage < total - 1 ? "Next >"  : "");
        if (currentPage > 0)
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#PrevButton", EventData.of("Action", "prev"));
        if (currentPage < total - 1)
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#NextButton", EventData.of("Action", "next"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void sendRefresh(boolean fullRebuild) {
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder   evt = new UIEventBuilder();
        if (fullRebuild) cmd.append(uiFileForTab());
        populateUI(cmd, evt);
        sendUpdate(cmd, evt, fullRebuild);
    }

    private void noOp() {
        sendUpdate(new UICommandBuilder(), new UIEventBuilder(), false);
    }

    private static String formatSpecies(String roleName) {
        return roleName == null ? "" : roleName.replace('_', ' ');
    }
}
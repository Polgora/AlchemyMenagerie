package me.Polgora.pages;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import me.Polgora.PouchManager;
import me.Polgora.PouchManager.FlaskState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ArcanoscopePage extends InteractiveCustomUIPage<ArcanoscopePage.HarvestAction> {

    public static class HarvestAction {
        public static final BuilderCodec<HarvestAction> CODEC = BuilderCodec.builder(HarvestAction.class, HarvestAction::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("Element", Codec.STRING), (d, v) -> d.element = v, d -> d.element)
                .addField(new KeyedCodec<>("Flask", Codec.STRING), (d, v) -> d.flask = v, d -> d.flask)
                .build();
        public String action;
        public String element;  // for "select" — e.g. "Carbon (72%)"
        public String flask;    // for "harvest" — flask index as string
    }

    private final String displayName;
    private final List<String> elements;
    private final int harvestPower;
    private final int blockX;
    private final int blockY;
    private final int blockZ;

    /** Which element the player has chosen; null until they click one. */
    private String pendingElement;

    public ArcanoscopePage(PlayerRef playerRef, String displayName, List<String> elements, int harvestPower,
                           int blockX, int blockY, int blockZ) {
        super(playerRef, CustomPageLifetime.CanDismiss, HarvestAction.CODEC);
        this.displayName = displayName;
        this.elements = elements;
        this.harvestPower = harvestPower;
        this.blockX = blockX;
        this.blockY = blockY;
        this.blockZ = blockZ;
    }

    // ── Build ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder evt, @Nonnull Store<EntityStore> store) {
        cmd.append("ArcanoscopeUI.ui");
        cmd.set("#BlockNameLabel.Text", displayName.toUpperCase());
        populateHelpText(cmd);
        populateElements(cmd, evt);
        populateFlasks(cmd, evt, ref, store);
    }

    // ── Events ───────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings("removal")
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull HarvestAction data) {
        if (data.action == null) return;
        switch (data.action) {
            case "select" -> {
                if (data.element == null) return;
                pendingElement = data.element;
                UICommandBuilder update = new UICommandBuilder();
                UIEventBuilder events = new UIEventBuilder();
                populateHelpText(update);
                populateElements(update, events);
                populateFlasks(update, events, ref, store);
                sendUpdate(update, events, false);
            }
            case "harvest" -> {
                if (data.flask == null || pendingElement == null) return;
                try {
                    doHarvest(ref, store, Integer.parseInt(data.flask));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    // ── Section builders ─────────────────────────────────────────────────────

    private void populateHelpText(UICommandBuilder cmd) {
        if (pendingElement == null) {
            cmd.set("#HelpText.Text", "Step 1 of 2 - Select an element to harvest.");
            cmd.set("#FlaskSectionLabel.Text", "Flask Pouch");
        } else {
            String name = parseElementName(pendingElement);
            int amount = (parseElementPct(pendingElement) * harvestPower) / 100;
            cmd.set("#HelpText.Text", "Step 2 of 2 - Now select a flask to fill.");
            cmd.set("#FlaskSectionLabel.Text", "Flask Pouch - " + name + ": +" + amount + "%");
        }
    }

    private void populateElements(UICommandBuilder cmd, UIEventBuilder evt) {
        cmd.clear("#ElementList");
        for (int i = 0; i < elements.size(); i++) {
            String element = elements.get(i);
            boolean selected = element.equals(pendingElement);
            cmd.append("#ElementList", "ElementSlot.ui");
            // Prefix selected element with an arrow so it stands out visually
            cmd.set("#ElementList[" + i + "] #ElementLabel.Text",
                    (selected ? "> " : "  ") + element);
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ElementList[" + i + "] #ElementButton",
                    EventData.of("Action", "select").append("Element", element));
        }
    }

    @SuppressWarnings("removal")
    private void populateFlasks(UICommandBuilder cmd, UIEventBuilder evt, Ref<EntityStore> ref, Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        List<FlaskState> flasks = List.of();
        if (player != null) {
            ItemStack pouch = findPouch(player.getInventory().getCombinedEverything());
            if (pouch != null) flasks = PouchManager.getFlaskStates(pouch);
        }

        for (int i = 0; i < PouchManager.MAX_SLOTS; i++) {
            if (i < flasks.size()) {
                cmd.set("#ArcFlaskIcon" + i + ".ItemId", PouchManager.FLASK_ITEM_PREFIX);
                cmd.set("#ArcFlaskFill" + i + ".Text", flasks.get(i).fillLabel());
            } else {
                cmd.set("#ArcFlaskFill" + i + ".Text", "");
            }
            // Harvest on click — doHarvest validates pendingElement before acting
            evt.addEventBinding(CustomUIEventBindingType.Activating, "#ArcFlaskButton" + i,
                    EventData.of("Action", "harvest").append("Flask", String.valueOf(i)));
        }

        populateFlaskInfo(cmd, flasks);
    }

    private void populateFlaskInfo(UICommandBuilder cmd, List<FlaskState> flasks) {
        for (int i = 0; i < PouchManager.MAX_SLOTS; i++) {
            String text = "";
            if (i < flasks.size()) {
                FlaskState flask = flasks.get(i);
                if (!flask.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Flask ").append(i + 1).append(" (").append(flask.fill).append("%): ");
                    for (int j = 0; j < flask.elements.size(); j++) {
                        if (j > 0) sb.append(", ");
                        FlaskState.ElementEntry e = flask.elements.get(j);
                        sb.append(e.name).append(" ").append(e.amount).append("%");
                    }
                    text = sb.toString();
                }
            }
            cmd.set("#FlaskInfo" + i + ".Text", text);
        }
    }

    // ── Harvest execution ────────────────────────────────────────────────────

    @SuppressWarnings("removal")
    private void doHarvest(Ref<EntityStore> ref, Store<EntityStore> store, int flaskIndex) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) return;

        ItemContainer inventory = player.getInventory().getCombinedEverything();
        short pouchSlot = findPouchSlot(inventory);
        if (pouchSlot < 0) {
            player.sendMessage(Message.raw("[Arcanoscope] No Flask Pouch found."));
            return;
        }

        ItemStack pouchStack = inventory.getItemStack(pouchSlot);
        List<FlaskState> flasks = PouchManager.getFlaskStates(pouchStack);
        if (flaskIndex < 0 || flaskIndex >= flasks.size()) return;

        FlaskState flask = flasks.get(flaskIndex);
        if (flask.isFull()) {
            player.sendMessage(Message.raw("[Arcanoscope] Flask " + (flaskIndex + 1) + " is already full."));
            return;
        }

        String elementName = parseElementName(pendingElement);
        int elementPct = parseElementPct(pendingElement);
        int harvestAmount = (elementPct * harvestPower) / 100;
        if (harvestAmount <= 0) {
            player.sendMessage(Message.raw("[Arcanoscope] Harvest power too low to extract " + elementName + "."));
            return;
        }

        FlaskState updated = flask.withHarvest(elementName, harvestAmount);
        inventory.setItemStackForSlot(pouchSlot, PouchManager.withFlaskStateUpdated(pouchStack, flaskIndex, updated));

        int actual = updated.fill - flask.fill;
        if (updated.isFull()) {
            player.sendMessage(Message.raw("[Arcanoscope] Flask " + (flaskIndex + 1) + " is now full!"));
        } else {
            player.sendMessage(Message.raw(
                    "[Arcanoscope] +" + actual + "% " + elementName + " -> Flask " + (flaskIndex + 1) +
                    " (" + updated.fill + "% full)"));
        }

        // Destroy the harvested block
        var world = Universe.get().getWorld(playerRef.getWorldUuid());
        if (world != null) {
            world.breakBlock(blockX, blockY, blockZ, 0);
        }

        close();
    }

    // ── Static helpers ───────────────────────────────────────────────────────

    /** Parses element lines from the _Known variant's description in server.lang. */
    public static List<String> parseElements(String blockId) {
        String key = "server.items." + blockId + "_Known.description";
        String desc = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        if (desc == null || desc.isBlank()) return List.of();
        String[] lines = desc.split("\\\\n|\n");
        List<String> elements = new ArrayList<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("Elements:")) continue;
            elements.add(line);
        }
        return elements;
    }

    /** Resolves the display name for a block ID from server.lang. */
    public static String resolveDisplayName(String blockId) {
        String key = "server.items." + blockId + ".name";
        String name = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        return (name != null && !name.isBlank()) ? name : blockId;
    }

    /**
     * Reads the harvest power percentage from the Arcanoscope description in server.lang.
     * Expects "Harvest Power: XX%" somewhere in the description. Defaults to 50 on parse failure.
     */
    public static int parseHarvestPower() {
        String key = "server.items.Tool_Arcanoscope.description";
        String desc = I18nModule.get().getMessage(I18nModule.DEFAULT_LANGUAGE, key);
        if (desc == null) return 50;
        String marker = "Harvest Power: ";
        int idx = desc.indexOf(marker);
        if (idx < 0) return 50;
        int start = idx + marker.length();
        int end = desc.indexOf('%', start);
        if (end < 0) return 50;
        try {
            return Integer.parseInt(desc.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /** Extracts the element name from a string like "Carbon (72%)". */
    public static String parseElementName(String element) {
        if (element == null) return "";
        int open = element.lastIndexOf('(');
        return (open > 0 ? element.substring(0, open) : element).trim();
    }

    /** Extracts the element percentage from a string like "Carbon (72%)". */
    public static int parseElementPct(String element) {
        if (element == null) return 0;
        int open = element.lastIndexOf('(');
        int close = element.lastIndexOf('%');
        if (open < 0 || close < 0 || close <= open) return 0;
        try {
            return Integer.parseInt(element.substring(open + 1, close).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ── Inventory helpers ────────────────────────────────────────────────────

    private static ItemStack findPouch(ItemContainer inventory) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) return stack;
        }
        return null;
    }

    private static short findPouchSlot(ItemContainer inventory) {
        short capacity = inventory.getCapacity();
        for (short s = 0; s < capacity; s++) {
            ItemStack stack = inventory.getItemStack(s);
            if (stack != null && !stack.isEmpty() && stack.getItemId().equals(PouchManager.POUCH_ITEM_ID)) return s;
        }
        return -1;
    }
}
package me.Polgora;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Stateless utility for Flask Pouch operations.
 *
 * Each Flask Pouch item stores its own contents in ItemStack metadata:
 *   "am_id"     — unique UUID string identifying this specific pouch
 *   "am_flasks" — BsonArray of BsonDocuments, one per flask slot:
 *                 { fill: int(0–100), elements: [{name: str, amount: int}, ...] }
 *
 * Persistence is handled automatically by the engine when it saves/loads
 * player inventories, since ItemStack.CODEC includes the metadata field.
 */
public class PouchManager {

    public static final int MAX_SLOTS = 3;
    public static final String FLASK_ITEM_PREFIX = "Alchemy_Flask";
    public static final String POUCH_ITEM_ID = "Flask_Pouch";

    private static final String META_ID = "am_id";
    private static final String META_FLASKS = "am_flasks";

    // ── FlaskState ───────────────────────────────────────────────────────────

    /**
     * Represents the state of a single Alchemy Flask stored inside a Flask Pouch.
     * Tracks fill level (0–100) and which elements have been harvested into it.
     */
    public static class FlaskState {

        public final int fill;
        public final List<ElementEntry> elements;

        public static class ElementEntry {
            public final String name;
            public final int amount;

            public ElementEntry(String name, int amount) {
                this.name = name;
                this.amount = amount;
            }
        }

        /** Constructs an empty flask. */
        public FlaskState() {
            this(0, Collections.emptyList());
        }

        public FlaskState(int fill, List<ElementEntry> elements) {
            this.fill = fill;
            this.elements = Collections.unmodifiableList(elements);
        }

        public boolean isEmpty() { return fill == 0; }
        public boolean isFull()  { return fill >= 100; }

        /** Human-readable fill label: "Empty", "36%", or "Full". */
        public String fillLabel() {
            if (isEmpty()) return "Empty";
            if (isFull()) return "Full";
            return fill + "%";
        }

        /**
         * Returns a new FlaskState with {@code harvestAmount} of {@code elementName}
         * added. Caps total fill at 100; any excess is silently discarded.
         */
        public static final int MAX_ELEMENTS = 3;

        /** Returns true if elementName is already in this flask or there is room for a new element. */
        public boolean canAcceptElement(String elementName) {
            for (ElementEntry e : elements) {
                if (e.name.equals(elementName)) return true;
            }
            return elements.size() < MAX_ELEMENTS;
        }

        public FlaskState withHarvest(String elementName, int harvestAmount) {
            if (isFull() || harvestAmount <= 0) return this;
            int actual = Math.min(harvestAmount, 100 - fill);
            List<ElementEntry> updated = new ArrayList<>(elements);
            boolean found = false;
            for (int i = 0; i < updated.size(); i++) {
                if (updated.get(i).name.equals(elementName)) {
                    updated.set(i, new ElementEntry(elementName, updated.get(i).amount + actual));
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (updated.size() >= MAX_ELEMENTS) return this;
                updated.add(new ElementEntry(elementName, actual));
            }
            return new FlaskState(fill + actual, updated);
        }

        BsonDocument toBson() {
            BsonArray arr = new BsonArray();
            for (ElementEntry e : elements) {
                BsonDocument entry = new BsonDocument();
                entry.put("name", new BsonString(e.name));
                entry.put("amount", new BsonInt32(e.amount));
                arr.add(entry);
            }
            BsonDocument doc = new BsonDocument();
            doc.put("fill", new BsonInt32(fill));
            doc.put("elements", arr);
            return doc;
        }

        static FlaskState fromBson(BsonValue value) {
            if (value == null || !value.isDocument()) return new FlaskState();
            BsonDocument doc = value.asDocument();
            int fill = doc.containsKey("fill") ? doc.getInt32("fill").getValue() : 0;
            List<ElementEntry> elements = new ArrayList<>();
            if (doc.containsKey("elements")) {
                for (BsonValue ev : doc.getArray("elements")) {
                    if (!ev.isDocument()) continue;
                    BsonDocument ed = ev.asDocument();
                    if (!ed.containsKey("name") || !ed.containsKey("amount")) continue;
                    elements.add(new ElementEntry(
                            ed.getString("name").getValue(),
                            ed.getInt32("amount").getValue()));
                }
            }
            return new FlaskState(fill, elements);
        }
    }

    // ── Pouch identity ───────────────────────────────────────────────────────

    /** Returns the pouchId stored in this ItemStack's metadata, or null if not yet assigned. */
    public static String getPouchId(ItemStack pouch) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_ID)) return null;
        return meta.getString(META_ID).getValue();
    }

    /** Returns a new ItemStack with a fresh UUID assigned as the pouchId. */
    public static ItemStack withNewPouchId(ItemStack pouch) {
        return pouch.withMetadata(META_ID, new BsonString(UUID.randomUUID().toString()));
    }

    // ── Flask state accessors ────────────────────────────────────────────────

    /** Returns the number of flask slots currently used in the pouch. */
    public static int getFlaskCount(ItemStack pouch) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_FLASKS)) return 0;
        return meta.getArray(META_FLASKS).size();
    }

    public static boolean isFull(ItemStack pouch) {
        return getFlaskCount(pouch) >= MAX_SLOTS;
    }

    /** Returns the list of {@link FlaskState}s stored in this pouch. */
    public static List<FlaskState> getFlaskStates(ItemStack pouch) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_FLASKS)) return Collections.emptyList();
        List<FlaskState> result = new ArrayList<>();
        for (BsonValue v : meta.getArray(META_FLASKS)) {
            result.add(FlaskState.fromBson(v));
        }
        return Collections.unmodifiableList(result);
    }

    // ── Flask mutation ───────────────────────────────────────────────────────

    /**
     * Returns a new ItemStack with a fresh empty Alchemy Flask added to the pouch,
     * or the original pouch if it is already full.
     */
    public static ItemStack withEmptyFlaskAdded(ItemStack pouch) {
        if (isFull(pouch)) return pouch;
        BsonDocument meta = pouch.getMetadata();
        List<BsonValue> values = new ArrayList<>();
        if (meta != null && meta.containsKey(META_FLASKS)) {
            values.addAll(meta.getArray(META_FLASKS));
        }
        values.add(new FlaskState().toBson());
        return pouch.withMetadata(META_FLASKS, new BsonArray(values));
    }

    /**
     * Returns a new ItemStack with the flask at {@code index} replaced by {@code newState},
     * or the original pouch if the index is out of range.
     */
    public static ItemStack withFlaskStateUpdated(ItemStack pouch, int index, FlaskState newState) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_FLASKS)) return pouch;
        List<BsonValue> values = new ArrayList<>(meta.getArray(META_FLASKS));
        if (index < 0 || index >= values.size()) return pouch;
        values.set(index, newState.toBson());
        return pouch.withMetadata(META_FLASKS, new BsonArray(values));
    }

    /**
     * Returns a new ItemStack with the flask at {@code index} removed,
     * or the original pouch if the index is out of range.
     */
    public static ItemStack withFlaskRemoved(ItemStack pouch, int index) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_FLASKS)) return pouch;
        List<BsonValue> values = new ArrayList<>(meta.getArray(META_FLASKS));
        if (index < 0 || index >= values.size()) return pouch;
        values.remove(index);
        return pouch.withMetadata(META_FLASKS, new BsonArray(values));
    }
}
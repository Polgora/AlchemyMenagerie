package me.Polgora;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Stateless utility for Flask Pouch operations.
 * Each Flask Pouch item stores its own contents in ItemStack metadata:
 *   "am_id"     — unique UUID string identifying this specific pouch
 *   "am_flasks" — BsonArray of "itemId,qty" strings
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

    /** Returns the flask list stored in this pouch's metadata. */
    public static List<ItemStack> getFlasks(ItemStack pouch) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_FLASKS)) return Collections.emptyList();
        List<ItemStack> result = new ArrayList<>();
        for (BsonValue v : meta.getArray(META_FLASKS)) {
            String entry = v.asString().getValue();
            int comma = entry.lastIndexOf(',');
            if (comma < 0) continue;
            try {
                result.add(new ItemStack(entry.substring(0, comma), Integer.parseInt(entry.substring(comma + 1))));
            } catch (NumberFormatException ignored) {
            }
        }
        return Collections.unmodifiableList(result);
    }

    public static int getFlaskCount(ItemStack pouch) {
        BsonDocument meta = pouch.getMetadata();
        if (meta == null || !meta.containsKey(META_FLASKS)) return 0;
        return meta.getArray(META_FLASKS).size();
    }

    public static boolean isFull(ItemStack pouch) {
        return getFlaskCount(pouch) >= MAX_SLOTS;
    }

    /**
     * Returns a new ItemStack with the flask appended, or the original if full/invalid.
     */
    public static ItemStack withFlaskAdded(ItemStack pouch, ItemStack flask) {
        if (isFull(pouch) || !flask.getItemId().startsWith(FLASK_ITEM_PREFIX)) return pouch;
        BsonDocument meta = pouch.getMetadata();
        List<BsonValue> values = new ArrayList<>();
        if (meta != null && meta.containsKey(META_FLASKS)) {
            values.addAll(meta.getArray(META_FLASKS));
        }
        values.add(new BsonString(flask.getItemId() + "," + flask.getQuantity()));
        return pouch.withMetadata(META_FLASKS, new BsonArray(values));
    }

    /**
     * Returns a new ItemStack with the flask at {@code index} removed,
     * or the original if the index is out of range.
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
package net.pitan76.assetbridge.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds the loot table JSON a bridged block needs in order to drop itself.
 *
 * <p>Written by hand rather than through {@code LootTable.Builder} so it stays in the
 * Minecraft-free half of the mod and can be tested without the game. The shape is the one
 * vanilla generates for a simple block.
 */
public class LootTables {
    private LootTables() {
    }

    /** A single guaranteed drop of {@code itemId}, lost to an explosion like vanilla blocks. */
    public static JsonObject dropSelf(String itemId) {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", itemId);

        JsonArray entries = new JsonArray();
        entries.add(entry);

        JsonObject condition = new JsonObject();
        condition.addProperty("condition", "minecraft:survives_explosion");

        JsonArray conditions = new JsonArray();
        conditions.add(condition);

        JsonObject pool = new JsonObject();
        pool.addProperty("rolls", 1);
        pool.addProperty("bonus_rolls", 0);
        pool.add("entries", entries);
        pool.add("conditions", conditions);

        JsonArray pools = new JsonArray();
        pools.add(pool);

        JsonObject root = new JsonObject();
        root.addProperty("type", "minecraft:block");
        root.add("pools", pools);
        return root;
    }
}

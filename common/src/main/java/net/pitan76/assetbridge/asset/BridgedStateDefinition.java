package net.pitan76.assetbridge.asset;

import java.util.List;

/**
 * The properties a bridged block needs so that the original blockstate file can be used
 * as-is. An empty definition means the block is registered without properties and gets a
 * generated single-variant blockstate instead.
 */
public record BridgedStateDefinition(List<BridgedProperty> properties) {
    private static final BridgedStateDefinition EMPTY = new BridgedStateDefinition(List.of());

    public BridgedStateDefinition {
        properties = List.copyOf(properties);
    }

    public static BridgedStateDefinition empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }
}

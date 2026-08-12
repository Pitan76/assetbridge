package net.pitan76.assetbridge.asset;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The properties a bridged block needs so that the original blockstate file can be used
 * as-is. An empty definition means the block is registered without properties and gets a
 * generated single-variant blockstate instead.
 */
public class BridgedStateDefinition {
    private final List<BridgedProperty> properties;

    private static final BridgedStateDefinition EMPTY =
            new BridgedStateDefinition(Collections.<BridgedProperty>emptyList());

    public BridgedStateDefinition(List<BridgedProperty> properties) {
        this.properties = Collections.unmodifiableList(new ArrayList<>(properties));
    }

    public List<BridgedProperty> properties() {
        return properties;
    }

    public static BridgedStateDefinition empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return properties.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BridgedStateDefinition)) return false;
        BridgedStateDefinition other = (BridgedStateDefinition) o;
        return properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return properties.hashCode();
    }

    @Override
    public String toString() {
        return "BridgedStateDefinition[properties=" + properties + "]";
    }
}

package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Collects the items an archive might contribute, so they can be resolved once everything
 * has been read.
 *
 * <p>Nothing can be decided while scanning: a block item takes precedence over a standalone
 * item of the same id, and a 1.21.4+ item definition takes precedence over guessing from an
 * item model. Within each of those sources, the first archive to claim an id wins.
 */
public class ItemCandidates {
    /** Declared outright by an item definition file — the mod's own list of its items. */
    private final Map<String, BridgedItemDefinition> fromDefinitions = new LinkedHashMap<>();
    /** Guessed from an item model, which may just as well belong to a block. */
    private final Map<String, BridgedItemDefinition> fromModels = new LinkedHashMap<>();
    private final Set<String> namespacesWithDefinitions = new HashSet<>();

    public void addDefinition(String namespace, @Nullable String name, String archive, AssetVersion version) {
        namespacesWithDefinitions.add(namespace);
        add(fromDefinitions, namespace, name, archive, version);
    }

    public void addModel(String namespace, @Nullable String name, String archive, AssetVersion version) {
        add(fromModels, namespace, name, archive, version);
    }

    private static void add(Map<String, BridgedItemDefinition> into, String namespace, @Nullable String name,
                            String archive, AssetVersion version) {
        if (name == null) return;

        into.putIfAbsent(namespace + ":" + name, new BridgedItemDefinition(namespace, name, archive, version));
    }

    /**
     * @param claimedByBlocks ids that a bridged block's own item already owns
     * @return the items that belong to no block, in the order they were found
     */
    public Collection<BridgedItemDefinition> resolve(Set<String> claimedByBlocks) {
        Map<String, BridgedItemDefinition> resolved = new LinkedHashMap<>(fromDefinitions);
        for (Map.Entry<String, BridgedItemDefinition> candidate : fromModels.entrySet()) {
            // A namespace that ships item definitions has already listed everything it has;
            // its remaining item models are block items and shared fragments.
            if (namespacesWithDefinitions.contains(candidate.getValue().namespace())) continue;
            resolved.putIfAbsent(candidate.getKey(), candidate.getValue());
        }
        claimedByBlocks.forEach(resolved::remove);
        return resolved.values();
    }
}

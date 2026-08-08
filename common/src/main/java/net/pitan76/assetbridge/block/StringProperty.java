package net.pitan76.assetbridge.block;

import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A block property over an arbitrary set of strings.
 *
 * <p>Vanilla's {@code EnumProperty} needs an {@code Enum} implementing {@code StringRepresentable},
 * which cannot be produced for value sets that are only known at runtime. Since
 * {@code Property} is generic over any {@code Comparable}, {@code String} works directly.
 */
public final class StringProperty extends Property<String> {
    private final Set<String> values;

    private StringProperty(String name, Set<String> values) {
        super(name, String.class);
        this.values = values;
    }

    public static StringProperty create(String name, Collection<String> values) {
        return new StringProperty(name, new LinkedHashSet<>(values));
    }

    @Override
    public Collection<String> getPossibleValues() {
        return values;
    }

    @Override
    public String getName(String value) {
        return value;
    }

    @Override
    public Optional<String> getValue(String name) {
        return values.contains(name) ? Optional.of(name) : Optional.empty();
    }

    /**
     * The base class compares on name and value type only, which would make two bridged
     * properties that happen to share a name interchangeable even with different values.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof StringProperty property
                && super.equals(other)
                && values.equals(property.values);
    }

    /** {@code Property#hashCode} is final and delegates here. */
    @Override
    public int generateHashCode() {
        return 31 * super.generateHashCode() + values.hashCode();
    }
}

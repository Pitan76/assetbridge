package net.pitan76.assetbridge.block;

import net.minecraft.world.level.block.state.properties.Property;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * A block property over an arbitrary set of strings.
 *
 * <p>Vanilla's {@code EnumProperty} needs an {@code Enum} implementing {@code StringRepresentable},
 * which cannot be produced for value sets that are only known at runtime. Since
 * {@code Property} is generic over any {@code Comparable}, {@code String} works directly.
 */
public class StringProperty extends Property<String> {
    /**
     * A list rather than a set: 26.1 narrowed {@code getPossibleValues} to {@code List} and added
     * {@code getInternalIndex}, both of which want a defined order. Duplicates are dropped on the
     * way in, so it still behaves as a set of values.
     */
    private final List<String> values;

    private StringProperty(String name, List<String> values) {
        super(name, String.class);
        this.values = values;
    }

    public static StringProperty create(String name, Collection<String> values) {
        return new StringProperty(name, List.copyOf(new LinkedHashSet<>(values)));
    }

    /**
     * Returning {@code List} rather than {@code Collection} satisfies 26.1 and remains a valid
     * covariant override of the older {@code Collection}-returning declaration.
     */
    @Override
    public List<String> getPossibleValues() {
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
     * Abstract as of 26.1 and absent before it, so this deliberately carries no {@code @Override}:
     * the annotation would fail to compile on the older nodes, while an extra method there is
     * simply unused.
     */
    public int getInternalIndex(String value) {
        return values.indexOf(value);
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

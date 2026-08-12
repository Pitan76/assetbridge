package net.pitan76.assetbridge.block;

import com.google.common.base.Optional;
import net.minecraft.block.properties.IProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * A block property over an arbitrary set of strings, matching 1.12.2's {@code IProperty<T>}.
 *
 * <p>Vanilla's {@code PropertyEnum} needs an actual {@code Enum}, which cannot be produced for
 * value sets only known at runtime. {@code IProperty} only requires {@code Comparable}, so
 * {@code String} works directly -- the same reasoning as the modern-version {@code Property}
 * subclass this mirrors.
 *
 * <p>Not currently used by {@link BridgedBlock}: on 1.12.2, bridged blocks are registered
 * without any {@code IProperty}/metadata variants at all (see {@link BridgedBlock}), so this
 * class exists only so the shape of a bridged property is representable here too, the same
 * way it is on every other supported version.
 */
public class StringProperty implements IProperty<String> {
    private final String name;
    private final List<String> values;

    private StringProperty(String name, List<String> values) {
        this.name = name;
        this.values = values;
    }

    public static StringProperty create(String name, Collection<String> values) {
        return new StringProperty(name, Collections.unmodifiableList(new ArrayList<>(new LinkedHashSet<>(values))));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Collection<String> getAllowedValues() {
        return values;
    }

    @Override
    public Class<String> getValueClass() {
        return String.class;
    }

    @Override
    public Optional<String> parseValue(String value) {
        return values.contains(value) ? Optional.of(value) : Optional.<String>absent();
    }

    @Override
    public String getName(String value) {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StringProperty)) return false;
        StringProperty property = (StringProperty) other;
        return name.equals(property.name) && values.equals(property.values);
    }

    @Override
    public int hashCode() {
        return 31 * name.hashCode() + values.hashCode();
    }
}

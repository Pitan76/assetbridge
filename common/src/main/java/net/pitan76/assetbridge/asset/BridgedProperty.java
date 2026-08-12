package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A blockstate property recovered from an external blockstate file.
 *
 * <p>Version-neutral: it only records the name, the values seen and which shape they take.
 * Turning this into a Minecraft {@code Property} is the provider layer's job.
 *
 * @param values the values in the order they were first seen; the first one becomes the default
 */
public class BridgedProperty {
    private final String name;
    private final List<String> values;
    private final Kind kind;

    public BridgedProperty(String name, List<String> values, Kind kind) {
        this.name = name;
        this.values = values;
        this.kind = kind;
    }

    public String name() {
        return name;
    }

    public List<String> values() {
        return values;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BridgedProperty)) return false;
        BridgedProperty other = (BridgedProperty) o;
        return name.equals(other.name) && values.equals(other.values) && kind == other.kind;
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + values.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "BridgedProperty[name=" + name + ", values=" + values + ", kind=" + kind + "]";
    }

    public enum Kind {
        BOOLEAN,
        INTEGER,
        STRING
    }

    /** Minecraft rejects property names and values outside this shape. */
    private static final Pattern VALID = Pattern.compile("[a-z0-9_]+");

    private static final Set<String> BOOLEANS =
            Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList("true", "false")));

    /**
     * @return the property, or {@code null} when Minecraft could not represent it
     *         (an illegal name or value). Callers must then avoid passing the blockstate
     *         through, because the model loader would reject the unknown property.
     */
    @Nullable
    public static BridgedProperty of(String name, List<String> values) {
        if (values.isEmpty() || !VALID.matcher(name).matches()) return null;
        for (String value : values) {
            if (!VALID.matcher(value).matches()) return null;
        }
        return new BridgedProperty(name, Collections.unmodifiableList(new ArrayList<>(values)), kindOf(values));
    }

    public String defaultValue() {
        return values.get(0);
    }

    public int min() {
        return orElseThrow(values.stream().mapToInt(Integer::parseInt).min());
    }

    public int max() {
        return orElseThrow(values.stream().mapToInt(Integer::parseInt).max());
    }

    private static int orElseThrow(java.util.OptionalInt value) {
        if (!value.isPresent()) throw new NoSuchElementException("No value present");
        return value.getAsInt();
    }

    private static Kind kindOf(List<String> values) {
        if (values.size() == 2 && copyOfSet(values).equals(BOOLEANS)) return Kind.BOOLEAN;
        return isContiguousRange(values) ? Kind.INTEGER : Kind.STRING;
    }

    /**
     * Minecraft's integer property is a range, so values with a gap in them (or written with
     * a leading zero) have to stay strings.
     */
    private static boolean isContiguousRange(List<String> values) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (String value : values) {
            int parsed;
            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return false;
            }
            if (parsed < 0 || !value.equals(Integer.toString(parsed))) return false;
            min = Math.min(min, parsed);
            max = Math.max(max, parsed);
        }
        return max - min + 1 == copyOfSet(values).size();
    }

    private static Set<String> copyOfSet(List<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }
}

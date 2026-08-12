package net.pitan76.assetbridge.asset;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A blockstate property recovered from an external blockstate file.
 *
 * <p>Version-neutral: it only records the name, the values seen and which shape they take.
 * Turning this into a Minecraft {@code Property} is the provider layer's job.
 */
public class BridgedProperty {
    public final String name;
    public final List<String> values;
    public final Kind kind;

    /**
     * @param values the values in the order they were first seen; the first one becomes the default
     */
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

    public enum Kind {
        BOOLEAN,
        INTEGER,
        STRING
    }

    /** Minecraft rejects property names and values outside this shape. */
    private static final Pattern VALID = Pattern.compile("[a-z0-9_]+");

    private static final Set<String> BOOLEANS = Set.of("true", "false");

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
        return new BridgedProperty(name, List.copyOf(values), kindOf(values));
    }

    public String defaultValue() {
        return values.get(0);
    }

    public int min() {
        return values.stream().mapToInt(Integer::parseInt).min().orElseThrow();
    }

    public int max() {
        return values.stream().mapToInt(Integer::parseInt).max().orElseThrow();
    }

    private static Kind kindOf(List<String> values) {
        if (values.size() == 2 && Set.copyOf(values).equals(BOOLEANS)) return Kind.BOOLEAN;
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
        return max - min + 1 == Set.copyOf(values).size();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BridgedProperty)) return false;
        BridgedProperty other = (BridgedProperty) obj;
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
        return "BridgedProperty{" +
                "name='" + name + '\'' +
                ", values=" + values +
                ", kind=" + kind +
                '}';
    }
}

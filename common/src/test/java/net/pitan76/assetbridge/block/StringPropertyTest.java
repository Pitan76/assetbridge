package net.pitan76.assetbridge.block;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StringPropertyTest {
    @Test
    void keepsTheDeclaredValuesInOrder() {
        StringProperty property = StringProperty.create("facing", List.of("north", "south"));

        assertEquals(List.of("north", "south"), List.copyOf(property.getPossibleValues()));
        assertEquals(Optional.of("north"), property.getValue("north"));
        assertEquals(Optional.empty(), property.getValue("west"));
        assertEquals("north", property.getName("north"));
    }

    @Test
    void propertiesWithTheSameNameButDifferentValuesAreNotEqual() {
        // The base class compares on name and value type only, which would make these two
        // interchangeable and let one block's property leak into another's state.
        StringProperty four = StringProperty.create("facing", List.of("north", "south", "east", "west"));
        StringProperty two = StringProperty.create("facing", List.of("north", "south"));

        assertNotEquals(four, two);
        assertNotEquals(four.hashCode(), two.hashCode());
    }

    @Test
    void propertiesWithTheSameNameAndValuesAreEqual() {
        StringProperty one = StringProperty.create("facing", List.of("north", "south"));
        StringProperty other = StringProperty.create("facing", List.of("north", "south"));

        assertEquals(one, other);
        assertEquals(one.hashCode(), other.hashCode());
    }
}

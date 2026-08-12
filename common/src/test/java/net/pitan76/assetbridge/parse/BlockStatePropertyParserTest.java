package net.pitan76.assetbridge.parse;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.asset.BridgedProperty;
import net.pitan76.assetbridge.asset.BridgedProperty.Kind;
import net.pitan76.assetbridge.asset.BridgedStateDefinition;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStatePropertyParserTest {
    @Test
    void collectsPropertiesFromVariantKeys() {
        BridgedStateDefinition states = parse("\n                {\"variants\": {\n                    \"facing=north,half=bottom\": {\"model\": \"examplemod:block/a\"},\n                    \"facing=south,half=top\":    {\"model\": \"examplemod:block/b\"}\n                }}");

        assertEquals(Arrays.asList("facing", "half"), names(states));
        assertEquals(Arrays.asList("north", "south"), states.properties().get(0).values());
        assertEquals(Arrays.asList("bottom", "top"), states.properties().get(1).values());
    }

    @Test
    void theFirstValueSeenBecomesTheDefault() {
        BridgedStateDefinition states = parse("\n                {\"variants\": {\n                    \"facing=south\": {\"model\": \"examplemod:block/a\"},\n                    \"facing=north\": {\"model\": \"examplemod:block/b\"}\n                }}");

        assertEquals("south", states.properties().get(0).defaultValue());
    }

    @Test
    void reportsNoPropertiesForASingleVariantBlock() {
        assertTrue(parse("{\"variants\": {\"\": {\"model\": \"examplemod:block/a\"}}}").isEmpty());
    }

    @Test
    void collectsPropertiesFromMultipartConditions() {
        BridgedStateDefinition states = parse("\n                {\"multipart\": [\n                    {\"apply\": {\"model\": \"examplemod:block/post\"}},\n                    {\"when\": {\"north\": \"true\"}, \"apply\": {\"model\": \"examplemod:block/side\"}},\n                    {\"when\": {\"south\": \"true\"}, \"apply\": {\"model\": \"examplemod:block/side\"}}\n                ]}");

        assertEquals(Arrays.asList("north", "south"), names(states));
    }

    @Test
    void expandsAlternativeValuesAndNestedConditions() {
        BridgedStateDefinition states = parse("\n                {\"multipart\": [\n                    {\"when\": {\"OR\": [\n                        {\"facing\": \"north|south\"},\n                        {\"facing\": \"east\", \"lit\": \"true\"}\n                    ]}, \"apply\": {\"model\": \"examplemod:block/a\"}}\n                ]}");

        assertEquals(Arrays.asList("facing", "lit"), names(states));
        assertEquals(Arrays.asList("north", "south", "east"), states.properties().get(0).values());
    }

    @Test
    void detectsBooleanProperties() {
        BridgedStateDefinition states = parse("\n                {\"variants\": {\"lit=true\": {\"model\": \"a\"}, \"lit=false\": {\"model\": \"b\"}}}");

        assertEquals(Kind.BOOLEAN, states.properties().get(0).kind());
    }

    @Test
    void detectsContiguousIntegerProperties() {
        BridgedStateDefinition states = parse("\n                {\"variants\": {\n                    \"level=0\": {\"model\": \"a\"}, \"level=2\": {\"model\": \"c\"}, \"level=1\": {\"model\": \"b\"}\n                }}");

        BridgedProperty level = states.properties().get(0);
        assertEquals(Kind.INTEGER, level.kind());
        assertEquals(0, level.min());
        assertEquals(2, level.max());
    }

    @Test
    void integersWithAGapStayStrings() {
        // IntegerProperty is a range, so 0/1/3 cannot be expressed as one.
        BridgedStateDefinition states = parse("\n                {\"variants\": {\"level=0\": {\"model\": \"a\"}, \"level=1\": {\"model\": \"b\"}, \"level=3\": {\"model\": \"c\"}}}");

        assertEquals(Kind.STRING, states.properties().get(0).kind());
    }

    @Test
    void everythingElseIsAStringProperty() {
        BridgedStateDefinition states = parse("\n                {\"variants\": {\"facing=north\": {\"model\": \"a\"}, \"facing=south\": {\"model\": \"b\"}}}");

        assertEquals(Kind.STRING, states.properties().get(0).kind());
    }

    @Test
    void refusesVariantKeysThatAreNotAssignments() {
        // Pre-1.13 'normal' has to be rewritten by the converter first; if it reaches the
        // parser we must not claim the blockstate can be passed through.
        assertNull(BlockStatePropertyParser.parse(json("\n                {\"variants\": {\"normal\": {\"model\": \"examplemod:block/a\"}}}")));
    }

    @Test
    void refusesPropertiesMinecraftCannotRegister() {
        assertNull(BlockStatePropertyParser.parse(json("\n                {\"variants\": {\"Facing=north\": {\"model\": \"a\"}}}")));
        assertNull(BlockStatePropertyParser.parse(json("\n                {\"variants\": {\"facing=North\": {\"model\": \"a\"}}}")));
        assertNull(BlockStatePropertyParser.parse(json("\n                {\"variants\": {\"facing=north-east\": {\"model\": \"a\"}}}")));
    }

    private static List<String> names(BridgedStateDefinition states) {
        return states.properties().stream().map(BridgedProperty::name).toList();
    }

    private static BridgedStateDefinition parse(String text) {
        BridgedStateDefinition states = BlockStatePropertyParser.parse(json(text));
        assertNotNull(states);
        return states;
    }

    private static JsonObject json(String text) {
        JsonObject parsed = Json.parse(text);
        assertNotNull(parsed);
        return parsed;
    }
}

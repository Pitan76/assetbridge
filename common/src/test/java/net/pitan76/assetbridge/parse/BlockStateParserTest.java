package net.pitan76.assetbridge.parse;

import com.google.gson.JsonObject;
import net.pitan76.assetbridge.util.Json;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockStateParserTest {
    @Test
    void prefersTheEmptyVariant() {
        JsonObject json = parse("{\"variants\": {\"facing=north\": {\"model\": \"examplemod:block/wrong\"},\"\": {\"model\": \"examplemod:block/right\"}}}");

        assertEquals("examplemod:block/right", BlockStateParser.findModel(json));
    }

    @Test
    void fallsBackToTheFirstVariant() {
        JsonObject json = parse("{\"variants\": {\"facing=north\": {\"model\": \"examplemod:block/first\"},\"facing=south\": {\"model\": \"examplemod:block/second\"}}}");

        assertEquals("examplemod:block/first", BlockStateParser.findModel(json));
    }

    @Test
    void takesTheFirstEntryOfAWeightedVariant() {
        JsonObject json = parse("{\"variants\": {\"\": [ {\"model\": \"examplemod:block/a\", \"weight\": 3},{\"model\": \"examplemod:block/b\"}]}}");

        assertEquals("examplemod:block/a", BlockStateParser.findModel(json));
    }

    @Test
    void prefersAnUnconditionalMultipartEntry() {
        JsonObject json = parse("{\"multipart\": [{\"when\": {\"north\": \"true\"}, \"apply\": {\"model\": \"examplemod:block/side\"}},{\"apply\": {\"model\": \"examplemod:block/core\"}}]}");

        assertEquals("examplemod:block/core", BlockStateParser.findModel(json));
    }

    @Test
    void fallsBackToAConditionalMultipartEntry() {
        JsonObject json = parse("{\"multipart\": [{\"when\": {\"north\": \"true\"}, \"apply\": {\"model\": \"examplemod:block/side\"}}]}");

        assertEquals("examplemod:block/side", BlockStateParser.findModel(json));
    }

    @Test
    void acceptsUnqualifiedModelReferences() {
        // Qualifying with the namespace is the pipeline's job; the parser reports what it read.
        assertEquals("block/foo", BlockStateParser.findModel(parse("{\"variants\": {\"\": {\"model\": \"block/foo\"}}}")));
    }

    @Test
    void returnsNullWhenThereIsNoModel() {
        assertNull(BlockStateParser.findModel(parse("{}")));
        assertNull(BlockStateParser.findModel(parse("{\"variants\": {}}")));
        assertNull(BlockStateParser.findModel(parse("{\"variants\": {\"\": []}}")));
        assertNull(BlockStateParser.findModel(parse("{\"multipart\": []}")));
    }

    private static JsonObject parse(String json) {
        JsonObject parsed = Json.parse(json);
        assertNotNull(parsed);
        return parsed;
    }
}

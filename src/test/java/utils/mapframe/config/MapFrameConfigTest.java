package utils.mapframe.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapFrameConfigTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsDocumentedDefaultFile() {
        Path path = temporaryDirectory.resolve(MapFrameConfig.FILE_NAME);
        List<String> warnings = new ArrayList<>();

        MapFrameConfig config = MapFrameConfig.load(path, warnings::add);

        assertEquals(UpdateMode.PLAYER_PROXIMITY, config.updateMode());
        assertTrue(warnings.isEmpty());
        assertTrue(Files.exists(path));
        assertFileContains(path, "update_mode = \"player_proximity\"");
        assertFileContains(path, "Performance warning for \"loaded_chunks\"");
        assertFileContains(path, "never loads or generates");
    }

    @Test
    void acceptsLoadedChunksMode() throws IOException {
        Path path = write("update_mode = \"loaded_chunks\"\n");

        MapFrameConfig config = MapFrameConfig.load(path, message -> { });

        assertEquals(UpdateMode.LOADED_CHUNKS, config.updateMode());
    }

    @Test
    void fallsBackWithoutOverwritingUnknownValue() throws IOException {
        String invalid = "update_mode = \"warp_speed\"\n";
        Path path = write(invalid);
        List<String> warnings = new ArrayList<>();

        MapFrameConfig config = MapFrameConfig.load(path, warnings::add);

        assertEquals(UpdateMode.PLAYER_PROXIMITY, config.updateMode());
        assertFalse(warnings.isEmpty());
        assertEquals(invalid, Files.readString(path));
    }

    @Test
    void fallsBackForWrongType() throws IOException {
        Path path = write("update_mode = 42\n");
        List<String> warnings = new ArrayList<>();

        MapFrameConfig config = MapFrameConfig.load(path, warnings::add);

        assertEquals(UpdateMode.PLAYER_PROXIMITY, config.updateMode());
        assertFalse(warnings.isEmpty());
    }

    @Test
    void fallsBackForMalformedToml() throws IOException {
        Path path = write("update_mode = [\n");
        List<String> warnings = new ArrayList<>();

        MapFrameConfig config = MapFrameConfig.load(path, warnings::add);

        assertEquals(UpdateMode.PLAYER_PROXIMITY, config.updateMode());
        assertFalse(warnings.isEmpty());
    }

    private Path write(String contents) throws IOException {
        Path path = temporaryDirectory.resolve(MapFrameConfig.FILE_NAME);
        Files.writeString(path, contents);
        return path;
    }

    private static void assertFileContains(Path path, String expected) {
        try {
            assertTrue(Files.readString(path).contains(expected));
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}

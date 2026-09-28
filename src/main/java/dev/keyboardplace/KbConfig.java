package dev.keyboardplace;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Everything the mod remembers between launches, as a plain mutable bean so gson
 * can fill it in without any custom adapters.
 */
public class KbConfig {

    /** Master switch. Off means the game behaves exactly as it does without the mod. */
    public boolean enabled = true;

    /**
     * Ticks between placements while the use button is held.
     * 1 is one block every tick (20 a second), 4 is what vanilla does on its own,
     * and anything above that is slower than vanilla.
     */
    public int placeIntervalTicks = 2;

    /**
     * Half-width of the square filled around each placement, on the face being
     * built against. 0 places a single block, 1 fills 3x3, 2 fills 5x5.
     *
     * <p>Off by default. It is a building convenience and has nothing to do with
     * keyboard place - left on, it turns every placement into a slab of obsidian.
     */
    public int areaRadius = 0;

    /**
     * Whether the interval also applies to things you throw with right-click: splash
     * and lingering potions and bottles o' enchanting. Vanilla holds those to the same
     * four ticks it holds blocks to, so the same override speeds them up. On by
     * default; off leaves potting and mending at vanilla's five a second.
     */
    public boolean fastThrow = true;

    /**
     * Whether to do any of this while connected to a server. Off by default: a
     * server that runs an anti-cheat will read 20 placements a second as cheating,
     * and that is not a thing to opt somebody into quietly.
     */
    public boolean allowMultiplayer = false;

    public static final int MIN_INTERVAL = 1;
    public static final int MAX_INTERVAL = 10;
    public static final int MAX_RADIUS = 4;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("keyboardplace.json");
    }

    public static KbConfig load() {
        Path file = path();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                KbConfig loaded = GSON.fromJson(reader, KbConfig.class);
                if (loaded != null) {
                    loaded.clamp();
                    return loaded;
                }
            } catch (IOException | RuntimeException e) {
                // A hand-edited config that no longer parses should not stop the game
                // from starting; fall through to defaults and overwrite on the next save.
                KeyboardPlaceClient.LOGGER.warn("Could not read {}, using defaults", file, e);
            }
        }
        return new KbConfig();
    }

    public void save() {
        clamp();
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            KeyboardPlaceClient.LOGGER.warn("Could not write {}", file, e);
        }
    }

    /** Values arrive here from a text file and from commands, so nothing is trusted. */
    public void clamp() {
        placeIntervalTicks = Math.clamp(placeIntervalTicks, MIN_INTERVAL, MAX_INTERVAL);
        areaRadius = Math.clamp(areaRadius, 0, MAX_RADIUS);
    }

    /** "10.0 blocks/sec", for a given interval. */
    public static String describeRate(int intervalTicks) {
        return String.format(java.util.Locale.ROOT, "%.1f blocks/sec", 20.0 / intervalTicks);
    }

    /** "3x3", for the side of the square a given radius fills. */
    public static String describeArea(int radius) {
        return radius <= 0 ? "single block" : (radius * 2 + 1) + "x" + (radius * 2 + 1);
    }
}

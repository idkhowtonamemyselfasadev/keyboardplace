package dev.keyboardplace;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyboardPlaceClient implements ClientModInitializer {

    public static final String MOD_ID = "keyboardplace";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KbConfig config;

    private static KeyMapping toggleKey;

    /** GLFW's "no key". A binding created with this shows as unbound in Controls. */
    private static final int UNBOUND = -1;

    /**
     * Place keys. Hold one down and the operating system repeats the key, roughly 25
     * times a second; every repeat is a real key event that the game turns into a click,
     * and clicks place a block on a path vanilla never throttled. That is the whole
     * mechanism, and it is why this is a keyboard trick - mouse buttons do not repeat.
     *
     * <p>The first is on M so it works without a trip to the controls screen. The other
     * two ship unbound, for anybody who would rather roll several keys than hold one.
     */
    private static final int[] PLACE_KEY_DEFAULTS = {InputConstants.KEY_M, UNBOUND, UNBOUND};
    private static final KeyMapping[] placeKeys = new KeyMapping[PLACE_KEY_DEFAULTS.length];

    public static KbConfig config() {
        // The mixin runs every tick and can be reached before onInitializeClient on a
        // very early tick, so never hand back null.
        if (config == null) {
            config = new KbConfig();
        }
        return config;
    }

    @Override
    public void onInitializeClient() {
        config = KbConfig.load();

        KeyMapping.Category category =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.keyboardplace.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_Y, category));
        for (int i = 0; i < placeKeys.length; i++) {
            placeKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    "key.keyboardplace.place" + (i + 1),
                    InputConstants.Type.KEYSYM, PLACE_KEY_DEFAULTS[i], category));
        }
        ClientTickEvents.END_CLIENT_TICK.register(this::handleKeys);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> registerCommands(dispatcher));
    }

    private void handleKeys(Minecraft mc) {
        KbConfig c = config();
        handlePlaceKeys(mc);
        while (toggleKey.consumeClick()) {
            c.enabled = !c.enabled;
            c.save();
            say(mc, c.enabled
                    ? "Keyboard Place on - " + rate(c) + ", " + KbConfig.describeArea(c.areaRadius)
                    : "Keyboard Place off");
        }
    }

    /** Pumps the place keys. One press, one block - never more than was pressed. */
    private void handlePlaceKeys(Minecraft mc) {
        for (KeyMapping key : placeKeys) {
            while (key.consumeClick()) {
                Placer.pressPlaceKey(mc);
            }
        }
    }

    /** Above the hotbar, not in chat: this fires on a keypress mid-build. */
    private static void say(Minecraft mc, String message) {
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(message), true);
        }
    }

    private static String rate(KbConfig c) {
        return KbConfig.describeRate(c.placeIntervalTicks);
    }

    private void registerCommands(com.mojang.brigadier.CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("kbplace")
                .executes(ctx -> {
                    KbConfig c = config();
                    reply(ctx.getSource(), "Keyboard Place is " + (c.enabled ? "on" : "off")
                            + " - " + rate(c)
                            + ", area " + KbConfig.describeArea(c.areaRadius)
                            + ", pots/xp " + (c.fastThrow ? "on" : "off")
                            + ", " + (c.allowMultiplayer ? "servers allowed" : "singleplayer only"));
                    return 1;
                })
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    KbConfig c = config();
                    c.enabled = true;
                    c.save();
                    reply(ctx.getSource(), "Keyboard Place on - " + rate(c)
                            + ", area " + KbConfig.describeArea(c.areaRadius));
                    return 1;
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    KbConfig c = config();
                    c.enabled = false;
                    c.save();
                    reply(ctx.getSource(), "Keyboard Place off");
                    return 1;
                }))
                .then(ClientCommandManager.literal("speed")
                        .then(ClientCommandManager.argument("ticks",
                                        IntegerArgumentType.integer(KbConfig.MIN_INTERVAL, KbConfig.MAX_INTERVAL))
                                .executes(ctx -> {
                                    KbConfig c = config();
                                    c.placeIntervalTicks = IntegerArgumentType.getInteger(ctx, "ticks");
                                    c.save();
                                    reply(ctx.getSource(), "Placing every " + c.placeIntervalTicks
                                            + " tick(s) - " + rate(c));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("area")
                        .then(ClientCommandManager.argument("radius",
                                        IntegerArgumentType.integer(0, KbConfig.MAX_RADIUS))
                                .executes(ctx -> {
                                    KbConfig c = config();
                                    c.areaRadius = IntegerArgumentType.getInteger(ctx, "radius");
                                    c.save();
                                    reply(ctx.getSource(), "Area: " + KbConfig.describeArea(c.areaRadius));
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("throw")
                        .then(ClientCommandManager.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    KbConfig c = config();
                                    c.fastThrow = BoolArgumentType.getBool(ctx, "enabled");
                                    c.save();
                                    reply(ctx.getSource(), c.fastThrow
                                            ? "Splash pots, lingering pots and XP bottles throw at " + rate(c)
                                            : "Pots and XP bottles back on vanilla's throw rate");
                                    return 1;
                                })))
                .then(ClientCommandManager.literal("multiplayer")
                        .then(ClientCommandManager.argument("allow", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    KbConfig c = config();
                                    c.allowMultiplayer = BoolArgumentType.getBool(ctx, "allow");
                                    c.save();
                                    reply(ctx.getSource(), c.allowMultiplayer
                                            ? "Enabled on servers too. Plenty of servers treat this as cheating."
                                            : "Singleplayer only");
                                    return 1;
                                }))));
    }

    private static void reply(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
    }
}

package dev.keyboardplace;

import dev.keyboardplace.mixin.MinecraftInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The whole behaviour of the mod.
 *
 * <p>Two independent pieces:
 *
 * <ul>
 *   <li><b>Fast place</b> - vanilla parks a four tick cooldown on the use button after
 *       every placement, which caps building at five blocks a second. We overwrite that
 *       cooldown with the configured interval the moment it is set, so the interval is
 *       exact and can be faster or slower than vanilla. Vanilla still does the placing,
 *       so everything it normally checks is still checked. Works for end crystals too:
 *       the same cooldown is what limits how fast those go down, and for splash and
 *       lingering potions and bottles o' enchanting, which vanilla throws off the
 *       very same four tick timer.
 *   <li><b>Area place</b> - after vanilla places a block, fill the square around it on
 *       the same face. Each one of those is a real interaction sent to the server, so
 *       reach and inventory are enforced by the server exactly as usual.
 * </ul>
 */
public final class Placer {

    private Placer() {
    }

    /**
     * What the current right-click was aiming at, captured before vanilla acts on it.
     *
     * <p>It has to be captured beforehand: once the block lands, the world no longer
     * says which position the placement was going to go into. Clicking tall grass, for
     * instance, replaces the grass itself rather than the space in front of it, and
     * after the fact those two cases look identical.
     */
    private record Pending(BlockHitResult hit, BlockPos target, InteractionHand hand) {
    }

    private static Pending pending;

    /**
     * True while the player is genuinely holding the use button to build. Everything
     * the mod does is gated on this.
     */
    private static boolean building(Minecraft mc) {
        KbConfig config = KeyboardPlaceClient.config();
        if (!config.enabled) {
            return false;
        }
        if (mc.player == null || mc.level == null) {
            return false;
        }
        // A screen is open, so the button being down is somebody clicking a menu.
        if (mc.screen != null) {
            return false;
        }
        if (mc.player.isSpectator()) {
            return false;
        }
        if (!mc.options.keyUse.isDown()) {
            return false;
        }
        // Eating, drawing a bow, raising a shield - leave all of that alone.
        if (mc.player.isUsingItem()) {
            return false;
        }
        if (!config.allowMultiplayer && !mc.isSingleplayer()) {
            return false;
        }
        return placingHand(mc.player) != null;
    }

    /**
     * The hand holding something this mod will place or throw, or null.
     *
     * <p>Deliberately a short list rather than "any item". A held right-click on any
     * other item is somebody eating, drawing a bow, throwing pearls or emptying a
     * bucket, and speeding those up is never what was wanted.
     */
    private static InteractionHand placingHand(LocalPlayer player) {
        if (isFastUsable(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (isFastUsable(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    private static boolean isFastUsable(ItemStack stack) {
        return isPlaceable(stack) || (KeyboardPlaceClient.config().fastThrow && isThrowable(stack));
    }

    private static boolean isPlaceable(ItemStack stack) {
        return stack.getItem() instanceof BlockItem || stack.getItem() instanceof EndCrystalItem;
    }

    /**
     * Splash and lingering potions, and bottles o' enchanting. Drinkable potions are
     * a different class entirely and stay on vanilla's drink timer - and the
     * {@code isUsingItem} check above would keep them out anyway.
     */
    private static boolean isThrowable(ItemStack stack) {
        return stack.getItem() instanceof ThrowablePotionItem || stack.getItem() instanceof ExperienceBottleItem;
    }

    /** What is actually in the given hand. */
    private static ItemStack heldIn(LocalPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainHandItem() : player.getOffhandItem();
    }

    /**
     * How many ticks should sit between placements right now, or -1 to leave vanilla's
     * cooldown alone.
     *
     * <p>The tick decrements the cooldown before reading it, so a cooldown of n gives a
     * placement every n ticks: 1 is every tick, 4 is vanilla's own rate, more is slower.
     */
    public static int placeInterval(Minecraft mc) {
        return building(mc) ? KeyboardPlaceClient.config().placeIntervalTicks : -1;
    }

    /**
     * One press of a place key: exactly one placement, wherever the crosshair is.
     *
     * <p>Nothing here is on a timer. The key has to be pressed for each block, which is
     * what makes this ordinary input rather than automation, and it is why binding place
     * to keys is a thing people do openly rather than something that needs hiding.
     */
    public static void pressPlaceKey(Minecraft mc) {
        if (!KeyboardPlaceClient.config().enabled) {
            return;
        }
        if (mc.player == null || mc.level == null || mc.screen != null) {
            return;
        }
        ((MinecraftInvoker) mc).keyboardplace$startUseItem();
    }

    /** Runs just before vanilla acts on a right-click. */
    public static void beforeUseItem(Minecraft mc) {
        pending = null;
        if (KeyboardPlaceClient.config().areaRadius <= 0 || !building(mc)) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        InteractionHand hand = placingHand(mc.player);
        // Blocks only. Filling a 3x3 with end crystals would burn nine of them on a
        // spot that only ever needed the one, and a potion is not placed at all.
        if (hand == null || !(heldIn(mc.player, hand).getItem() instanceof BlockItem)) {
            return;
        }
        pending = new Pending(hit, placementTarget(mc.level, hit.getBlockPos(), hit.getDirection()), hand);
    }

    /** Runs just after vanilla has acted on a right-click. */
    public static void afterUseItem(Minecraft mc) {
        Pending p = pending;
        pending = null;
        if (p == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        // Right-clicking a chest or a door with a block in hand goes through the same
        // code path but places nothing. If the target is still empty, or a container
        // screen just opened, this was an interaction and not a placement - filling a
        // square of blocks around somebody's chest is the last thing they want.
        if (mc.screen != null || mc.level.getBlockState(p.target()).canBeReplaced()) {
            return;
        }

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        BlockHitResult hit = p.hit();
        Direction face = hit.getDirection();
        BlockPos clicked = hit.getBlockPos();

        // Two directions spanning the face being built against, so the fill lies flat
        // on that surface: across the ground for a floor, up a wall for a wall.
        Direction across;
        Direction along;
        switch (face.getAxis()) {
            case Y -> {
                across = Direction.EAST;
                along = Direction.SOUTH;
            }
            case X -> {
                across = Direction.UP;
                along = Direction.SOUTH;
            }
            default -> {
                across = Direction.UP;
                along = Direction.EAST;
            }
        }
        Vec3 acrossVec = across.getUnitVec3();
        Vec3 alongVec = along.getUnitVec3();

        int radius = KeyboardPlaceClient.config().areaRadius;
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                if (i == 0 && j == 0) {
                    continue; // vanilla already did the middle one
                }
                BlockPos against = clicked.relative(across, i).relative(along, j);
                BlockPos target = placementTarget(level, against, face);
                if (!level.getBlockState(target).canBeReplaced()) {
                    continue;
                }
                // The same padding the server allows itself, so anything that would be
                // rejected as out of reach is never sent in the first place.
                if (!player.isWithinBlockInteractionRange(against, 1.0)
                        || !player.isWithinBlockInteractionRange(target, 1.0)) {
                    continue;
                }
                // Shift the hit point by the same offset as the block, keeping it inside
                // the face being clicked. The server rejects a hit point that sits more
                // than a block away from the position it claims to be on.
                Vec3 where = hit.getLocation().add(acrossVec.scale(i)).add(alongVec.scale(j));
                mc.gameMode.useItemOn(player, p.hand(),
                        new BlockHitResult(where, face, against, hit.isInside()));
            }
        }
    }

    /**
     * Where a block clicked onto {@code against} actually ends up: inside that position
     * if what is there can be replaced, otherwise in the space off its clicked face.
     * This mirrors what vanilla's placement context decides.
     */
    private static BlockPos placementTarget(ClientLevel level, BlockPos against, Direction face) {
        return level.getBlockState(against).canBeReplaced() ? against : against.relative(face);
    }
}

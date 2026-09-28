package dev.keyboardplace.mixin;

import dev.keyboardplace.Placer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    private int rightClickDelay;

    /**
     * Catch a cooldown left over from before the mod was switched on, or from a longer
     * interval than the one now configured, so a change takes effect on the next tick
     * rather than after the old wait has run out.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void keyboardplace$fastPlace(CallbackInfo ci) {
        int interval = Placer.placeInterval((Minecraft) (Object) this);
        if (interval >= 0 && this.rightClickDelay > interval) {
            this.rightClickDelay = interval;
        }
    }

    /**
     * Bracket vanilla's right-click handling. Before, to record what was being aimed at
     * while the world still shows it; after, to fill the rest of the square once the
     * middle block has actually landed.
     */
    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void keyboardplace$beforeUseItem(CallbackInfo ci) {
        Placer.beforeUseItem((Minecraft) (Object) this);
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void keyboardplace$afterUseItem(CallbackInfo ci) {
        // Vanilla has just written its own four ticks into the cooldown. Replacing it
        // here rather than clamping it later is what makes the interval exact, and is
        // the only way to ask for a gap longer than vanilla's own.
        int interval = Placer.placeInterval((Minecraft) (Object) this);
        if (interval >= 0) {
            this.rightClickDelay = interval;
        }
        Placer.afterUseItem((Minecraft) (Object) this);
    }
}

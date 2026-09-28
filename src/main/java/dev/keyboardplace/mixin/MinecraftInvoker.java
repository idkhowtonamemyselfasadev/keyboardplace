package dev.keyboardplace.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches vanilla's own "use the held item once" method - the same call the game makes
 * for a single click of the use button, on the path that has no cooldown on it.
 *
 * <p>A place key calls this once per press and never on its own. That is the whole
 * difference between the place keys and fast place: one block per finger movement is
 * ordinary input, and a timer that keeps placing while a button is held is not.
 */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {

    @Invoker("startUseItem")
    void keyboardplace$startUseItem();
}

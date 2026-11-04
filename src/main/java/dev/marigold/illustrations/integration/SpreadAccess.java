package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=SpreadBookScreen.class,remap=false)
public interface SpreadAccess {
    @Accessor("currentSpread") int illustrations$spread();
    @Accessor("leftPos") int illustrations$left();
    @Accessor("topPos") int illustrations$top();
}

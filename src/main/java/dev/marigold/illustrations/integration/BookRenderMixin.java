package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen;
import dev.marigold.illustrations.client.ClientIntegration;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=SpreadBookScreen.class,remap=false)
abstract class BookRenderMixin {
    @Inject(method="renderBook",at=@At("TAIL"))
    private void illustrations$render(GuiGraphics graphics,int mouseX,int mouseY,float partial,CallbackInfo ci){
        ClientIntegration.renderArt((SpreadBookScreen)(Object)this,graphics);
    }
}

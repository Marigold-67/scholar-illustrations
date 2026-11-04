package dev.marigold.illustrations.integration;

import dev.marigold.illustrations.client.ClientIntegration;
import io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen;
import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scholar's writable editor overrides renderBook without calling the base implementation. */
@Mixin(value=SpreadBookEditScreen.class,remap=false)
abstract class EditBookRenderMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void illustrations$renderAboveText(GuiGraphics graphics,int mouseX,int mouseY,float partial,CallbackInfo ci){
        ClientIntegration.renderArt((SpreadBookScreen)(Object)this,graphics,true);
    }
    @Inject(method="renderBook",at=@At("TAIL"))
    private void illustrations$renderEditorArt(GuiGraphics graphics,int mouseX,int mouseY,float partial,CallbackInfo ci){
        ClientIntegration.renderArt((SpreadBookScreen)(Object)this,graphics);
    }
}

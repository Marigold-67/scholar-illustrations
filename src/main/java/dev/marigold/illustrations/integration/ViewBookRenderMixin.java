package dev.marigold.illustrations.integration;

import dev.marigold.illustrations.client.ClientIntegration;
import io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen;
import io.github.mortuusars.scholar.client.gui.screen.view.SpreadBookViewScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render after both page texts, before Scholar's link hover tooltip and controls. */
@Mixin(value=SpreadBookViewScreen.class,remap=false)
abstract class ViewBookRenderMixin {
    @Inject(method="render",at=@At(value="INVOKE",target="Lio/github/mortuusars/scholar/client/gui/screen/view/SpreadBookViewScreen;renderPageContents(Lnet/minecraft/client/gui/GuiGraphics;Ljava/util/List;II)V",ordinal=1,shift=At.Shift.AFTER))
    private void illustrations$renderAboveText(GuiGraphics graphics,int mouseX,int mouseY,float partial,CallbackInfo ci){
        ClientIntegration.renderArt((SpreadBookScreen)(Object)this,graphics,true);
    }
}

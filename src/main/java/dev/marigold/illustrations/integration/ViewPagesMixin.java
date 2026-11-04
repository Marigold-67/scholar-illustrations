package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.view.SpreadBookViewScreen;
import io.github.mortuusars.scholar.client.gui.screen.view.BookViewAccess;
import dev.marigold.illustrations.Artwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=SpreadBookViewScreen.class,remap=false)
abstract class ViewPagesMixin {
    @Shadow public abstract BookViewAccess getBookAccess();
    @Inject(method="getPageCount",at=@At("RETURN"),cancellable=true)
    private void illustrations$count(CallbackInfoReturnable<Integer> ci){
        ci.setReturnValue(Math.max(ci.getReturnValue(),Artwork.read(getBookAccess().getBook()).pageCount()));
    }
}

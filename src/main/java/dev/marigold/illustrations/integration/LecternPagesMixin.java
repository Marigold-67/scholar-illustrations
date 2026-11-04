package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.menu.LecternSpreadMenu;
import dev.marigold.illustrations.Artwork;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=LecternSpreadMenu.class,remap=false)
abstract class LecternPagesMixin {
    @Inject(method="getPageCount",at=@At("RETURN"),cancellable=true)
    private void illustrations$count(CallbackInfoReturnable<Integer> ci){
        ci.setReturnValue(Math.max(ci.getReturnValue(),Artwork.read(((LecternSpreadMenu)(Object)this).getBook()).pageCount()));
    }
}

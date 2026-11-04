package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import dev.marigold.illustrations.Artwork;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.List;
@Mixin(value=SpreadBookEditScreen.class,remap=false)
abstract class EditPagesMixin {
    @Shadow @Final protected ItemStack bookStack;
    @Shadow @Final protected List<String> pages;
    @Inject(method="removeEmptyTrailingPages",at=@At("TAIL"))
    private void illustrations$keepArtPages(CallbackInfo ci){
        int count=Artwork.read(bookStack).pageCount();
        while(pages.size()<count)pages.add("");
    }
}

package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=SpreadBookEditScreen.class,remap=false)
public interface EditAccess {
    @Accessor("bookStack") ItemStack illustrations$book();
    @Accessor("bookModified") void illustrations$dirty(boolean value);
    @Invoker("saveChanges") void illustrations$save(boolean sign,String title);
}

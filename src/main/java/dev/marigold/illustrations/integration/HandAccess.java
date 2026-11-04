package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.edit.InHandSpreadBookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=InHandSpreadBookEditScreen.class,remap=false)
public interface HandAccess { @Invoker("getBookSlot") int illustrations$slot(); }

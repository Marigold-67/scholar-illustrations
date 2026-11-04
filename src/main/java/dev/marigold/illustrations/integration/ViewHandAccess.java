package dev.marigold.illustrations.integration;
import io.github.mortuusars.scholar.client.gui.screen.view.InHandSpreadBookViewScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=InHandSpreadBookViewScreen.class,remap=false)
public interface ViewHandAccess { @Invoker("getBookSlot") int illustrations$slot(); }

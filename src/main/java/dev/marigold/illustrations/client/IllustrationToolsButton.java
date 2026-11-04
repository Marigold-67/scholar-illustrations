package dev.marigold.illustrations.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Small, narratable pencil button; no extra textures or changes to Scholar resources. */
final class IllustrationToolsButton extends Button {
    private final BooleanSupplier expanded;
    IllustrationToolsButton(int x,int y,OnPress onPress,BooleanSupplier expanded){
        super(x,y,18,18,Component.literal("Illustration tools"),onPress,DEFAULT_NARRATION);
        this.expanded=expanded;
    }
    @Override protected void renderWidget(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        int x=getX(),y=getY();
        g.fill(x,y,x+18,y+18,isHoveredOrFocused()?0xFFAC8552:0xFF795A38);
        g.fill(x+1,y+1,x+17,y+17,expanded.getAsBoolean()?0xFF594A38:0xFF3C342B);
        g.renderOutline(x,y,18,18,isHoveredOrFocused()?0xFFFFDB99:0xFFB19B76);
        // Diagonal pencil with a light barrel, gold edge, and dark point.
        for(int i=0;i<8;i++){
            g.fill(x+4+i,y+11-i,x+6+i,y+13-i,0xFFF1D9A0);
            g.fill(x+6+i,y+11-i,x+7+i,y+12-i,0xFFBD8E43);
        }
        g.fill(x+3,y+13,x+5,y+15,0xFFDDC6A4);
        g.fill(x+3,y+14,x+4,y+15,0xFF292624);
        // A plus or minus makes the collapsed state visible without a large label.
        g.fill(x+11,y+13,x+16,y+14,0xFFFFE8AC);
        if(!expanded.getAsBoolean())g.fill(x+13,y+11,x+14,y+16,0xFFFFE8AC);
    }
}

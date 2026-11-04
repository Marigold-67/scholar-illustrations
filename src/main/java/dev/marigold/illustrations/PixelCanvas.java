package dev.marigold.illustrations;

import java.util.ArrayDeque;
import java.util.Arrays;

/** UI-independent raster editor with bounded stroke history. */
public final class PixelCanvas {
    private int[] pixels;
    private final ArrayDeque<int[]> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public PixelCanvas(int[] initial){
        if(initial.length!=RasterCodec.PIXELS)throw new IllegalArgumentException("Wrong canvas size");
        pixels=initial.clone();
    }
    public int[] pixels(){return pixels.clone();}
    public void checkpoint(){undo.addLast(pixels.clone());while(undo.size()>40)undo.removeFirst();redo.clear();}
    public void undo(){if(!undo.isEmpty()){redo.addLast(pixels);pixels=undo.removeLast();}}
    public void redo(){if(!redo.isEmpty()){undo.addLast(pixels);pixels=redo.removeLast();}}
    public void line(int x0,int y0,int x1,int y1,int size,int color){
        int steps=Math.max(Math.abs(x1-x0),Math.abs(y1-y0));
        for(int i=0;i<=steps;i++){
            int x=steps==0?x0:Math.round(x0+(x1-x0)*(float)i/steps);
            int y=steps==0?y0:Math.round(y0+(y1-y0)*(float)i/steps);
            for(int dy=-(size/2);dy<size-size/2;dy++)for(int dx=-(size/2);dx<size-size/2;dx++){
                int px=x+dx,py=y+dy;
                if(px>=0&&px<114&&py>=0&&py<128&&dx*dx+dy*dy<=(size/2f)*(size/2f)+.5f)pixels[py*114+px]=color;
            }
        }
    }
    public void replace(int[] value){if(value.length!=pixels.length)throw new IllegalArgumentException();pixels=value.clone();}
    public boolean empty(){return Arrays.stream(pixels).allMatch(c->(c>>>24)==0);}
    public static int blend(int below,int above){
        int a=above>>>24,b=below>>>24;
        int alpha=a+b*(255-a)/255;if(alpha==0)return 0;
        int result=alpha<<24;
        for(int shift:new int[]{16,8,0}){
            int channel=(((above>>>shift)&255)*a+((below>>>shift)&255)*b*(255-a)/255)/alpha;
            result|=channel<<shift;
        }
        return result;
    }
}

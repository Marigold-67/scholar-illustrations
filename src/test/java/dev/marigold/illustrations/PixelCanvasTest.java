package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelCanvasTest {
    @Test void strokesInterpolateAndUndoRedoRestorePixels(){
        PixelCanvas canvas=new PixelCanvas(new int[RasterCodec.PIXELS]);
        canvas.checkpoint();canvas.line(0,0,113,127,1,0xFFAA5533);
        assertFalse(canvas.empty());assertEquals(0xFFAA5533,canvas.pixels()[127*114+113]);
        canvas.undo();assertTrue(canvas.empty());canvas.redo();assertFalse(canvas.empty());
    }
    @Test void eraserAndHistoryAreBounded(){
        PixelCanvas canvas=new PixelCanvas(new int[RasterCodec.PIXELS]);
        for(int i=0;i<50;i++){canvas.checkpoint();canvas.line(4,4,4,4,2,0xFF000000|i);}
        for(int i=0;i<41;i++)canvas.undo();
        assertFalse(canvas.empty()); // Only the most recent 40 snapshots are retained.
        canvas.line(4,4,4,4,12,0);assertTrue(canvas.empty());
    }
    @Test void alphaCompositingIsCorrect(){
        assertEquals(0xFFFF0000,PixelCanvas.blend(0xFFFF0000,0));
        assertEquals(0x8000FF00,PixelCanvas.blend(0,0x8000FF00));
        assertEquals(0xFF7F8000,PixelCanvas.blend(0xFFFF0000,0x8000FF00));
    }
}

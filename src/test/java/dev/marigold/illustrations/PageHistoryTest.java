package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PageHistoryTest {
    @org.junit.jupiter.api.Test void undoAndRedoRestoreTextOrder(){
        var history=new PageHistory();int[] drawing=new int[RasterCodec.PIXELS];drawing[0]=0xFF123456;
        history.checkpoint(drawing,null,false);
        var undone=history.undo(drawing,null,true);org.junit.jupiter.api.Assertions.assertFalse(undone.aboveText());
        var redone=history.redo(undone.drawing(),undone.image(),undone.aboveText());
        org.junit.jupiter.api.Assertions.assertTrue(redone.aboveText());org.junit.jupiter.api.Assertions.assertArrayEquals(drawing,redone.drawing());
    }
    @Test void undoAndRedoRestorePlacementWithoutResamplingTheSource(){
        var source=new PageAsset.Source(200,100,new int[20000]);
        var original=new PageAsset.Layer(source,new PageAsset.Rect(0,0,100,50));
        var moved=original.at(new PageAsset.Rect(60,60,40,20));int[] drawing=new int[RasterCodec.PIXELS];
        PageHistory history=new PageHistory();history.checkpoint(drawing,original);
        var undo=history.undo(drawing,moved);assertEquals(original,undo.image());assertSame(source,undo.image().source());
        assertEquals(moved,history.redo(undo.drawing(),undo.image()).image());
    }
}

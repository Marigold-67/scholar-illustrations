package dev.marigold.illustrations;
import java.util.*;

/** Undo snapshots share immutable source pixels; both snapshot count and retained bytes are bounded. */
public final class PageHistory {
    public record State(int[] drawing,PageAsset.Layer image,boolean aboveText){}
    private final ArrayDeque<State> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    public void checkpoint(int[] drawing,PageAsset.Layer image){checkpoint(drawing,image,false);}
    public void checkpoint(int[] drawing,PageAsset.Layer image,boolean aboveText){undo.addLast(new State(drawing.clone(),image,aboveText));redo.clear();trim();}
    public State undo(int[] current,PageAsset.Layer image){
        return undo(current,image,false);
    }
    public State undo(int[] current,PageAsset.Layer image,boolean aboveText){
        if(undo.isEmpty())return null;redo.addLast(new State(current.clone(),image,aboveText));return undo.removeLast();
    }
    public State redo(int[] current,PageAsset.Layer image){
        return redo(current,image,false);
    }
    public State redo(int[] current,PageAsset.Layer image,boolean aboveText){
        if(redo.isEmpty())return null;undo.addLast(new State(current.clone(),image,aboveText));return redo.removeLast();
    }
    private void trim(){
        while(undo.size()>40||bytes()>64L*1024*1024)undo.removeFirst();
    }
    private long bytes(){
        Set<PageAsset.Source> sources=Collections.newSetFromMap(new IdentityHashMap<>());
        long total=(long)(undo.size()+redo.size())*RasterCodec.PIXELS*4;
        for(State s:undo)if(s.image!=null)sources.add(s.image.source());
        for(State s:redo)if(s.image!=null)sources.add(s.image.source());
        for(var source:sources)total+=source.bytes();return total;
    }
}

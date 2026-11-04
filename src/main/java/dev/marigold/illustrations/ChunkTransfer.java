package dev.marigold.illustrations;

import java.nio.ByteBuffer;
import java.util.Arrays;

/** Strictly ordered, bounded assembly; individual Minecraft payloads remain below 32 KiB. */
public final class ChunkTransfer {
    public static final int CHUNK=24*1024;
    private final byte[] bytes;
    private int offset;
    public ChunkTransfer(int total){
        if(total<1||total>PageCodec.MAX_BYTES)throw new IllegalArgumentException("Invalid artwork transfer size");
        bytes=new byte[total];
    }
    public void append(int position,byte[] data){
        if(position!=offset||data.length<1||data.length>CHUNK||offset+data.length>bytes.length)
            throw new IllegalArgumentException("Invalid or out-of-order artwork chunk");
        System.arraycopy(data,0,bytes,offset,data.length);offset+=data.length;
    }
    public int position(){return offset;}
    public int size(){return bytes.length;}
    public boolean complete(){return offset==bytes.length;}
    public byte[] finish(){if(!complete())throw new IllegalStateException("Artwork transfer is incomplete");return bytes;}
    public static byte[] part(byte[] bytes,int offset){
        int count=Math.min(CHUNK,bytes.length-offset);
        return ByteBuffer.allocate(count+4).putInt(offset).put(bytes,offset,count).array();
    }
    public static int number(byte[] data){if(data.length!=4)throw new IllegalArgumentException("Invalid transfer number");return ByteBuffer.wrap(data).getInt();}
    public static byte[] number(int number){return ByteBuffer.allocate(4).putInt(number).array();}
    public static int partOffset(byte[] part){if(part.length<5)throw new IllegalArgumentException("Empty artwork chunk");return ByteBuffer.wrap(part).getInt();}
    public static byte[] partBytes(byte[] part){return Arrays.copyOfRange(part,4,part.length);}
}

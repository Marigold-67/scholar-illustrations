package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;
class PageCodecTest {
    @Test void existingDrawingAssetsRemainByteCompatible()throws Exception{
        byte[] old=RasterCodec.encode(new Random(9).ints(RasterCodec.PIXELS).toArray());
        PageAsset read=PageCodec.decode(old);assertNull(read.image());assertArrayEquals(old,PageCodec.encode(read));
    }
    @Test void resizingPreservesOriginalDimensionsPixelsAndAlpha()throws Exception{
        int[] original=new Random(42).ints(512*256).toArray();original[0]=0x7F123456;
        var source=new PageAsset.Source(512,256,original);
        var first=new PageAsset(new int[RasterCodec.PIXELS],new PageAsset.Layer(source,new PageAsset.Rect(0,0,114,57)));
        PageAsset reopened=PageCodec.decode(PageCodec.encode(first));
        var smaller=reopened.image().at(new PageAsset.Rect(72,82,40,20));
        PageAsset resized=PageCodec.decode(PageCodec.encode(new PageAsset(reopened.drawing(),smaller)));
        assertEquals(512,resized.image().source().width());assertEquals(256,resized.image().source().height());
        for(int i=0;i<original.length;i++)assertEquals(original[i],resized.image().source().pixel(i));
        assertEquals(new PageAsset.Rect(72,82,40,20),resized.image().rect());
    }
    @Test void acceptsMaximumSourceDimensions()throws Exception{
        int[] pixels=new Random(821).ints(2048*2048).toArray();pixels[pixels.length-1]=0x83ABCDEF;
        var asset=new PageAsset(new int[RasterCodec.PIXELS],new PageAsset.Layer(new PageAsset.Source(2048,2048,pixels),new PageAsset.Rect(0,0,114,128)));
        byte[] encoded=PageCodec.encode(asset);assertTrue(encoded.length<=PageCodec.MAX_BYTES);
        assertEquals(0x83ABCDEF,PageCodec.decode(encoded).image().source().pixel(pixels.length-1));
    }
    @Test void rejectsInvalidDimensionsPlacementAndTrailingData()throws Exception{
        var asset=new PageAsset(new int[RasterCodec.PIXELS],new PageAsset.Layer(new PageAsset.Source(2,2,new int[4]),new PageAsset.Rect(0,0,2,2)));
        byte[] bytes=PageCodec.encode(asset),huge=bytes.clone();huge[4]=0x7F;huge[5]=(byte)0xFF;
        assertThrows(IOException.class,()->PageCodec.decode(huge));
        assertThrows(IOException.class,()->PageCodec.decode(Arrays.copyOf(bytes,bytes.length-1)));
        assertThrows(IOException.class,()->PageCodec.decode(Arrays.copyOf(bytes,bytes.length+1)));
        assertThrows(IllegalArgumentException.class,()->new PageAsset.Rect(100,0,20,20));
        assertThrows(IllegalArgumentException.class,()->new PageAsset.Source(2049,1,new int[2049]));
    }
    @Test void extraInflatedPixelsAreRejected()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();var header=new DataOutputStream(bytes);
        header.write(new byte[]{'S','I','A',2});for(int n:new int[]{1,1,0,0,1,1})header.writeShort(n);
        try(var out=new DataOutputStream(new DeflaterOutputStream(bytes))){
            byte[] drawing=RasterCodec.encode(new int[RasterCodec.PIXELS]);out.writeShort(drawing.length);out.write(drawing);out.write(new byte[1024*1024]);
        }
        assertThrows(IOException.class,()->PageCodec.decode(bytes.toByteArray()));
    }
}

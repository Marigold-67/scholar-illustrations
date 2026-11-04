package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.zip.DeflaterOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class RasterCodecTest {
    @Test void noisyImageStaysBelowServerboundPacketLimit()throws Exception{
        int[] pixels=new Random(42).ints(RasterCodec.PIXELS).toArray();
        byte[] bytes=RasterCodec.encode(pixels);
        assertTrue(bytes.length<=RasterCodec.MAX_BYTES);
        assertTrue(bytes.length+256<32767);
        assertArrayEquals(bytes,RasterCodec.encode(RasterCodec.decode(bytes)));
    }
    @Test void transparencyAndColorRoundTrip()throws Exception{
        int[] pixels=new int[RasterCodec.PIXELS];pixels[0]=0x8855AAFF;pixels[1]=0xFF112233;
        int[] decoded=RasterCodec.decode(RasterCodec.encode(pixels));
        assertEquals(pixels[0],decoded[0]);assertEquals(pixels[1],decoded[1]);assertEquals(0,decoded[2]);
    }
    @Test void rejectsTruncatedCorruptTrailingAndOversizedPayloads()throws Exception{
        byte[] valid=RasterCodec.encode(new int[RasterCodec.PIXELS]);
        assertThrows(Exception.class,()->RasterCodec.decode(Arrays.copyOf(valid,valid.length-1)));
        assertThrows(Exception.class,()->RasterCodec.decode(Arrays.copyOf(valid,valid.length+1)));
        assertThrows(Exception.class,()->RasterCodec.decode(new byte[RasterCodec.MAX_BYTES+1]));
        byte[] corrupt=valid.clone();corrupt[corrupt.length-1]^=1;
        assertThrows(Exception.class,()->RasterCodec.decode(corrupt));
        byte[] version=valid.clone();version[3]=2;
        assertThrows(Exception.class,()->RasterCodec.decode(version));
    }
    @Test void rejectsDecompressionBombBeforeAllocatingExpandedData()throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();output.write(new byte[]{'S','I','A',1});
        try(var zip=new DeflaterOutputStream(output)){for(int i=0;i<200;i++)zip.write(new byte[4096]);}
        assertTrue(output.size()<RasterCodec.MAX_BYTES);
        assertThrows(Exception.class,()->RasterCodec.decode(output.toByteArray()));
    }
    @Test void hashesCannotEscapeAssetDirectory(){
        assertFalse(RasterCodec.validHash("../example"));assertFalse(RasterCodec.validHash("A".repeat(64)));
        assertEquals(64,RasterCodec.hash(new byte[0]).length());
    }
}

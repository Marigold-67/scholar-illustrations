package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ChunkTransferTest {
    @Test void multiPacketArtworkRoundTripsExactly(){
        byte[] bytes=new byte[512*1024+7];new Random(9).nextBytes(bytes);ChunkTransfer transfer=new ChunkTransfer(bytes.length);
        for(int offset=0;offset<bytes.length;offset+=ChunkTransfer.CHUNK){byte[] part=ChunkTransfer.part(bytes,offset);
            assertTrue(part.length+256<32767);transfer.append(ChunkTransfer.partOffset(part),ChunkTransfer.partBytes(part));}
        assertTrue(transfer.complete());assertArrayEquals(bytes,transfer.finish());
    }
    @Test void rejectsOversizeOutOfOrderDuplicateAndIncompleteTransfers(){
        assertThrows(IllegalArgumentException.class,()->new ChunkTransfer(PageCodec.MAX_BYTES+1));
        ChunkTransfer transfer=new ChunkTransfer(100);
        assertThrows(IllegalArgumentException.class,()->transfer.append(1,new byte[10]));
        transfer.append(0,new byte[10]);assertThrows(IllegalArgumentException.class,()->transfer.append(0,new byte[10]));
        assertThrows(IllegalArgumentException.class,()->transfer.append(10,new byte[ChunkTransfer.CHUNK+1]));
        assertThrows(IllegalStateException.class,transfer::finish);
    }
}

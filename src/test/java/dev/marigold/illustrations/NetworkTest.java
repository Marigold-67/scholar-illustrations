package dev.marigold.illustrations;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class NetworkTest {
    @Test void orderOnlyRequestRoundTripsWithNoImageBytes(){
        var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        var r=new Network.Request(22,Network.ORDER,0,-1,UUID.randomUUID(),7,3,"",new byte[0],true);
        try{Network.Request.CODEC.encode(b,r);var decoded=Network.Request.CODEC.decode(b);
            assertTrue(decoded.aboveText());assertEquals(Network.ORDER,decoded.op());assertEquals(0,decoded.data().length);
        }finally{b.release();}
    }
    @Test void boundedImagePayloadRoundTripsUnderVanillaC2sLimit(){
        byte[] image=RasterCodec.encode(new java.util.Random(7).ints(RasterCodec.PIXELS).toArray());
        var payload=new Network.Request(1,Network.PUT,0,-1,UUID.randomUUID(),99,5,"",image);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{
            Network.Request.CODEC.encode(buffer,payload);assertTrue(buffer.readableBytes()<32767);
            var result=Network.Request.CODEC.decode(buffer);assertArrayEquals(image,result.data());assertEquals(99,result.page());
        }finally{buffer.release();}
    }
    @Test void decoderRejectsOversizedDataBeforeAllocatingIt(){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{
            Network.Request.CODEC.encode(buffer,new Network.Request(1,Network.PUT,0,-1,Network.NONE,0,0,"",new byte[30001]));
            assertThrows(Exception.class,()->Network.Request.CODEC.decode(buffer));
        }finally{buffer.release();}
    }
    @Test void replyManifestAndImageRoundTrip(){
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        var reply=new Network.Reply(2,Network.SESSION,UUID.randomUUID(),new Artwork(3,Map.of(1,"a".repeat(64)),java.util.Set.of(1)),"","",new byte[0]);
        try{Network.Reply.CODEC.encode(buffer,reply);assertEquals(reply.artwork(),Network.Reply.CODEC.decode(buffer).artwork());}
        finally{buffer.release();}
    }
}

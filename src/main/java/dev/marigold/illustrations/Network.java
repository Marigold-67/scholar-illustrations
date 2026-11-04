package dev.marigold.illustrations;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;

public final class Network {
    public static final UUID NONE=new UUID(0,0);
    public static final int OPEN=0, GET=1, PUT=2, CLOSE=3, BEGIN=4, PART=5, COMMIT=6, ORDER=7;
    public static final int SESSION=0, SAVED=1, IMAGE=2, ERROR=3, PROGRESS=4, IMAGE_PART=5;
    // Installed only by the client entrypoint. Dedicated server never loads a client class.
    public static Consumer<Reply> clientReceiver=reply->{};
    public record Request(int id,int op,int slot,int menu,UUID token,int page,int revision,String hash,byte[] data,boolean aboveText)
            implements CustomPacketPayload {
        public Request(int id,int op,int slot,int menu,UUID token,int page,int revision,String hash,byte[] data){
            this(id,op,slot,menu,token,page,revision,hash,data,false);
        }
        public static final Type<Request> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Illustrations.ID,"request"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=new StreamCodec<>() {
            public Request decode(RegistryFriendlyByteBuf b) {
                return new Request(b.readVarInt(),b.readVarInt(),b.readInt(),b.readInt(),b.readUUID(),
                        b.readVarInt(),b.readInt(),b.readUtf(64),b.readByteArray(RasterCodec.MAX_BYTES),b.readBoolean());
            }
            public void encode(RegistryFriendlyByteBuf b,Request p) {
                b.writeVarInt(p.id);b.writeVarInt(p.op);b.writeInt(p.slot);b.writeInt(p.menu);b.writeUUID(p.token);
                b.writeVarInt(p.page);b.writeInt(p.revision);b.writeUtf(p.hash,64);b.writeByteArray(p.data);
                b.writeBoolean(p.aboveText);
            }
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public record Reply(int id,int kind,UUID token,Artwork artwork,String hash,String message,byte[] data)
            implements CustomPacketPayload {
        public static final Type<Reply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(Illustrations.ID,"reply"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC=new StreamCodec<>() {
            public Reply decode(RegistryFriendlyByteBuf b) {
                int id=b.readVarInt(),kind=b.readVarInt();UUID token=b.readUUID();int revision=b.readInt();
                int count=b.readVarInt();if(count<0||count>Artwork.MAX_PAGES)throw new DecoderException("Invalid artwork count");
                Map<Integer,String> pages=new TreeMap<>();
                Set<Integer> above=new HashSet<>();
                for(int i=0;i<count;i++){int page=b.readVarInt();String hash=b.readUtf(64);
                    if(page<0||page>=100||!RasterCodec.validHash(hash))throw new DecoderException("Invalid artwork manifest");
                    pages.put(page,hash);
                    if(b.readBoolean())above.add(page);
                }
                return new Reply(id,kind,token,new Artwork(revision,pages,above),b.readUtf(64),b.readUtf(160),b.readByteArray(RasterCodec.MAX_BYTES));
            }
            public void encode(RegistryFriendlyByteBuf b,Reply p) {
                b.writeVarInt(p.id);b.writeVarInt(p.kind);b.writeUUID(p.token);b.writeInt(p.artwork.revision());
                b.writeVarInt(p.artwork.pages().size());new TreeMap<>(p.artwork.pages()).forEach((page,hash)->{b.writeVarInt(page);b.writeUtf(hash,64);b.writeBoolean(p.artwork.isAboveText(page));});
                b.writeUtf(p.hash,64);b.writeUtf(p.message,160);b.writeByteArray(p.data);
            }
        };
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("3");
        registrar.playToServer(Request.TYPE,Request.CODEC,(p,c)->c.enqueueWork(()->ServerArtwork.handle(p,c)));
        registrar.playToClient(Reply.TYPE,Reply.CODEC,(p,c)->c.enqueueWork(()->clientReceiver.accept(p)));
    }
    static void reply(IPayloadContext context,Reply reply){context.reply(reply);}
}

package dev.marigold.illustrations;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Real headless GameTestServer, with server player fixtures and captured payload responses. */
@GameTestHolder(Illustrations.ID)
@PrefixGameTestTemplate(false)
public final class DedicatedSmoke {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    @GameTest(template="empty",timeoutTicks=20000)
    public static void highResolutionChunkedPersistence(GameTestHelper helper){
        // The older synchronous cases finish first, before this case resets shared server state.
        helper.runAfterDelay(5,()->{
            var level=helper.getLevel();
            var creator=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HQCreator"));
            var reader=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"HQReader"));
            var book=new ItemStack(Items.WRITABLE_BOOK);
            creator.getInventory().selected=0;creator.getInventory().setItem(0,book);
            AtomicReference<Network.Reply> response=new AtomicReference<>();var ctx=context(creator,response);
            ServerArtwork.clear();
            ServerArtwork.handle(new Network.Request(101,Network.OPEN,0,-1,Network.NONE,0,0,"",new byte[0]),ctx);
            check(response.get().kind()==Network.SESSION,"HQ session failed");UUID token=response.get().token();
            int[] pixels=new int[512*256];var random=new Random(129);for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();
            pixels[0]=0x7F123456;
            byte[] encoded=PageCodec.encode(new PageAsset(new int[RasterCodec.PIXELS],new PageAsset.Layer(new PageAsset.Source(512,256,pixels),new PageAsset.Rect(3,22,108,54))));
            check(encoded.length>30000,"HQ fixture must span packets");
            byte[] tiny=PageCodec.encode(new PageAsset(new int[RasterCodec.PIXELS],new PageAsset.Layer(new PageAsset.Source(1,1,new int[]{-1}),new PageAsset.Rect(0,0,1,1))));
            ServerArtwork.handle(new Network.Request(102,Network.PUT,0,-1,token,0,0,"",tiny),ctx);
            check(response.get().kind()==Network.ERROR,"HQ payload accepted through synchronous legacy path");
            ServerArtwork.handle(new Network.Request(103,Network.BEGIN,0,-1,token,0,0,"",ChunkTransfer.number(PageCodec.MAX_BYTES+1)),ctx);
            check(response.get().kind()==Network.ERROR,"Oversized transfer accepted");
            ServerArtwork.handle(new Network.Request(104,Network.BEGIN,0,-1,token,0,0,"",ChunkTransfer.number(encoded.length)),ctx);
            ServerArtwork.handle(new Network.Request(104,Network.PART,0,-1,token,0,0,"",ChunkTransfer.part(encoded,ChunkTransfer.CHUNK)),ctx);
            check(response.get().kind()==Network.ERROR,"Out-of-order upload accepted");
            ServerArtwork.handle(new Network.Request(105,Network.BEGIN,0,-1,token,0,0,"",ChunkTransfer.number(encoded.length)),ctx);
            check(response.get().kind()==Network.PROGRESS,"Upload did not recover after rejection");
            int parts=(encoded.length+ChunkTransfer.CHUNK-1)/ChunkTransfer.CHUNK;
            for(int i=0;i<parts;i+=4){
                int first=i;
                helper.runAfterDelay(1+i/4,()->{
                    for(int part=first;part<Math.min(parts,first+4);part++){
                        ServerArtwork.handle(new Network.Request(105,Network.PART,0,-1,token,0,0,"",ChunkTransfer.part(encoded,part*ChunkTransfer.CHUNK)),ctx);
                        check(response.get().kind()==Network.PROGRESS,"HQ part rejected: "+response.get().message());
                    }
                });
            }
            helper.runAfterDelay(3+parts/4,()->{
                response.set(null);ServerArtwork.handle(new Network.Request(105,Network.COMMIT,0,-1,token,0,0,"",new byte[0]),ctx);
                helper.runAfterDelay(1,new Runnable(){
                    boolean reading;ChunkTransfer received;IPayloadContext readContext;String hash;
                    private void later(){Runnable poll=this;helper.runAfterDelay(1,new Runnable(){public void run(){poll.run();}});}
                    @Override public void run(){
                        Network.Reply reply=response.getAndSet(null);
                        if(reply==null){later();return;}
                        check(reply.kind()!=Network.ERROR,"HQ server failed: "+reply.message());
                        if(!reading){
                            check(reply.kind()==Network.SAVED,"Expected HQ save acknowledgement");hash=reply.hash();
                            ItemStack copied=ItemStack.parse(level.registryAccess(),book.save(level.registryAccess())).orElseThrow();
                            reader.getInventory().selected=0;reader.getInventory().setItem(0,copied);creator.getInventory().setItem(0,ItemStack.EMPTY);
                            ServerArtwork.logout(creator.getUUID());ServerArtwork.clear();reading=true;readContext=context(reader,response);
                            ServerArtwork.handle(new Network.Request(106,Network.GET,0,-1,Network.NONE,0,0,hash,new byte[0]),readContext);
                        }else{
                            check(reply.kind()==Network.IMAGE_PART,"Expected chunked image download");
                            var buffer=java.nio.ByteBuffer.wrap(reply.data());int total=buffer.getInt(),offset=buffer.getInt();
                            if(received==null)received=new ChunkTransfer(total);byte[] part=new byte[buffer.remaining()];buffer.get(part);received.append(offset,part);
                            if(received.complete()){
                                check(Arrays.equals(encoded,received.finish()),"Persistent HQ bytes changed");
                                try{PageAsset loaded=PageCodec.decode(received.finish());check(loaded.image().source().width()==512&&loaded.image().source().pixel(0)==0x7F123456,"HQ pixels lost");}
                                catch(java.io.IOException e){throw new AssertionError("HQ decode failed",e);}
                                ServerArtwork.clear();Illustrations.LOG.info("DEDICATED SMOKE PASS: HQ chunk upload/download, malformed/oversized rejection, legacy-path rejection, serialized transfer to second player, store restart, exact source pixels");helper.succeed();return;
                            }
                            ServerArtwork.handle(new Network.Request(106,Network.GET,0,-1,Network.NONE,received.position(),0,hash,new byte[0]),readContext);
                        }
                        later();
                    }
                });
            });
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void artworkSecurityAndServerPersistence(GameTestHelper helper){
        var level=helper.getLevel();
        var creator=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ArtCreator"));
        var reader=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"ArtReader"));
        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);
        var text=new WritableBookContent(List.of(Filterable.passThrough("\u00a7lDedicated journal\u00a7r")));
        book.set(DataComponents.WRITABLE_BOOK_CONTENT,text);
        creator.getInventory().selected=0;creator.getInventory().setItem(0,book);
        AtomicReference<Network.Reply> response=new AtomicReference<>();
        IPayloadContext ctx=context(creator,response);
        ServerArtwork.clear();
        ServerArtwork.handle(new Network.Request(1,Network.OPEN,0,-1,Network.NONE,0,0,"",new byte[0]),ctx);
        check(response.get().kind()==Network.SESSION,"Open session failed: "+response.get().message());
        UUID token=response.get().token();
        PixelCanvas canvas=new PixelCanvas(new int[RasterCodec.PIXELS]);canvas.line(2,2,100,100,4,0xFFAA5522);
        byte[] encoded=RasterCodec.encode(canvas.pixels());
        ServerArtwork.handle(new Network.Request(2,Network.PUT,0,-1,token,3,0,"",encoded),ctx);
        check(response.get().kind()==Network.SAVED,"Save failed: "+response.get().message());
        String hash=response.get().hash();
        check(Artwork.read(book).pages().get(3).equals(hash),"Book association lost");
        check(book.get(DataComponents.WRITABLE_BOOK_CONTENT).pages().getFirst().equals(text.pages().getFirst()),"Existing formatting changed");
        ServerArtwork.handle(new Network.Request(3,Network.PUT,0,-1,UUID.randomUUID(),3,1,"",encoded),ctx);
        check(response.get().kind()==Network.ERROR,"Forged session accepted");
        ServerArtwork.handle(new Network.Request(4,Network.PUT,0,-1,token,3,0,"",encoded),ctx);
        check(response.get().kind()==Network.ERROR,"Stale revision accepted");
        ServerArtwork.handle(new Network.Request(5,Network.PUT,0,-1,token,3,1,"",new byte[20]),ctx);
        check(response.get().kind()==Network.ERROR,"Malformed image accepted");
        ServerArtwork.handle(new Network.Request(6,Network.GET,0,-1,Network.NONE,0,0,"b".repeat(64),new byte[0]),ctx);
        check(response.get().kind()==Network.ERROR,"Unrelated asset access accepted");
        ServerArtwork.handle(new Network.Request(7,Network.OPEN,1,-1,Network.NONE,0,0,"",new byte[0]),ctx);
        check(response.get().kind()==Network.ERROR,"Unheld slot accepted");
        ServerArtwork.handle(new Network.Request(20,Network.ORDER,0,-1,UUID.randomUUID(),3,1,"",new byte[0],true),ctx);
        check(response.get().kind()==Network.ERROR,"Forged session reordered artwork");
        ServerArtwork.handle(new Network.Request(21,Network.ORDER,0,-1,token,3,0,"",new byte[0],true),ctx);
        check(response.get().kind()==Network.ERROR,"Stale revision reordered artwork");
        ServerArtwork.handle(new Network.Request(22,Network.ORDER,0,-1,token,3,1,"",new byte[0],true),ctx);
        check(response.get().kind()==Network.SAVED&&Artwork.read(book).isAboveText(3),"Order-only save failed");
        check(response.get().hash().equals(hash),"Order toggle replaced the immutable image asset");
        ItemStack reloaded=ItemStack.parse(level.registryAccess(),book.save(level.registryAccess())).orElseThrow();
        reader.getInventory().selected=0;reader.getInventory().setItem(0,reloaded);
        creator.getInventory().setItem(0,ItemStack.EMPTY);
        ServerArtwork.logout(creator.getUUID());ServerArtwork.clear();
        // Recreate the world asset index after transferring a serialized book to a different player.
        ServerArtwork.handle(new Network.Request(8,Network.GET,0,-1,Network.NONE,0,0,hash,new byte[0]),context(reader,response));
        check(response.get().kind()==Network.IMAGE,"Other player could not load persistent artwork: "+response.get().message());
        check(Arrays.equals(encoded,response.get().data()),"Artwork data changed during transfer");
        check(response.get().artwork().isAboveText(3),"Text order lost on serialization/transfer/store restart");
        ServerArtwork.clear();
        Illustrations.LOG.info("DEDICATED SMOKE PASS: save, text, session/revision/access/malformed rejection, serialized transfer, offline creator, persistent asset reload");
        helper.succeed();
    }
    private static IPayloadContext context(net.minecraft.server.level.ServerPlayer player,AtomicReference<Network.Reply> response){
        return (IPayloadContext)Proxy.newProxyInstance(IPayloadContext.class.getClassLoader(),new Class[]{IPayloadContext.class},(proxy,method,args)->{
            if(method.getName().equals("player"))return player;
            if(method.getName().equals("reply")){response.set((Network.Reply)args[0]);return null;}
            if(method.getName().equals("toString"))return "SmokePayloadContext";
            throw new UnsupportedOperationException(method.getName());
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void lecternEditingAndDeletion(GameTestHelper helper){
        var level=helper.getLevel();var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.LECTERN.defaultBlockState());
        var lectern=(net.minecraft.world.level.block.entity.LecternBlockEntity)level.getBlockEntity(pos);
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"LecternArtist"));
        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);lectern.setBook(book,player);
        var container=new net.minecraft.world.SimpleContainer(lectern.getBook());
        player.containerMenu=new io.github.mortuusars.scholar.menu.LecternSpreadBookEditMenu(77,container,new net.minecraft.world.inventory.SimpleContainerData(1),pos);
        AtomicReference<Network.Reply> response=new AtomicReference<>();var ctx=context(player,response);
        ServerArtwork.clear();
        ServerArtwork.handle(new Network.Request(1,Network.OPEN,-1,77,Network.NONE,0,0,"",new byte[0]),ctx);
        check(response.get().kind()==Network.SESSION,"Scholar lectern editing menu rejected: "+response.get().message());
        UUID token=response.get().token();byte[] bytes=RasterCodec.encode(new int[RasterCodec.PIXELS]);
        ServerArtwork.handle(new Network.Request(2,Network.PUT,-1,77,token,0,0,"",bytes),ctx);
        check(response.get().kind()==Network.SAVED,"Lectern save failed: "+response.get().message());
        check(Artwork.read(lectern.getBook()).pages().size()==1,"Lectern did not retain art reference");
        ServerArtwork.handle(new Network.Request(3,Network.PUT,-1,77,token,0,1,"",new byte[0]),ctx);
        check(response.get().kind()==Network.SAVED&&Artwork.read(lectern.getBook()).pages().isEmpty(),"Delete illustration failed");
        player.containerMenu=new io.github.mortuusars.scholar.menu.LecternSpreadMenu(78,container,new net.minecraft.world.inventory.SimpleContainerData(1),pos);
        ServerArtwork.handle(new Network.Request(4,Network.OPEN,-1,78,Network.NONE,0,0,"",new byte[0]),ctx);
        check(response.get().kind()==Network.ERROR,"Lectern read-only menu gained edit permission");
        ServerArtwork.clear();
        Illustrations.LOG.info("DEDICATED SMOKE PASS: Scholar lectern edit menu, persistent book association, deletion, read-only menu denial");
        helper.succeed();
    }
}

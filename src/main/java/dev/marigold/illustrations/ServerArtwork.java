package dev.marigold.illustrations;

import io.github.mortuusars.scholar.menu.LecternSpreadBookEditMenu;
import io.github.mortuusars.scholar.menu.LecternSpreadMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;

public final class ServerArtwork {
    private record Session(UUID token,ItemStack book,int slot,int menu){}
    private record Key(UUID player,int request){}
    private record Outgoing(byte[] bytes,String hash,long expires){}
    private static final class Upload {
        final Network.Request begin;final ChunkTransfer chunks;long updated=System.nanoTime();boolean committing;
        Upload(Network.Request begin,int size){this.begin=begin;chunks=new ChunkTransfer(size);}
    }
    private static final Map<UUID,Session> sessions=new HashMap<>();
    private static final Map<UUID,long[]> rates=new HashMap<>();
    private static final Map<UUID,Upload> uploads=new HashMap<>();
    private static final Map<Key,Outgoing> outgoing=new HashMap<>();
    private static final Map<Key,Long> loading=new HashMap<>();
    private static final long BUFFER_BUDGET=64L*1024*1024;
    private static long reserved,generation;
    private static AssetStore store;
    private static ExecutorService worker;
    public static void logout(UUID id){
        close(id);rates.remove(id);
    }
    private static void close(UUID id){
        sessions.remove(id);Upload u=uploads.get(id);
        if(u!=null&&!u.committing){uploads.remove(id);reserved-=u.chunks.size();}
        outgoing.entrySet().removeIf(e->{if(e.getKey().player.equals(id)){reserved-=e.getValue().bytes.length;return true;}return false;});
    }
    public static void clear(){
        generation++;sessions.clear();rates.clear();uploads.clear();outgoing.clear();loading.clear();reserved=0;store=null;
        if(worker!=null){worker.shutdownNow();worker=null;}
    }
    public static void handle(Network.Request r,IPayloadContext context) {
        if(!(context.player() instanceof ServerPlayer player))return;
        try {
            expire();
            if(r.op()==Network.CLOSE){rate(player.getUUID());close(player.getUUID());return;}
            if(r.op()==Network.PART||r.op()==Network.GET&&r.page()>0)transferRate(player.getUUID(),r.data().length);
            else rate(player.getUUID());
            ItemStack book=target(player,r.slot(),r.menu(),r.op()!=Network.GET);
            if(r.op()!=Network.GET)Artwork.validateForEditing(book);
            Artwork art=Artwork.read(book);
            if(store==null)store=new AssetStore(player.server);
            if(r.op()==Network.GET){get(r,context,player,art);return;}
            if(r.op()==Network.OPEN){
                Session s=new Session(UUID.randomUUID(),book,r.slot(),r.menu());sessions.put(player.getUUID(),s);
                reply(context,r,Network.SESSION,s.token,art,"","",new byte[0]);return;
            }
            Session session=validateSession(player,book,r,art);
            if(r.page()<0||r.page()>=100)throw new IllegalArgumentException("Invalid book page");
            if(r.op()==Network.ORDER){
                String hash=art.pages().get(r.page());
                if(hash==null||r.data().length!=0)throw new IllegalArgumentException("No saved artwork to reorder");
                applySave(r,context,player,book,art,session,hash);return;
            }
            if(r.op()==Network.BEGIN){
                if(uploads.containsKey(player.getUUID()))throw new IllegalArgumentException("An artwork upload is already active");
                int size=ChunkTransfer.number(r.data());
                if(size<1||size>PageCodec.MAX_BYTES)throw new IllegalArgumentException("Artwork exceeds 17 MiB");
                checkPageLimit(art,r.page(),true);reserve(size);uploads.put(player.getUUID(),new Upload(r,size));
                progress(context,r,art,0);return;
            }
            if(r.op()==Network.PART||r.op()==Network.COMMIT){
                Upload u=uploads.get(player.getUUID());
                if(u==null||u.begin.id()!=r.id()||u.begin.page()!=r.page()||u.begin.aboveText()!=r.aboveText()||!u.begin.token().equals(r.token())||u.committing)
                    throw new IllegalArgumentException("Invalid artwork upload session");
                u.updated=System.nanoTime();
                if(r.op()==Network.PART){
                    u.chunks.append(ChunkTransfer.partOffset(r.data()),ChunkTransfer.partBytes(r.data()));
                    progress(context,r,art,u.chunks.position());return;
                }
                byte[] bytes=u.chunks.finish();u.committing=true;AssetStore captured=store;long epoch=generation;
                try{worker().execute(()->{
                    String hash=null;Exception failure=null;
                    try{hash=captured.save(player.getUUID(),bytes);}catch(Exception e){failure=e;}
                    String saved=hash;Exception error=failure;
                    player.server.execute(()->{
                        if(epoch!=generation)return;
                        uploads.remove(player.getUUID());reserved-=u.chunks.size();
                        try{
                            if(error!=null)throw error;
                            ItemStack current=target(player,r.slot(),r.menu(),true);
                            Artwork now=Artwork.read(current);validateSession(player,current,r,now);
                            applySave(r,context,player,current,now,session,saved);
                        }catch(Exception e){error(context,r,e);}
                    });
                });}catch(RejectedExecutionException e){
                    uploads.remove(player.getUUID());reserved-=u.chunks.size();throw new IllegalArgumentException("Artwork processor is busy; try Save again");
                }
                return;
            }
            if(r.op()!=Network.PUT)throw new IllegalArgumentException("Unknown artwork operation");
            if(r.data().length>0&&(r.data().length<4||r.data()[3]!=1))
                throw new IllegalArgumentException("HQ artwork requires a chunked upload");
            checkPageLimit(art,r.page(),r.data().length>0);
            String hash=r.data().length==0?"":store.save(player.getUUID(),r.data());
            applySave(r,context,player,book,art,session,hash);
        }catch(Exception e){error(context,r,e);}
    }
    private static void applySave(Network.Request r,IPayloadContext context,ServerPlayer player,ItemStack book,Artwork art,Session session,String hash){
        checkPageLimit(art,r.page(),!hash.isEmpty());Artwork next=art.with(r.page(),hash,r.aboveText());next.write(book);
        var content=book.getOrDefault(DataComponents.WRITABLE_BOOK_CONTENT,WritableBookContent.EMPTY);
        var pages=new ArrayList<>(content.pages());while(pages.size()<next.pageCount())pages.add(Filterable.passThrough(""));
        book.set(DataComponents.WRITABLE_BOOK_CONTENT,new WritableBookContent(pages));
        if(r.menu()>=0&&player.containerMenu instanceof LecternSpreadMenu menu
                &&player.level().getBlockEntity(menu.getLecternPos()) instanceof LecternBlockEntity lectern){
            lectern.setChanged();player.containerMenu.broadcastChanges();var update=lectern.getUpdatePacket();
            if(update!=null)for(ServerPlayer viewer:player.serverLevel().players())if(viewer.distanceToSqr(player)<4096)viewer.connection.send(update);
        }else player.inventoryMenu.broadcastChanges();
        reply(context,r,Network.SAVED,session.token,next,hash,"Saved",new byte[0]);
    }
    private static void get(Network.Request r,IPayloadContext context,ServerPlayer player,Artwork art)throws Exception{
        if(!art.pages().containsValue(r.hash()))throw new IllegalArgumentException("Artwork does not belong to this book");
        Key key=new Key(player.getUUID(),r.id());Outgoing cached=outgoing.get(key);
        if(cached!=null){sendPart(r,context,art,key,cached);return;}
        if(loading.containsKey(key))throw new IllegalArgumentException("Artwork is still loading");
        if(r.page()!=0)throw new IllegalArgumentException("Artwork download expired; reopen the book");
        long size=store.size(r.hash());
        if(size<=RasterCodec.MAX_BYTES&&!store.highResolution(r.hash())){reply(context,r,Network.IMAGE,Network.NONE,art,r.hash(),"",store.read(r.hash()));return;}
        if(size>PageCodec.MAX_BYTES)throw new IllegalArgumentException("Invalid artwork file size");
        if(outgoing.keySet().stream().filter(k->k.player.equals(player.getUUID())).count()
                +loading.keySet().stream().filter(k->k.player.equals(player.getUUID())).count()>=2)
            throw new IllegalArgumentException("Too many active image downloads");
        reserve(size);loading.put(key,size);AssetStore captured=store;long epoch=generation;
        try{worker().execute(()->{
            byte[] bytes=null;Exception failure=null;try{bytes=captured.read(r.hash());}catch(Exception e){failure=e;}
            byte[] result=bytes;Exception error=failure;
            player.server.execute(()->{
                if(epoch!=generation)return;loading.remove(key);
                try{
                    if(error!=null)throw error;
                    ItemStack current=target(player,r.slot(),r.menu(),false);Artwork now=Artwork.read(current);
                    if(!now.pages().containsValue(r.hash()))throw new IllegalArgumentException("Book changed during download");
                    Outgoing transfer=new Outgoing(result,r.hash(),System.nanoTime()+120_000_000_000L);outgoing.put(key,transfer);
                    sendPart(r,context,now,key,transfer);
                }catch(Exception e){outgoing.remove(key);reserved-=size;error(context,r,e);}
            });
        });}catch(RejectedExecutionException e){loading.remove(key);reserved-=size;throw new IllegalArgumentException("Artwork processor is busy; reopen the book shortly");}
    }
    private static void sendPart(Network.Request r,IPayloadContext context,Artwork art,Key key,Outgoing transfer){
        int offset=r.page();
        if(!transfer.hash.equals(r.hash())||offset<0||offset>=transfer.bytes.length||offset%ChunkTransfer.CHUNK!=0)
            throw new IllegalArgumentException("Invalid artwork download offset");
        int count=Math.min(ChunkTransfer.CHUNK,transfer.bytes.length-offset);transferRate(key.player,count);
        byte[] bytes=ByteBuffer.allocate(count+8).putInt(transfer.bytes.length).putInt(offset).put(transfer.bytes,offset,count).array();
        reply(context,r,Network.IMAGE_PART,Network.NONE,art,transfer.hash,"",bytes);
        if(offset+count==transfer.bytes.length){outgoing.remove(key);reserved-=transfer.bytes.length;}
    }
    private static Session validateSession(ServerPlayer player,ItemStack book,Network.Request r,Artwork art){
        Session s=sessions.get(player.getUUID());
        if(s==null||!s.token.equals(r.token())||s.book!=book||s.slot!=r.slot()||s.menu!=r.menu())
            throw new IllegalArgumentException("Book session changed; reopen illustration mode");
        if(r.revision()!=art.revision())throw new IllegalArgumentException("Artwork changed elsewhere; reopen illustration mode");return s;
    }
    private static void checkPageLimit(Artwork art,int page,boolean adding){
        if(adding&&!art.pages().containsKey(page)&&art.pages().size()>=Artwork.MAX_PAGES)throw new IllegalArgumentException("Maximum 16 illustrated pages per book");
    }
    private static ItemStack target(ServerPlayer player,int slot,int container,boolean editing){
        if(player.isSpectator())throw new IllegalArgumentException("Spectators cannot use artwork transfers");
        ItemStack book;
        if(container>=0){
            if(!(player.containerMenu instanceof LecternSpreadMenu menu)||menu.containerId!=container||!menu.stillValid(player)
                    ||editing&&(!(menu instanceof LecternSpreadBookEditMenu)||!player.mayBuild()))throw new IllegalArgumentException("No permission to edit this lectern");
            if(!player.level().hasChunkAt(menu.getLecternPos())||!(player.level().getBlockEntity(menu.getLecternPos()) instanceof LecternBlockEntity lectern))
                throw new IllegalArgumentException("Lectern is unavailable");book=lectern.getBook();
        }else{
            if(slot!=40&&slot!=player.getInventory().selected)throw new IllegalArgumentException("Book must be held");book=player.getInventory().getItem(slot);
        }
        if(!book.is(Items.WRITABLE_BOOK)&&(!book.is(Items.WRITTEN_BOOK)||editing))throw new IllegalArgumentException("Book is not editable");return book;
    }
    private static void progress(IPayloadContext c,Network.Request r,Artwork art,int offset){reply(c,r,Network.PROGRESS,r.token(),art,"","",ChunkTransfer.number(offset));}
    private static void reply(IPayloadContext c,Network.Request r,int kind,UUID token,Artwork art,String hash,String message,byte[] bytes){Network.reply(c,new Network.Reply(r.id(),kind,token,art,hash,message,bytes));}
    private static void error(IPayloadContext c,Network.Request r,Exception e){
        if(c.player() instanceof ServerPlayer player){
            Upload u=uploads.get(player.getUUID());
            if(u!=null&&!u.committing&&u.begin.id()==r.id()){uploads.remove(player.getUUID());reserved-=u.chunks.size();}
        }
        String message=e.getMessage()==null?"Artwork transfer failed":e.getMessage();if(message.length()>160)message=message.substring(0,160);
        reply(c,r,Network.ERROR,Network.NONE,new Artwork(0,Map.of()),r.hash(),message,new byte[0]);
    }
    private static ExecutorService worker(){
        if(worker==null)worker=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(3),r->{Thread t=new Thread(r,"Scholar artwork I/O");t.setDaemon(true);return t;});return worker;
    }
    private static void reserve(long bytes){if(bytes<1||bytes>PageCodec.MAX_BYTES||reserved+bytes>BUFFER_BUDGET)throw new IllegalArgumentException("Artwork transfers are busy; try again shortly");reserved+=bytes;}
    private static void expire(){
        long now=System.nanoTime();uploads.entrySet().removeIf(e->{Upload u=e.getValue();if(!u.committing&&now-u.updated>120_000_000_000L){reserved-=u.chunks.size();return true;}return false;});
        outgoing.entrySet().removeIf(e->{if(e.getValue().expires<now){reserved-=e.getValue().bytes.length;return true;}return false;});
    }
    private static long[] bucket(UUID id){long now=System.nanoTime();long[] b=rates.computeIfAbsent(id,k->new long[]{now,0,0,0});if(now-b[0]>1_000_000_000L){Arrays.fill(b,1,b.length,0);b[0]=now;}return b;}
    private static void rate(UUID id){if(++bucket(id)[1]>12)throw new IllegalArgumentException("Too many artwork requests; wait a moment");}
    private static void transferRate(UUID id,int bytes){long[] b=bucket(id);b[2]+=bytes;if(++b[3]>256||b[2]>4L*1024*1024)throw new IllegalArgumentException("Artwork transfer rate exceeded; try again shortly");}
}

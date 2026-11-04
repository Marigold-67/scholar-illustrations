package dev.marigold.illustrations.client;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.marigold.illustrations.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

final class ClientAssets {
    record Image(ResourceLocation texture,ResourceLocation imageTexture,PageAsset asset){
        int[] pixels(){return asset.drawing();}
        void draw(GuiGraphics g,int x,int y,int scale){
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            if(asset.image()!=null){var layer=asset.image();var r=layer.rect();var s=layer.source();
                g.blit(imageTexture,x+r.x()*scale,y+r.y()*scale,r.width()*scale,r.height()*scale,0,0,s.width(),s.height(),s.width(),s.height());}
            g.blit(texture,x,y,114*scale,128*scale,0,0,114,128,114,128);
            RenderSystem.disableBlend();
        }
    }
    private static final Minecraft MC=Minecraft.getInstance();
    private static final LinkedHashMap<String,Image> images=new LinkedHashMap<>(32,.75f,true);
    private static final Set<String> tried=new HashSet<>(),loading=new HashSet<>();
    private static final Path ROOT=MC.gameDirectory.toPath().resolve("cache/scholar_illustrations");
    private static long decodedBytes;
    private static int writes;
    private static long epoch;
    static boolean loading(String hash){return loading.contains(hash);}
    static Image get(String hash){
        Image cached=images.get(hash);if(cached!=null)return cached;
        if(!RasterCodec.validHash(hash)||!tried.add(hash))return null;
        Path file=ROOT.resolve(hash+".sia");
        try{
            if(Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)&&Files.size(file)<=PageCodec.MAX_BYTES)
                loadAsync(hash,()->Files.readAllBytes(file),false,message->{});
        }catch(IOException ignored){}
        return null;
    }
    interface Bytes {byte[] read()throws IOException;}
    static void accept(String hash,byte[] bytes,Consumer<String> failure){
        loadAsync(hash,()->bytes,true,failure);
    }
    private static void loadAsync(String hash,Bytes input,boolean write,Consumer<String> failure){
        if(!loading.add(hash))return;long generation=epoch;
        CompletableFuture.supplyAsync(()->{
            try{byte[] bytes=input.read();
                if(bytes.length>PageCodec.MAX_BYTES||!RasterCodec.validHash(hash)||!RasterCodec.hash(bytes).equals(hash))throw new IOException("Artwork checksum mismatch");
                PageAsset asset=PageCodec.decode(bytes);
                if(write)try{writeDisk(hash,bytes);}catch(IOException ignored){}
                return asset;
            }catch(Exception e){throw new IllegalArgumentException("Invalid or unavailable artwork",e);}
        }).whenComplete((asset,error)->MC.execute(()->{
            if(epoch!=generation)return;loading.remove(hash);
            if(error!=null){failure.accept("Artwork could not be decoded");return;}
            String prefix="cached/"+hash;
            int[] drawing=asset.drawing();
            ResourceLocation texture=upload(prefix+"/drawing",114,128,i->drawing[i],false);
            ResourceLocation image=null;
            if(asset.image()!=null){var source=asset.image().source();image=upload(prefix+"/image",source.width(),source.height(),source::pixel,true);}
            Image previous=images.put(hash,new Image(texture,image,asset));
            if(previous!=null)decodedBytes-=previous.asset.decodedBytes();
            decodedBytes+=asset.decodedBytes();
            while(images.size()>32||decodedBytes>64L*1024*1024){
                String oldest=images.keySet().iterator().next();evict(oldest);tried.remove(oldest);
            }
        }));
    }
    static ResourceLocation upload(String path,int width,int height,IntUnaryOperator pixels,boolean smooth){
        NativeImage nativeImage=new NativeImage(width,height,true);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int c=pixels.applyAsInt(y*width+x);nativeImage.setPixelRGBA(x,y,(c&0xFF00FF00)|((c>>>16)&255)|((c&255)<<16));
        }
        ResourceLocation id=ResourceLocation.fromNamespaceAndPath(Illustrations.ID,path);
        DynamicTexture texture=new DynamicTexture(nativeImage);texture.setFilter(smooth,false);
        MC.getTextureManager().register(id,texture);return id;
    }
    private static synchronized void writeDisk(String hash,byte[] bytes)throws IOException{
        Files.createDirectories(ROOT);Files.write(ROOT.resolve(hash+".sia"),bytes);
        if(bytes.length>1024*1024||writes++%32==0)trim();
    }
    private static void trim()throws IOException{
        List<Path> ordered;try(var stream=Files.list(ROOT)){ordered=new ArrayList<>(stream.filter(p->p.getFileName().toString().matches("[0-9a-f]{64}\\.sia")).toList());}
        ordered.sort(Comparator.comparingLong(p->{try{return Files.getLastModifiedTime(p).toMillis();}catch(IOException e){return 0L;}}));
        long total=0;for(Path p:ordered)total+=Files.size(p);
        for(Path p:ordered){if(total<=64L*1024*1024)break;long n=Files.size(p);Files.deleteIfExists(p);total-=n;}
    }
    private static void evict(String hash){
        Image removed=images.remove(hash);if(removed==null)return;decodedBytes-=removed.asset.decodedBytes();
        MC.getTextureManager().release(removed.texture);if(removed.imageTexture!=null)MC.getTextureManager().release(removed.imageTexture);
    }
    static void clear(){epoch++;for(String hash:new ArrayList<>(images.keySet()))evict(hash);tried.clear();loading.clear();}
}

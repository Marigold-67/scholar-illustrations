package dev.marigold.illustrations.client;

import dev.marigold.illustrations.*;
import dev.marigold.illustrations.integration.*;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.mortuusars.scholar.client.gui.screen.SpreadBookScreen;
import io.github.mortuusars.scholar.client.gui.screen.edit.SpreadBookEditScreen;
import io.github.mortuusars.scholar.client.gui.screen.view.SpreadBookViewScreen;
import io.github.mortuusars.scholar.menu.LecternSpreadMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@Mod(value=Illustrations.ID,dist=Dist.CLIENT)
public final class ClientIntegration {
    private static final Minecraft MC=Minecraft.getInstance();
    private static Context current;
    private static int sequence;
    // Client-session preference only; independent of Scholar's own tools/configuration.
    private static boolean toolsExpanded;
    private static final Map<Integer,Download> downloads=new HashMap<>();
    private static final Set<String> failed=new HashSet<>();
    static final class Context {
        final SpreadBookScreen screen; final int slot,menu;
        UUID token=Network.NONE;
        int pending=-1;
        int page;
        IllustrationPanel panel;
        Upload upload;
        boolean aboveText;
        Context(SpreadBookScreen screen) {
            this.screen=screen;
            slot=screen instanceof HandAccess hand?hand.illustrations$slot():
                    screen instanceof ViewHandAccess hand?hand.illustrations$slot():-1;
            menu=screen instanceof MenuAccess<?> access&&access.getMenu() instanceof LecternSpreadMenu lectern?lectern.containerId:-1;
        }
        ItemStack book(){return screen instanceof EditAccess edit?edit.illustrations$book():
                screen instanceof SpreadBookViewScreen view?view.getBookAccess().getBook():ItemStack.EMPTY;}
        void send(int op,int page,byte[] data,String hash){
            pending=++sequence;
            PacketDistributor.sendToServer(new Network.Request(pending,op,slot,menu,token,page,Artwork.read(book()).revision(),hash,data,aboveText));
        }
    }
    private static final class Upload {
        final byte[] bytes;int sent,acknowledged;boolean begun,committed;
        Upload(byte[] bytes){this.bytes=bytes;}
    }
    private static final class Download {
        final Context context;final String hash;ChunkTransfer chunks;int next;
        Download(Context context,String hash){this.context=context;this.hash=hash;}
    }
    public ClientIntegration(IEventBus bus) {
        Network.clientReceiver=ClientIntegration::receive;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->pump());
        NeoForge.EVENT_BUS.addListener(ClientIntegration::init);
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Post e)->{
            if(active(e.getScreen()))current.panel.render(e.getGuiGraphics(),e.getMouseX(),e.getMouseY());
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre e)->{
            if(active(e.getScreen())){current.panel.click(e.getMouseX(),e.getMouseY(),e.getButton());e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseDragged.Pre e)->{
            if(active(e.getScreen())){current.panel.drag(e.getMouseX(),e.getMouseY());e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre e)->{
            if(active(e.getScreen())){current.panel.release();e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseScrolled.Pre e)->{
            if(active(e.getScreen())){current.panel.scroll(e.getScrollDeltaY());e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre e)->{
            if(active(e.getScreen())){current.panel.key(e.getKeyCode());e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyReleased.Pre e)->{if(active(e.getScreen()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((ScreenEvent.CharacterTyped.Pre e)->{if(active(e.getScreen()))e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Closing e)->{
            if(current!=null&&current.screen==e.getScreen()){
                if(!current.token.equals(Network.NONE))current.send(Network.CLOSE,0,new byte[0],"");
                if(current.panel!=null)current.panel.dispose();
                current=null;
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e)->{
            current=null;downloads.clear();failed.clear();ClientAssets.clear();
        });
    }
    private static boolean active(Screen screen){return current!=null&&current.screen==screen&&current.panel!=null;}
    private static void init(ScreenEvent.Init.Post event) {
        if(!(event.getScreen() instanceof SpreadBookScreen screen))return;
        if(current==null||current.screen!=screen){current=new Context(screen);failed.clear();}
        if(!(screen instanceof SpreadBookEditScreen))return;
        SpreadAccess spread=(SpreadAccess)screen;
        int y=spread.illustrations$top()+2;
        Button left=Button.builder(Component.literal("Draw L"),button->open(screen,0))
                .bounds(spread.illustrations$left()+56,y,48,16)
                .tooltip(Tooltip.create(Component.literal("Draw, import or edit artwork on the left page"))).build();
        Button right=Button.builder(Component.literal("Draw R"),button->open(screen,1))
                .bounds(spread.illustrations$left()+193,y,48,16)
                .tooltip(Tooltip.create(Component.literal("Draw, import or edit artwork on the right page"))).build();
        left.visible=toolsExpanded;right.visible=toolsExpanded;
        event.addListener(left);event.addListener(right);
        IllustrationToolsButton toggle=new IllustrationToolsButton(
                Math.max(2,Math.min(screen.width-20,spread.illustrations$left()+299)),spread.illustrations$top()+70,
                button->{
                    toolsExpanded=!toolsExpanded;
                    left.visible=toolsExpanded;right.visible=toolsExpanded;
                    button.setTooltip(Tooltip.create(Component.literal(toolsExpanded?"Hide illustration tools":"Show illustration tools")));
                },()->toolsExpanded);
        toggle.setTooltip(Tooltip.create(Component.literal(toolsExpanded?"Hide illustration tools":"Show illustration tools")));
        event.addListener(toggle);
    }
    private static void open(SpreadBookScreen screen,int side) {
        if(current==null||current.screen!=screen)return;
        current.page=((SpreadAccess)screen).illustrations$spread()*2+side;
        ((EditAccess)screen).illustrations$save(false,null);
        current.panel=new IllustrationPanel(current,current.page);
        current.send(Network.OPEN,current.page,new byte[0],"");
    }
    static void save(Context context,byte[] data) {
        save(context,data,false);
    }
    static void saveOrder(Context context,boolean aboveText){
        context.aboveText=aboveText;context.send(Network.ORDER,context.page,new byte[0],"");
    }
    static void save(Context context,byte[] data,boolean aboveText) {
        context.aboveText=aboveText;
        if(data.length<=RasterCodec.MAX_BYTES&&(data.length<4||data[3]==1)){context.send(Network.PUT,context.page,data,"");return;}
        context.upload=new Upload(data);context.send(Network.BEGIN,context.page,ChunkTransfer.number(data.length),"");
    }
    private static void receive(Network.Reply reply) {
        Download requested=downloads.get(reply.id());
        if(requested!=null){
            try{
                if(!requested.hash.equals(reply.hash())||reply.kind()==Network.ERROR)throw new IllegalArgumentException();
                if(reply.kind()==Network.IMAGE){
                    downloads.remove(reply.id());ClientAssets.accept(reply.hash(),reply.data(),message->failed.add(requested.hash));
                }else if(reply.kind()==Network.IMAGE_PART){
                    var buffer=java.nio.ByteBuffer.wrap(reply.data());int total=buffer.getInt(),offset=buffer.getInt();
                    if(requested.chunks==null){requested.chunks=new ChunkTransfer(total);requested.next=ChunkTransfer.CHUNK;}
                    if(requested.chunks.size()!=total)throw new IllegalArgumentException();
                    byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);requested.chunks.append(offset,bytes);
                    if(requested.chunks.complete()){
                        downloads.remove(reply.id());ClientAssets.accept(reply.hash(),requested.chunks.finish(),message->failed.add(requested.hash));
                    }
                }else throw new IllegalArgumentException();
            }catch(Exception e){downloads.remove(reply.id());failed.add(requested.hash);}
            return;
        }
        if(current==null||reply.id()!=current.pending)return;
        if(reply.kind()==Network.ERROR){current.upload=null;if(current.panel!=null)current.panel.error(reply.message());return;}
        if(reply.kind()==Network.PROGRESS){
            if(current.upload!=null){
                int position=ChunkTransfer.number(reply.data());
                if(position<current.upload.acknowledged||position>current.upload.sent){current.upload=null;if(current.panel!=null)current.panel.error("Invalid upload acknowledgement");return;}
                current.upload.begun=true;current.upload.acknowledged=position;
                if(current.panel!=null)current.panel.progress(position,current.upload.bytes.length);
            }return;
        }
        current.token=reply.token();reply.artwork().write(current.book());
        if(reply.kind()==Network.SESSION&&current.panel!=null)current.panel.ready();
        if(reply.kind()==Network.SAVED){
            current.upload=null;
            ((EditAccess)current.screen).illustrations$dirty(true);
            // Keep normal text save order and ensure art-only pages are serialized.
            ((EditAccess)current.screen).illustrations$save(false,null);
            if(current.panel!=null)current.panel.dispose();current.panel=null;
        }
    }
    static ClientAssets.Image image(Context context,String hash) {
        ClientAssets.Image image=ClientAssets.get(hash);
        if(image!=null||ClientAssets.loading(hash)||failed.contains(hash)||downloads.values().stream().anyMatch(d->d.hash.equals(hash))||downloads.size()>=2)return image;
        int id=++sequence;downloads.put(id,new Download(context,hash));
        PacketDistributor.sendToServer(new Network.Request(id,Network.GET,context.slot,context.menu,Network.NONE,0,0,hash,new byte[0]));
        return null;
    }
    static boolean missing(String hash){return failed.contains(hash);}
    public static void renderArt(SpreadBookScreen screen,GuiGraphics graphics) {
        renderArt(screen,graphics,false);
    }
    public static void renderArt(SpreadBookScreen screen,GuiGraphics graphics,boolean aboveText) {
        if(current==null||current.screen!=screen)current=new Context(screen);
        SpreadAccess spread=(SpreadAccess)screen;
        Artwork artwork=Artwork.read(current.book());
        if(aboveText){graphics.flush();graphics.pose().pushPose();graphics.pose().translate(0,0,10);}
        for(int side=0;side<2;side++){
            int page=spread.illustrations$spread()*2+side;
            String hash=artwork.pages().get(page);
            if(hash==null)continue;
            if(artwork.isAboveText(page)!=aboveText)continue;
            int x=spread.illustrations$left()+(side==0?22:159),y=spread.illustrations$top()+21;
            ClientAssets.Image image=image(current,hash);
            if(image!=null){
                image.draw(graphics,x,y,1);
            }
            else graphics.drawString(MC.font,missing(hash)?"[Art unavailable]":"[Loading art...]",x,y+116,0xFF995555,false);
        }
        if(aboveText){graphics.flush();graphics.pose().popPose();}
    }
    private static void pump(){
        int budget=4;
        if(current!=null&&current.upload!=null){
            Upload u=current.upload;
            if(u.begun&&!u.committed){
                while(budget>0&&u.sent<u.bytes.length&&u.sent-u.acknowledged<4*ChunkTransfer.CHUNK){
                    sendUpload(Network.PART,ChunkTransfer.part(u.bytes,u.sent));u.sent+=Math.min(ChunkTransfer.CHUNK,u.bytes.length-u.sent);budget--;
                }
                if(u.acknowledged==u.bytes.length){u.committed=true;sendUpload(Network.COMMIT,new byte[0]);}
            }
        }
        for(var entry:downloads.entrySet()){
            Download d=entry.getValue();if(d.chunks==null)continue;
            while(budget>0&&d.next<d.chunks.size()&&d.next-d.chunks.position()<4*ChunkTransfer.CHUNK){
                PacketDistributor.sendToServer(new Network.Request(entry.getKey(),Network.GET,d.context.slot,d.context.menu,Network.NONE,d.next,0,d.hash,new byte[0]));
                d.next+=ChunkTransfer.CHUNK;budget--;
            }
        }
    }
    private static void sendUpload(int op,byte[] bytes){
        Context c=current;PacketDistributor.sendToServer(new Network.Request(c.pending,op,c.slot,c.menu,c.token,c.page,Artwork.read(c.book()).revision(),"",bytes,c.aboveText));
    }
}

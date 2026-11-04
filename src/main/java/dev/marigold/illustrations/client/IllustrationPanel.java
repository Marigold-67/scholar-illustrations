package dev.marigold.illustrations.client;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.marigold.illustrations.*;
import io.github.mortuusars.scholar.client.util.FileDialogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class IllustrationPanel {
    private static final int[] COLORS={0xFF222222,0xFFFFFFFF,0xFF777777,0xFF663322,
            0xFFAA3333,0xFFFF8844,0xFFFFCC55,0xFFEEBB99,0xFF336633,0xFF77AA55,
            0xFF33AAAA,0xFF3366BB,0xFF663399,0xFFBB66AA,0xFFDD99BB,0xFFCCAA77};
    private static final int[] SIZES={1,2,4,8,12};
    private record Control(String text,int x,int y,int w,Runnable action){}
    private final ClientIntegration.Context context;
    private final int page;
    private PixelCanvas canvas;
    private final PageHistory history=new PageHistory();
    private PageAsset.Layer placedImage,originalImage;
    private int[] originalDrawing;
    private boolean aboveText;
    private java.util.List<net.minecraft.util.FormattedCharSequence> textLines=java.util.List.of();
    private boolean ready,loaded,dirty,eraser,stroke,busy,saving,discardConfirmed,deleteConfirmed;
    private boolean highQuality=true,pendingHq,movingExisting;
    private int color=COLORS[0],size=0,lastX,lastY;
    private String message="Opening book session...";
    private final List<Control> controls=new ArrayList<>();
    private int panelX,panelY,panelW,panelH,canvasX,canvasY,zoom;
    private BufferedImage imported;
    private PageAsset.Source pendingSource,shownSource;
    private int layerX,layerY,layerW,layerH,dragX,dragY;
    private boolean moving;
    private ResourceLocation previewId,imagePreview;
    private boolean previewDirty=true;
    IllustrationPanel(ClientIntegration.Context context,int page){this.context=context;this.page=page;}
    void ready(){ready=true;message="Draw, or import a PNG. HQ keeps original pixels.";}
    void error(String text){saving=false;busy=false;message=text;}
    void progress(int bytes,int total){message="Uploading artwork: "+(bytes*100L/total)+"%";}
    private void load(){
        if(!ready||loaded)return;
        var artwork=Artwork.read(context.book());aboveText=artwork.isAboveText(page);
        var content=context.book().get(net.minecraft.core.component.DataComponents.WRITABLE_BOOK_CONTENT);
        String text=content==null?"":content.getPages(Minecraft.getInstance().isTextFilteringEnabled()).skip(page).findFirst().orElse("");
        textLines=Minecraft.getInstance().font.split(net.minecraft.network.chat.Component.literal(text),114);
        String hash=artwork.pages().get(page);
        if(hash==null){canvas=new PixelCanvas(new int[RasterCodec.PIXELS]);originalDrawing=canvas.pixels();loaded=true;return;}
        ClientAssets.Image image=ClientIntegration.image(context,hash);
        if(image!=null){canvas=new PixelCanvas(image.asset().drawing());placedImage=image.asset().image();originalDrawing=canvas.pixels();originalImage=placedImage;loaded=true;
            message=placedImage==null?"Artwork loaded.":"HQ image loaded: "+placedImage.source().width()+" x "+placedImage.source().height()+". Use Move PNG.";
        }else message=ClientIntegration.missing(hash)?"Artwork unavailable; cancel and restore server assets.":"Loading artwork...";
    }
    void render(GuiGraphics g,int mouseX,int mouseY){
        Minecraft mc=Minecraft.getInstance();load();
        panelW=Math.min(context.screen.width-8,560);panelH=Math.min(context.screen.height-8,390);
        panelX=(context.screen.width-panelW)/2;panelY=(context.screen.height-panelH)/2;
        canvasX=panelX+154;canvasY=panelY+28;zoom=Math.max(1,Math.min((panelW-166)/114,(panelH-72)/128));
        g.pose().pushPose();g.pose().translate(0,0,400);
        g.fill(0,0,context.screen.width,context.screen.height,0xE6111518);
        g.fill(panelX,panelY,panelX+panelW,panelY+panelH,0xFF243037);
        g.drawString(mc.font,"Illustration - page "+(page+1),panelX+8,panelY+8,0xFFFFDA99,false);
        controls.clear();int x=panelX+8,y=panelY+28;
        control("Brush",x,y,67,()->{finishMove();eraser=false;message="Brush selected";});
        control("Eraser",x+71,y,67,()->{finishMove();eraser=true;message="Eraser affects drawn pixels; Remove PNG removes the image.";});y+=19;
        control("Size -",x,y,67,()->size=Math.max(0,size-1));
        control("Size +",x+71,y,67,()->size=Math.min(SIZES.length-1,size+1));y+=19;
        control("Undo",x,y,67,()->restore(history.undo(canvas.pixels(),placedImage,aboveText)));
        control("Redo",x+71,y,67,()->restore(history.redo(canvas.pixels(),placedImage,aboveText)));y+=19;
        control("Import PNG",x,y,67,this::importPng);
        control(highQuality?"HQ on":"HQ off",x+71,y,67,()->{
            highQuality=!highQuality;message=highQuality?"Next PNG: preserve original resolution and full color.":"Next PNG: flatten to the pixel-art canvas.";
        });y+=19;
        control(imported!=null?"Place":"Move PNG",x,y,67,()->{if(imported!=null)place();else moveImage();});
        control(imported!=null?"Drop layer":"Remove PNG",x+71,y,67,()->{
            if(imported!=null){imported=null;pendingSource=null;previewDirty=true;message="Pending import discarded.";}
            else if(placedImage!=null){checkpoint();placedImage=null;movingExisting=false;changed();message="Image removed; drawing kept. Undo can restore it.";}
        });y+=19;
        control("Scale -",x,y,67,()->scale(.9));control("Scale +",x+71,y,67,()->scale(1.1));y+=19;
        control("Save",x,y,67,this::save);control("Cancel",x+71,y,67,this::cancel);y+=19;
        control("Delete art",x,y,67,this::delete);
        control(aboveText?"Above text":"Below text",x+71,y,67,()->{
            checkpoint();aboveText=!aboveText;changed();message=aboveText?"Artwork goes above text. Save to share.":"Artwork goes below text. Save to share.";
        });y+=21;
        for(int i=0;i<COLORS.length;i++){
            int cx=x+(i%8)*17,cy=y+(i/8)*13,c=COLORS[i];g.fill(cx,cy,cx+14,cy+10,c);
            if(color==c)g.renderOutline(cx-1,cy-1,16,12,0xFFFFDD88);
        }
        for(Control c:controls){
            boolean hover=mouseX>=c.x&&mouseX<c.x+c.w&&mouseY>=c.y&&mouseY<c.y+16;
            g.fill(c.x,c.y,c.x+c.w,c.y+16,hover?0xFF64777E:0xFF3B4D55);
            g.drawCenteredString(mc.font,c.text,c.x+c.w/2,c.y+4,0xFFFFFFFF);
        }
        g.fill(canvasX,canvasY,canvasX+114*zoom,canvasY+128*zoom,0xFFF4E9CF);
        if(loaded){
            if(previewDirty)updatePreview();
            PageAsset.Layer layer=visibleLayer();
            if(aboveText)renderTextPreview(g);
            g.pose().pushPose();if(aboveText)g.pose().translate(0,0,10);
            RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            if(layer!=null){
                if(shownSource!=layer.source()){releaseImagePreview();shownSource=layer.source();
                    imagePreview=ClientAssets.upload("draft_image",shownSource.width(),shownSource.height(),shownSource::pixel,true);}
                var r=layer.rect();
                g.blit(imagePreview,canvasX+r.x()*zoom,canvasY+r.y()*zoom,r.width()*zoom,r.height()*zoom,0,0,shownSource.width(),shownSource.height(),shownSource.width(),shownSource.height());
            }
            g.blit(previewId,canvasX,canvasY,114*zoom,128*zoom,0,0,114,128,114,128);
            RenderSystem.disableBlend();
            g.pose().popPose();
            if(!aboveText)renderTextPreview(g);
            g.renderOutline(canvasX-1,canvasY-1,114*zoom+2,128*zoom+2,0xFFFFDA99);
            if(imported!=null||movingExisting)g.renderOutline(canvasX+layerX*zoom,canvasY+layerY*zoom,layerW*zoom,layerH*zoom,0xFF55CCFF);
        }
        int hintY=canvasY+128*zoom+6;
        g.drawString(mc.font,imported!=null||movingExisting?"Drag image; scale to fit.":(eraser?"Eraser":"Brush")+" size "+SIZES[size],canvasX,hintY,0xFFE4D9C3,false);
        if(loaded&&visibleLayer()!=null&&hintY+22<panelY+panelH-16){var s=visibleLayer().source();
            g.drawString(mc.font,"Original: "+s.width()+" x "+s.height(),canvasX,hintY+12,0xFFE4D9C3,false);}
        String status=busy?"Choosing / reading PNG...":saving&&context.upload==null?"Preparing / saving artwork...":message;
        var lines=mc.font.split(net.minecraft.network.chat.Component.literal(status),panelW-16);
        if(!lines.isEmpty())g.drawString(mc.font,lines.getFirst(),panelX+8,panelY+panelH-13,0xFFFFDA99,false);
        g.pose().popPose();
    }
    private void renderTextPreview(GuiGraphics g){
        var mc=Minecraft.getInstance();g.pose().pushPose();g.pose().translate(canvasX,canvasY,aboveText?0:20);g.pose().scale(zoom,zoom,1);
        for(int i=0;i<Math.min(128/mc.font.lineHeight,textLines.size());i++)g.drawString(mc.font,textLines.get(i),0,i*mc.font.lineHeight,0xFF55452D,false);
        g.flush();g.pose().popPose();
    }
    private void control(String text,int x,int y,int width,Runnable action){controls.add(new Control(text,x,y,width,action));}
    void click(double mx,double my,int button){
        if(button!=0||busy||saving)return;
        for(Control c:controls)if(mx>=c.x&&mx<c.x+c.w&&my>=c.y&&my<c.y+16){
            if(loaded||c.text.equals("Cancel"))c.action.run();return;
        }
        if(!loaded)return;
        int paletteY=panelY+28+7*19+21;
        if(mx>=panelX+8&&mx<panelX+144&&my>=paletteY&&my<paletteY+26){
            int index=((int)my-paletteY)/13*8+((int)mx-panelX-8)/17;
            if(index>=0&&index<COLORS.length){finishMove();color=COLORS[index];eraser=false;}return;
        }
        if(!onCanvas(mx,my))return;lastX=px(mx);lastY=py(my);
        if(imported!=null||movingExisting){
            if(movingExisting)checkpoint();moving=true;dragX=lastX-layerX;dragY=lastY-layerY;return;
        }
        checkpoint();stroke=true;canvas.line(lastX,lastY,lastX,lastY,SIZES[size],eraser?0:color);changed();
    }
    void drag(double mx,double my){
        if(!loaded||busy||saving)return;
        if(moving&&(imported!=null||movingExisting)){
            layerX=Math.max(0,Math.min(114-layerW,px(mx)-dragX));layerY=Math.max(0,Math.min(128-layerH,py(my)-dragY));
            if(movingExisting){placedImage=placedImage.at(rect());changed();}else previewDirty=true;
        }else if(stroke){
            int nx=Math.max(0,Math.min(113,px(mx))),ny=Math.max(0,Math.min(127,py(my)));
            canvas.line(lastX,lastY,nx,ny,SIZES[size],eraser?0:color);lastX=nx;lastY=ny;changed();
        }
    }
    void release(){stroke=false;moving=false;}
    void scroll(double delta){if(busy||saving)return;if(imported!=null||movingExisting)scale(delta>0?1.1:.9);else size=Math.max(0,Math.min(SIZES.length-1,size+(delta>0?1:-1)));}
    void key(int key){
        if(busy||saving)return;if(key==GLFW.GLFW_KEY_ESCAPE){cancel();return;}if(!loaded)return;
        if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_Z)restore(Screen.hasShiftDown()?history.redo(canvas.pixels(),placedImage,aboveText):history.undo(canvas.pixels(),placedImage,aboveText));
        if(Screen.hasControlDown()&&key==GLFW.GLFW_KEY_Y)restore(history.redo(canvas.pixels(),placedImage,aboveText));
    }
    private boolean onCanvas(double x,double y){return x>=canvasX&&x<canvasX+114*zoom&&y>=canvasY&&y<canvasY+128*zoom;}
    private int px(double x){return (int)Math.floor((x-canvasX)/zoom);}
    private int py(double y){return (int)Math.floor((y-canvasY)/zoom);}
    private void checkpoint(){history.checkpoint(canvas.pixels(),placedImage,aboveText); }
    private void restore(PageHistory.State state){
        if(state==null)return;canvas.replace(state.drawing());placedImage=state.image();aboveText=state.aboveText();movingExisting=false;changed();
    }
    private void changed(){dirty=true;discardConfirmed=false;deleteConfirmed=false;previewDirty=true;}
    private void importPng(){
        busy=true;
        CompletableFuture.supplyAsync(()->{
            var selected=FileDialogs.loadFile("","Import PNG artwork","PNG images",false,"*.png");if(selected.isEmpty())return null;
            try{return PngImport.read(Path.of(selected.get()));}catch(Exception e){throw new IllegalArgumentException("PNG rejected",e);}
        }).whenComplete((image,error)->Minecraft.getInstance().execute(()->{
            busy=false;if(context.panel!=this)return;
            if(error!=null){message="PNG rejected. Maximum 2048 x 2048, 8 MiB; PNG format only.";return;}
            if(image==null){message="Import cancelled";return;}beginImport(image);
        }));
    }
    void beginImport(BufferedImage image){
        finishMove();imported=image;pendingHq=highQuality;
        pendingSource=pendingHq?new PageAsset.Source(image.getWidth(),image.getHeight(),image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth())):null;
        double fit=Math.min(114.0/image.getWidth(),128.0/image.getHeight());
        layerW=Math.max(1,Math.min(114,(int)Math.round(image.getWidth()*fit)));
        layerH=Math.max(1,Math.min(128,(int)Math.round(image.getHeight()*fit)));
        layerX=(114-layerW)/2;layerY=(128-layerH)/2;previewDirty=true;
        message=pendingHq?"HQ preview keeps original pixels. Place saves a movable image layer.":"Pixel preview: Place flattens into the drawing.";
    }
    private void moveImage(){
        if(placedImage==null){message="Import and Place an HQ image first.";return;}
        movingExisting=!movingExisting;
        if(movingExisting){var r=placedImage.rect();layerX=r.x();layerY=r.y();layerW=r.width();layerH=r.height();message="Move PNG: drag or scale. Original pixels are unchanged.";}
    }
    private void finishMove(){movingExisting=false;moving=false;}
    private PageAsset.Rect rect(){return new PageAsset.Rect(layerX,layerY,layerW,layerH);}
    private PageAsset.Layer visibleLayer(){return imported!=null&&pendingHq?new PageAsset.Layer(pendingSource,rect()):placedImage;}
    private void scale(double factor){
        if(imported==null&&!movingExisting)return;
        if(movingExisting)checkpoint();
        double ratio=imported!=null?(double)imported.getHeight()/imported.getWidth():(double)placedImage.source().height()/placedImage.source().width();
        int maxWidth=Math.max(1,Math.min(114,(int)Math.floor(128/ratio)));
        layerW=Math.max(1,Math.min(maxWidth,(int)Math.round(layerW*factor)+(factor>1&&layerW<10?1:0)));
        layerH=Math.max(1,Math.min(128,(int)Math.round(layerW*ratio)));
        layerX=Math.min(layerX,114-layerW);layerY=Math.min(layerY,128-layerH);
        if(movingExisting){placedImage=placedImage.at(rect());changed();}else previewDirty=true;
    }
    private int[] composedDrawing(){
        int[] pixels=canvas.pixels();if(imported==null||pendingHq)return pixels;
        BufferedImage scaled=new BufferedImage(layerW,layerH,BufferedImage.TYPE_INT_ARGB);var graphics=scaled.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(imported,0,0,layerW,layerH,null);graphics.dispose();
        for(int y=0;y<layerH;y++)for(int x=0;x<layerW;x++){
            int index=(layerY+y)*114+layerX+x;pixels[index]=PixelCanvas.blend(pixels[index],scaled.getRGB(x,y));
        }return pixels;
    }
    private void place(){
        if(imported==null)return;checkpoint();
        if(pendingHq)placedImage=new PageAsset.Layer(pendingSource,rect());else canvas.replace(composedDrawing());
        imported=null;pendingSource=null;changed();message=placedImage==null?"Image flattened into the drawing. Save to share.":"HQ image placed. Move PNG can resize it again after saving.";
    }
    private void save(){
        if(!ready||!loaded)return;if(imported!=null)place();finishMove();saving=true;message="Preparing artwork...";
        if(Artwork.read(context.book()).pages().containsKey(page)&&java.util.Arrays.equals(originalDrawing,canvas.pixels())&&java.util.Objects.equals(originalImage,placedImage)){
            ClientIntegration.saveOrder(context,aboveText);return;
        }
        PageAsset asset=new PageAsset(canvas.pixels(),placedImage);boolean empty=canvas.empty()&&placedImage==null;
        boolean savedAbove=aboveText;
        CompletableFuture.supplyAsync(()->empty?new byte[0]:PageCodec.encode(asset)).whenComplete((bytes,error)->Minecraft.getInstance().execute(()->{
            if(context.panel!=this)return;if(error!=null){error("Artwork could not be prepared within the size limit");return;}ClientIntegration.save(context,bytes,savedAbove);
        }));
    }
    private void cancel(){
        if((dirty||imported!=null)&&!discardConfirmed){discardConfirmed=true;message="Unsaved changes. Click Cancel again to discard.";return;}
        dispose();context.panel=null;
    }
    private void delete(){
        if(!deleteConfirmed){deleteConfirmed=true;message="Click Delete art again to remove all artwork from this page.";return;}
        saving=true;ClientIntegration.save(context,new byte[0]);
    }
    private void updatePreview(){
        if(previewId!=null)Minecraft.getInstance().getTextureManager().release(previewId);
        int[] pixels=composedDrawing();previewId=ClientAssets.upload("draft_drawing",114,128,i->pixels[i],false);previewDirty=false;
    }
    private void releaseImagePreview(){
        if(imagePreview!=null)Minecraft.getInstance().getTextureManager().release(imagePreview);imagePreview=null;shownSource=null;
    }
    void dispose(){
        if(previewId!=null)Minecraft.getInstance().getTextureManager().release(previewId);previewId=null;
        releaseImagePreview();imported=null;pendingSource=null;placedImage=null;
    }
}

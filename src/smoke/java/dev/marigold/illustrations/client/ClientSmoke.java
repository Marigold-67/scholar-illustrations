package dev.marigold.illustrations.client;

import dev.marigold.illustrations.*;
import dev.marigold.illustrations.integration.EditAccess;
import dev.marigold.illustrations.integration.SpreadAccess;
import io.github.mortuusars.scholar.client.gui.screen.edit.InHandSpreadBookEditScreen;
import io.github.mortuusars.scholar.client.gui.screen.view.InHandSpreadBookViewScreen;
import io.github.mortuusars.scholar.client.gui.screen.view.BookViewAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Opt-in development harness. Excluded from all normal release builds. */
@EventBusSubscriber(modid=Illustrations.ID,value=Dist.CLIENT)
public final class ClientSmoke {
    private static int state,ticks,total;
    private static String world,firstHash,secondHash;
    private static BlockPos chest;
    private static CompletableFuture<Void> task;
    private static final StringBuilder result=new StringBuilder();
    private static boolean complete;
    private static PageAsset.Rect resizedRect;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void pass(String message)throws Exception{
        result.append("PASS: ").append(message).append('\n');
        Illustrations.LOG.info("SMOKE PASS: {}",message);write();
    }
    private static void write()throws Exception{
        String filename=System.getProperty("scholar_illustrations.smokeExisting")!=null?"smoke-existing-book.txt":
                Boolean.getBoolean("scholar_illustrations.smokeReload")?"smoke-client-restart.txt":"smoke-results.txt";
        Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve(filename),result);
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static ClientIntegration.Context context()throws Exception{
        Field f=ClientIntegration.class.getDeclaredField("current");f.setAccessible(true);return (ClientIntegration.Context)f.get(null);
    }
    private static void next(){state++;ticks=0;}
    private static Button button(String label){
        return Minecraft.getInstance().screen.children().stream().filter(c->c instanceof Button b&&b.getMessage().getString().equals(label))
                .map(c->(Button)c).findFirst().orElseThrow();
    }
    private static void press(String label){
        Button b=button(label);check(b.visible&&b.active,"Button unavailable: "+label);b.onPress();
    }
    private static void control(IllustrationPanel panel,int row,boolean right)throws Exception{
        panel.click((int)field(panel,"panelX")+8+(right?71:0)+5,(int)field(panel,"panelY")+28+19*row+5,0);
    }
    private static void assertPageStrokeVisible(String filename)throws Exception{
        Minecraft mc=Minecraft.getInstance();SpreadAccess spread=(SpreadAccess)mc.screen;
        double scale=mc.getWindow().getGuiScale();
        int x=(int)((spread.illustrations$left()+22+90)*scale+scale/2);
        int y=(int)((spread.illustrations$top()+21+90)*scale+scale/2);
        try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            screenshot.writeToFile(mc.gameDirectory.toPath().resolve(filename));
            int pixel=screenshot.getPixelRGBA(x,y);
            check((pixel&0xFFFFFF)==0x222222,"Saved stroke is missing from the visible writable book page; pixel="+Integer.toHexString(pixel));
        }
    }
    private static void assertImportedImageVisible()throws Exception{
        Minecraft mc=Minecraft.getInstance();SpreadAccess spread=(SpreadAccess)mc.screen;double scale=mc.getWindow().getGuiScale();
        int x=(int)((spread.illustrations$left()+22+60)*scale+scale/2);
        int y=(int)((spread.illustrations$top()+21+70)*scale+scale/2);
        try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            int pixel=screenshot.getPixelRGBA(x,y),r=pixel&255,g=(pixel>>>8)&255,b=(pixel>>>16)&255;
            check(b>r+20&&b>g+10,"Saved PNG is missing from the visible book page; pixel="+Integer.toHexString(pixel));
        }
    }
    private static void assertTextOrder(boolean above,String filename)throws Exception{
        Minecraft mc=Minecraft.getInstance();SpreadAccess spread=(SpreadAccess)mc.screen;double scale=mc.getWindow().getGuiScale();int covered=0,visible=0;
        try(var screenshot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            for(int py=0;py<9;py++)for(int px=0;px<100;px++){
                int x=(int)((spread.illustrations$left()+22+px)*scale+scale/2),y=(int)((spread.illustrations$top()+21+py)*scale+scale/2);
                if((screenshot.getPixelRGBA(x,y)&0xFFFFFF)==0x3333AA)covered++;else visible++;
            }
            screenshot.writeToFile(mc.gameDirectory.toPath().resolve(filename));
        }
        check(covered>500,"Opaque scribble missing from page");
        check(above?visible==0:visible>5,"Wrong text order: above="+above+", visible="+visible+", covered="+covered);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!Boolean.getBoolean("scholar_illustrations.smokeTest")||complete)return;
        Minecraft mc=Minecraft.getInstance();ticks++;total++;
        try{
            if(total>18000)throw new AssertionError("Smoke run timed out in phase "+state);
            if(ticks<15)return;
            switch(state){
                case 0->{
                    if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen){
                        mc.options.onboardAccessibility=false;mc.options.save();mc.setScreen(new TitleScreen());return;
                    }
                    if(!(mc.screen instanceof TitleScreen))return;
                    if(System.getProperty("scholar_illustrations.smokeExisting")!=null){
                        world=System.getProperty("scholar_illustrations.smokeExisting");state=22;ticks=0;
                        mc.createWorldOpenFlows().openWorld(world,()->mc.setScreen(new TitleScreen()));return;
                    }
                    if(Boolean.getBoolean("scholar_illustrations.smokeReload")){
                        var props=new java.util.Properties();
                        try(var stream=Files.newInputStream(mc.gameDirectory.toPath().resolve("smoke-reload.properties"))){props.load(stream);}
                        world=props.getProperty("world");secondHash=props.getProperty("hash");state=14;ticks=0;
                        mc.createWorldOpenFlows().openWorld(world,()->mc.setScreen(new TitleScreen()));return;
                    }
                    world="scholar-illustrations-test-"+System.currentTimeMillis();
                    mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);
                    mc.options.pauseOnLostFocus=false;
                    LevelSettings settings=new LevelSettings("Scholar Illustrations Test",GameType.CREATIVE,false,Difficulty.PEACEFUL,
                            true,new GameRules(),WorldDataConfiguration.DEFAULT);
                    next();
                    mc.createWorldOpenFlows().createFreshLevel(world,settings,new WorldOptions(42,false,false),
                            registry->registry.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),new TitleScreen());
                }
                case 1->{
                    if(mc.player==null||mc.getSingleplayerServer()==null)return;
                    var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                    task=server.submit(()->{
                        var player=server.getPlayerList().getPlayer(uuid);
                        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);
                        book.set(DataComponents.WRITABLE_BOOK_CONTENT,new WritableBookContent(List.of(Filterable.passThrough("\u00a7lJournal\u00a7r\nPreserved Scholar text"))));
                        player.getInventory().setItem(0,book);player.getInventory().selected=0;player.inventoryMenu.broadcastChanges();
                    });next();
                }
                case 2->{
                    if(!task.isDone()||mc.player==null||!mc.player.getMainHandItem().is(Items.WRITABLE_BOOK))return;
                    mc.setScreen(new InHandSpreadBookEditScreen(mc.player.getMainHandItem(),InteractionHand.MAIN_HAND));
                    pass("Scholar editor opens with original formatted text");
                    state=20;ticks=0;
                }
                case 3->{
                    var ctx=context();if(ctx==null||ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    Screenshot.grab(mc.gameDirectory,"scholar-illustration-panel-test.png",mc.getMainRenderTarget(),component->{});
                    var panel=ctx.panel;int x=(int)field(panel,"canvasX"),y=(int)field(panel,"canvasY"),z=(int)field(panel,"zoom");
                    NeoForge.EVENT_BUS.post(new ScreenEvent.MouseButtonPressed.Pre(mc.screen,x+8*z,y+8*z,0));
                    NeoForge.EVENT_BUS.post(new ScreenEvent.MouseDragged.Pre(mc.screen,x+90*z,y+90*z,0,82*z,82*z));
                    NeoForge.EVENT_BUS.post(new ScreenEvent.MouseButtonReleased.Pre(mc.screen,x+90*z,y+90*z,0));
                    PixelCanvas canvas=(PixelCanvas)field(panel,"canvas");check(!canvas.empty(),"Drawing input did not reach canvas");
                    control(panel,2,false);check(canvas.empty(),"Undo failed");control(panel,2,true);check(!canvas.empty(),"Redo failed");
                    control(panel,0,true);panel.click(x+8*z,y+8*z,0);panel.release();
                    check(canvas.pixels()[8*114+8]==0,"Eraser tool failed");
                    control(panel,2,false);control(panel,0,false);
                    pass("Brush input routed through NeoForge screen events; undo and redo work inside Scholar");
                    control(panel,6,false);next();
                }
                case 4->{
                    var ctx=context();if(ctx.panel!=null)return;
                    assertPageStrokeVisible("regression-after-save.png");
                    pass("Saved drawing is visibly rendered on the writable book page before signing");
                    Artwork art=Artwork.read(ctx.book());check(art.pages().size()==1,"Illustration was not saved");firstHash=art.pages().get(0);
                    pass("Drawing upload acknowledged by server; book stores content hash");press("Draw L");next();
                }
                case 5->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    var panel=ctx.panel;PixelCanvas canvas=(PixelCanvas)field(panel,"canvas");check(!canvas.empty(),"Saved drawing did not reopen");
                    BufferedImage fixture=new BufferedImage(1024,512,BufferedImage.TYPE_INT_ARGB);
                    var random=new java.util.Random(72);
                    for(int y=0;y<512;y++)for(int x=0;x<1024;x++)fixture.setRGB(x,y,random.nextInt());
                    for(int y=80;y<460;y++)for(int x=80;x<960;x++)fixture.setRGB(x,y,0xAA4488DD);
                    fixture.setRGB(0,0,0x7F123456);
                    Path file=mc.gameDirectory.toPath().resolve("fixture.png");ImageIO.write(fixture,"png",file.toFile());
                    BufferedImage decoded=PngImport.read(file);Files.delete(file);panel.beginImport(decoded);
                    control(panel,5,false); // Scale down in actual panel controls.
                    int x=(int)field(panel,"canvasX"),y=(int)field(panel,"canvasY"),z=(int)field(panel,"zoom");
                    panel.click(x+40*z,y+40*z,0);panel.drag(x+45*z,y+45*z);panel.release();control(panel,4,false);
                    check(field(panel,"imported")==null,"Place did not commit the import");
                    var image=(PageAsset.Layer)field(panel,"placedImage");
                    check(image.source().width()==1024&&image.source().height()==512&&image.source().pixel(0)==0x7F123456,"HQ import resampled original pixels");
                    check(PageCodec.encode(new PageAsset(canvas.pixels(),image)).length>30000,"Fixture did not exercise large transfers");
                    pass("1024 x 512 transparent PNG decoded, positioned and resized without resampling; multi-packet fixture (picker bypassed)");
                    control(panel,6,false);next();
                }
                case 6->{
                    var ctx=context();if(ctx.panel!=null)return;secondHash=Artwork.read(ctx.book()).pages().get(0);
                    if(ClientAssets.get(secondHash)==null){ticks=0;return;}
                    assertPageStrokeVisible("regression-after-import.png");
                    assertImportedImageVisible();
                    pass("Imported transparent PNG is visibly rendered beside drawing on the writable book page");
                    check(!firstHash.equals(secondHash),"PNG did not alter stored artwork");
                    check(ctx.book().get(DataComponents.WRITABLE_BOOK_CONTENT).pages().getFirst().raw().contains("\u00a7lJournal"),"Text formatting was lost");
                    pass("PNG plus drawing saved without original file; Scholar formatting retained");
                    press("Draw L");state=24;ticks=0;
                }
                case 7->{
                    if(!mc.player.getMainHandItem().is(Items.WRITTEN_BOOK))return;
                    check(secondHash.equals(Artwork.read(mc.player.getMainHandItem()).pages().get(0)),"Signing lost artwork");
                    mc.setScreen(new InHandSpreadBookViewScreen(BookViewAccess.fromItem(mc.player.getMainHandItem()),0xFFFFFF,InteractionHand.MAIN_HAND));
                    pass("Scholar signs illustrated book; normal Scholar reading screen opens");next();
                }
                case 8->{
                    assertTextOrder(true,"order-above-reader.png");pass("Signed Scholar reader renders artwork over its text");
                    Screenshot.grab(mc.gameDirectory,"scholar-reading-test.png",mc.getMainRenderTarget(),component->{});
                    var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();chest=mc.player.blockPosition().offset(1,0,0);
                    task=server.submit(()->{
                        var player=server.getPlayerList().getPlayer(uuid);var level=server.overworld();
                        level.setBlockAndUpdate(chest,Blocks.CHEST.defaultBlockState());
                        ((ChestBlockEntity)level.getBlockEntity(chest)).setItem(0,player.getMainHandItem().copy());
                        player.getInventory().setItem(0,ItemStack.EMPTY);player.inventoryMenu.broadcastChanges();server.saveEverything(true,true,true);
                    });next();
                }
                case 9->{
                    if(!task.isDone())return;task.join();pass("Signed book transferred into a real chest and world saved");
                    next();mc.level.disconnect();mc.disconnect(new TitleScreen());
                }
                case 10->{
                    if(mc.level!=null||mc.getSingleplayerServer()!=null)return;
                    ClientAssets.clear();pass("Integrated server shut down; client texture cache cleared");
                    mc.createWorldOpenFlows().openWorld(world,()->mc.setScreen(new TitleScreen()));next();
                }
                case 11->{
                    if(mc.player==null||mc.getSingleplayerServer()==null)return;
                    var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
                    task=server.submit(()->{
                        var level=server.overworld();level.getChunkAt(chest);
                        ItemStack recovered=((ChestBlockEntity)level.getBlockEntity(chest)).getItem(0).copy();
                        check(secondHash.equals(Artwork.read(recovered).pages().get(0)),"World reload lost manifest");
                        var player=server.getPlayerList().getPlayer(uuid);player.getInventory().setItem(0,recovered);player.inventoryMenu.broadcastChanges();
                    });next();
                }
                case 12->{
                    if(!task.isDone())return;task.join();
                    if(!mc.player.getMainHandItem().is(Items.WRITTEN_BOOK))return;
                    // Remove local cached image so re-reading must fetch from the restarted server.
                    Files.deleteIfExists(mc.gameDirectory.toPath().resolve("cache/scholar_illustrations/"+secondHash+".sia"));ClientAssets.clear();
                    mc.setScreen(new InHandSpreadBookViewScreen(BookViewAccess.fromItem(mc.player.getMainHandItem()),0xFFFFFF,InteractionHand.MAIN_HAND));next();
                }
                case 13->{
                    if(ClientAssets.get(secondHash)==null){ticks=0;return;}
                    check(Artwork.read(mc.player.getMainHandItem()).isAboveText(0),"Restart lost text order");
                    assertTextOrder(true,"order-after-world-restart.png");
                    pass("Book recovered from chest after server restart; artwork re-downloaded from persistent server assets");
                    var props=new java.util.Properties();props.setProperty("world",world);props.setProperty("hash",secondHash);
                    try(var stream=Files.newOutputStream(mc.gameDirectory.toPath().resolve("smoke-reload.properties"))){props.store(stream,"Isolated development test fixture");}
                    result.append("NOT TESTED: native file picker interaction; two independent clients; dedicated WinterNode; 170-mod pack.\n");write();
                    complete=true;mc.level.disconnect();mc.disconnect(new TitleScreen());mc.stop();
                }
                case 14->{
                    if(mc.player==null||!mc.player.getMainHandItem().is(Items.WRITTEN_BOOK))return;
                    check(secondHash.equals(Artwork.read(mc.player.getMainHandItem()).pages().get(0)),"Client restart lost book reference");
                    mc.setScreen(new InHandSpreadBookViewScreen(BookViewAccess.fromItem(mc.player.getMainHandItem()),0xFFFFFF,InteractionHand.MAIN_HAND));next();
                }
                case 15->{
                    if(ClientAssets.get(secondHash)==null){ticks=0;return;}
                    check(Artwork.read(mc.player.getMainHandItem()).isAboveText(0),"Client restart lost text order");
                    assertTextOrder(true,"order-after-client-restart.png");
                    pass("Separate Minecraft client process reopens the saved world, book, and persistent image cache");
                    Files.writeString(mc.gameDirectory.toPath().resolve("smoke-client-restart.txt"),result);
                    complete=true;mc.level.disconnect();mc.disconnect(new TitleScreen());mc.stop();
                }
                case 20->{
                    check(!button("Draw L").visible&&!button("Draw R").visible,"Illustration tools should start hidden");
                    Screenshot.grab(mc.gameDirectory,"illustration-tools-collapsed.png",mc.getMainRenderTarget(),component->{});
                    var original=mc.screen;
                    press("Illustration tools");check(button("Draw L").visible&&button("Draw R").visible,"Toggle did not show the page controls");
                    press("Illustration tools");check(!button("Draw L").visible&&!button("Draw R").visible,"Toggle did not hide the page controls");
                    check(mc.screen==original,"Toggle replaced the Scholar editor");
                    press("Illustration tools");state=21;ticks=0;
                    pass("Small pencil toggle shows/hides compact page controls without rebuilding Scholar's screen");
                }
                case 21->{
                    Screenshot.grab(mc.gameDirectory,"illustration-tools-expanded.png",mc.getMainRenderTarget(),component->{});
                    press("Draw L");state=3;ticks=0;
                }
                case 22->{
                    if(mc.player==null||!mc.player.getMainHandItem().is(Items.WRITABLE_BOOK))return;
                    check(!Artwork.read(mc.player.getMainHandItem()).pages().isEmpty(),"Old test book has no saved artwork");
                    mc.setScreen(new InHandSpreadBookEditScreen(mc.player.getMainHandItem(),InteractionHand.MAIN_HAND));
                    state=23;ticks=0;
                }
                case 23->{
                    if(context()==null||ClientAssets.get(Artwork.read(context().book()).pages().get(0))==null)return;
                    assertPageStrokeVisible("existing-artwork-recovered.png");
                    pass("Artwork saved by the old build is visible in the writable editor without reimporting or resaving it");
                    complete=true;mc.level.disconnect();mc.disconnect(new TitleScreen());mc.stop();
                }
                case 24->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    var panel=ctx.panel;var image=(PageAsset.Layer)field(panel,"placedImage");
                    check(image!=null&&image.source().width()==1024&&image.source().pixel(0)==0x7F123456,"Saved HQ source did not reopen losslessly");
                    control(panel,4,false);control(panel,5,false);control(panel,5,false);
                    int x=(int)field(panel,"canvasX"),y=(int)field(panel,"canvasY"),z=(int)field(panel,"zoom");
                    panel.click(x+40*z,y+50*z,0);panel.drag(x+43*z,y+53*z);panel.release();
                    var moved=(PageAsset.Layer)field(panel,"placedImage");resizedRect=moved.rect();
                    check(moved.source()==image.source()&&!moved.rect().equals(image.rect()),"Resize replaced source or did not change placement");
                    control(panel,2,false);control(panel,2,true);
                    check(((PageAsset.Layer)field(panel,"placedImage")).rect().equals(resizedRect),"Layer undo/redo lost placement");
                    pass("Saved HQ PNG reopens with original pixels; Move PNG, resizing, dragging and undo/redo retain the same source");
                    control(panel,6,false);state=25;ticks=0;
                }
                case 25->{
                    var ctx=context();if(ctx.panel!=null)return;
                    secondHash=Artwork.read(ctx.book()).pages().get(0);
                    if(ClientAssets.get(secondHash)==null){ticks=0;return;}
                    assertPageStrokeVisible("hq-resized-after-save.png");
                    press("Draw L");state=26;ticks=0;
                }
                case 26->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    var image=(PageAsset.Layer)field(ctx.panel,"placedImage");
                    check(image.source().width()==1024&&image.source().height()==512&&image.source().pixel(0)==0x7F123456&&image.rect().equals(resizedRect),"Resize/save/reopen lost resolution or placement");
                    pass("Second HQ save/reopen retains 1024 x 512 source, exact full-color alpha pixel, new size and position");
                    var panel=ctx.panel;var canvas=(PixelCanvas)field(panel,"canvas");int[] before=canvas.pixels();
                    control(panel,4,true);check(field(panel,"placedImage")==null&&java.util.Arrays.equals(before,canvas.pixels()),"Remove PNG affected drawing");
                    control(panel,2,false);check(((PageAsset.Layer)field(panel,"placedImage")).source()==image.source(),"Undo did not restore removed HQ image");
                    control(panel,3,true);var small=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);small.setRGB(0,0,0xFFFF0000);
                    panel.beginImport(small);control(panel,4,false);
                    check(!java.util.Arrays.equals(before,canvas.pixels())&&((PageAsset.Layer)field(panel,"placedImage")).source()==image.source(),"HQ off did not flatten into drawing independently");
                    control(panel,2,false);check(java.util.Arrays.equals(before,canvas.pixels()),"Undo did not restore drawing after flattened import");
                    panel.beginImport(small);control(panel,4,true);
                    check(field(panel,"imported")==null&&((PageAsset.Layer)field(panel,"placedImage")).source()==image.source(),"Drop layer removed saved HQ image");
                    pass("Remove PNG preserves drawing and supports Undo; HQ off flattens separately; Drop layer only discards pending import");
                    control(panel,6,true);control(panel,6,true);
                    press("Draw L");state=30;ticks=0;
                }
                case 30->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    var canvas=(PixelCanvas)field(ctx.panel,"canvas");int[] pixels=canvas.pixels();
                    for(int y=0;y<9;y++)for(int x=0;x<114;x++)pixels[y*114+x]=0xFFAA3333;
                    canvas.replace(pixels);check(!(boolean)field(ctx.panel,"aboveText"),"Existing page should default below text");
                    control(ctx.panel,6,false);state=31;ticks=0;
                }
                case 31->{
                    var ctx=context();if(ctx.panel!=null)return;secondHash=Artwork.read(ctx.book()).pages().get(0);
                    if(ClientAssets.get(secondHash)==null){ticks=0;return;}
                    assertTextOrder(false,"order-below-editor.png");pass("Opaque scribble renders beneath real Scholar text in writable editor");
                    mc.setScreen(new InHandSpreadBookViewScreen(BookViewAccess.fromItem(mc.player.getMainHandItem()),0xFFFFFF,InteractionHand.MAIN_HAND));state=36;ticks=0;
                }
                case 36->{
                    assertTextOrder(false,"order-below-reader.png");pass("Below-text art leaves actual Scholar reader text visible");
                    mc.setScreen(new InHandSpreadBookEditScreen(mc.player.getMainHandItem(),InteractionHand.MAIN_HAND));press("Draw L");state=32;ticks=0;
                }
                case 32->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    control(ctx.panel,7,true);check((boolean)field(ctx.panel,"aboveText"),"Above text button failed");
                    control(ctx.panel,2,false);check(!(boolean)field(ctx.panel,"aboveText"),"Undo did not restore below text");
                    control(ctx.panel,2,true);check((boolean)field(ctx.panel,"aboveText"),"Redo did not restore above text");
                    control(ctx.panel,6,false);state=33;ticks=0;
                }
                case 33->{
                    var ctx=context();if(ctx.panel!=null)return;
                    check(Artwork.read(ctx.book()).isAboveText(0)&&secondHash.equals(Artwork.read(ctx.book()).pages().get(0)),"Order-only save lost flag or replaced asset");
                    assertTextOrder(true,"order-above-editor.png");pass("Above text toggle, Undo/Redo and Save cover actual editor glyphs without reuploading/replacing image asset");
                    press("Draw L");state=34;ticks=0;
                }
                case 34->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    check((boolean)field(ctx.panel,"aboveText"),"Saved above-text selection did not reopen");
                    control(ctx.panel,7,true);control(ctx.panel,6,false);state=35;ticks=0;
                }
                case 35->{
                    var ctx=context();if(ctx.panel!=null)return;
                    assertTextOrder(false,"order-switched-back.png");pass("Saved illustration reopens above text, then switches back below without modifying text");
                    press("Draw L");state=37;ticks=0;
                }
                case 37->{
                    var ctx=context();if(ctx.panel==null||field(ctx.panel,"canvas")==null)return;
                    control(ctx.panel,7,true);control(ctx.panel,6,false);state=38;ticks=0;
                }
                case 38->{
                    var ctx=context();if(ctx.panel!=null)return;
                    ((EditAccess)ctx.screen).illustrations$save(true,"Illustrated Test");mc.setScreen(null);state=7;ticks=0;
                }
            }
        }catch(Throwable error){
            complete=true;result.append("FAIL in phase ").append(state).append(": ").append(error).append('\n');
            Illustrations.LOG.error("SMOKE FAILURE",error);try{write();}catch(Exception ignored){}mc.stop();
        }
    }
}

package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class BookDataTest {
    @Test void textOrderIsPerPageAndSurvivesItemSerialization(){
        String hash="a".repeat(64);
        Artwork artwork=new Artwork(0,Map.of()).with(0,hash,true).with(1,hash,false);
        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);artwork.write(book);
        Artwork reopened=Artwork.read(book.transmuteCopy(Items.WRITTEN_BOOK).copy());
        assertTrue(reopened.isAboveText(0));assertFalse(reopened.isAboveText(1));
        assertEquals(artwork,Artwork.read(artwork.tag()));
        assertFalse(artwork.with(0,"").isAboveText(0));
        assertEquals(hash,artwork.with(0,hash,false).pages().get(0));
    }
    @Test void olderManifestsDefaultBelowWithoutAddingFieldsOnRead(){
        var old=new Artwork(4,Map.of(0,"b".repeat(64))).tag();
        var before=old.copy();assertFalse(Artwork.read(old).isAboveText(0));assertEquals(before,old);
        assertFalse(old.getList("pages",net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0).contains("above_text"));
    }
    static {SharedConstants.tryDetectVersion();Bootstrap.bootStrap();}
    @Test void vanillaBooksStayUnchangedUntilIllustrated(){
        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);
        assertEquals(0,Artwork.read(book).pages().size());assertFalse(book.has(DataComponents.CUSTOM_DATA));
    }
    @Test void bookCopyAndSigningKeepArtworkAndUnrelatedCustomData(){
        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);
        CompoundTag existing=new CompoundTag();existing.putString("other_mod","keep me");book.set(DataComponents.CUSTOM_DATA,CustomData.of(existing));
        Artwork artwork=new Artwork(7,Map.of(4,"a".repeat(64)));artwork.write(book);
        ItemStack signed=book.transmuteCopy(Items.WRITTEN_BOOK);
        assertEquals(artwork,Artwork.read(signed));assertEquals(artwork,Artwork.read(signed.copy()));
        assertEquals("keep me",signed.get(DataComponents.CUSTOM_DATA).copyTag().getString("other_mod"));
        assertEquals(5,Artwork.read(signed).pageCount());
        assertEquals(artwork,Artwork.read(artwork.tag()));
    }
    @Test void pageBudgetAndDeleteAreEnforced(){
        Artwork artwork=new Artwork(0,Map.of());
        for(int i=0;i<16;i++)artwork=artwork.with(i,"a".repeat(64));
        Artwork full=artwork;assertThrows(IllegalArgumentException.class,()->full.with(16,"b".repeat(64)));
        assertEquals(15,artwork.with(0,"").pages().size());
    }
    @Test void futureArtworkFormatsArePreservedAndRejectedForEditing(){
        ItemStack book=new ItemStack(Items.WRITABLE_BOOK);CompoundTag root=new CompoundTag(),future=new CompoundTag();
        future.putInt("format",2);future.putString("future_data","preserve me");root.put(Artwork.KEY,future);
        book.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
        assertThrows(IllegalArgumentException.class,()->Artwork.validateForEditing(book));
        assertEquals("preserve me",book.get(DataComponents.CUSTOM_DATA).copyTag().getCompound(Artwork.KEY).getString("future_data"));
    }
}

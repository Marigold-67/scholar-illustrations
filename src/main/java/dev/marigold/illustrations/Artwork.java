package dev.marigold.illustrations;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;

/** Vanilla custom_data keeps unknown illustration metadata readable without this addon. */
public record Artwork(int revision, Map<Integer,String> pages, Set<Integer> aboveText) {
    public static final String KEY = "scholar_illustrations";
    public static final int MAX_PAGES = 16;
    public Artwork(int revision,Map<Integer,String> pages){this(revision,pages,Set.of());}
    public Artwork {
        pages = Map.copyOf(pages);aboveText=Set.copyOf(aboveText);
        if(!pages.keySet().containsAll(aboveText))throw new IllegalArgumentException("Artwork order requires a saved page");
    }
    public boolean isAboveText(int page){return aboveText.contains(page);}
    public static Artwork read(ItemStack book) {
        return read(book.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(KEY));
    }
    public static void validateForEditing(ItemStack book) {
        CompoundTag root=book.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        if(!root.contains(KEY))return;
        if(!root.contains(KEY,Tag.TAG_COMPOUND)||root.getCompound(KEY).getInt("format")!=1)
            throw new IllegalArgumentException("Unsupported artwork data version; keep the original book and use its matching addon");
        CompoundTag tag=root.getCompound(KEY);ListTag entries=tag.getList("pages",Tag.TAG_COMPOUND);
        if(entries.size()>MAX_PAGES||read(tag).pages().size()!=entries.size())
            throw new IllegalArgumentException("Damaged artwork manifest; restore this book from backup before editing");
    }
    public static Artwork read(CompoundTag tag) {
        Map<Integer,String> pages = new TreeMap<>();
        Set<Integer> above=new HashSet<>();
        if (tag.getInt("format") != 1) return new Artwork(0,pages);
        ListTag list=tag.getList("pages",Tag.TAG_COMPOUND);
        for (int i=0;i<Math.min(MAX_PAGES,list.size());i++) {
            CompoundTag entry=list.getCompound(i); int page=entry.getInt("page");
            String hash=entry.getString("hash");
            if (page>=0 && page<100 && RasterCodec.validHash(hash)) {
                pages.put(page,hash);if(entry.getBoolean("above_text"))above.add(page);else above.remove(page);
            }
        }
        return new Artwork(tag.getInt("revision"),pages,above);
    }
    public CompoundTag tag() {
        CompoundTag tag=new CompoundTag(); tag.putInt("format",1); tag.putInt("revision",revision);
        ListTag entries=new ListTag();
        new TreeMap<>(pages).forEach((page,hash)->{
            CompoundTag entry=new CompoundTag(); entry.putInt("page",page);entry.putString("hash",hash);entries.add(entry);
            if(isAboveText(page))entry.putBoolean("above_text",true);
        });
        tag.put("pages",entries);return tag;
    }
    public void write(ItemStack book) {
        CustomData.update(DataComponents.CUSTOM_DATA,book,tag->tag.put(KEY,tag()));
    }
    public int pageCount() { return pages.keySet().stream().mapToInt(x->x+1).max().orElse(0); }
    public Artwork with(int page,String hash) {
        return with(page,hash,isAboveText(page));
    }
    public Artwork with(int page,String hash,boolean above) {
        Map<Integer,String> updated=new TreeMap<>(pages);
        Set<Integer> order=new HashSet<>(aboveText);
        if (hash.isEmpty()) updated.remove(page); else updated.put(page,hash);
        if(!hash.isEmpty()&&above)order.add(page);else order.remove(page);
        if(updated.size()>MAX_PAGES)throw new IllegalArgumentException("Maximum 16 illustrated pages per book");
        return new Artwork(revision+1,updated,order);
    }
}

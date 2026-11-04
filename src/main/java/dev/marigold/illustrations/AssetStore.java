package dev.marigold.illustrations;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Immutable, content-addressed world assets; no garbage collection of potentially live books. */
public final class AssetStore {
    public static final long WORLD_QUOTA=512L*1024*1024, PLAYER_QUOTA=64L*1024*1024;
    private final Path root;
    private long used;
    public AssetStore(MinecraftServer server) throws IOException {
        this(server.getWorldPath(LevelResource.ROOT).resolve("data/scholar_illustrations"));
    }
    AssetStore(Path directory) throws IOException {
        root=directory;
        Files.createDirectories(root);
        try(var entries=Files.newDirectoryStream(root,"*.sia")){
            for(Path path:entries)if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))used+=Files.size(path);
        }
    }
    public synchronized String save(UUID owner,byte[] submitted)throws IOException {
        byte[] canonical=PageCodec.encode(PageCodec.decode(submitted));
        String hash=RasterCodec.hash(canonical);Path target=path(hash);
        if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
            // Never silently reuse a corrupted asset.
            read(hash);return hash;
        }
        Path ledger=root.resolve("usage_"+owner+".txt");
        long playerUsed=Files.exists(ledger)?Long.parseLong(Files.readString(ledger,StandardCharsets.US_ASCII).trim()):0;
        if(used+canonical.length>WORLD_QUOTA)throw new IOException("World artwork quota reached (512 MiB)");
        if(playerUsed+canonical.length>PLAYER_QUOTA)throw new IOException("Your artwork upload quota is reached (64 MiB)");
        atomic(ledger,Long.toString(playerUsed+canonical.length).getBytes(StandardCharsets.US_ASCII));
        atomic(target,canonical);used+=canonical.length;return hash;
    }
    public synchronized byte[] read(String hash)throws IOException {
        Path target=path(hash);
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))throw new IOException("Artwork is missing from this world backup");
        long size=Files.size(target);
        if(size<6||size>PageCodec.MAX_BYTES)throw new IOException("Invalid artwork file size");
        byte[] bytes=Files.readAllBytes(target);
        if(!RasterCodec.hash(bytes).equals(hash))throw new IOException("Artwork file checksum mismatch");
        PageCodec.decode(bytes);return bytes;
    }
    public synchronized long size(String hash)throws IOException{return Files.size(path(hash));}
    public synchronized boolean highResolution(String hash)throws IOException{
        try(var input=Files.newInputStream(path(hash))){byte[] header=input.readNBytes(4);return header.length==4&&header[3]==2;}
    }
    private Path path(String hash)throws IOException {
        if(!RasterCodec.validHash(hash))throw new IOException("Invalid artwork reference");
        return root.resolve(hash+".sia");
    }
    private static void atomic(Path target,byte[] bytes)throws IOException {
        Path temp=Files.createTempFile(target.getParent(),"upload-",".tmp");
        try {
            Files.write(temp,bytes);
            try{Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
}

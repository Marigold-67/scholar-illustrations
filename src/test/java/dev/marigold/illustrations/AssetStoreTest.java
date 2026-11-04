package dev.marigold.illustrations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class AssetStoreTest {
    @TempDir Path directory;
    @Test void assetsSurviveStoreRecreationAndAreDeduplicated()throws Exception{
        byte[] bytes=RasterCodec.encode(new int[RasterCodec.PIXELS]);
        AssetStore first=new AssetStore(directory);String hash=first.save(UUID.randomUUID(),bytes);
        assertEquals(hash,first.save(UUID.randomUUID(),bytes));
        AssetStore reopened=new AssetStore(directory);assertArrayEquals(bytes,reopened.read(hash));
        try(var files=Files.list(directory)){assertEquals(1,files.filter(p->p.toString().endsWith(".sia")).count());}
    }
    @Test void malformedUploadsMissingAssetsAndCorruptionFailClearly()throws Exception{
        AssetStore store=new AssetStore(directory);
        assertThrows(Exception.class,()->store.save(UUID.randomUUID(),new byte[100]));
        assertThrows(Exception.class,()->store.read("../bad"));
        assertThrows(Exception.class,()->store.read("a".repeat(64)));
        String hash=store.save(UUID.randomUUID(),RasterCodec.encode(new int[RasterCodec.PIXELS]));
        Files.write(directory.resolve(hash+".sia"),new byte[10]);
        assertThrows(Exception.class,()->new AssetStore(directory).read(hash));
    }
}

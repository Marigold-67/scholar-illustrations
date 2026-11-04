package dev.marigold.illustrations.client;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;
class PngImportTest {
    @TempDir Path directory;
    @Test void importsRealPngTransparencyWithoutKeepingSource()throws Exception{
        BufferedImage image=new BufferedImage(40,30,BufferedImage.TYPE_INT_ARGB);image.setRGB(1,2,0x8055AAFF);
        Path file=directory.resolve("art.png");ImageIO.write(image,"png",file.toFile());
        BufferedImage imported=PngImport.read(file);Files.delete(file);
        assertEquals(40,imported.getWidth());assertEquals(0x8055AAFF,imported.getRGB(1,2));
    }
    @Test void rejectsFakePngAndLargeDimensions()throws Exception{
        Path file=directory.resolve("fake.png");Files.writeString(file,"this is not a PNG");
        assertThrows(Exception.class,()->PngImport.read(file));
        BufferedImage big=new BufferedImage(2049,1,BufferedImage.TYPE_INT_ARGB);
        ImageIO.write(big,"png",file.toFile());assertThrows(Exception.class,()->PngImport.read(file));
    }
    @Test void rejectsLargeFileBeforeDecoding()throws Exception{
        Path file=directory.resolve("oversized.png");Files.write(file,new byte[PngImport.MAX_FILE_BYTES+1]);
        assertThrows(Exception.class,()->PngImport.read(file));
    }
}

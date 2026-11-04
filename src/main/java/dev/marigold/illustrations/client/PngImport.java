package dev.marigold.illustrations.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class PngImport {
    static final int MAX_FILE_BYTES=8*1024*1024,MAX_DIMENSION=2048;
    static BufferedImage read(Path selected)throws IOException {
        byte[] bytes;
        try(var input=Files.newInputStream(selected)){bytes=input.readNBytes(MAX_FILE_BYTES+1);}
        if(bytes.length>MAX_FILE_BYTES)throw new IOException("PNG must be at most 8 MiB");
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        if(bytes.length<24||!Arrays.equals(signature,Arrays.copyOf(bytes,8)))throw new IOException("Choose a valid PNG file");
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReadersByFormatName("PNG");
            if(!readers.hasNext())throw new IOException("PNG decoder unavailable");
            ImageReader reader=readers.next();
            try{
                reader.setInput(input,true,true);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<1||height<1||width>MAX_DIMENSION||height>MAX_DIMENSION)
                    throw new IOException("Maximum PNG dimensions: 2048 x 2048");
                return reader.read(0);
            }finally{reader.dispose();}
        }
    }
}

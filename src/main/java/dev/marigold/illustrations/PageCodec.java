package dev.marigold.illustrations;

import java.io.*;
import java.util.zip.*;

/** SIA1 stays byte-compatible. SIA2 adds original-resolution RGBA8888 pixels and page placement. */
public final class PageCodec {
    public static final int MAX_BYTES=17*1024*1024;
    private PageCodec(){}
    public static byte[] encode(PageAsset asset){
        byte[] drawing=RasterCodec.encode(asset.drawing());
        if(asset.image()==null)return drawing;
        var layer=asset.image();var source=layer.source();var rect=layer.rect();
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream header=new DataOutputStream(bytes);
            header.write(new byte[]{'S','I','A',2});header.writeShort(source.width());header.writeShort(source.height());
            header.writeShort(rect.x());header.writeShort(rect.y());header.writeShort(rect.width());header.writeShort(rect.height());
            Deflater deflater=new Deflater(6);
            try(var zip=new DeflaterOutputStream(bytes,deflater,16384)){
                DataOutputStream out=new DataOutputStream(zip);out.writeShort(drawing.length);out.write(drawing);
                byte[] block=new byte[16384];int offset=0;
                for(int i=0;i<source.width()*source.height();i++){
                    int c=source.pixel(i);block[offset++]=(byte)(c>>>24);block[offset++]=(byte)(c>>>16);
                    block[offset++]=(byte)(c>>>8);block[offset++]=(byte)c;
                    if(offset==block.length){out.write(block);offset=0;}
                }
                if(offset>0)out.write(block,0,offset);
            }finally{deflater.end();}
            if(bytes.size()>MAX_BYTES)throw new IllegalArgumentException("Artwork exceeds 17 MiB");
            return bytes.toByteArray();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static PageAsset decode(byte[] bytes)throws IOException{
        if(bytes.length<4||bytes.length>MAX_BYTES||bytes[0]!='S'||bytes[1]!='I'||bytes[2]!='A')
            throw new IOException("Unsupported or oversized artwork");
        if(bytes[3]==1)return new PageAsset(RasterCodec.decode(bytes),null);
        if(bytes[3]!=2||bytes.length<20)throw new IOException("Unsupported artwork format");
        DataInputStream header=new DataInputStream(new ByteArrayInputStream(bytes,4,12));
        int width=header.readUnsignedShort(),height=header.readUnsignedShort();
        if(width<1||height<1||width>2048||height>2048)throw new IOException("Invalid high-resolution image dimensions");
        PageAsset.Rect rect;
        try{rect=new PageAsset.Rect(header.readUnsignedShort(),header.readUnsignedShort(),header.readUnsignedShort(),header.readUnsignedShort());}
        catch(IllegalArgumentException e){throw new IOException(e.getMessage());}
        Inflater inflater=new Inflater();
        try{
            inflater.setInput(bytes,16,bytes.length-16);
            // Allocate only the size proved by the bounded dimensions and drawing-length field.
            byte[] length=inflate(inflater,2);int count=((length[0]&255)<<8)|(length[1]&255);
            if(count<6||count>RasterCodec.MAX_BYTES)throw new IOException("Invalid drawing length");
            int[] drawing=RasterCodec.decode(inflate(inflater,count));
            int[] pixels=new int[width*height];byte[] block=new byte[16384];int index=0;
            while(index<pixels.length){
                int n=Math.min(block.length,(pixels.length-index)*4);inflateInto(inflater,block,n);
                for(int i=0;i<n;i+=4)pixels[index++]=((block[i]&255)<<24)|((block[i+1]&255)<<16)|((block[i+2]&255)<<8)|(block[i+3]&255);
            }
            if(inflater.inflate(new byte[1])!=0||!inflater.finished()||inflater.getRemaining()!=0)
                throw new IOException("Invalid artwork length or trailing data");
            return new PageAsset(drawing,new PageAsset.Layer(new PageAsset.Source(width,height,pixels),rect));
        }catch(DataFormatException e){throw new IOException("Malformed artwork compression",e);}
        finally{inflater.end();}
    }
    private static byte[] inflate(Inflater inflater,int count)throws IOException,DataFormatException{
        byte[] result=new byte[count];inflateInto(inflater,result,count);return result;
    }
    private static void inflateInto(Inflater inflater,byte[] result,int count)throws IOException,DataFormatException{
        int offset=0;
        while(offset<count){int n=inflater.inflate(result,offset,count-offset);if(n==0)throw new IOException("Incomplete artwork");offset+=n;}
    }
}

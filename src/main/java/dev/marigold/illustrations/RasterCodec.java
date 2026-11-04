package dev.marigold.illustrations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import java.util.zip.DataFormatException;

/** Fixed 114x128 RGBA4444 raster. No PNG decoder or metadata on the server. */
public final class RasterCodec {
    public static final int WIDTH = 114, HEIGHT = 128, PIXELS = WIDTH * HEIGHT;
    public static final int MAX_BYTES = 30000;
    private RasterCodec() {}

    public static byte[] encode(int[] argb) {
        if (argb.length != PIXELS) throw new IllegalArgumentException("Wrong canvas dimensions");
        byte[] raw = new byte[PIXELS * 2];
        for (int i = 0; i < PIXELS; i++) {
            int c = argb[i];
            int value = ((c >>> 28) << 12) | (((c >>> 20) & 15) << 8)
                    | (((c >>> 12) & 15) << 4) | ((c >>> 4) & 15);
            if ((value >>> 12) == 0) value = 0;
            raw[2*i] = (byte)(value >>> 8); raw[2*i+1] = (byte)value;
        }
        Deflater deflater = new Deflater(6);
        try {
            deflater.setInput(raw); deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(new byte[]{'S','I','A',1});
            byte[] block = new byte[4096];
            while (!deflater.finished()) {
                int n = deflater.deflate(block); out.write(block, 0, n);
                if (out.size() > MAX_BYTES) throw new IllegalArgumentException("Image exceeds limit");
            }
            return out.toByteArray();
        } finally { deflater.end(); }
    }

    public static int[] decode(byte[] encoded) throws IOException {
        if (encoded.length < 6 || encoded.length > MAX_BYTES || encoded[0]!='S'
                || encoded[1]!='I' || encoded[2]!='A' || encoded[3]!=1)
            throw new IOException("Unsupported or oversized illustration");
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(encoded,4,encoded.length-4);
            byte[] raw = new byte[PIXELS*2];
            int offset = 0;
            while (offset < raw.length && !inflater.finished()) {
                int n = inflater.inflate(raw, offset, raw.length-offset);
                if (n == 0) throw new IOException("Incomplete illustration");
                offset += n;
            }
            byte[] extra = new byte[1];
            int excess = inflater.inflate(extra);
            if (offset != raw.length || excess != 0 || !inflater.finished() || inflater.getRemaining()!=0)
                throw new IOException("Invalid illustration length or trailing data");
            int[] result = new int[PIXELS];
            for (int i=0;i<PIXELS;i++) {
                int c = ((raw[i*2]&255)<<8) | (raw[i*2+1]&255);
                result[i] = ((c>>>12)*17<<24) | (((c>>>8)&15)*17<<16)
                        | (((c>>>4)&15)*17<<8) | ((c&15)*17);
            }
            return result;
        } catch (DataFormatException e) { throw new IOException("Malformed illustration",e); }
        finally { inflater.end(); }
    }

    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static boolean validHash(String hash) { return hash.matches("[0-9a-f]{64}"); }
}

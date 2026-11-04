package dev.marigold.illustrations;

/** Pixel drawing plus one independently positioned, lossless image layer. */
public record PageAsset(int[] drawing, Layer image) {
    public PageAsset {
        if(drawing.length!=RasterCodec.PIXELS)throw new IllegalArgumentException("Wrong drawing dimensions");
        drawing=drawing.clone();
    }
    @Override public int[] drawing(){return drawing.clone();}
    public record Rect(int x,int y,int width,int height){
        public Rect {
            if(x<0||y<0||width<1||height<1||x+width>114||y+height>128)
                throw new IllegalArgumentException("Image must fit inside the page");
        }
    }
    public record Layer(Source source,Rect rect) {
        public Layer {if(source==null||rect==null)throw new IllegalArgumentException("Incomplete image layer");}
        public Layer at(Rect placement){return new Layer(source,placement);}
    }
    public static final class Source {
        private final int width,height;
        private final int[] pixels;
        public Source(int width,int height,int[] pixels){
            if(width<1||height<1||width>2048||height>2048||pixels.length!=(long)width*height)
                throw new IllegalArgumentException("Maximum image dimensions: 2048 x 2048");
            this.width=width;this.height=height;this.pixels=pixels.clone();
        }
        public int width(){return width;}
        public int height(){return height;}
        public int pixel(int index){return pixels[index];}
        public long bytes(){return (long)pixels.length*4;}
    }
    public long decodedBytes(){return RasterCodec.PIXELS*4L+(image==null?0:image.source.bytes());}
}

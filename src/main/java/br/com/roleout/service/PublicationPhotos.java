package br.com.roleout.service;

import br.com.roleout.model.PublicationData.Photo;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.Semaphore;

/** Decode bounded JPEG/PNG input, resize and re-encode without EXIF or other original metadata. */
public final class PublicationPhotos {
    private static final Semaphore SLOTS = new Semaphore(2);
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private PublicationPhotos() {}

    public static Photo sanitize(Photo photo) {
        return resize(photo,1280,MAX_BYTES);
    }
    /** A bounded feed preview; the stored publication photo is never modified. */
    public static Photo thumbnail(Photo photo) {
        return resize(photo,480,64*1024);
    }
    private static Photo resize(Photo photo,int maxEdge,int maxBytes) {
        if (photo == null || photo.base64() == null || photo.base64().length() > 2796204)
            throw new IllegalArgumentException("Foto muito grande: use até 2 MB.");
        String caption = PublicationService.text(photo.legenda(), 200, false);
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(photo.base64()); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Foto inválida."); }
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("Foto inválida ou muito grande.");
        if (!SLOTS.tryAcquire()) throw new br.com.roleout.api.ApiException(429,"FOTOS_OCUPADAS","Aguarde um instante e tente salvar novamente.");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Use uma foto JPEG ou PNG.");
            var reader = readers.next();
            try {
                if (!java.util.Set.of("JPEG", "PNG").contains(reader.getFormatName().toUpperCase(java.util.Locale.ROOT)))
                    throw new IllegalArgumentException("Use uma foto JPEG ou PNG.");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long)width * height > 24000000 || width > 12000 || height > 12000)
                    throw new IllegalArgumentException("Reduza as dimensões da foto.");
                var params = reader.getDefaultReadParam();
                int sample = Math.max(1, Math.max(width, height) / 1600);
                params.setSourceSubsampling(sample, sample, 0, 0);
                var source = reader.read(0, params);
                double scale = Math.min(1d, (double)maxEdge / Math.max(source.getWidth(), source.getHeight()));
                var output = new BufferedImage(Math.max(1,(int)(source.getWidth()*scale)), Math.max(1,(int)(source.getHeight()*scale)), BufferedImage.TYPE_INT_RGB);
                var graphics = output.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0,0,output.getWidth(),output.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source,0,0,output.getWidth(),output.getHeight(),null);
                } finally { graphics.dispose(); source.flush(); }
                var buffer = new ByteArrayOutputStream();
                for(float quality: maxEdge==480?new float[]{0.55f,0.35f,0.15f}:new float[]{0.75f}){
                    buffer.reset();
                    var writers=ImageIO.getImageWritersByFormatName("jpeg");
                    if(!writers.hasNext())throw new IllegalStateException("JPEG encoder unavailable");
                    var writer=writers.next();
                    try(var stream=new javax.imageio.stream.MemoryCacheImageOutputStream(buffer)){
                        writer.setOutput(stream);
                        var encoding=writer.getDefaultWriteParam();
                        encoding.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                        encoding.setCompressionQuality(quality);
                        writer.write(null,new javax.imageio.IIOImage(output,null,null),encoding);
                    }finally{writer.dispose();}
                    if(buffer.size()<=maxBytes)break;
                }
                output.flush();
                if (buffer.size()>maxBytes) throw new IllegalArgumentException("Reduza a foto.");
                return new Photo(Base64.getEncoder().encodeToString(buffer.toByteArray()),caption);
            } finally { reader.dispose(); }
        } catch (IOException e) { throw new IllegalArgumentException("Não foi possível ler a foto."); }
        finally { SLOTS.release(); }
    }
}

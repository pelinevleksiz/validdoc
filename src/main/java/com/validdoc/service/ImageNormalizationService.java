package com.validdoc.service;

import com.validdoc.config.DocumentGeometry;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

@Service
public class ImageNormalizationService {

    public BufferedImage normalizeToA4Canvas(byte[] fileBytes) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(fileBytes));
        if (source == null) {
            throw new IOException("Goruntu formati desteklenmiyor veya bozuk");
        }

        BufferedImage normalized = new BufferedImage(
                DocumentGeometry.A4_WIDTH_PX_INT, DocumentGeometry.A4_HEIGHT_PX_INT, BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = normalized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, DocumentGeometry.A4_WIDTH_PX_INT, DocumentGeometry.A4_HEIGHT_PX_INT, null);
        graphics.dispose();

        return normalized;
    }
}
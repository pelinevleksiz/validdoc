package com.validdoc.service;

import com.validdoc.exception.PageOutOfBoundsException;
import com.validdoc.exception.PdfRasterizationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class PdfRasterService {

    private static final int RENDER_DPI = 300;

    public Map<Integer, BufferedImage> renderPages(InputStream pdfStream, Set<Integer> pageNumbers) {
        byte[] pdfBytes;
        try {
            pdfBytes = pdfStream.readAllBytes();
        } catch (IOException e) {
            throw new PdfRasterizationException("PDF stream okunamadi", e);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            if (totalPages == 0) {
                throw new PdfRasterizationException("Yuklenen PDF sayfa icermiyor", null);
            }

            for (Integer pageNumber : pageNumbers) {
                if (pageNumber == null || pageNumber < 1 || pageNumber > totalPages) {
                    throw new PageOutOfBoundsException(
                            "Template segmenti " + pageNumber + ". sayfayi referans veriyor, ancak belge "
                                    + totalPages + " sayfa iceriyor", null);
                }
            }

            PDFRenderer renderer = new PDFRenderer(document);
            Map<Integer, BufferedImage> rendered = new LinkedHashMap<>();
            for (Integer pageNumber : pageNumbers) {
                rendered.put(pageNumber, renderer.renderImageWithDPI(pageNumber - 1, RENDER_DPI));
            }
            return rendered;
        } catch (IOException e) {
            throw new PdfRasterizationException(
                    "PDF rasterize edilemedi: dosya bozuk, sifreli veya gecersiz olabilir", e);
        }
    }
}
package com.validdoc.service;

import com.validdoc.exception.PdfRasterizationException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Service
public class PdfRasterService {

    private static final int RENDER_DPI = 300;
    private static final int FIRST_PAGE_INDEX = 0;

    /**
     * PDF'in ilk sayfasini in-memory BufferedImage'a cevirir (SRS 1.2, SDD S5.1).
     * Bozuk/sifreli/gecersiz PDF durumunda PdfRasterizationException firlatir;
     * bu, DocumentService/@ControllerAdvice tarafinda PENDING_REVIEW + 0.0 skor
     * olarak ele alinmalidir (SDD S8).
     */
    public BufferedImage renderFirstPage(InputStream pdfStream) {
        byte[] pdfBytes;
        try {
            pdfBytes = pdfStream.readAllBytes();
        } catch (IOException e) {
            throw new PdfRasterizationException("PDF stream okunamadi", e);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new PdfRasterizationException("Yuklenen PDF sayfa icermiyor", null);
            }
            PDFRenderer renderer = new PDFRenderer(document);
            return renderer.renderImageWithDPI(FIRST_PAGE_INDEX, RENDER_DPI);
        } catch (IOException e) {
            // Loader.loadPDF / renderImageWithDPI bozuk, sifreli ya da gecersiz PDF'te
            // IOException firlatir - burada PdfRasterizationException'a ceviriyoruz ki
            // global exception handler diger motor hatalariyla ayni yolu izlesin.
            throw new PdfRasterizationException(
                    "PDF rasterize edilemedi: dosya bozuk, sifreli veya gecersiz olabilir", e);
        }
    }
}
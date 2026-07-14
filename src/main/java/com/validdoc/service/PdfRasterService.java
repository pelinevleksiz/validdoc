package com.validdoc.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

@Service
public class PdfRasterService {

    public BufferedImage renderFirstPage(InputStream pdfStream) throws IOException {
        // InputStream verisini byte dizisine dönüştürerek Loader ile güvenle yüklüyoruz
        byte[] pdfBytes = pdfStream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.getNumberOfPages() == 0) {
                throw new IOException("yüklenen pdf dosyası boş veya sayfa içermiyor.");
            }
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            return pdfRenderer.renderImageWithDPI(0, 300);
        }
    }
}
package com.validdoc.service;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.stereotype.Service;
import java.awt.image.BufferedImage;

@Service
public class OcrService {

    private final Tesseract tesseract;

    public OcrService(Tesseract tesseract) {
        this.tesseract = tesseract;
    }

    public String doOcr(BufferedImage image) throws TesseractException {
        if (image == null) {
            throw new IllegalArgumentException("ocr processing failed: input image cannot be null");
        }
        return this.tesseract.doOCR(image);
    }
}
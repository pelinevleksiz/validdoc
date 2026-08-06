package com.validdoc.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.validdoc.config.TesseractFactory;
import com.validdoc.dto.internal.SegmentReading;
import com.validdoc.exception.OcrEngineException;
import com.validdoc.exception.TemplateDefinitionException;
import com.validdoc.model.Template;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.SegmentRuleType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final ThreadLocal<Tesseract> tesseractHolder;

    public OcrService(TesseractFactory tesseractFactory) {
        this.tesseractHolder = ThreadLocal.withInitial(tesseractFactory::create);
    }

    public List<SegmentReading> process(Map<Integer, BufferedImage> pages, Template template, DocumentLanguage language) throws TesseractException {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("ocr processing failed: page map cannot be null or empty");
        }
        if (template == null) {
            throw new TemplateDefinitionException("Template zorunludur, template-free mod artik desteklenmiyor", null);
        }

        Tesseract tesseract = tesseractHolder.get();
        tesseract.setLanguage(language.getTesseractCode());

        List<SegmentReading> readings = new ArrayList<>();
        for (TemplateSegment segment : template.getSegments()) {
            BufferedImage page = pages.get(segment.getPage());
            if (page == null) {
                throw new IllegalStateException(
                        "Segment " + segment.getLabel() + " icin sayfa " + segment.getPage()
                                + " rasterize edilmemis, bu PdfRasterService'in beklenen davranisi degil");
            }

            validateSegmentBounds(segment, page);
            BufferedImage region = ImageProcessingUtil.safeCrop(page, (int) segment.getX(), (int) segment.getY(),
                    (int) segment.getW(), (int) segment.getH());
            boolean inkSegment = isInkSegment(segment);

            if (inkSegment) {
                double density = ImageProcessingUtil.computeInkDensity(region);
                byte[] croppedImage = encodeForStorage(region, inkSegment, segment.getLabel());
                readings.add(new SegmentReading(segment, null, density, null, croppedImage));
            } else {
                OcrExtraction extraction = runOcr(tesseract, region, segment.getLabel());
                byte[] croppedImage = encodeForStorage(region, inkSegment, segment.getLabel());
                Double pixelDensity = extraction.text().isEmpty() ? ImageProcessingUtil.computeInkDensity(region) : null;
                readings.add(new SegmentReading(segment, extraction.text(), pixelDensity, extraction.confidence(), croppedImage));
            }
        }
        return readings;
    }

    private record OcrExtraction(String text, Double confidence) {}

    private OcrExtraction runOcr(Tesseract tesseract, BufferedImage region, String segmentLabel) {
        try {
            List<Word> words = tesseract.getWords(region, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            String text = words.stream().map(Word::getText).collect(Collectors.joining(" ")).trim();
            Double confidence = words.isEmpty() ? null : words.stream()
                    .mapToDouble(Word::getConfidence)
                    .average()
                    .orElse(0.0);
            return new OcrExtraction(text, confidence);
        } catch (Throwable t) {
            throw new OcrEngineException("Tesseract OCR calismasi basarisiz, segment=" + segmentLabel, t);
        }
    }

    private static final int MAX_STORAGE_WIDTH_PX = 1000;
    private static final float JPEG_QUALITY = 0.75f;

    private byte[] encodeForStorage(BufferedImage region, boolean preserveColor, String segmentLabel) {
        try {
            BufferedImage prepared = preserveColor ? region : toGrayscale(region);
            BufferedImage resized = scaleToMaxWidth(prepared, MAX_STORAGE_WIDTH_PX);
            return writeJpeg(resized, JPEG_QUALITY);
        } catch (IOException e) {
            log.warn("Segment goruntusu saklama icin kodlanamadi, label={}", segmentLabel, e);
            return null;
        }
    }

    private BufferedImage toGrayscale(BufferedImage source) {
        BufferedImage gray = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        java.awt.Graphics2D g = gray.createGraphics();
        g.drawImage(source, 0, 0, null);
        g.dispose();
        return gray;
    }

    private BufferedImage scaleToMaxWidth(BufferedImage source, int maxWidth) {
        if (source.getWidth() <= maxWidth) {
            return source;
        }
        int targetHeight = (int) ((double) source.getHeight() * maxWidth / source.getWidth());
        BufferedImage scaled = new BufferedImage(maxWidth, targetHeight, source.getType());
        java.awt.Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, maxWidth, targetHeight, null);
        g.dispose();
        return scaled;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        java.util.Iterator<javax.imageio.ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        javax.imageio.ImageWriter writer = writers.next();
        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private boolean isInkSegment(TemplateSegment segment) {
        return segment.getRules().stream().anyMatch(r ->
                r.getRuleType() == SegmentRuleType.SIGNATURE_INK
                        || r.getRuleType() == SegmentRuleType.STAMP_INK);
    }

    private void validateSegmentBounds(TemplateSegment segment, BufferedImage image) {
        int x = (int) segment.getX();
        int y = (int) segment.getY();
        int w = (int) segment.getW();
        int h = (int) segment.getH();
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            throw new TemplateDefinitionException(
                    "Template segmenti gecersiz koordinatlara sahip, label=" + segment.getLabel()
                            + " page=" + segment.getPage() + " x=" + x + " y=" + y + " w=" + w + " h=" + h, null);
        }
    }
}
package com.validdoc.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.validdoc.config.TesseractFactory;
import com.validdoc.dto.internal.SegmentReading;
import com.validdoc.exception.OcrEngineException;
import com.validdoc.exception.TemplateDefinitionException;
import com.validdoc.exception.OpenCVException;
import com.validdoc.model.Template;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.SegmentRuleType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final TesseractFactory tesseractFactory;
    private final ThreadLocal<Tesseract> tesseractHolder;

    public OcrService(TesseractFactory tesseractFactory) {
        this.tesseractFactory = tesseractFactory;
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
            BufferedImage region = safeCrop(page, (int) segment.getX(), (int) segment.getY(),
                    (int) segment.getW(), (int) segment.getH());

            if (isInkSegment(segment)) {
                double density = computeInkDensity(region);
                readings.add(new SegmentReading(segment, null, density, null, null));
            } else {
                OcrExtraction extraction = runOcr(tesseract, region, segment.getLabel());
                byte[] croppedImagePng = encodeToPng(region, segment.getLabel());
                readings.add(new SegmentReading(segment, extraction.text(), null, extraction.confidence(), croppedImagePng));
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

    private byte[] encodeToPng(BufferedImage region, String segmentLabel) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(region, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            log.warn("Segment goruntusu PNG'e cevrilemedi, label={}", segmentLabel, e);
            return null;
        }
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

    private BufferedImage safeCrop(BufferedImage image, int x, int y, int w, int h) {
        int clampedX = Math.max(0, Math.min(x, image.getWidth() - 1));
        int clampedY = Math.max(0, Math.min(y, image.getHeight() - 1));
        int clampedW = Math.max(1, Math.min(w, image.getWidth() - clampedX));
        int clampedH = Math.max(1, Math.min(h, image.getHeight() - clampedY));
        return image.getSubimage(clampedX, clampedY, clampedW, clampedH);
    }

    private double computeInkDensity(BufferedImage region) {
        Mat mat = bufferedImageToMat(region);
        Mat gray = new Mat();
        Mat binary = new Mat();
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

            long total = (long) binary.rows() * binary.cols();
            if (total == 0) {
                throw new OpenCVException("Piksel yogunlugu hesaplanamadi: bos bolge");
            }
            int inkPixels = Core.countNonZero(binary);
            return (double) inkPixels / total;
        } finally {
            mat.release();
            gray.release();
            binary.release();
        }
    }

    private Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage normalized = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        normalized.getGraphics().drawImage(bi, 0, 0, null);

        byte[] data = ((DataBufferByte) normalized.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(normalized.getHeight(), normalized.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }
}
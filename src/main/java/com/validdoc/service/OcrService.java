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

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
                double density = ImageProcessingUtil.computePixelStdDev(region);
                byte[] croppedImage = encodeForStorage(region, inkSegment, segment.getLabel());
                readings.add(new SegmentReading(segment, null, density, null, croppedImage));
            } else {
                double inkDensity = ImageProcessingUtil.computePixelStdDev(region);
                byte[] croppedImage = encodeForStorage(region, inkSegment, segment.getLabel());

                log.warn(">>> TXTSTD2 label='{}' value={}", segment.getLabel(), inkDensity);
                if (inkDensity < EMPTY_TEXT_SEGMENT_DENSITY_THRESHOLD) {
                    readings.add(new SegmentReading(segment, "", inkDensity, null, croppedImage));
                } else {
                    OcrExtraction extraction = runOcr(tesseract, region, segment);
                    Double pixelDensity = extraction.text().isEmpty() ? inkDensity : null;
                    readings.add(new SegmentReading(segment, extraction.text(), pixelDensity, extraction.confidence(), croppedImage));
                }
            }
        }
        return readings;
    }

    private record OcrExtraction(String text, Double confidence) {}

    private static final int MAX_PLAUSIBLE_WORD_COUNT = 10;
    private static final double EMPTY_TEXT_SEGMENT_DENSITY_THRESHOLD = 5.0;

    private OcrExtraction runOcr(Tesseract tesseract, BufferedImage region, TemplateSegment segment) {
        String whitelist = resolveNarrowWhitelist(segment);
        boolean isSingleTokenField = whitelist != null;
        try {
            if (whitelist != null) {
                tesseract.setTessVariable("tessedit_char_whitelist", whitelist);
            }
            if (isSingleTokenField) {
                tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_LINE);
            }
            BufferedImage binarized = ImageProcessingUtil.binarizeForOcr(region);
            List<Word> words = tesseract.getWords(binarized, ITessAPI.TessPageIteratorLevel.RIL_WORD);
            if (words.size() > MAX_PLAUSIBLE_WORD_COUNT) {
                log.warn("Segment '{}' icin OCR anormal derecede uzun cikti uretti ({} kelime), gurultu olarak degerlendirilip bos kabul ediliyor",
                        segment.getLabel(), words.size());
                return new OcrExtraction("", 0.0);
            }
            String rawText = words.stream().map(Word::getText).collect(Collectors.joining(" ")).trim();
            String text = applyKnownOcrCorrections(rawText, segment);
            Double confidence = words.isEmpty() ? null : words.stream()
                    .mapToDouble(Word::getConfidence)
                    .average()
                    .orElse(0.0);
            return new OcrExtraction(text, confidence);
        } catch (Throwable t) {
            throw new OcrEngineException("Tesseract OCR calismasi basarisiz, segment=" + segment.getLabel(), t);
        } finally {
            if (whitelist != null) {
                tesseract.setTessVariable("tessedit_char_whitelist", "");
            }
            if (isSingleTokenField) {
                tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK);
            }
        }
    }

    private String resolveNarrowWhitelist(TemplateSegment segment) {
        boolean isPhone = segment.getRules().stream().anyMatch(r -> r.getRuleType() == SegmentRuleType.PHONE);
        boolean isEmail = segment.getRules().stream().anyMatch(r -> r.getRuleType() == SegmentRuleType.EMAIL);
        if (isPhone) {
            return PHONE_WHITELIST;
        }
        if (isEmail) {
            return EMAIL_WHITELIST;
        }
        return null;
    }

    private static final Pattern PHONE_LEADING_T_MISREAD = Pattern.compile("^t(\\d[\\d\\s()\\-./]*)$");
    private static final Pattern EMAIL_MD_AS_AT_MISREAD = Pattern.compile("^([\\w.+-]+)MD([\\w-]+\\.[a-zA-Z]{2,})$");
    private static final String PHONE_WHITELIST = "0123456789+";
    private static final String EMAIL_WHITELIST = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789@._-";

    private String applyKnownOcrCorrections(String text, TemplateSegment segment) {
        boolean isPhone = segment.getRules().stream().anyMatch(r -> r.getRuleType() == SegmentRuleType.PHONE);
        boolean isEmail = segment.getRules().stream().anyMatch(r -> r.getRuleType() == SegmentRuleType.EMAIL);

        if (isPhone) {
            Matcher matcher = PHONE_LEADING_T_MISREAD.matcher(text);
            if (matcher.matches()) {
                return "+" + matcher.group(1);
            }
        }
        if (isEmail) {
            if (text.contains("©")) {
                text = text.replace("©", "@");
            }
            if (!text.contains("@")) {
                Matcher matcher = EMAIL_MD_AS_AT_MISREAD.matcher(text);
                if (matcher.matches()) {
                    return matcher.group(1) + "@" + matcher.group(2);
                }
            }
        }
        return text;
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
        Graphics2D g = gray.createGraphics();
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
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(source, 0, 0, maxWidth, targetHeight, null);
        g.dispose();
        return scaled;
    }

    private byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
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
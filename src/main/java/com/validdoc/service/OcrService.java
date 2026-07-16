package com.validdoc.service;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;

import com.validdoc.dto.internal.TemplateFieldDefinition;
import com.validdoc.dto.ocr.FieldType;
import com.validdoc.dto.ocr.OcrDocumentResult;
import com.validdoc.dto.ocr.OcrFieldResult;
import com.validdoc.exception.OpenCVException;
import com.validdoc.exception.TemplateDefinitionException;
import com.validdoc.model.Template;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class OcrService {

    private static final Set<String> INK_LABELS =
            Set.of("signature", "stamp", "imza", "muhur", "mühür", "kaşe", "kase");

    private static final java.util.Map<String, List<String>> ANCHOR_KEYWORDS = java.util.Map.of(
            "name", List.of("ad soyad", "adı soyadı", "isim", "name"),
            "date", List.of("tarih", "date"),
            "id_number", List.of("tc kimlik", "tc no", "kimlik no", "id no", "id number"),
            "vkn", List.of("vergi kimlik", "vkn"),
            "phone", List.of("telefon", "gsm", "phone"),
            "signature", List.of("imza", "signature"),
            "stamp", List.of("kaşe", "kase", "mühür", "muhur", "stamp", "seal")
    );

    private static final int INK_REGION_WIDTH = 150;
    private static final int INK_REGION_HEIGHT = 60;
    private static final int INK_REGION_Y_OFFSET = 5;

    private final Tesseract tesseract;
    private final JsonMapper jsonMapper;

    public OcrService(Tesseract tesseract, JsonMapper jsonMapper) {
        this.tesseract = tesseract;
        this.jsonMapper = jsonMapper;
    }

    public OcrDocumentResult process(BufferedImage image, Template template) throws TesseractException {
        if (image == null) {
            throw new IllegalArgumentException("ocr processing failed: input image cannot be null");
        }
        String rawFullText = tesseract.doOCR(image);

        List<OcrFieldResult> fields = (template != null)
                ? processTemplated(image, template)
                : processTemplateFree(image, rawFullText);

        return new OcrDocumentResult(rawFullText, fields);
    }

    private List<OcrFieldResult> processTemplated(BufferedImage image, Template template) throws TesseractException {
        List<TemplateFieldDefinition> defs = parseTemplate(template);
        List<OcrFieldResult> results = new ArrayList<>();

        for (TemplateFieldDefinition def : defs) {
            validateTemplateFieldBounds(def, image);
            BufferedImage region = safeCrop(image, (int) def.getX(), (int) def.getY(),
                    (int) def.getW(), (int) def.getH(), def.getLabel());

            FieldType type = resolveFieldType(def);
            if (type == FieldType.INK_ZONE) {
                double density = computeInkDensity(region);
                results.add(new OcrFieldResult(def.getLabel(), FieldType.INK_ZONE, null, density));
            } else {
                String text = tesseract.doOCR(region).trim();
                results.add(new OcrFieldResult(def.getLabel(), FieldType.TEXT, text, null));
            }
        }
        return results;
    }

    private void validateTemplateFieldBounds(TemplateFieldDefinition def, BufferedImage image) {
        int x = (int) def.getX();
        int y = (int) def.getY();
        int w = (int) def.getW();
        int h = (int) def.getH();
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            throw new TemplateDefinitionException(
                    "Template alani gecersiz koordinatlara sahip, label=" + def.getLabel()
                            + " x=" + x + " y=" + y + " w=" + w + " h=" + h, null);
        }
    }

    private FieldType resolveFieldType(TemplateFieldDefinition def) {
        if (def.getType() != null && !def.getType().isBlank()) {
            return "INK_ZONE".equalsIgnoreCase(def.getType()) ? FieldType.INK_ZONE : FieldType.TEXT;
        }
        return isInkLabel(def.getLabel()) ? FieldType.INK_ZONE : FieldType.TEXT;
    }

    private List<TemplateFieldDefinition> parseTemplate(Template template) {
        try {
            return jsonMapper.readValue(template.getFieldDefinitions(),
                    new TypeReference<List<TemplateFieldDefinition>>() {
                    });
        } catch (JacksonException e) {
            throw new TemplateDefinitionException(
                    "Template field_definitions parse edilemedi, templateId=" + template.getId(), e);
        }
    }

    private List<OcrFieldResult> processTemplateFree(BufferedImage image, String rawFullText) throws TesseractException {
        List<OcrFieldResult> results = new ArrayList<>();
        List<Word> words = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
        String[] lines = rawFullText.split("\\r?\\n");

        for (var entry : ANCHOR_KEYWORDS.entrySet()) {
            String label = entry.getKey();
            List<String> variants = entry.getValue();

            if (isInkLabel(label)) {
                Word anchorWord = findAnchorWord(words, variants);
                if (anchorWord == null) continue;
                BufferedImage region = deriveInkRegion(image, anchorWord.getBoundingBox());
                double density = computeInkDensity(region);
                results.add(new OcrFieldResult(label, FieldType.INK_ZONE, null, density));
            } else {
                String value = findAnchorLineValue(lines, variants);
                if (value != null) {
                    results.add(new OcrFieldResult(label, FieldType.TEXT, value, null));
                }
            }
        }
        return results;
    }

    private Word findAnchorWord(List<Word> words, List<String> variants) {
        for (Word w : words) {
            String normalized = normalizeForMatch(w.getText());
            for (String variant : variants) {
                if (normalized.contains(normalizeForMatch(variant))) return w;
            }
        }
        return null;
    }

    private String findAnchorLineValue(String[] lines, List<String> variants) {
        for (String line : lines) {
            String normalized = normalizeForMatch(line);
            for (String variant : variants) {
                String normalizedVariant = normalizeForMatch(variant);
                if (normalized.contains(normalizedVariant)) {
                    int colonIdx = line.indexOf(':');
                    if (colonIdx >= 0 && colonIdx + 1 < line.length()) {
                        String value = line.substring(colonIdx + 1).trim();
                        if (!value.isEmpty()) return value;
                    }
                    int variantIdx = normalized.indexOf(normalizedVariant);
                    String remainder = line.substring(Math.min(variantIdx + normalizedVariant.length(), line.length())).trim();
                    return remainder.isEmpty() ? null : remainder;
                }
            }
        }
        return null;
    }

    private static String normalizeForMatch(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '̇') {
                continue;
            }
            if (c == 'ı') {
                sb.append('i');
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private BufferedImage deriveInkRegion(BufferedImage image, Rectangle anchorBox) {
        int x = anchorBox.x;
        int y = anchorBox.y + anchorBox.height + INK_REGION_Y_OFFSET;
        return safeCrop(image, x, y, INK_REGION_WIDTH, INK_REGION_HEIGHT, "derived-ink-region");
    }

    private BufferedImage safeCrop(BufferedImage image, int x, int y, int w, int h, String label) {
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

    private boolean isInkLabel(String label) {
        return INK_LABELS.contains(normalizeForMatch(label));
    }
}
package com.validdoc.service;

import com.validdoc.dto.request.TemplatePreviewSegmentRequest;
import com.validdoc.dto.response.TemplatePreviewSegmentResponse;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.exception.OpenCVException;
import com.validdoc.exception.PageOutOfBoundsException;
import com.validdoc.exception.PdfRasterizationException;
import net.sourceforge.tess4j.Tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.validdoc.config.TesseractFactory;
import com.validdoc.model.enums.DocumentLanguage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TemplatePreviewService {

    private static final Logger log = LoggerFactory.getLogger(TemplatePreviewService.class);
    private static final int SINGLE_IMAGE_PAGE_NUMBER = 1;

    private final PdfRasterService pdfRasterService;
    private final ThreadLocal<Tesseract> tesseractHolder;
    private final ImageNormalizationService imageNormalizationService;

    public TemplatePreviewService(PdfRasterService pdfRasterService,
                                  TesseractFactory tesseractFactory,
                                  ImageNormalizationService imageNormalizationService) {
        this.pdfRasterService = pdfRasterService;
        this.tesseractHolder = ThreadLocal.withInitial(tesseractFactory::create);
        this.imageNormalizationService = imageNormalizationService;
    }

    public List<TemplatePreviewSegmentResponse> preview(byte[] fileBytes, String contentType,
                                                        List<TemplatePreviewSegmentRequest> segments,
                                                        DocumentLanguage language) {
        Set<Integer> requiredPages = segments.stream()
                .map(TemplatePreviewSegmentRequest::getPage)
                .collect(Collectors.toSet());

        Map<Integer, BufferedImage> pages;
        try {
            pages = FileSignatureValidator.PDF_CONTENT_TYPE.equals(contentType)
                    ? pdfRasterService.renderPages(new ByteArrayInputStream(fileBytes), requiredPages)
                    : renderSingleImagePage(fileBytes, requiredPages);
        } catch (PdfRasterizationException | PageOutOfBoundsException | IOException e) {
            throw new ApiException(ErrorCode.PREVIEW_FAILED, e.getMessage());
        }

        Tesseract tesseract = tesseractHolder.get();
        tesseract.setLanguage(language.getTesseractCode());

        List<TemplatePreviewSegmentResponse> results = new ArrayList<>();
        for (TemplatePreviewSegmentRequest segment : segments) {
            BufferedImage page = pages.get(segment.getPage());
            validateBounds(segment, page);
            BufferedImage region = ImageProcessingUtil.safeCrop(page, (int) (double) segment.getX(), (int) (double) segment.getY(),
                    (int) (double) segment.getW(), (int) (double) segment.getH());

            String text = tryOcr(tesseract, region, segment.getLabel());
            double density = computeInkDensityLenient(region, segment.getLabel());
            results.add(new TemplatePreviewSegmentResponse(segment.getLabel(), segment.getPage(), text, density));
        }
        return results;
    }

    private String tryOcr(Tesseract tesseract, BufferedImage region, String segmentLabel) {
        try {
            return tesseract.doOCR(region).trim();
        } catch (Throwable t) {
            log.warn("Onizlemede OCR basarisiz oldu, segment={}, ink yogunlugu yine de donuluyor", segmentLabel, t);
            return null;
        }
    }

    private double computeInkDensityLenient(BufferedImage region, String segmentLabel) {
        try {
            return ImageProcessingUtil.computeInkDensity(region);
        } catch (OpenCVException e) {
            log.warn("Onizlemede piksel yogunlugu hesaplanamadi, segment={}, 0.0 donuluyor", segmentLabel, e);
            return 0.0;
        }
    }

    private Map<Integer, BufferedImage> renderSingleImagePage(byte[] fileBytes, Set<Integer> requiredPages) throws IOException {
        BufferedImage image = imageNormalizationService.normalizeToA4Canvas(fileBytes);
        for (Integer requiredPage : requiredPages) {
            if (requiredPage == null || requiredPage != SINGLE_IMAGE_PAGE_NUMBER) {
                throw new PageOutOfBoundsException(
                        "Tek sayfalik resim yuklendi, ancak segment " + requiredPage + ". sayfayi referans veriyor", null);
            }
        }
        return Map.of(SINGLE_IMAGE_PAGE_NUMBER, image);
    }

    private void validateBounds(TemplatePreviewSegmentRequest segment, BufferedImage image) {
        int x = (int) (double) segment.getX();
        int y = (int) (double) segment.getY();
        int w = (int) (double) segment.getW();
        int h = (int) (double) segment.getH();
        if (w <= 0 || h <= 0 || x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
            throw new ApiException(ErrorCode.INVALID_SEGMENT_COORDINATES, segment.getLabel());
        }
    }
}
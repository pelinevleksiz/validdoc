package com.validdoc.service;

import com.validdoc.dto.request.TemplatePreviewSegmentRequest;
import com.validdoc.dto.response.TemplatePreviewSegmentResponse;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.exception.PageOutOfBoundsException;
import com.validdoc.exception.PdfRasterizationException;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import org.springframework.stereotype.Service;

import com.validdoc.config.TesseractFactory;
import com.validdoc.model.enums.DocumentLanguage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TemplatePreviewService {

    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final int SINGLE_IMAGE_PAGE_NUMBER = 1;

    private final PdfRasterService pdfRasterService;
    private final ThreadLocal<Tesseract> tesseractHolder;

    public TemplatePreviewService(PdfRasterService pdfRasterService, TesseractFactory tesseractFactory) {
        this.pdfRasterService = pdfRasterService;
        this.tesseractHolder = ThreadLocal.withInitial(tesseractFactory::create);
    }

    public List<TemplatePreviewSegmentResponse> preview(byte[] fileBytes, String contentType,
                                                        List<TemplatePreviewSegmentRequest> segments,
                                                        DocumentLanguage language) {
        Set<Integer> requiredPages = segments.stream()
                .map(TemplatePreviewSegmentRequest::getPage)
                .collect(Collectors.toSet());

        Map<Integer, BufferedImage> pages;
        try {
            pages = PDF_CONTENT_TYPE.equals(contentType)
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
            BufferedImage region = safeCrop(page, (int) (double) segment.getX(), (int) (double) segment.getY(),
                    (int) (double) segment.getW(), (int) (double) segment.getH());

            try {
                String text = tesseract.doOCR(region).trim();
                double density = computeInkDensity(region);
                results.add(new TemplatePreviewSegmentResponse(segment.getLabel(), segment.getPage(), text, density));
            } catch (TesseractException e) {
                throw new ApiException(ErrorCode.PREVIEW_FAILED, e.getMessage());
            }
        }
        return results;
    }

    private Map<Integer, BufferedImage> renderSingleImagePage(byte[] fileBytes, Set<Integer> requiredPages) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
        if (image == null) {
            throw new IOException("Goruntu formati desteklenmiyor veya bozuk");
        }
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
                return 0.0;
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
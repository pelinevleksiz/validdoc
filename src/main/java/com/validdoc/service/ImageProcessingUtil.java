package com.validdoc.service;

import com.validdoc.exception.OpenCVException;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public final class ImageProcessingUtil {

    private ImageProcessingUtil() {
    }

    public static BufferedImage safeCrop(BufferedImage image, int x, int y, int w, int h) {
        int clampedX = Math.max(0, Math.min(x, image.getWidth() - 1));
        int clampedY = Math.max(0, Math.min(y, image.getHeight() - 1));
        int clampedW = Math.max(1, Math.min(w, image.getWidth() - clampedX));
        int clampedH = Math.max(1, Math.min(h, image.getHeight() - clampedY));
        return image.getSubimage(clampedX, clampedY, clampedW, clampedH);
    }

    public static double computeInkDensity(BufferedImage region) {
        Mat mat = bufferedImageToMat(region);
        Mat gray = new Mat();
        Mat binary = new Mat();
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU);

            long total = (long) binary.rows() * binary.cols();
            if (total == 0) {
                throw new OpenCVException("Piksel yoğunluğu hesaplanamadı: boş bölge");
            }
            int inkPixels = Core.countNonZero(binary);
            return (double) inkPixels / total;
        } finally {
            mat.release();
            gray.release();
            binary.release();
        }
    }

    private static final int OCR_BORDER_PX = 15;

    public static BufferedImage binarizeForOcr(BufferedImage region) {
        Mat mat = bufferedImageToMat(region);
        Mat gray = new Mat();
        Mat binary = new Mat();
        Mat bordered = new Mat();
        try {
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU);
            org.opencv.core.Core.copyMakeBorder(binary, bordered,
                    OCR_BORDER_PX, OCR_BORDER_PX, OCR_BORDER_PX, OCR_BORDER_PX,
                    org.opencv.core.Core.BORDER_CONSTANT, new org.opencv.core.Scalar(255));
            BufferedImage result = new BufferedImage(bordered.cols(), bordered.rows(), BufferedImage.TYPE_BYTE_GRAY);
            byte[] data = new byte[bordered.cols() * bordered.rows()];
            bordered.get(0, 0, data);
            result.getRaster().setDataElements(0, 0, bordered.cols(), bordered.rows(), data);
            return result;
        } finally {
            mat.release();
            gray.release();
            binary.release();
            bordered.release();
        }
    }
    public static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage normalized = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        normalized.getGraphics().drawImage(bi, 0, 0, null);

        byte[] data = ((DataBufferByte) normalized.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(normalized.getHeight(), normalized.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }
}
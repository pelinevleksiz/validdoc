package com.validdoc.config;

public final class DocumentGeometry {

    private DocumentGeometry() {}

    public static final int RENDER_DPI = 300;

    private static final double A4_WIDTH_MM = 210;
    private static final double A4_HEIGHT_MM = 297;
    private static final double MM_PER_INCH = 25.4;

    public static final double A4_WIDTH_PX = A4_WIDTH_MM / MM_PER_INCH * RENDER_DPI;
    public static final double A4_HEIGHT_PX = A4_HEIGHT_MM / MM_PER_INCH * RENDER_DPI;

    public static final int A4_WIDTH_PX_INT = (int) Math.round(A4_WIDTH_PX);
    public static final int A4_HEIGHT_PX_INT = (int) Math.round(A4_HEIGHT_PX);
}
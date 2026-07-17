package com.validdoc.config;

import net.sourceforge.tess4j.Tesseract;

public class TesseractFactory {

    private final String tessDataPath;

    public TesseractFactory(String tessDataPath) {
        this.tessDataPath = tessDataPath;
    }

    public Tesseract create() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        return tesseract;
    }
}
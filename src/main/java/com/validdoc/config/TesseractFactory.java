package com.validdoc.config;

import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.Tesseract;

import java.util.List;

public class TesseractFactory {

    private final String tessDataPath;

    public TesseractFactory(String tessDataPath) {
        this.tessDataPath = tessDataPath;
    }

    public Tesseract create() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_SINGLE_BLOCK);
        tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
        tesseract.setConfigs(List.of("validdoc"));
        return tesseract;
    }
}
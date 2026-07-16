package com.validdoc.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    @Value("${tesseract.datapath:/usr/share/tesseract-ocr/5/tessdata}")
    private String tessDataPath;

    @Value("${tesseract.language:tur+eng}")
    private String tessLanguage;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessDataPath);
        tesseract.setLanguage(tessLanguage);
        return tesseract;
    }
}
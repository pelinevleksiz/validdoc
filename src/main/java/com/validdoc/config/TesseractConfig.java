package com.validdoc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    @Value("${tesseract.datapath:/usr/share/tesseract-ocr/4.00/tessdata}")
    private String tessDataPath;

    @Bean
    public TesseractFactory tesseractFactory() {
        return new TesseractFactory(tessDataPath);
    }
}
package com.validdoc.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        // tesseract dil verilerinin (tessdata) yer aldığı klasör yolu
        tesseract.setDatapath("C:/Program Files/Tesseract-OCR/tessdata");
        // doğrulama yapacağımız dökümanların diline göre türkçe ve ingilizce aktif ediyoruz
        tesseract.setLanguage("tur+eng");
        return tesseract;
    }
}
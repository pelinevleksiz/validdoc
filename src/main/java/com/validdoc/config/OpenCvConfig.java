package com.validdoc.config;

import nu.pattern.OpenCV;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenCvConfig {

    @PostConstruct
    public void loadNativeLibrary() {
        OpenCV.loadLocally();
    }
}
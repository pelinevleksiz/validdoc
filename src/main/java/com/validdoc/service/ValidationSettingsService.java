package com.validdoc.service;

import com.validdoc.config.ValidationProperties;
import com.validdoc.model.ValidationSettings;
import com.validdoc.repository.ValidationSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ValidationSettingsService {

    private final ValidationSettingsRepository repository;
    private final ValidationProperties defaults;

    private volatile ValidationSettings current;

    public ValidationSettingsService(ValidationSettingsRepository repository, ValidationProperties defaults) {
        this.repository = repository;
        this.defaults = defaults;
    }

    @PostConstruct
    public void loadOrSeed() {
        current = repository.findById(ValidationSettings.SINGLETON_ID).orElseGet(this::seedFromDefaults);
    }

    private ValidationSettings seedFromDefaults() {
        ValidationSettings seeded = new ValidationSettings();
        seeded.setId(ValidationSettings.SINGLETON_ID);
        seeded.setRetentionDays(defaults.getRetentionDays());
        seeded.setInkDensityThreshold(defaults.getInkDensityThreshold());
        seeded.setUpdatedAt(LocalDateTime.now());
        seeded.setUpdatedBy("SYSTEM_SEED");
        return repository.save(seeded);
    }

    public int getRetentionDays() { return current.getRetentionDays(); }
    public double getInkDensityThreshold() { return current.getInkDensityThreshold(); }

    public ValidationSettings getSnapshot() {
        return current;
    }

    public synchronized ValidationSettings update(int retentionDays, double inkDensityThreshold, String updatedBy) {
        ValidationSettings updated = new ValidationSettings();
        updated.setId(ValidationSettings.SINGLETON_ID);
        updated.setRetentionDays(retentionDays);
        updated.setInkDensityThreshold(inkDensityThreshold);
        updated.setUpdatedAt(LocalDateTime.now());
        updated.setUpdatedBy(updatedBy);

        current = repository.save(updated);
        return current;
    }
}}
package com.validdoc.service;

import com.validdoc.config.ValidationProperties;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.model.ValidationSettings;
import com.validdoc.repository.ValidationSettingsRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Locale;

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
        seeded.setConfidenceThreshold(defaults.getConfidenceThreshold());
        seeded.setReviewMargin(defaults.getReviewMargin());
        seeded.setRetentionDays(defaults.getRetentionDays());
        seeded.setInkDensityThreshold(defaults.getInkDensityThreshold());
        seeded.setWeightCompleteness(defaults.getWeightCompleteness());
        seeded.setWeightFormat(defaults.getWeightFormat());
        seeded.setWeightSignature(defaults.getWeightSignature());
        seeded.setUpdatedAt(LocalDateTime.now());
        seeded.setUpdatedBy("SYSTEM_SEED");
        return repository.save(seeded);
    }

    public double getConfidenceThreshold() { return current.getConfidenceThreshold(); }
    public double getReviewMargin() { return current.getReviewMargin(); }
    public int getRetentionDays() { return current.getRetentionDays(); }
    public double getInkDensityThreshold() { return current.getInkDensityThreshold(); }
    public double getWeightCompleteness() { return current.getWeightCompleteness(); }
    public double getWeightFormat() { return current.getWeightFormat(); }
    public double getWeightSignature() { return current.getWeightSignature(); }

    public ValidationSettings getSnapshot() {
        return current;
    }

    public synchronized ValidationSettings update(double confidenceThreshold,
                                                  double reviewMargin,
                                                  int retentionDays,
                                                  double inkDensityThreshold,
                                                  double weightCompleteness,
                                                  double weightFormat,
                                                  double weightSignature,
                                                  String updatedBy) {
        double weightSum = weightCompleteness + weightFormat + weightSignature;
        if (Math.abs(weightSum - 1.0) > 0.0001) {
            throw new ApiException(ErrorCode.INVALID_WEIGHTS_SUM, String.format(Locale.ROOT, "%.4f", weightSum));
        }

        ValidationSettings updated = new ValidationSettings();
        updated.setId(ValidationSettings.SINGLETON_ID);
        updated.setConfidenceThreshold(confidenceThreshold);
        updated.setReviewMargin(reviewMargin);
        updated.setRetentionDays(retentionDays);
        updated.setInkDensityThreshold(inkDensityThreshold);
        updated.setWeightCompleteness(weightCompleteness);
        updated.setWeightFormat(weightFormat);
        updated.setWeightSignature(weightSignature);
        updated.setUpdatedAt(LocalDateTime.now());
        updated.setUpdatedBy(updatedBy);

        current = repository.save(updated);
        return current;
    }
}
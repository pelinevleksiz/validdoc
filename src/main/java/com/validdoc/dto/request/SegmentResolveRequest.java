package com.validdoc.dto.request;

import com.validdoc.model.enums.SegmentOutcome;
import jakarta.validation.constraints.NotNull;

public class SegmentResolveRequest {

    @NotNull
    private SegmentOutcome outcome;

    public SegmentOutcome getOutcome() { return outcome; }
    public void setOutcome(SegmentOutcome outcome) { this.outcome = outcome; }
}
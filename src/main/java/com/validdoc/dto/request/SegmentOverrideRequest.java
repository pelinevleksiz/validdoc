package com.validdoc.dto.request;

import com.validdoc.model.enums.OverrideReason;
import com.validdoc.model.enums.SegmentOutcome;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SegmentOverrideRequest {

    @NotNull
    private SegmentOutcome outcome;

    @NotNull
    private OverrideReason reasonCode;

    @Size(max = 120)
    private String note;

    public SegmentOutcome getOutcome() { return outcome; }
    public void setOutcome(SegmentOutcome outcome) { this.outcome = outcome; }

    public OverrideReason getReasonCode() { return reasonCode; }
    public void setReasonCode(OverrideReason reasonCode) { this.reasonCode = reasonCode; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
package com.validdoc.dto.request;

import com.validdoc.model.enums.SegmentRuleType;
import jakarta.validation.constraints.NotNull;

public class SegmentRuleRequest {

    @NotNull
    private SegmentRuleType type;

    private Integer param;

    public SegmentRuleType getType() { return type; }
    public void setType(SegmentRuleType type) { this.type = type; }

    public Integer getParam() { return param; }
    public void setParam(Integer param) { this.param = param; }
}
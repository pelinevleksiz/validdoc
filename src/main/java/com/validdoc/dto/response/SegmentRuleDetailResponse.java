package com.validdoc.dto.response;

public class SegmentRuleDetailResponse {

    private final String type;
    private final Integer param;

    public SegmentRuleDetailResponse(String type, Integer param) {
        this.type = type;
        this.param = param;
    }

    public String getType() { return type; }
    public Integer getParam() { return param; }
}
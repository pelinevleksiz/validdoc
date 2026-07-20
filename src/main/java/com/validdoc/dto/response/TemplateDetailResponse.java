package com.validdoc.dto.response;

import java.util.List;

public class TemplateDetailResponse {

    private final Long templateId;
    private final String name;
    private final List<TemplateSegmentDetailResponse> segments;

    public TemplateDetailResponse(Long templateId, String name, List<TemplateSegmentDetailResponse> segments) {
        this.templateId = templateId;
        this.name = name;
        this.segments = segments;
    }

    public Long getTemplateId() { return templateId; }
    public String getName() { return name; }
    public List<TemplateSegmentDetailResponse> getSegments() { return segments; }
}
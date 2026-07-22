package com.validdoc.dto.response;

import java.util.List;

public class TemplateDetailResponse {

    private final Long id;
    private final String name;
    private final List<TemplateSegmentDetailResponse> segments;

    public TemplateDetailResponse(Long id, String name, List<TemplateSegmentDetailResponse> segments) {
        this.id = id;
        this.name = name;
        this.segments = segments;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public List<TemplateSegmentDetailResponse> getSegments() { return segments; }
}
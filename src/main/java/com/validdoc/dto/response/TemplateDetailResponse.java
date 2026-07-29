package com.validdoc.dto.response;

import java.util.List;

public class TemplateDetailResponse {

    private final Long id;
    private final String name;
    private final int pageCount;
    private final List<TemplateSegmentDetailResponse> segments;

    public TemplateDetailResponse(Long id, String name, int pageCount, List<TemplateSegmentDetailResponse> segments) {
        this.id = id;
        this.name = name;
        this.pageCount = pageCount;
        this.segments = segments;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public int getPageCount() { return pageCount; }
    public List<TemplateSegmentDetailResponse> getSegments() { return segments; }
}
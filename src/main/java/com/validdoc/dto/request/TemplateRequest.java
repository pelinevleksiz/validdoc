package com.validdoc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class TemplateRequest {

    @NotBlank
    private String name;

    @Positive
    private int pageCount = 1;

    @NotEmpty
    private List<@Valid TemplateSegmentRequest> segments;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getPageCount() { return pageCount; }
    public void setPageCount(int pageCount) { this.pageCount = pageCount; }

    public List<TemplateSegmentRequest> getSegments() { return segments; }
    public void setSegments(List<TemplateSegmentRequest> segments) { this.segments = segments; }
}
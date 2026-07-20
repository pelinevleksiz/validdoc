package com.validdoc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class TemplateRequest {

    @NotBlank
    private String name;

    @NotEmpty
    private List<@Valid TemplateSegmentRequest> segments;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<TemplateSegmentRequest> getSegments() { return segments; }
    public void setSegments(List<TemplateSegmentRequest> segments) { this.segments = segments; }
}
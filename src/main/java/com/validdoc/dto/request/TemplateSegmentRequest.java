package com.validdoc.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public class TemplateSegmentRequest {

    @NotBlank
    @Size(max = 30)
    private String label;

    @NotNull
    @Positive
    private Integer page;

    @NotNull
    private Double x;

    @NotNull
    private Double y;

    @NotNull
    private Double w;

    @NotNull
    private Double h;

    @NotEmpty
    private List<@Valid SegmentRuleRequest> rules;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }

    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }

    public Double getW() { return w; }
    public void setW(Double w) { this.w = w; }

    public Double getH() { return h; }
    public void setH(Double h) { this.h = h; }

    public List<SegmentRuleRequest> getRules() { return rules; }
    public void setRules(List<SegmentRuleRequest> rules) { this.rules = rules; }
}
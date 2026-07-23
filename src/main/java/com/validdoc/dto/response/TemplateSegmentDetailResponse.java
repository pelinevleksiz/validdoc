package com.validdoc.dto.response;

import java.util.List;

public class TemplateSegmentDetailResponse {

    private final Long id;
    private final String label;
    private final int page;
    private final double x;
    private final double y;
    private final double w;
    private final double h;
    private final List<SegmentRuleDetailResponse> rules;

    public TemplateSegmentDetailResponse(Long id, String label, int page, double x, double y, double w, double h,
                                         List<SegmentRuleDetailResponse> rules) {
        this.id = id;
        this.label = label;
        this.page = page;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.rules = rules;
    }

    public Long getId() { return id; }
    public String getLabel() { return label; }
    public int getPage() { return page; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getW() { return w; }
    public double getH() { return h; }
    public List<SegmentRuleDetailResponse> getRules() { return rules; }
}
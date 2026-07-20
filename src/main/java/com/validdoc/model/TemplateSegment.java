package com.validdoc.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "template_segments")
public class TemplateSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private Template template;

    @Column(nullable = false, length = 40)
    private String label;

    @Column(nullable = false)
    private int page;

    @Column(nullable = false)
    private double x;

    @Column(nullable = false)
    private double y;

    @Column(nullable = false)
    private double w;

    @Column(nullable = false)
    private double h;

    @OneToMany(mappedBy = "segment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SegmentRule> rules = new ArrayList<>();

    public TemplateSegment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Template getTemplate() { return template; }
    public void setTemplate(Template template) { this.template = template; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = page; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getW() { return w; }
    public void setW(double w) { this.w = w; }

    public double getH() { return h; }
    public void setH(double h) { this.h = h; }

    public List<SegmentRule> getRules() { return rules; }
    public void setRules(List<SegmentRule> rules) { this.rules = rules; }
}
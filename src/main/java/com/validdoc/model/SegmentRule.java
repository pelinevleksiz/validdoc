package com.validdoc.model;

import com.validdoc.model.enums.SegmentRuleType;
import jakarta.persistence.*;

@Entity
@Table(name = "segment_rules")
public class SegmentRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "segment_id", nullable = false)
    private TemplateSegment segment;

    @Column(name = "rule_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SegmentRuleType ruleType;

    @Column(name = "param")
    private Integer param;

    public SegmentRule() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public TemplateSegment getSegment() { return segment; }
    public void setSegment(TemplateSegment segment) { this.segment = segment; }

    public SegmentRuleType getRuleType() { return ruleType; }
    public void setRuleType(SegmentRuleType ruleType) { this.ruleType = ruleType; }

    public Integer getParam() { return param; }
    public void setParam(Integer param) { this.param = param; }
}
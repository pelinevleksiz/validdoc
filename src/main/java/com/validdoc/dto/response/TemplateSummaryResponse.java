package com.validdoc.dto.response;

public class TemplateSummaryResponse {

    private Long templateId;
    private String name;

    public TemplateSummaryResponse() {}

    public TemplateSummaryResponse(Long templateId, String name) {
        this.templateId = templateId;
        this.name = name;
    }

    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
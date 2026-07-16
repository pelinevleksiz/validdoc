package com.validdoc.dto.request;

import jakarta.validation.constraints.NotBlank;

public class TemplateRequest {

    @NotBlank
    private String name;

    @NotBlank
    private String fieldDefinitions;

    public TemplateRequest() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFieldDefinitions() { return fieldDefinitions; }
    public void setFieldDefinitions(String fieldDefinitions) { this.fieldDefinitions = fieldDefinitions; }
}
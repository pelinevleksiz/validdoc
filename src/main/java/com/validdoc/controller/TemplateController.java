package com.validdoc.controller;

import com.validdoc.dto.internal.TemplateFieldDefinition;
import com.validdoc.dto.request.TemplateRequest;
import com.validdoc.dto.response.TemplateSummaryResponse;
import com.validdoc.model.Template;
import com.validdoc.repository.TemplateRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRepository templateRepository;
    private final JsonMapper jsonMapper;

    public TemplateController(TemplateRepository templateRepository, JsonMapper jsonMapper) {
        this.templateRepository = templateRepository;
        this.jsonMapper = jsonMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<List<TemplateSummaryResponse>> list() {
        List<TemplateSummaryResponse> response = templateRepository.findAll().stream()
                .map(t -> new TemplateSummaryResponse(t.getId(), t.getName()))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Long>> create(@Valid @RequestBody TemplateRequest request) {
        try {
            jsonMapper.readValue(request.getFieldDefinitions(), new TypeReference<List<TemplateFieldDefinition>>() {});
        } catch (JacksonException e) {
            throw new IllegalArgumentException("fieldDefinitions gecerli bir JSON alan listesi degil: " + e.getMessage());
        }

        Template template = new Template();
        template.setName(request.getName());
        template.setFieldDefinitions(request.getFieldDefinitions());
        template = templateRepository.save(template);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("templateId", template.getId()));
    }
}
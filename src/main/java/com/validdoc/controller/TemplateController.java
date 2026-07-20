package com.validdoc.controller;

import com.validdoc.config.DocumentGeometry;
import com.validdoc.dto.request.SegmentRuleRequest;
import com.validdoc.dto.request.TemplatePreviewSegmentRequest;
import com.validdoc.dto.request.TemplateRequest;
import com.validdoc.dto.request.TemplateSegmentRequest;
import com.validdoc.dto.response.TemplatePreviewSegmentResponse;
import com.validdoc.dto.response.TemplateSummaryResponse;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.model.SegmentRule;
import com.validdoc.model.Template;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.SegmentRuleType;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.service.TemplatePreviewService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

    private final TemplateRepository templateRepository;
    private final TemplatePreviewService templatePreviewService;
    private final JsonMapper jsonMapper;

    public TemplateController(TemplateRepository templateRepository,
                              TemplatePreviewService templatePreviewService,
                              JsonMapper jsonMapper) {
        this.templateRepository = templateRepository;
        this.templatePreviewService = templatePreviewService;
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
        for (TemplateSegmentRequest segmentReq : request.getSegments()) {
            validateSegmentCoordinates(segmentReq.getLabel(), segmentReq.getX(), segmentReq.getY(),
                    segmentReq.getW(), segmentReq.getH());
            validateSegmentRules(segmentReq);
        }

        Template template = new Template();
        template.setName(request.getName());

        for (TemplateSegmentRequest segmentReq : request.getSegments()) {
            TemplateSegment segment = new TemplateSegment();
            segment.setTemplate(template);
            segment.setLabel(segmentReq.getLabel());
            segment.setPage(segmentReq.getPage());
            segment.setX(segmentReq.getX());
            segment.setY(segmentReq.getY());
            segment.setW(segmentReq.getW());
            segment.setH(segmentReq.getH());

            for (SegmentRuleRequest ruleReq : segmentReq.getRules()) {
                SegmentRule rule = new SegmentRule();
                rule.setSegment(segment);
                rule.setRuleType(ruleReq.getType());
                rule.setParam(ruleReq.getParam());
                segment.getRules().add(rule);
            }
            template.getSegments().add(segment);
        }

        template = templateRepository.save(template);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("templateId", template.getId()));
    }

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> preview(@RequestParam("file") MultipartFile file,
                                                       @RequestParam("segments") String segmentsJson,
                                                       @RequestParam(value = "lang", required = false) String lang) throws IOException {
        List<TemplatePreviewSegmentRequest> segments;
        try {
            segments = jsonMapper.readValue(segmentsJson, new TypeReference<List<TemplatePreviewSegmentRequest>>() {});
        } catch (JacksonException e) {
            log.warn("Preview segments parse edilemedi", e);
            throw new ApiException(ErrorCode.PREVIEW_FAILED, "segments alani gecerli bir JSON listesi degil");
        }

        if (segments.isEmpty()) {
            throw new ApiException(ErrorCode.PREVIEW_FAILED, "en az bir segment gereklidir");
        }
        for (TemplatePreviewSegmentRequest segment : segments) {
            validateSegmentCoordinates(segment.getLabel(), segment.getX(), segment.getY(),
                    segment.getW(), segment.getH());
        }

        List<TemplatePreviewSegmentResponse> results = templatePreviewService.preview(
                file.getBytes(), file.getContentType(), segments, DocumentLanguage.fromParam(lang));

        return ResponseEntity.ok(Map.of("segments", results));
    }

    private void validateSegmentCoordinates(String label, double x, double y, double w, double h) {
        if (x < 0 || y < 0 || w <= 0 || h <= 0
                || x + w > DocumentGeometry.A4_WIDTH_PX
                || y + h > DocumentGeometry.A4_HEIGHT_PX) {
            throw new ApiException(ErrorCode.INVALID_SEGMENT_COORDINATES, label);
        }
    }

    private void validateSegmentRules(TemplateSegmentRequest segment) {
        boolean hasInkRule = segment.getRules().stream()
                .anyMatch(r -> r.getType() == SegmentRuleType.SIGNATURE_INK || r.getType() == SegmentRuleType.STAMP_INK);
        if (hasInkRule && segment.getRules().size() > 1) {
            throw new ApiException(ErrorCode.INVALID_SEGMENT_RULE_COMBINATION, segment.getLabel());
        }

        for (SegmentRuleRequest rule : segment.getRules()) {
            boolean isLengthRule = rule.getType() == SegmentRuleType.MIN_LENGTH || rule.getType() == SegmentRuleType.MAX_LENGTH;
            if (isLengthRule && (rule.getParam() == null || rule.getParam() <= 0)) {
                throw new ApiException(ErrorCode.INVALID_RULE_PARAM, segment.getLabel(), rule.getType().name());
            }
            if (!isLengthRule && rule.getParam() != null) {
                throw new ApiException(ErrorCode.INVALID_RULE_PARAM, segment.getLabel(), rule.getType().name());
            }
        }
    }
}
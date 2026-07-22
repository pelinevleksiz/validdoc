package com.validdoc.controller;

import com.validdoc.config.DocumentGeometry;
import com.validdoc.dto.request.SegmentRuleRequest;
import com.validdoc.dto.request.TemplatePreviewSegmentRequest;
import com.validdoc.dto.request.TemplateRequest;
import com.validdoc.dto.request.TemplateSegmentRequest;
import com.validdoc.dto.response.PagedResponse;
import com.validdoc.dto.response.RuleTypeResponse;
import com.validdoc.dto.response.SegmentRuleDetailResponse;
import com.validdoc.dto.response.TemplateDetailResponse;
import com.validdoc.dto.response.TemplatePreviewSegmentResponse;
import com.validdoc.dto.response.TemplateSegmentDetailResponse;
import com.validdoc.dto.response.TemplateSummaryResponse;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.model.SegmentRule;
import com.validdoc.model.Template;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.SegmentRuleType;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.service.FileSignatureValidator;
import com.validdoc.service.TemplatePreviewService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Arrays;
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
    public ResponseEntity<PagedResponse<TemplateSummaryResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        Page<Template> result = templateRepository.findAll(PageRequest.of(page, size));
        List<TemplateSummaryResponse> content = result.getContent().stream()
                .map(t -> new TemplateSummaryResponse(t.getId(), t.getName()))
                .toList();
        return ResponseEntity.ok(new PagedResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/rule-types")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RuleTypeResponse>> ruleTypes() {
        List<RuleTypeResponse> response = Arrays.stream(SegmentRuleType.values())
                .map(rt -> new RuleTypeResponse(
                        rt.name(),
                        rt == SegmentRuleType.MIN_LENGTH || rt == SegmentRuleType.MAX_LENGTH,
                        rt == SegmentRuleType.SIGNATURE_INK || rt == SegmentRuleType.STAMP_INK))
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<TemplateDetailResponse> getById(@PathVariable Long id) {
        Template template = templateRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, String.valueOf(id)));
        return ResponseEntity.ok(toDetail(template));
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

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", template.getId()));
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

        byte[] fileBytes = file.getBytes();
        String detectedContentType = FileSignatureValidator.detectContentType(fileBytes);
        if (detectedContentType == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        List<TemplatePreviewSegmentResponse> results = templatePreviewService.preview(
                fileBytes, detectedContentType, segments, DocumentLanguage.fromParam(lang));

        return ResponseEntity.ok(Map.of("segments", results));
    }

    private TemplateDetailResponse toDetail(Template template) {
        List<TemplateSegmentDetailResponse> segments = template.getSegments().stream()
                .map(segment -> new TemplateSegmentDetailResponse(
                        segment.getLabel(),
                        segment.getPage(),
                        segment.getX(),
                        segment.getY(),
                        segment.getW(),
                        segment.getH(),
                        segment.getRules().stream()
                                .map(rule -> new SegmentRuleDetailResponse(rule.getRuleType().name(), rule.getParam()))
                                .toList()))
                .toList();
        return new TemplateDetailResponse(template.getId(), template.getName(), segments);
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
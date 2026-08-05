package com.validdoc;

import com.validdoc.config.DocumentGeometry;
import com.validdoc.dto.internal.SegmentReading;
import com.validdoc.dto.internal.SegmentResultEntry;
import com.validdoc.model.*;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.SegmentOutcome;
import com.validdoc.model.enums.UserRole;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.SegmentImageRepository;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.repository.UserRepository;
import com.validdoc.scheduler.RetentionCleanupJob;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import com.validdoc.model.enums.SegmentRuleType;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.validdoc.dto.internal.ValidationResult;
import com.validdoc.service.ValidationService;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegrationTest extends AbstractIntegrationTest {

    private static final String RATE_LIMIT_TEST_REMOTE_ADDR = "203.0.113.10";
    private static final String AUX_LOGIN_REMOTE_ADDR = "203.0.113.30";
    private static final String RUN_ID = String.valueOf(System.currentTimeMillis());
    private static final String ADMIN_USERNAME = "admin_test_" + RUN_ID;
    private static final String ADMIN_PASSWORD = "AdminTestPass1!";
    private static final String OPERATOR_USERNAME = "operator_" + RUN_ID;
    private static final String TEMPLATE_NAME = "Integration Test Template " + RUN_ID;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private SegmentImageRepository segmentImageRepository;

    @Autowired
    private RetentionCleanupJob retentionCleanupJob;

    @Autowired
    private ValidationService validationService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;


    private static String adminToken;
    private static String operatorToken;
    private static Long createdTemplateId;
    private static Long inkTemplateId;
    private static Long multiPageTemplateId;
    private static Long signedDocumentId;
    private static Long mismatchDocumentId;
    private static Long resolveTemplateId;
    private static Long resolveSegmentAId;
    private static Long resolveSegmentBId;
    private static Long resolveTestDocumentId;

    private String extractToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertTrue(matcher.find(), "Response did not contain a token: " + body);
        return matcher.group(1);
    }

    private Long extractLongField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*(\\d+)").matcher(body);
        assertTrue(matcher.find(), "Response did not contain field " + field + ": " + body);
        return Long.valueOf(matcher.group(1));
    }

    private String extractStringField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
        assertTrue(matcher.find(), "Response did not contain field " + field + ": " + body);
        return matcher.group(1);
    }

    private byte[] generateInkImage(boolean withInk) throws IOException {
        BufferedImage image = new BufferedImage(
                DocumentGeometry.A4_WIDTH_PX_INT, DocumentGeometry.A4_HEIGHT_PX_INT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, DocumentGeometry.A4_WIDTH_PX_INT, DocumentGeometry.A4_HEIGHT_PX_INT);
        if (withInk) {
            g.setColor(Color.BLACK);
            g.fillRect(10, 10, 80, 80);
        }
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private static final float PDF_POINTS_PER_PIXEL = 72f / DocumentGeometry.RENDER_DPI;

    private byte[] generateInkPdf(int totalPages, int inkPage, boolean withInk) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDRectangle pageSize = new PDRectangle(
                    (float) (DocumentGeometry.A4_WIDTH_PX * PDF_POINTS_PER_PIXEL),
                    (float) (DocumentGeometry.A4_HEIGHT_PX * PDF_POINTS_PER_PIXEL));
            for (int i = 0; i < totalPages; i++) {
                document.addPage(new PDPage(pageSize));
            }
            if (withInk) {
                PDPage targetPage = document.getPage(inkPage - 1);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, targetPage)) {
                    contentStream.setNonStrokingColor(0, 0, 0);
                    float inkSizePt = 80 * PDF_POINTS_PER_PIXEL;
                    float xPt = 10 * PDF_POINTS_PER_PIXEL;
                    float yPt = pageSize.getHeight() - (10 * PDF_POINTS_PER_PIXEL) - inkSizePt;
                    contentStream.addRect(xPt, yPt, inkSizePt, inkSizePt);
                    contentStream.fill();
                }
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        }
    }

    private String pollForFinalStatus(Long documentId, String token) throws Exception {
        String docStatus = "PROCESSING";
        for (int attempt = 0; attempt < 30 && "PROCESSING".equals(docStatus); attempt++) {
            Thread.sleep(200);
            MvcResult result = mockMvc.perform(get("/api/documents/" + documentId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            docStatus = extractStringField(result, "status");
        }
        if ("PROCESSING".equals(docStatus)) {
            fail("Document " + documentId + " never left PROCESSING status within the polling window");
        }
        return docStatus;
    }

    @Test
    @Order(1)
    void setupAdminUserAndLogIn() throws Exception {
        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        adminToken = extractToken(result);
        assertNotNull(adminToken);
    }

    @Test
    @Order(2)
    void adminCanCreateOperatorUser() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + OPERATOR_USERNAME + "\",\"password\":\"OperatorPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    @Order(3)
    void creatingDuplicateUsernameFails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + OPERATOR_USERNAME + "\",\"password\":\"OperatorPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @Order(4)
    void operatorCanLogInAndCannotCreateUsers() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + OPERATOR_USERNAME + "\",\"password\":\"OperatorPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        operatorToken = extractToken(result);

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"x_" + RUN_ID + "\",\"password\":\"whatever1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void ruleCatalogListsAllRuleTypes() throws Exception {
        mockMvc.perform(get("/api/templates/rule-types")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.type == 'SIGNATURE_INK')].inkRule").value(contains(true)))
                .andExpect(jsonPath("$[?(@.type == 'MIN_LENGTH')].requiresParam").value(contains(true)));
    }

    @Test
    @Order(6)
    void adminCanCreateValidTemplate() throws Exception {
        String requestBody = """
                {
                  "name": "%s",
                  "segments": [
                    {
                      "label": "Ad Soyad",
                      "page": 1,
                      "x": 100,
                      "y": 100,
                      "w": 400,
                      "h": 80,
                      "rules": [ { "type": "LETTERS_ONLY" } ]
                    }
                  ]
                }
                """.formatted(TEMPLATE_NAME);

        MvcResult result = mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        createdTemplateId = extractLongField(result, "id");
    }

    @Test
    @Order(7)
    void templateDetailReturnsSegmentAndRule() throws Exception {
        mockMvc.perform(get("/api/templates/" + createdTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(TEMPLATE_NAME))
                .andExpect(jsonPath("$.segments[0].label").value("Ad Soyad"))
                .andExpect(jsonPath("$.segments[0].rules[0].type").value("LETTERS_ONLY"));
    }

    @Test
    @Order(8)
    void templateListIsPaginated() throws Exception {
        mockMvc.perform(get("/api/templates?page=0&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    @Order(9)
    void segmentOutsideA4BoundsIsRejected() throws Exception {
        String requestBody = """
                {
                  "name": "Bad Coordinates Template %s",
                  "segments": [
                    { "label": "Out of bounds", "page": 1, "x": 2000, "y": 3000, "w": 1000, "h": 1000,
                      "rules": [ { "type": "DIGITS_ONLY" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEGMENT_COORDINATES"));
    }

    @Test
    @Order(10)
    void combiningInkRuleWithOtherRuleIsRejected() throws Exception {
        String requestBody = """
                {
                  "name": "Bad Rule Combo Template %s",
                  "segments": [
                    { "label": "Signature", "page": 1, "x": 100, "y": 100, "w": 200, "h": 100,
                      "rules": [ { "type": "SIGNATURE_INK" }, { "type": "LETTERS_ONLY" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEGMENT_RULE_COMBINATION"));
    }

    @Test
    @Order(11)
    void minLengthRuleWithoutParamIsRejected() throws Exception {
        String requestBody = """
                {
                  "name": "Bad Param Template %s",
                  "segments": [
                    { "label": "Code", "page": 1, "x": 100, "y": 100, "w": 200, "h": 100,
                      "rules": [ { "type": "MIN_LENGTH" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RULE_PARAM"));
    }

    @Test
    @Order(12)
    void uploadWithoutTemplateIdIsRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEMPLATE_ID_REQUIRED"));
    }

    @Test
    @Order(13)
    void uploadWithGarbageBytesIsRejectedAsUnsupportedType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png",
                "this is not a real image".getBytes());

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(createdTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @Order(14)
    void uploadWithValidPngIsAcceptedAndQueuedForProcessing() throws Exception {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", baos.toByteArray());

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(createdTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PROCESSING"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @Order(15)
    void validationSettingsCanBeReadAndUpdatedByAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/validation-settings")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").exists());

        mockMvc.perform(put("/api/admin/validation-settings")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retentionDays\": 45, \"inkDensityThreshold\": 0.02, \"ocrConfidenceThreshold\": 55.0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retentionDays").value(45));
    }

    @Test
    @Order(16)
    void validationSettingsAreForbiddenForOperator() throws Exception {
        mockMvc.perform(get("/api/admin/validation-settings")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(17)
    void auditLogContainsRecentActions() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'VALIDATION_SETTINGS_UPDATED')]").exists());
    }

    @Test
    @Order(18)
    void auditLogsAreForbiddenForOperator() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(19)
    void loginRateLimiterBlocksAfterFiveAttempts() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(request -> {
                                request.setRemoteAddr(RATE_LIMIT_TEST_REMOTE_ADDR);
                                return request;
                            })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"rate-limit-test\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(RATE_LIMIT_TEST_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"rate-limit-test\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_LOGIN_ATTEMPTS"));
    }

    @Test
    @Order(20)
    void adminCanCreateInkOnlyTemplate() throws Exception {
        String requestBody = """
                {
                  "name": "Ink Test Template %s",
                  "segments": [
                    { "label": "Imza", "page": 1, "x": 0, "y": 0, "w": 100, "h": 100,
                      "rules": [ { "type": "SIGNATURE_INK" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        MvcResult result = mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();

        inkTemplateId = extractLongField(result, "id");
    }

    @Test
    @Order(21)
    void signedImageIsValidatedEndToEnd() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "signed.png", "image/png", generateInkImage(true));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isAccepted())
                .andReturn();

        signedDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(signedDocumentId, operatorToken);
        assertTrue("VALIDATED".equals(finalStatus), "Expected VALIDATED but was " + finalStatus);

        mockMvc.perform(get("/api/documents/" + signedDocumentId)
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentResults").exists());
    }

    @Test
    @Order(22)
    void blankImageIsRejectedAsEmptyEndToEnd() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "blank.png", "image/png", generateInkImage(false));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isAccepted())
                .andReturn();

        Long blankDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(blankDocumentId, operatorToken);
        assertTrue("REJECTED_EMPTY".equals(finalStatus), "Expected REJECTED_EMPTY but was " + finalStatus);
    }

    @Test
    @Order(23)
    void documentListIsPaginatedAndContainsUploads() throws Exception {
        mockMvc.perform(get("/api/documents?page=0&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.content[?(@.id == " + signedDocumentId + ")]").exists());
    }

    @Test
    @Order(24)
    void pageMismatchRoutesToPendingReview() throws Exception {
        Template inconsistentTemplate = new Template();
        inconsistentTemplate.setName("Multi Page Template " + RUN_ID);
        inconsistentTemplate.setPageCount(1);

        TemplateSegment segment = new TemplateSegment();
        segment.setTemplate(inconsistentTemplate);
        segment.setLabel("Page2Field");
        segment.setPage(2);
        segment.setX(0.0);
        segment.setY(0.0);
        segment.setW(100.0);
        segment.setH(100.0);

        SegmentRule rule = new SegmentRule();
        rule.setSegment(segment);
        rule.setRuleType(SegmentRuleType.DIGITS_ONLY);
        segment.getRules().add(rule);

        inconsistentTemplate.getSegments().add(segment);
        inconsistentTemplate = templateRepository.save(inconsistentTemplate);
        multiPageTemplateId = inconsistentTemplate.getId();

        MockMultipartFile file = new MockMultipartFile("file", "single-page.png", "image/png", generateInkImage(false));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(multiPageTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isAccepted())
                .andReturn();

        mismatchDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(mismatchDocumentId, operatorToken);
        assertTrue("PENDING_REVIEW".equals(finalStatus), "Expected PENDING_REVIEW but was " + finalStatus);
    }

    @Test
    @Order(25)
    void reviewQueueContainsPendingReviewDocument() throws Exception {
        mockMvc.perform(get("/api/documents/queue?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + mismatchDocumentId + ")]").exists());
    }

    @Test
    @Order(27)
    void templatePreviewReturnsInkDensityWithoutPersisting() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "preview.png", "image/png", generateInkImage(true));
        String segmentsJson = "[{\"label\":\"Imza\",\"page\":1,\"x\":0,\"y\":0,\"w\":100,\"h\":100}]";

        mockMvc.perform(multipart("/api/templates/preview")
                        .file(file)
                        .param("segments", segmentsJson)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[0].inkDensity").exists())
                .andExpect(jsonPath("$.segments[0].label").value("Imza"));
    }

    @Test
    @Order(28)
    void resolveTestTemplateAndDocumentAreSeededAsPendingReview() throws Exception {
        String requestBody = """
                {
                  "name": "Resolve Test Template %s",
                  "segments": [
                    { "label": "AlanA", "page": 1, "x": 100, "y": 100, "w": 200, "h": 80,
                      "rules": [ { "type": "LETTERS_ONLY" } ] },
                    { "label": "AlanB", "page": 1, "x": 100, "y": 300, "w": 200, "h": 80,
                      "rules": [ { "type": "DIGITS_ONLY" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        MvcResult templateResult = mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        resolveTemplateId = extractLongField(templateResult, "id");

        MvcResult detailResult = mockMvc.perform(get("/api/templates/" + resolveTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        String detailBody = detailResult.getResponse().getContentAsString();
        Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(detailBody);
        List<Long> ids = new ArrayList<>();
        while (idMatcher.find()) {
            ids.add(Long.valueOf(idMatcher.group(1)));
        }
        assertEquals(3, ids.size(), "Template + iki segment icin toplam 3 id bekleniyordu: " + detailBody);
        resolveSegmentAId = ids.get(1);
        resolveSegmentBId = ids.get(2);

        MockMultipartFile file = new MockMultipartFile("file", "resolve-seed.png", "image/png", generateInkImage(false));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(resolveTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isAccepted())
                .andReturn();
        resolveTestDocumentId = extractLongField(uploadResult, "id");
        pollForFinalStatus(resolveTestDocumentId, operatorToken);

        SegmentResultEntry entryA = new SegmentResultEntry();
        entryA.setSegmentId(resolveSegmentAId);
        entryA.setLabel("AlanA");
        entryA.setOutcome(SegmentOutcome.PENDING_REVIEW);

        SegmentResultEntry entryB = new SegmentResultEntry();
        entryB.setSegmentId(resolveSegmentBId);
        entryB.setLabel("AlanB");
        entryB.setOutcome(SegmentOutcome.PENDING_REVIEW);

        String segmentResultsJson = jsonMapper.writeValueAsString(List.of(entryA, entryB));

        DocumentMetadata document = documentRepository.findById(resolveTestDocumentId).orElseThrow();
        document.setStatus(DocumentStatus.PENDING_REVIEW);
        document.setSegmentResults(segmentResultsJson);
        documentRepository.save(document);

        mockMvc.perform(get("/api/documents/" + resolveTestDocumentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    @Order(29)
    void segmentImageIsAvailableWhilePending() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentBId + "/image")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertTrue(contentType != null && contentType.startsWith("image/jpeg"),
                "Beklenen content-type image/jpeg değil: " + contentType);
        assertTrue(result.getResponse().getContentAsByteArray().length > 0, "Segment goruntusu bos donuyor");
    }

    @Test
    @Order(30)
    void operatorCanResolveOneOfTwoPendingSegmentsAndDocumentStaysPendingReview() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/resolve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    @Order(31)
    void resolvingAlreadyResolvedSegmentIsRejected() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/resolve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEGMENT_ALREADY_RESOLVED"));
    }

    @Test
    @Order(32)
    void resolvingSegmentWithPendingReviewOutcomeIsRejected() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentBId + "/resolve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"PENDING_REVIEW\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEGMENT_RESOLUTION_OUTCOME"));
    }

    @Test
    @Order(33)
    void resolvingLastPendingSegmentRecomputesDocumentStatus() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentBId + "/resolve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_INVALID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED_INVALID"));
    }

    @Test
    @Order(34)
    void segmentImageRemainsAvailableAfterResolve() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentBId + "/image")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isOk())
                .andReturn();

        String contentType = result.getResponse().getContentType();
        assertTrue(contentType != null && contentType.startsWith("image/jpeg"),
                "Beklenen content-type image/jpeg degil: " + contentType);
        assertTrue(result.getResponse().getContentAsByteArray().length > 0, "Segment goruntusu bos donuyor");
    }

    @Test
    @Order(35)
    void resolvingSegmentOnDocumentNotInPendingReviewIsRejected() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/resolve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_PENDING_REVIEW"));
    }

    @Test
    @Order(36)
    void engineFailurePendingReviewDocumentCannotHaveSegmentsResolved() throws Exception {
        MvcResult detailResult = mockMvc.perform(get("/api/templates/" + multiPageTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(detailResult.getResponse().getContentAsString());
        assertTrue(idMatcher.find(), "Template id bulunamadi");
        assertTrue(idMatcher.find(), "Segment id bulunamadi");
        Long multiPageSegmentId = Long.valueOf(idMatcher.group(1));

        MockMultipartFile file = new MockMultipartFile("file", "another-single-page.png", "image/png", generateInkImage(false));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(multiPageTemplateId))
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isAccepted())
                .andReturn();
        Long engineFailureDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(engineFailureDocumentId, operatorToken);
        assertTrue("PENDING_REVIEW".equals(finalStatus), "Expected PENDING_REVIEW but was " + finalStatus);

        mockMvc.perform(post("/api/documents/" + engineFailureDocumentId
                        + "/segments/" + multiPageSegmentId + "/resolve")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_PENDING_REVIEW"));
    }

    @Test
    @Order(37)
    void abandonedPendingReviewDocumentAutoExpiresToRejectedInvalid() throws Exception {
        User uploader = userRepository.findByUsernameAndActiveTrue(OPERATOR_USERNAME).orElseThrow();
        Template template = templateRepository.findById(createdTemplateId).orElseThrow();

        DocumentMetadata abandoned = new DocumentMetadata();
        abandoned.setFileName("abandoned-test.png");
        abandoned.setUploadedBy(uploader);
        abandoned.setTemplate(template);
        abandoned.setStatus(DocumentStatus.PENDING_REVIEW);
        abandoned.setProcessedAt(Instant.now().minus(5 * 365, ChronoUnit.DAYS));
        abandoned = documentRepository.save(abandoned);
        Long abandonedDocumentId = abandoned.getId();

        retentionCleanupJob.expireAbandonedReviews();

        DocumentMetadata reloaded = documentRepository.findById(abandonedDocumentId).orElseThrow();
        assertEquals(DocumentStatus.REJECTED_INVALID, reloaded.getStatus());
        assertNotNull(reloaded.getPurgeAt());
    }

    @Test
    @Order(38)
    void userListIsPaginatedForAdmin() throws Exception {
        mockMvc.perform(get("/api/users?page=0&size=5")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").exists());
    }

    @Test
    @Order(39)
    void userListIsForbiddenForOperator() throws Exception {
        mockMvc.perform(get("/api/users")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(40)
    void adminCanDeleteUserWithoutLinkedDocuments() throws Exception {
        String throwawayUsername = "throwaway_" + RUN_ID;
        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + throwawayUsername + "\",\"password\":\"ThrowawayPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long throwawayUserId = extractLongField(createResult, "id");

        mockMvc.perform(delete("/api/users/" + throwawayUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + throwawayUsername + "\",\"password\":\"ThrowawayPass1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(41)
    void adminCanDeactivateUserWithLinkedDocuments() throws Exception {
        User operatorUser = userRepository.findByUsernameAndActiveTrue(OPERATOR_USERNAME).orElseThrow();

        mockMvc.perform(delete("/api/users/" + operatorUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + OPERATOR_USERNAME + "\",\"password\":\"OperatorPass1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(42)
    @Transactional
    void cannotDeleteLastRemainingAdmin() throws Exception {
        User self = userRepository.findByUsernameAndActiveTrue(ADMIN_USERNAME).orElseThrow();

        List<User> otherAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .filter(u -> !u.getId().equals(self.getId()))
                .toList();
        otherAdmins.forEach(admin -> {
            admin.setActive(false);
            userRepository.save(admin);
        });

        assertEquals(1L, userRepository.countByRoleAndActiveTrue(UserRole.ADMIN),
                "Test setup tek admin birakmadi, once diger adminler silinmeliydi");

        mockMvc.perform(delete("/api/users/" + self.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_DELETE_LAST_ADMIN"));
    }

    @Test
    @Order(43)
    void changingOwnPasswordWithWrongCurrentPasswordFails() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"WrongPassword1!\",\"newPassword\":\"NewAdminPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    @Order(44)
    void changingOwnPasswordSucceedsAndOldPasswordNoLongerWorks() throws Exception {
        String newPassword = "NewAdminPass1!";

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + ADMIN_PASSWORD + "\",\"newPassword\":\"" + newPassword + "\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ADMIN_USERNAME + "\",\"password\":\"" + newPassword + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @Order(45)
    void purgeJobAnonymizesSegmentResultsPastRetention() throws Exception {
        User uploader = userRepository.findByUsername(OPERATOR_USERNAME).orElseThrow();
        Template template = templateRepository.findById(createdTemplateId).orElseThrow();

        DocumentMetadata expired = new DocumentMetadata();
        expired.setFileName("purge-test.png");
        expired.setUploadedBy(uploader);
        expired.setTemplate(template);
        expired.setStatus(DocumentStatus.VALIDATED);
        expired.setSegmentResults("[{\"segmentId\":1,\"label\":\"Test\",\"outcome\":\"FILLED_VALID\"}]");
        expired.setProcessedAt(Instant.now().minus(200, ChronoUnit.DAYS));
        expired.setPurgeAt(Instant.now().minus(1, ChronoUnit.DAYS));
        expired = documentRepository.save(expired);
        Long expiredDocumentId = expired.getId();

        SegmentImage staleImage = new SegmentImage();
        staleImage.setDocumentId(expiredDocumentId);
        staleImage.setSegmentId(resolveSegmentAId);
        staleImage.setImageDataBase64(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}));
        staleImage.setCreatedAt(Instant.now());
        segmentImageRepository.save(staleImage);

        retentionCleanupJob.purgeExpiredSegmentResults();

        DocumentMetadata reloaded = documentRepository.findById(expiredDocumentId).orElseThrow();
        assertNull(reloaded.getSegmentResults());
        assertTrue(segmentImageRepository.findByDocumentId(expiredDocumentId).isEmpty(),
                "Retention purge sonrasi segment goruntuleri de silinmis olmali");
    }


    @Test
    @Order(46)
    void operatorDocumentListIsScopedToOwnUploads() throws Exception {
        String secondOperatorUsername = "operator2_" + RUN_ID;
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + secondOperatorUsername + "\",\"password\":\"Operator2Pass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + secondOperatorUsername + "\",\"password\":\"Operator2Pass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String secondOperatorToken = extractToken(loginResult);

        mockMvc.perform(get("/api/documents?page=0&size=50")
                        .header("Authorization", "Bearer " + secondOperatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/documents?page=0&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + signedDocumentId + ")]").exists());
    }

    @Test
    @Order(47)
    void pdfSinglePageSignedDocumentIsValidatedEndToEnd() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "signed.pdf", "application/pdf", generateInkPdf(1, 1, true));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();

        Long pdfSignedDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(pdfSignedDocumentId, adminToken);
        assertTrue("VALIDATED".equals(finalStatus), "Expected VALIDATED but was " + finalStatus);
    }

    @Test
    @Order(48)
    void pdfSinglePageBlankDocumentIsRejectedAsEmptyEndToEnd() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "blank.pdf", "application/pdf", generateInkPdf(1, 1, false));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();

        Long pdfBlankDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(pdfBlankDocumentId, adminToken);
        assertTrue("REJECTED_EMPTY".equals(finalStatus), "Expected REJECTED_EMPTY but was " + finalStatus);
    }

    @Test
    @Order(49)
    void pdfMultiPageDocumentIsRasterizedFromCorrectPage() throws Exception {
        String requestBody = """
                {
                  "name": "PDF Multi Page Template %s",
                  "pageCount": 2,
                  "segments": [
                    { "label": "Page2Field", "page": 2, "x": 0, "y": 0, "w": 100, "h": 100,
                      "rules": [ { "type": "DIGITS_ONLY" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        MvcResult templateResult = mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long twoPageTemplateId = extractLongField(templateResult, "id");

        MockMultipartFile file = new MockMultipartFile("file", "two-pages.pdf", "application/pdf", generateInkPdf(2, 2, false));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(twoPageTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();

        Long twoPageDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(twoPageDocumentId, adminToken);
        assertTrue("REJECTED_EMPTY".equals(finalStatus),
                "Expected REJECTED_EMPTY (page 2 correctly rasterized and read as blank) but was " + finalStatus);
    }

    @Test
    @Order(50)
    void pdfWithFewerPagesThanTemplateRequiresRoutesToPendingReview() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "one-page.pdf", "application/pdf", generateInkPdf(1, 1, false));

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(multiPageTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();

        Long shortPdfDocumentId = extractLongField(uploadResult, "id");
        String finalStatus = pollForFinalStatus(shortPdfDocumentId, adminToken);
        assertTrue("PENDING_REVIEW".equals(finalStatus), "Expected PENDING_REVIEW but was " + finalStatus);
    }

    @Test
    @Order(51)
    void multipleFilesUploadedInSuccessionAreProcessedIndependently() throws Exception {
        MockMultipartFile signedFile = new MockMultipartFile("file", "batch-signed.png", "image/png", generateInkImage(true));
        MockMultipartFile blankFile = new MockMultipartFile("file", "batch-blank.png", "image/png", generateInkImage(false));

        MvcResult signedUpload = mockMvc.perform(multipart("/api/documents/upload")
                        .file(signedFile)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();
        Long batchSignedId = extractLongField(signedUpload, "id");

        MvcResult blankUpload = mockMvc.perform(multipart("/api/documents/upload")
                        .file(blankFile)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();
        Long batchBlankId = extractLongField(blankUpload, "id");

        String signedStatus = pollForFinalStatus(batchSignedId, adminToken);
        String blankStatus = pollForFinalStatus(batchBlankId, adminToken);

        assertTrue("VALIDATED".equals(signedStatus), "Expected VALIDATED but was " + signedStatus);
        assertTrue("REJECTED_EMPTY".equals(blankStatus), "Expected REJECTED_EMPTY but was " + blankStatus);
    }

    @Test
    @Order(52)
    void uploadWithMismatchedPageCountIsRejectedBeforeProcessing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "wrong-page-count.pdf", "application/pdf", generateInkPdf(3, 1, false));

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(inkTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAGE_COUNT_MISMATCH"));
    }

    @Test
    @Order(53)
    void operatorAccessAndReviewQueueAreScopedToOwnUploads() throws Exception {
        String ownerUsername = "operator_owner_" + RUN_ID;
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ownerUsername + "\",\"password\":\"OwnerPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());

        MvcResult ownerLoginResult = mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ownerUsername + "\",\"password\":\"OwnerPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String ownerToken = extractToken(ownerLoginResult);

        String requestBody = """
                {
                  "name": "IDOR Test Template %s",
                  "segments": [
                    { "label": "Imza", "page": 1, "x": 100, "y": 100, "w": 200, "h": 80,
                      "rules": [ { "type": "SIGNATURE_INK" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        MvcResult templateResult = mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long idorTemplateId = extractLongField(templateResult, "id");

        MvcResult detailResult = mockMvc.perform(get("/api/templates/" + idorTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(detailResult.getResponse().getContentAsString());
        assertTrue(idMatcher.find(), "Template id bulunamadi");
        assertTrue(idMatcher.find(), "Segment id bulunamadi");
        Long idorSegmentId = Long.valueOf(idMatcher.group(1));

        MockMultipartFile file = new MockMultipartFile("file", "idor-seed.png", "image/png", generateInkImage(false));
        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("templateId", String.valueOf(idorTemplateId))
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isAccepted())
                .andReturn();
        Long idorDocumentId = extractLongField(uploadResult, "id");
        pollForFinalStatus(idorDocumentId, ownerToken);

        SegmentResultEntry entry = new SegmentResultEntry();
        entry.setSegmentId(idorSegmentId);
        entry.setLabel("Imza");
        entry.setOutcome(SegmentOutcome.PENDING_REVIEW);

        DocumentMetadata document = documentRepository.findById(idorDocumentId).orElseThrow();
        document.setStatus(DocumentStatus.PENDING_REVIEW);
        document.setSegmentResults(jsonMapper.writeValueAsString(List.of(entry)));
        documentRepository.save(document);

        String outsiderUsername = "operator_outsider_" + RUN_ID;
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + outsiderUsername + "\",\"password\":\"OutsiderPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());

        MvcResult outsiderLoginResult = mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + outsiderUsername + "\",\"password\":\"OutsiderPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String outsiderToken = extractToken(outsiderLoginResult);

        mockMvc.perform(get("/api/documents/" + idorDocumentId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/documents/" + idorDocumentId + "/segments/" + idorSegmentId + "/image")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        mockMvc.perform(post("/api/documents/" + idorDocumentId + "/segments/" + idorSegmentId + "/resolve")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));

        mockMvc.perform(get("/api/documents/queue?page=0&size=50")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/documents/stats")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReview").value(0));

        mockMvc.perform(get("/api/documents/" + idorDocumentId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));

        mockMvc.perform(get("/api/documents/" + idorDocumentId + "/segments/" + idorSegmentId + "/image")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/queue?page=0&size=50")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/api/documents/" + idorDocumentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/queue?page=0&size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)));
    }

    @Test
    @Order(54)
    void frameworkExceptionsMapToProperErrorCodes() throws Exception {
        mockMvc.perform(get("/api/does-not-exist")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(delete("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(get("/api/documents/not-a-number")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_TYPE"));

        MockMultipartFile file = new MockMultipartFile("file", "preview-missing-param.png", "image/png", generateInkImage(true));
        mockMvc.perform(multipart("/api/templates/preview")
                        .file(file)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_REQUEST_PARAMETER"));
    }

    @Test
    @Order(55)
    void previewRejectsSegmentWithMissingCoordinatesInsteadOfCrashing() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "preview-bad.png", "image/png", generateInkImage(true));
        String segmentsJson = "[{\"label\":\"Imza\",\"page\":1,\"x\":null,\"y\":0,\"w\":100,\"h\":100}]";

        mockMvc.perform(multipart("/api/templates/preview")
                        .file(file)
                        .param("segments", segmentsJson)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PREVIEW_FAILED"));
    }

    @Test
    @Order(56)
    void adminCanOverrideResolvedSegmentAndDocumentStatusRecomputes() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentBId + "/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\",\"reasonCode\":\"OCR_MISREAD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));

        mockMvc.perform(get("/api/admin/audit-logs?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'SEGMENT_OVERRIDDEN')]").exists());
    }

    @Test
    @Order(57)
    void operatorCannotOverrideSegment() throws Exception {
        String freshOperatorUsername = "operator_override_check_" + RUN_ID;
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + freshOperatorUsername + "\",\"password\":\"FreshOpPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + freshOperatorUsername + "\",\"password\":\"FreshOpPass1!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String freshOperatorToken = extractToken(loginResult);

        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/override")
                        .header("Authorization", "Bearer " + freshOperatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_INVALID\",\"reasonCode\":\"OCR_MISREAD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(58)
    void overridingPendingSegmentIsRejected() throws Exception {
        MvcResult detailResult = mockMvc.perform(get("/api/templates/" + multiPageTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(detailResult.getResponse().getContentAsString());
        assertTrue(idMatcher.find(), "Template id bulunamadi");
        assertTrue(idMatcher.find(), "Segment id bulunamadi");
        Long pendingSegmentId = Long.valueOf(idMatcher.group(1));

        String requestBody = """
                {
                  "name": "Override Pending Test Template %s",
                  "segments": [
                    { "label": "BekleyenAlan", "page": 1, "x": 100, "y": 100, "w": 200, "h": 80,
                      "rules": [ { "type": "LETTERS_ONLY" } ] }
                  ]
                }
                """.formatted(RUN_ID);

        MvcResult templateResult = mockMvc.perform(post("/api/templates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long stillPendingTemplateId = extractLongField(templateResult, "id");

        MvcResult detailResult2 = mockMvc.perform(get("/api/templates/" + stillPendingTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Matcher segmentIdMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(detailResult2.getResponse().getContentAsString());
        assertTrue(segmentIdMatcher.find(), "Template id bulunamadi");
        assertTrue(segmentIdMatcher.find(), "Segment id bulunamadi");
        Long stillPendingSegmentId = Long.valueOf(segmentIdMatcher.group(1));

        MockMultipartFile stillPendingFile = new MockMultipartFile("file", "still-pending.png", "image/png", generateInkImage(false));
        MvcResult stillPendingUpload = mockMvc.perform(multipart("/api/documents/upload")
                        .file(stillPendingFile)
                        .param("templateId", String.valueOf(stillPendingTemplateId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted())
                .andReturn();
        Long stillPendingDocumentId = extractLongField(stillPendingUpload, "id");
        pollForFinalStatus(stillPendingDocumentId, adminToken);

        SegmentResultEntry stillPendingEntry = new SegmentResultEntry();
        stillPendingEntry.setSegmentId(stillPendingSegmentId);
        stillPendingEntry.setLabel("BekleyenAlan");
        stillPendingEntry.setOutcome(SegmentOutcome.PENDING_REVIEW);

        DocumentMetadata stillPendingDocument = documentRepository.findById(stillPendingDocumentId).orElseThrow();
        stillPendingDocument.setStatus(DocumentStatus.PENDING_REVIEW);
        stillPendingDocument.setSegmentResults(jsonMapper.writeValueAsString(List.of(stillPendingEntry)));
        documentRepository.save(stillPendingDocument);

        mockMvc.perform(post("/api/documents/" + stillPendingDocumentId
                        + "/segments/" + stillPendingSegmentId + "/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_VALID\",\"reasonCode\":\"OCR_MISREAD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SEGMENT_NOT_YET_RESOLVED"));
    }

    @Test
    @Order(59)
    void overrideWithOtherReasonRequiresNote() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_INVALID\",\"reasonCode\":\"OTHER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVERRIDE_NOTE_REQUIRED"));

        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_INVALID\",\"reasonCode\":\"OTHER\",\"note\":\"Belge kalitesi dusuktu\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @Order(60)
    void overrideWithSameOutcomeIsRejected() throws Exception {
        mockMvc.perform(post("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentAId + "/override")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"FILLED_INVALID\",\"reasonCode\":\"OCR_MISREAD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OVERRIDE_OUTCOME_UNCHANGED"));
    }

    @Test
    @Order(61)
    void adminCanResetUserPasswordWithOwnPasswordConfirmation() throws Exception {
        String targetUsername = "reset_target_" + RUN_ID;
        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"OldPassword1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long targetUserId = extractLongField(createResult, "id");

        String currentAdminPassword = "NewAdminPass1!";

        mockMvc.perform(put("/api/users/" + targetUserId + "/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminPassword\":\"WrongAdminPassword1!\",\"newPassword\":\"NewTargetPass1!\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mockMvc.perform(put("/api/users/" + targetUserId + "/password")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"adminPassword\":\"" + currentAdminPassword + "\",\"newPassword\":\"NewTargetPass1!\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"OldPassword1!\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr(AUX_LOGIN_REMOTE_ADDR);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"NewTargetPass1!\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/audit-logs?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'PASSWORD_RESET_BY_ADMIN')]").exists());
    }

    @Test
    @Order(62)
    void tcKimlikNoChecksumValidatesRealAlgorithm() {
        TemplateSegment segment = new TemplateSegment();
        segment.setId(9001L);
        segment.setLabel("TC");
        SegmentRule rule = new SegmentRule();
        rule.setSegment(segment);
        rule.setRuleType(SegmentRuleType.TC_KIMLIK_NO);
        segment.getRules().add(rule);

        SegmentReading validReading = new SegmentReading(segment, "10562272296", null, 95.0, null);
        SegmentReading invalidReading = new SegmentReading(segment, "11111111111", null, 95.0, null);
        SegmentReading leadingZeroReading = new SegmentReading(segment, "01562272296", null, 95.0, null);

        ValidationResult validResult = validationService.validate(List.of(validReading));
        ValidationResult invalidResult = validationService.validate(List.of(invalidReading));
        ValidationResult leadingZeroResult = validationService.validate(List.of(leadingZeroReading));

        assertEquals(SegmentOutcome.FILLED_VALID, validResult.getEntries().get(0).getOutcome());
        assertEquals(SegmentOutcome.FILLED_INVALID, invalidResult.getEntries().get(0).getOutcome());
        assertEquals(SegmentOutcome.FILLED_INVALID, leadingZeroResult.getEntries().get(0).getOutcome());
    }

    @Test
    @Order(63)
    void vknChecksumValidatesRealAlgorithm() {
        TemplateSegment segment = new TemplateSegment();
        segment.setId(9002L);
        segment.setLabel("VKN");
        SegmentRule rule = new SegmentRule();
        rule.setSegment(segment);
        rule.setRuleType(SegmentRuleType.VKN);
        segment.getRules().add(rule);

        SegmentReading validReading = new SegmentReading(segment, "1234567890", null, 95.0, null);
        SegmentReading invalidReading = new SegmentReading(segment, "1234567891", null, 95.0, null);

        ValidationResult validResult = validationService.validate(List.of(validReading));
        ValidationResult invalidResult = validationService.validate(List.of(invalidReading));

        assertEquals(SegmentOutcome.FILLED_VALID, validResult.getEntries().get(0).getOutcome());
        assertEquals(SegmentOutcome.FILLED_INVALID, invalidResult.getEntries().get(0).getOutcome());
    }

    @Test
    @Order(64)
    void phoneRuleAcceptsInternationalNumbersAndStripsSeparators() {
        TemplateSegment segment = new TemplateSegment();
        segment.setId(9003L);
        segment.setLabel("Telefon");
        SegmentRule rule = new SegmentRule();
        rule.setSegment(segment);
        rule.setRuleType(SegmentRuleType.PHONE);
        segment.getRules().add(rule);

        SegmentReading internationalReading = new SegmentReading(segment, "+905321234567", null, 95.0, null);
        SegmentReading withSeparatorsReading = new SegmentReading(segment, "0532 123 45 67", null, 95.0, null);
        SegmentReading tooShortReading = new SegmentReading(segment, "123", null, 95.0, null);
        SegmentReading tooLongReading = new SegmentReading(segment, "1234567890123456", null, 95.0, null);

        ValidationResult internationalResult = validationService.validate(List.of(internationalReading));
        ValidationResult withSeparatorsResult = validationService.validate(List.of(withSeparatorsReading));
        ValidationResult tooShortResult = validationService.validate(List.of(tooShortReading));
        ValidationResult tooLongResult = validationService.validate(List.of(tooLongReading));

        assertEquals(SegmentOutcome.FILLED_VALID, internationalResult.getEntries().get(0).getOutcome());
        assertEquals(SegmentOutcome.FILLED_VALID, withSeparatorsResult.getEntries().get(0).getOutcome());
        assertEquals(SegmentOutcome.FILLED_INVALID, tooShortResult.getEntries().get(0).getOutcome());
        assertEquals(SegmentOutcome.FILLED_INVALID, tooLongResult.getEntries().get(0).getOutcome());
    }

    @Test
    @Order(65)
    void inkSegmentImageIsPersistedAfterValidation() throws Exception {
        MvcResult detailResult = mockMvc.perform(get("/api/templates/" + inkTemplateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        Matcher idMatcher = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(detailResult.getResponse().getContentAsString());
        assertTrue(idMatcher.find(), "Template id bulunamadi");
        assertTrue(idMatcher.find(), "Segment id bulunamadi");
        Long inkSegmentId = Long.valueOf(idMatcher.group(1));

        mockMvc.perform(get("/api/documents/" + signedDocumentId + "/segments/" + inkSegmentId + "/image")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_JPEG));
    }

    @Test
    @Order(66)
    void auditLogRecordsCorrectTargetUserIdOnDeactivation() throws Exception {
        String targetUsername = "audit_target_" + RUN_ID;
        MvcResult createResult = mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + targetUsername + "\",\"password\":\"AuditTargetPass1!\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        Long targetUserId = extractLongField(createResult, "id");

        mockMvc.perform(delete("/api/users/" + targetUserId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/audit-logs?size=50")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.action == 'USER_DEACTIVATED' && @.targetUserId == " + targetUserId + ")]").exists());
    }
}
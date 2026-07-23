package com.validdoc;

import com.validdoc.config.DocumentGeometry;
import com.validdoc.dto.internal.SegmentResultEntry;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.SegmentImage;
import com.validdoc.model.Template;
import com.validdoc.model.User;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.SegmentOutcome;
import com.validdoc.model.enums.UserRole;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.SegmentImageRepository;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.repository.UserRepository;
import com.validdoc.scheduler.RetentionCleanupJob;
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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.contains;
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
class ApiIntegrationTest {

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
        admin.setEmail(ADMIN_USERNAME + "@validdoc.local");
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
                        .content("{\"username\":\"" + OPERATOR_USERNAME + "\",\"password\":\"OperatorPass1!\",\"email\":\"" + OPERATOR_USERNAME + "@validdoc.local\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    @Order(3)
    void creatingDuplicateUsernameFails() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + OPERATOR_USERNAME + "\",\"password\":\"OperatorPass1!\",\"email\":\"dup@validdoc.local\",\"role\":\"OPERATOR\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RECORD"));
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
                        .content("{\"username\":\"x_" + RUN_ID + "\",\"password\":\"whatever1!\",\"email\":\"x@x.com\",\"role\":\"OPERATOR\"}"))
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
        for (int i = 0; i < 5; i++) {
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
        String requestBody = """
                {
                  "name": "Multi Page Template %s",
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
        multiPageTemplateId = extractLongField(templateResult, "id");

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
    @Order(26)
    void operatorCanManuallyOverridePendingReviewDocument() throws Exception {
        mockMvc.perform(post("/api/documents/" + mismatchDocumentId + "/verify")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALIDATED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/documents/" + mismatchDocumentId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"));
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

        SegmentImage image = new SegmentImage();
        image.setDocumentId(resolveTestDocumentId);
        image.setSegmentId(resolveSegmentBId);
        image.setImageDataBase64(Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4}));
        image.setCreatedAt(LocalDateTime.now());
        segmentImageRepository.save(image);

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
        assertTrue(contentType != null && contentType.startsWith("image/png"),
                "Beklenen content-type image/png degil: " + contentType);
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
    void segmentImageIsDeletedAfterResolve() throws Exception {
        mockMvc.perform(get("/api/documents/" + resolveTestDocumentId
                        + "/segments/" + resolveSegmentBId + "/image")
                        .header("Authorization", "Bearer " + operatorToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEGMENT_IMAGE_NOT_FOUND"));
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
        User uploader = userRepository.findByUsername(OPERATOR_USERNAME).orElseThrow();
        Template template = templateRepository.findById(createdTemplateId).orElseThrow();

        DocumentMetadata abandoned = new DocumentMetadata();
        abandoned.setFileName("abandoned-test.png");
        abandoned.setUploadedBy(uploader);
        abandoned.setTemplate(template);
        abandoned.setStatus(DocumentStatus.PENDING_REVIEW);
        abandoned.setProcessedAt(LocalDateTime.now().minusYears(5));
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
                        .content("{\"username\":\"" + throwawayUsername + "\",\"password\":\"ThrowawayPass1!\",\"email\":\""
                                + throwawayUsername + "@validdoc.local\",\"role\":\"OPERATOR\"}"))
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
    void cannotDeleteUserWithLinkedDocuments() throws Exception {
        User operatorUser = userRepository.findByUsername(OPERATOR_USERNAME).orElseThrow();

        mockMvc.perform(delete("/api/users/" + operatorUser.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_LINKED_DOCUMENTS"));
    }

    // Bu test tek admin kalmis senaryosunu, mevcut gercek admin hesaplarini kalici olarak
    // silmeden simule etmek icin @Transactional kullaniyor: testin ici, diger tum admin
    // kayitlarini gecici olarak siler, kontrolu yapar, sonra Spring test rollback'i sayesinde
    // test bitince hepsi otomatik geri gelir. Kalici hicbir silme olmaz.
    @Test
    @Order(42)
    @Transactional
    void cannotDeleteLastRemainingAdmin() throws Exception {
        User self = userRepository.findByUsername(ADMIN_USERNAME).orElseThrow();

        List<User> otherAdmins = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .filter(u -> !u.getId().equals(self.getId()))
                .toList();
        userRepository.deleteAll(otherAdmins);

        assertEquals(1L, userRepository.countByRole(UserRole.ADMIN),
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
        expired.setProcessedAt(LocalDateTime.now().minusDays(200));
        expired.setPurgeAt(LocalDateTime.now().minusDays(1));
        expired = documentRepository.save(expired);
        Long expiredDocumentId = expired.getId();

        retentionCleanupJob.purgeExpiredSegmentResults();

        DocumentMetadata reloaded = documentRepository.findById(expiredDocumentId).orElseThrow();
        assertNull(reloaded.getSegmentResults());
    }
}
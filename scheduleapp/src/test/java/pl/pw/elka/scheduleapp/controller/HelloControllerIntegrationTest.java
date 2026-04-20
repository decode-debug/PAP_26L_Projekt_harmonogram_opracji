package pl.pw.elka.scheduleapp.controller;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:hello-controller-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class HelloControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperationRepository operationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void clearDatabase() {
        operationRepository.deleteAll();
    }

    @Test
    void shouldReturnHelloMessage() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Serwer")));
    }

    @Test
    void shouldCreateStandardOperation() throws Exception {
        String payload = """
                {
                  "name": "Spawanie",
                  "startTime": "2026-04-20T08:00",
                  "endTime": "2026-04-22T12:00",
                  "workerCount": 4,
                  "resources": "Spawarka",
                  "maxCrashingDays": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.uuid").isString())
                .andExpect(jsonPath("$.name").value("Spawanie"))
                .andExpect(jsonPath("$.workerCount").value(4));

        assertThat(operationRepository.findAll()).hasSize(1);
    }

    @Test
    void shouldRejectOperationWithBlankName() throws Exception {
        String payload = """
                {
                  "name": "   ",
                  "startTime": "2026-04-20T08:00",
                  "endTime": "2026-04-20T12:00",
                  "workerCount": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(operationRepository.findAll()).isEmpty();
    }

    @Test
    void shouldRejectStandardOperationWithInvalidDateRange() throws Exception {
        String payload = """
                {
                  "name": "Lakierowanie",
                  "startTime": "2026-04-20T12:00",
                  "endTime": "2026-04-20T12:00",
                  "workerCount": 2
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        assertThat(operationRepository.findAll()).isEmpty();
    }

    @Test
    void shouldCreateAsapOperationAndClearFixedDates() throws Exception {
        String payload = """
                {
                  "name": "Kontrola",
                  "asap": true,
                  "asapDurationHours": 12,
                  "startTime": "2026-04-20T08:00",
                  "endTime": "2026-04-21T08:00",
                  "workerCount": 2
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asap").value(true))
                .andExpect(jsonPath("$.startTime").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.endTime").value(org.hamcrest.Matchers.nullValue()));

        Operation saved = operationRepository.findAll().getFirst();
        assertThat(saved.getStartTime()).isNull();
        assertThat(saved.getEndTime()).isNull();
    }

    @Test
    void shouldCrashOperationWithinAllowedLimit() throws Exception {
        Operation operation = saveStandardOperation("Montaż", "uuid-montaz",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 24, 8, 0),
                3);
        operation.setMaxCrashingDays(2);
        operationRepository.saveAndFlush(operation);

        mockMvc.perform(put("/api/operations/{id}/crash/{days}", operation.getId(), 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crashedDays").value(2));

        assertThat(operationRepository.findById(operation.getId()).orElseThrow().getCrashedDays()).isEqualTo(2);
    }

    @Test
    void shouldRejectCrashBeyondLimit() throws Exception {
        Operation operation = saveStandardOperation("Montaż", "uuid-montaz",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 24, 8, 0),
                3);
        operation.setMaxCrashingDays(1);
        operationRepository.saveAndFlush(operation);

        mockMvc.perform(put("/api/operations/{id}/crash/{days}", operation.getId(), 2))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("między 0 a 1")));
    }

    @Test
    void shouldDeleteOperationAndInheritPredecessorsForSuccessor() throws Exception {
        Operation first = saveStandardOperation("Pierwsza", "uuid-first",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0),
                1);
        Operation middle = saveStandardOperation("Środkowa", "uuid-middle",
                LocalDateTime.of(2026, 4, 21, 8, 0),
                LocalDateTime.of(2026, 4, 22, 8, 0),
                1);
        middle.setPredecessorIds(first.getUuid());
        middle = operationRepository.saveAndFlush(middle);

        Operation last = saveStandardOperation("Końcowa", "uuid-last",
                LocalDateTime.of(2026, 4, 22, 8, 0),
                LocalDateTime.of(2026, 4, 23, 8, 0),
                1);
        last.setPredecessorIds(first.getUuid() + "," + middle.getUuid());
        last = operationRepository.saveAndFlush(last);

        mockMvc.perform(delete("/api/operations/{id}", middle.getId()))
                .andExpect(status().isOk());

        Operation reloadedLast = operationRepository.findById(last.getId()).orElseThrow();
        assertThat(operationRepository.findById(middle.getId())).isEmpty();
        assertThat(reloadedLast.getPredecessorIds()).isEqualTo(first.getUuid());
    }

    @Test
    void shouldExportOperationsAsDownloadableJson() throws Exception {
        saveStandardOperation("Cięcie", "uuid-cut",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0),
                2);

        String response = mockMvc.perform(get("/api/operations/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("harmonogram.json")))
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode exported = objectMapper.readTree(response);
        assertThat(exported).hasSize(1);
        assertThat(exported.get(0).get("name").asText()).isEqualTo("Cięcie");
    }

    @Test
    void shouldImportOperationsAndConvertNumericPredecessorsToUuids() throws Exception {
        saveStandardOperation("Stara operacja", "uuid-old",
                LocalDateTime.of(2026, 4, 10, 8, 0),
                LocalDateTime.of(2026, 4, 11, 8, 0),
                1);

        String json = """
                [
                  {
                    "id": 10,
                    "name": "Przygotowanie",
                    "startTime": "2026-04-20T08:00",
                    "endTime": "2026-04-21T08:00",
                    "workerCount": 2
                  },
                  {
                    "id": 20,
                    "name": "Montaż",
                    "startTime": "2026-04-21T08:00",
                    "endTime": "2026-04-22T08:00",
                    "workerCount": 3,
                    "predecessorIds": "10"
                  }
                ]
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "harmonogram.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/operations/import")
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        List<Operation> saved = operationRepository.findAll().stream()
                .sorted(Comparator.comparing(Operation::getName))
                .toList();
        Operation montage = saved.stream().filter(op -> op.getName().equals("Montaż")).findFirst().orElseThrow();
        Operation preparation = saved.stream().filter(op -> op.getName().equals("Przygotowanie")).findFirst().orElseThrow();

        assertThat(montage.getPredecessorIds()).isEqualTo(preparation.getUuid());
        assertThat(saved).extracting(Operation::getName)
                .containsExactly("Montaż", "Przygotowanie");
    }

    @Test
    void shouldMergeImportedOperationsAndRemapInternalUuidReferences() throws Exception {
        Operation existing = saveStandardOperation("Istniejąca", "uuid-existing",
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 20, 8, 0),
                1);

        String json = """
                [
                  {
                    "id": 1,
                    "uuid": "11111111-1111-1111-1111-111111111111",
                    "name": "Import A",
                    "startTime": "2026-04-22T08:00",
                    "endTime": "2026-04-23T08:00",
                    "workerCount": 2
                  },
                  {
                    "id": 2,
                    "uuid": "22222222-2222-2222-2222-222222222222",
                    "name": "Import B",
                    "startTime": "2026-04-23T08:00",
                    "endTime": "2026-04-24T08:00",
                    "workerCount": 2,
                    "predecessorIds": "11111111-1111-1111-1111-111111111111"
                  }
                ]
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "merge.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/operations/import-merge")
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        List<Operation> all = operationRepository.findAll();
        assertThat(all).hasSize(3);
        assertThat(all).extracting(Operation::getName).contains(existing.getName(), "Import A", "Import B");

        Operation importA = all.stream().filter(op -> op.getName().equals("Import A")).findFirst().orElseThrow();
        Operation importB = all.stream().filter(op -> op.getName().equals("Import B")).findFirst().orElseThrow();
        assertThat(importA.getUuid()).isNotEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(importB.getUuid()).isNotEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(importB.getPredecessorIds()).isEqualTo(importA.getUuid());
    }

    @Test
    void shouldComputeEarlyGanttIncludingAsapDependenciesAndCrashing() throws Exception {
        Operation fixed = saveStandardOperation("Stała", "11111111-1111-1111-1111-111111111111",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 22, 8, 0),
                2);
        fixed.setCrashedDays(1);
        fixed = operationRepository.saveAndFlush(fixed);

        Operation asap = new Operation();
        asap.setUuid("22222222-2222-2222-2222-222222222222");
        asap.setName("ASAP");
        asap.setAsap(true);
        asap.setAsapDurationHours(12.0);
        asap.setWorkerCount(1);
        asap.setPredecessorIds(fixed.getUuid());
        operationRepository.saveAndFlush(asap);

        MvcResult result = mockMvc.perform(get("/api/operations/gantt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode fixedBar = findBarByName(root, "Stała");
        JsonNode asapBar = findBarByName(root, "ASAP");

        assertThat(root.path("projectStart").asText()).isEqualTo("2026-04-20T08:00:00");
        assertThat(root.path("projectEnd").asText()).isEqualTo("2026-04-21T20:00:00");
        assertThat(fixedBar.path("durationDays").asDouble()).isEqualTo(1.0);
        assertThat(asapBar.path("startTime").asText()).isEqualTo("2026-04-21T08:00:00");
        assertThat(asapBar.path("endTime").asText()).isEqualTo("2026-04-21T20:00:00");
        assertThat(asapBar.path("predecessorIds").get(0).asLong()).isEqualTo(fixed.getId());
    }

    @Test
    void shouldComputeLateGanttUsingBackwardPass() throws Exception {
        Operation first = saveStandardOperation("Pierwsza", "33333333-3333-3333-3333-333333333333",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0),
                1);
        Operation second = saveStandardOperation("Druga", "44444444-4444-4444-4444-444444444444",
                LocalDateTime.of(2026, 4, 21, 8, 0),
                LocalDateTime.of(2026, 4, 23, 8, 0),
                1);
        second.setPredecessorIds(first.getUuid());
        operationRepository.saveAndFlush(second);

        MvcResult result = mockMvc.perform(get("/api/operations/gantt-late"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode firstBar = findBarByName(root, "Pierwsza");
        JsonNode secondBar = findBarByName(root, "Druga");

        assertThat(root.path("projectStart").asText()).isEqualTo("2026-04-20T08:00:00");
        assertThat(root.path("projectEnd").asText()).isEqualTo("2026-04-23T08:00:00");
        assertThat(firstBar.path("startTime").asText()).isEqualTo("2026-04-20T08:00:00");
        assertThat(firstBar.path("endTime").asText()).isEqualTo("2026-04-21T08:00:00");
        assertThat(secondBar.path("startTime").asText()).isEqualTo("2026-04-21T08:00:00");
        assertThat(secondBar.path("endTime").asText()).isEqualTo("2026-04-23T08:00:00");
    }

    @Test
    void shouldReturnEmptyGanttWhenNoOperationsExist() throws Exception {
        mockMvc.perform(get("/api/operations/gantt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectStart").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.projectEnd").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.totalDays").value(0.0))
                .andExpect(jsonPath("$.bars", org.hamcrest.Matchers.hasSize(0)));
    }

    // ============================================================
    // TESTY: ASAP przesuwa się po usunięciu poprzednika
    // ============================================================

    /**
     * Op A (stała, kończy 23.04), Op B (stała, kończy 21.04), Op C (ASAP 12h, preds=A+B).
     * Przed usunięciem: C startuje po max(A_end, B_end) = 23.04.
     * Po usunięciu B: C startuje po A_end = 21.04T08:00.
     */
    @Test
    void shouldShiftAsapStartWhenOnePredecessorIsDeleted() throws Exception {
        Operation opA = saveStandardOperation("OpA", "aaaaaaaa-0001-0001-0001-000000000001",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0), 1);
        Operation opB = saveStandardOperation("OpB", "bbbbbbbb-0002-0002-0002-000000000002",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 23, 8, 0), 1);

        // Op C (ASAP) ma dwóch poprzedników
        Operation opC = new Operation();
        opC.setUuid("cccccccc-0003-0003-0003-000000000003");
        opC.setName("OpC-ASAP");
        opC.setAsap(true);
        opC.setAsapDurationHours(12.0);
        opC.setWorkerCount(1);
        opC.setPredecessorIds(opA.getUuid() + "," + opB.getUuid());
        opC = operationRepository.saveAndFlush(opC);

        // Sprawdź stan przed usunięciem: C startuje po 23.04T08:00
        MvcResult before = mockMvc.perform(get("/api/operations/gantt"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode beforeRoot = objectMapper.readTree(before.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode beforeC = findBarByName(beforeRoot, "OpC-ASAP");
        assertThat(beforeC.path("startTime").asText()).isEqualTo("2026-04-23T08:00:00");

        // Usuń B
        mockMvc.perform(delete("/api/operations/{id}", opB.getId()))
                .andExpect(status().isOk());

        // Sprawdź stan po usunięciu: C powinno startować po A_end = 21.04T08:00
        MvcResult after = mockMvc.perform(get("/api/operations/gantt"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode afterRoot = objectMapper.readTree(after.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode afterC = findBarByName(afterRoot, "OpC-ASAP");
        assertThat(afterC.path("startTime").asText()).isEqualTo("2026-04-21T08:00:00");

        // Poprzednicy C po usunięciu powinni zawierać tylko UUID-A
        Operation reloadedC = operationRepository.findById(opC.getId()).orElseThrow();
        assertThat(reloadedC.getPredecessorIds()).isEqualTo(opA.getUuid());
    }

    /**
     * Op Anchor (stała, startuje 25.04), Op A (stała, startuje 20.04 → kończy 21.04),
     * Op B (ASAP 8h, preds=A).
     * Po usunięciu A: B nie ma poprzedników → startuje przy projectStart = 25.04T08:00
     * (bo Anchor jest teraz jedyną stałą operacją).
     */
    @Test
    void shouldStartAsapAtProjectStartWhenAllPredecessorsRemoved() throws Exception {
        Operation anchor = saveStandardOperation("Anchor", "dddddddd-0004-0004-0004-000000000004",
                LocalDateTime.of(2026, 4, 25, 8, 0),
                LocalDateTime.of(2026, 4, 26, 8, 0), 1);
        Operation opA = saveStandardOperation("OpA", "eeeeeeee-0005-0005-0005-000000000005",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0), 1);

        Operation opB = new Operation();
        opB.setUuid("ffffffff-0006-0006-0006-000000000006");
        opB.setName("OpB-ASAP");
        opB.setAsap(true);
        opB.setAsapDurationHours(8.0);
        opB.setWorkerCount(1);
        opB.setPredecessorIds(opA.getUuid());
        opB = operationRepository.saveAndFlush(opB);

        // Usuń jedynego poprzednika B
        mockMvc.perform(delete("/api/operations/{id}", opA.getId()))
                .andExpect(status().isOk());

        // B nie ma teraz poprzedników → predecessorIds powinno być null
        Operation reloadedB = operationRepository.findById(opB.getId()).orElseThrow();
        assertThat(reloadedB.getPredecessorIds()).isNullOrEmpty();

        // Na wykresie B powinno startować przy projectStart = Anchor_start = 25.04T08:00
        MvcResult result = mockMvc.perform(get("/api/operations/gantt"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode barB = findBarByName(root, "OpB-ASAP");
        assertThat(barB.path("startTime").asText()).isEqualTo("2026-04-25T08:00:00");
    }

    /**
     * Łańcuch ASAP: B (ASAP) po A (stała), C (ASAP) po B.
     * Po usunięciu B, C przejmuje poprzednika B, czyli A → C startuje po A.
     */
    @Test
    void shouldChainAsapInheritanceWhenMiddleNodeDeleted() throws Exception {
        Operation opA = saveStandardOperation("ChainA", "a1a1a1a1-0007-0007-0007-000000000007",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0), 1);

        Operation opB = new Operation();
        opB.setUuid("b2b2b2b2-0008-0008-0008-000000000008");
        opB.setName("ChainB-ASAP");
        opB.setAsap(true);
        opB.setAsapDurationHours(8.0);
        opB.setWorkerCount(1);
        opB.setPredecessorIds(opA.getUuid());
        opB = operationRepository.saveAndFlush(opB);

        Operation opC = new Operation();
        opC.setUuid("c3c3c3c3-0009-0009-0009-000000000009");
        opC.setName("ChainC-ASAP");
        opC.setAsap(true);
        opC.setAsapDurationHours(6.0);
        opC.setWorkerCount(1);
        opC.setPredecessorIds(opB.getUuid());
        opC = operationRepository.saveAndFlush(opC);

        mockMvc.perform(delete("/api/operations/{id}", opB.getId()))
                .andExpect(status().isOk());

        Operation reloadedC = operationRepository.findById(opC.getId()).orElseThrow();
        assertThat(reloadedC.getPredecessorIds()).isEqualTo(opA.getUuid());
    }

    // ============================================================
    // TESTY: Import / Eksport / Dodawanie i usuwanie operacji
    // ============================================================

    /** Eksport pustej bazy zwraca pustą tablicę JSON. */
    @Test
    void shouldExportEmptyArrayWhenNoOperations() throws Exception {
        String response = mockMvc.perform(get("/api/operations/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode exported = objectMapper.readTree(response);
        assertThat(exported).isEmpty();
    }

    /** Import pustego pliku zwraca 400. */
    @Test
    void shouldRejectImportWithEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.json", MediaType.APPLICATION_JSON_VALUE, new byte[0]);

        mockMvc.perform(multipart("/api/operations/import")
                        .file(emptyFile)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pusty")));
    }

    /** Import niepoprawnego JSON zwraca 400. */
    @Test
    void shouldRejectImportWithInvalidJson() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile(
                "file", "bad.json", MediaType.APPLICATION_JSON_VALUE,
                "nie-json".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/operations/import")
                        .file(badFile)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Błąd")));
    }

    /** Import zastępuje istniejące operacje i poprawnie remapuje poprzedniki (UUID wewnętrzne). */
    @Test
    void shouldImportAndReplaceExistingOperationsWithUuidPredecessors() throws Exception {
        // Przed importem jest jedna stara operacja
        saveStandardOperation("Stara", "uuid-stara",
                LocalDateTime.of(2026, 4, 10, 8, 0),
                LocalDateTime.of(2026, 4, 11, 8, 0), 1);
        assertThat(operationRepository.findAll()).hasSize(1);

        String json = """
                [
                  {
                    "id": 1,
                    "name": "Nowa A",
                    "startTime": "2026-04-20T08:00",
                    "endTime": "2026-04-21T08:00",
                    "workerCount": 2
                  },
                  {
                    "id": 2,
                    "name": "Nowa B",
                    "startTime": "2026-04-21T08:00",
                    "endTime": "2026-04-22T08:00",
                    "workerCount": 1,
                    "predecessorIds": "1"
                  }
                ]
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "import.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/operations/import")
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        // Stara operacja zastąpiona — w bazie są tylko 2 nowe
        List<Operation> all = operationRepository.findAll();
        assertThat(all).hasSize(2);

        Operation nowaA = all.stream().filter(op -> "Nowa A".equals(op.getName())).findFirst().orElseThrow();
        Operation nowaB = all.stream().filter(op -> "Nowa B".equals(op.getName())).findFirst().orElseThrow();
        assertThat(nowaB.getPredecessorIds()).isEqualTo(nowaA.getUuid());
    }

    /** Import scalający (merge) nie usuwa istniejących operacji. */
    @Test
    void shouldMergeImportWithoutDeletingExistingOperations() throws Exception {
        saveStandardOperation("Istniejąca", "uuid-istniejaca",
                LocalDateTime.of(2026, 4, 19, 8, 0),
                LocalDateTime.of(2026, 4, 20, 8, 0), 1);

        String json = """
                [
                  {
                    "name": "Importowana",
                    "startTime": "2026-04-22T08:00",
                    "endTime": "2026-04-23T08:00",
                    "workerCount": 3
                  }
                ]
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "merge.json", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/operations/import-merge")
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        // Obie operacje powinny być w bazie
        assertThat(operationRepository.findAll()).hasSize(2);
    }

    /** Eksport po dodaniu kilku operacji zawiera je wszystkie z poprawnymi polami. */
    @Test
    void shouldExportAllFieldsForSavedOperations() throws Exception {
        Operation op = saveStandardOperation("Eksportowana", "uuid-eksport",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 22, 8, 0), 3);
        op.setResources("Żuraw");
        op.setTotalCost(5000.0);
        operationRepository.saveAndFlush(op);

        String response = mockMvc.perform(get("/api/operations/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        JsonNode arr = objectMapper.readTree(response);
        assertThat(arr).hasSize(1);
        JsonNode node = arr.get(0);
        assertThat(node.get("name").asText()).isEqualTo("Eksportowana");
        assertThat(node.get("resources").asText()).isEqualTo("Żuraw");
        assertThat(node.get("totalCost").asDouble()).isEqualTo(5000.0);
        assertThat(node.get("workerCount").asInt()).isEqualTo(3);
    }

    /** Eksportowany JSON można ponownie zaimportować (round-trip). */
    @Test
    void shouldRoundTripExportThenImport() throws Exception {
        saveStandardOperation("RoundTrip A", "uuid-rt-a",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0), 2);
        saveStandardOperation("RoundTrip B", "uuid-rt-b",
                LocalDateTime.of(2026, 4, 21, 8, 0),
                LocalDateTime.of(2026, 4, 22, 8, 0), 1);

        // Eksportuj
        byte[] exported = mockMvc.perform(get("/api/operations/export"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // Wyczyść bazę ręcznie
        operationRepository.deleteAll();
        assertThat(operationRepository.findAll()).isEmpty();

        // Importuj z powrotem
        MockMultipartFile file = new MockMultipartFile(
                "file", "harmonogram.json", MediaType.APPLICATION_JSON_VALUE, exported);

        mockMvc.perform(multipart("/api/operations/import")
                        .file(file)
                        .contentType(MULTIPART_FORM_DATA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        List<Operation> all = operationRepository.findAll();
        assertThat(all).extracting(Operation::getName)
                .containsExactlyInAnyOrder("RoundTrip A", "RoundTrip B");
    }

    // ============================================================
    // TESTY: Dodatkowe walidacje createOperation
    // ============================================================

    /** Operacja ASAP z czasem trwania 9.0h jest akceptowana. */
    @Test
    void shouldAcceptAsapOperationWith9Hours() throws Exception {
        String payload = """
                {
                  "name": "Test 9h",
                  "asap": true,
                  "asapDurationHours": 9.0,
                  "workerCount": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asap").value(true))
                .andExpect(jsonPath("$.asapDurationHours").value(9.0));
    }

    /** Operacja ASAP z czasem trwania 0.5h (minimalny step formularza) jest akceptowana. */
    @Test
    void shouldAcceptAsapOperationWithHalfHour() throws Exception {
        String payload = """
                {
                  "name": "Pół godziny",
                  "asap": true,
                  "asapDurationHours": 0.5,
                  "workerCount": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asapDurationHours").value(0.5));
    }

    /** Operacja ASAP z asapDurationHours = 0 jest odrzucana. */
    @Test
    void shouldRejectAsapOperationWithZeroDuration() throws Exception {
        String payload = """
                {
                  "name": "Zero",
                  "asap": true,
                  "asapDurationHours": 0,
                  "workerCount": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    /** Operacja ASAP z ujemnym czasem trwania jest odrzucana. */
    @Test
    void shouldRejectAsapOperationWithNegativeDuration() throws Exception {
        String payload = """
                {
                  "name": "Ujemna",
                  "asap": true,
                  "asapDurationHours": -1.0,
                  "workerCount": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    /** Operacja z workerCount = 0 jest odrzucana. */
    @Test
    void shouldRejectOperationWithZeroWorkers() throws Exception {
        String payload = """
                {
                  "name": "Bez pracowników",
                  "startTime": "2026-04-20T08:00",
                  "endTime": "2026-04-21T08:00",
                  "workerCount": 0
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pracowników")));
    }

    /** Operacja bez workerCount jest odrzucana. */
    @Test
    void shouldRejectOperationWithMissingWorkerCount() throws Exception {
        String payload = """
                {
                  "name": "Brak pracowników",
                  "startTime": "2026-04-20T08:00",
                  "endTime": "2026-04-21T08:00"
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    /** Operacja standardowa z brakującym startTime jest odrzucana. */
    @Test
    void shouldRejectStandardOperationWithMissingStartTime() throws Exception {
        String payload = """
                {
                  "name": "Bez startu",
                  "endTime": "2026-04-21T08:00",
                  "workerCount": 2
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    /** Operacja ASAP z crashingiem większym lub równym durationDays jest odrzucana. */
    @Test
    void shouldRejectAsapWhenCrashingExceedsDuration() throws Exception {
        // 24h = 1 dzień, maxCrashingDays = 1 → równe durationDays → niedozwolone
        String payload = """
                {
                  "name": "Crash za duży",
                  "asap": true,
                  "asapDurationHours": 24.0,
                  "workerCount": 1,
                  "maxCrashingDays": 1
                }
                """;

        mockMvc.perform(post("/api/operations")
                        .contentType(APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    // ============================================================
    // TESTY: Operacje CRUD / lista
    // ============================================================

    /** GET /api/operations zwraca pustą listę przy czystej bazie. */
    @Test
    void shouldReturnEmptyListWhenNoDatabaseOperations() throws Exception {
        mockMvc.perform(get("/api/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    /** GET /api/operations zwraca wszystkie zapisane operacje. */
    @Test
    void shouldReturnAllOperationsFromDatabase() throws Exception {
        saveStandardOperation("Op1", "uuid-list-1",
                LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0), 1);
        saveStandardOperation("Op2", "uuid-list-2",
                LocalDateTime.of(2026, 4, 21, 8, 0), LocalDateTime.of(2026, 4, 22, 8, 0), 2);

        mockMvc.perform(get("/api/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[*].name",
                        org.hamcrest.Matchers.containsInAnyOrder("Op1", "Op2")));
    }

    /** DELETE /api/operations/{id} dla nieistniejącego ID zwraca 400. */
    @Test
    void shouldReturnBadRequestWhenDeletingNonExistentOperation() throws Exception {
        mockMvc.perform(delete("/api/operations/{id}", Long.MAX_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nie istnieje")));
    }

    /** DELETE /api/operations usuwa wszystkie operacje. */
    @Test
    void shouldDeleteAllOperationsViaEndpoint() throws Exception {
        saveStandardOperation("DelAll1", "uuid-del1",
                LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0), 1);
        saveStandardOperation("DelAll2", "uuid-del2",
                LocalDateTime.of(2026, 4, 21, 8, 0), LocalDateTime.of(2026, 4, 22, 8, 0), 1);

        mockMvc.perform(delete("/api/operations"))
                .andExpect(status().isOk());

        assertThat(operationRepository.findAll()).isEmpty();
    }

    /** Po DELETE /api/operations Gantt zwraca pusty wynik. */
    @Test
    void shouldReturnEmptyGanttAfterDeleteAll() throws Exception {
        saveStandardOperation("TempOp", "uuid-temp",
                LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0), 1);

        mockMvc.perform(delete("/api/operations"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/operations/gantt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars", org.hamcrest.Matchers.hasSize(0)));
    }

    /** Crash = 0 jest dozwolony (reset skrócenia). */
    @Test
    void shouldAllowCrashResetToZero() throws Exception {
        Operation op = saveStandardOperation("Reset", "uuid-reset",
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 24, 8, 0), 1);
        op.setMaxCrashingDays(2);
        op.setCrashedDays(2);
        operationRepository.saveAndFlush(op);

        mockMvc.perform(put("/api/operations/{id}/crash/{days}", op.getId(), 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crashedDays").value(0));
    }

    /** Crash dla nieistniejącej operacji zwraca 400. */
    @Test
    void shouldReturnBadRequestWhenCrashingNonExistentOperation() throws Exception {
        mockMvc.perform(put("/api/operations/{id}/crash/{days}", Long.MAX_VALUE, 1))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nie istnieje")));
    }

    private Operation saveStandardOperation(
            String name,
            String uuid,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int workerCount) {
        Operation operation = new Operation();
        operation.setUuid(uuid);
        operation.setName(name);
        operation.setStartTime(startTime);
        operation.setEndTime(endTime);
        operation.setWorkerCount(workerCount);
        return operationRepository.saveAndFlush(operation);
    }

    private JsonNode findBarByName(JsonNode root, String name) {
        return StreamSupport.stream(root.path("bars").spliterator(), false)
                .filter(node -> name.equals(node.path("name").asText()))
                .findFirst()
                .orElseThrow();
    }
}

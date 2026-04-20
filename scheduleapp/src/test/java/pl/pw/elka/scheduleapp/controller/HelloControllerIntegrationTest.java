package pl.pw.elka.scheduleapp.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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

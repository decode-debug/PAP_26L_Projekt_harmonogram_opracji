package pl.pw.elka.scheduleapp.controller;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;

import pl.pw.elka.scheduleapp.controller.helper.OperationHelper;
import pl.pw.elka.scheduleapp.dto.MergeImportResponseDTO;
import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
@RequestMapping("/api/operations")
public class ImportExportController {

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private OperationHelper helper;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportOperations() {
        try {
            byte[] jsonBytes = helper.createMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(operationRepository.findByUserId(helper.currentUserUuid()));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("harmonogram.json").build());

            return ResponseEntity.ok().headers(headers).body(jsonBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importOperations(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Plik jest pusty.");
        }

        try {
            List<Operation> operations = helper.createMapper().readValue(
                    file.getInputStream(), new TypeReference<List<Operation>>() {});

            Map<Long, String> jsonIdToUuid = new HashMap<>();
            Map<String, String> oldUuidToNew = new HashMap<>();
            for (Operation op : operations) {
                // Zachowaj oryginalny UUID z pliku; generuj nowy tylko gdy brak UUID
                if (op.getUuid() == null || op.getUuid().isBlank()) {
                    op.setUuid(UUID.randomUUID().toString());
                }
                if (op.getId() != null) {
                    jsonIdToUuid.put(op.getId(), op.getUuid());
                }
                oldUuidToNew.put(op.getUuid(), op.getUuid());
            }

            List<Operation> sortedByJsonId = new ArrayList<>(operations);
            sortedByJsonId.sort(Comparator.comparing(op -> op.getId() != null ? op.getId() : Long.MAX_VALUE));
            Map<Long, String> ordinalToUuid = new HashMap<>();
            for (int i = 0; i < sortedByJsonId.size(); i++) {
                ordinalToUuid.put((long) (i + 1), sortedByJsonId.get(i).getUuid());
            }

            for (Operation op : operations) {
                List<String> preds = helper.parsePredecessorValues(op.getPredecessorIds());
                if (preds.isEmpty()) continue;
                List<String> converted = new ArrayList<>();
                for (String val : preds) {
                    if (helper.isUuidFormat(val)) {
                        converted.add(oldUuidToNew.getOrDefault(val, val));
                    } else if (val.matches("\\d+")) {
                        long pid = Long.parseLong(val);
                        String uuid = jsonIdToUuid.containsKey(pid)
                                ? jsonIdToUuid.get(pid)
                                : ordinalToUuid.get(pid);
                        if (uuid != null) converted.add(uuid);
                    }
                }
                op.setPredecessorIds(converted.isEmpty() ? null : String.join(",", converted));
            }

            operationRepository.deleteByUserId(helper.currentUserUuid());
            for (Operation op : operations) {
                op.setId(null);
                op.setUserId(helper.currentUserUuid());
            }
            List<Operation> saved = operationRepository.saveAll(operations);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Błąd parsowania pliku: " + e.getMessage());
        }
    }

    @PostMapping(value = "/import-merge", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importMergeOperations(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Plik jest pusty.");
        }

        try {
            List<Operation> operations = helper.createMapper().readValue(
                    file.getInputStream(), new TypeReference<List<Operation>>() {});

            String userId = helper.currentUserUuid();
            Set<String> existingUuids = new HashSet<>();
            for (Operation existing : operationRepository.findByUserId(userId)) {
                if (existing.getUuid() != null && !existing.getUuid().isBlank()) {
                    existingUuids.add(existing.getUuid());
                }
            }

            List<Operation> operationsToSave = new ArrayList<>();
            List<String> skippedNames = new ArrayList<>();
            Set<String> importedUuids = new HashSet<>();
            Map<Long, String> jsonIdToUuid = new HashMap<>();
            Map<String, String> oldUuidToResolved = new HashMap<>();
            Map<Long, String> ordinalToUuid = new HashMap<>();

            for (int i = 0; i < operations.size(); i++) {
                Operation op = operations.get(i);
                String originalUuid = op.getUuid();
                String resolvedUuid = originalUuid != null && !originalUuid.isBlank()
                        ? originalUuid
                        : UUID.randomUUID().toString();

                if (op.getId() != null) {
                    jsonIdToUuid.put(op.getId(), resolvedUuid);
                }
                ordinalToUuid.put((long) (i + 1), resolvedUuid);
                if (originalUuid != null && !originalUuid.isBlank()) {
                    oldUuidToResolved.put(originalUuid, resolvedUuid);
                }

                if (existingUuids.contains(resolvedUuid) || importedUuids.contains(resolvedUuid)) {
                    skippedNames.add(helper.operationLabel(op));
                    continue;
                }

                op.setUuid(resolvedUuid);
                operationsToSave.add(op);
                importedUuids.add(resolvedUuid);
            }

            for (Operation op : operationsToSave) {
                List<String> preds = helper.parsePredecessorValues(op.getPredecessorIds());
                if (preds.isEmpty()) continue;
                List<String> converted = new ArrayList<>();
                for (String val : preds) {
                    if (helper.isUuidFormat(val)) {
                        converted.add(oldUuidToResolved.getOrDefault(val, val));
                    } else if (val.matches("\\d+")) {
                        long pid = Long.parseLong(val);
                        String uuid = jsonIdToUuid.containsKey(pid)
                                ? jsonIdToUuid.get(pid)
                                : ordinalToUuid.get(pid);
                        if (uuid != null) converted.add(uuid);
                    }
                }
                op.setPredecessorIds(converted.isEmpty() ? null : String.join(",", converted));
            }

            for (Operation op : operationsToSave) {
                op.setId(null);
                op.setUserId(userId);
            }
            List<Operation> saved = operationRepository.saveAll(operationsToSave);
            List<String> addedNames = saved.stream()
                    .map(helper::operationLabel)
                    .toList();

            return ResponseEntity.ok(new MergeImportResponseDTO(
                    saved,
                    addedNames,
                    skippedNames,
                    helper.buildMergeMessage(addedNames, skippedNames)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Błąd parsowania pliku: " + e.getMessage());
        }
    }
}

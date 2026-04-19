package pl.pw.elka.scheduleapp.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import pl.pw.elka.scheduleapp.dto.GanttBarDTO;
import pl.pw.elka.scheduleapp.dto.GanttChartDTO;
import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class HelloController {

    @Autowired
    private OperationRepository operationRepository;

    @GetMapping("/operations")
    public List<Operation> getAllOperations() {
        return operationRepository.findAll();
    }

    @PostMapping("/operations")
    public ResponseEntity<?> createOperation(@RequestBody Operation operation) {
        // Walidacja: nazwa nie może być pusta
        if (operation.getName() == null || operation.getName().isBlank()) {
            return ResponseEntity.badRequest().body("Nazwa operacji nie może być pusta.");
        }

        // Walidacja: daty muszą być podane
        if (operation.getStartTime() == null || operation.getEndTime() == null) {
            return ResponseEntity.badRequest().body("Czas rozpoczęcia i zakończenia muszą być podane.");
        }

        // Walidacja: endTime musi być po startTime
        if (!operation.getEndTime().isAfter(operation.getStartTime())) {
            return ResponseEntity.badRequest().body("Czas zakończenia musi być późniejszy niż czas rozpoczęcia.");
        }

        // Walidacja: workerCount >= 1
        if (operation.getWorkerCount() == null || operation.getWorkerCount() < 1) {
            return ResponseEntity.badRequest().body("Liczba pracowników musi wynosić co najmniej 1.");
        }

        // Walidacja: maxCrashingDays < durationInDays
        long durationDays = operation.getDurationInDays();
        if (operation.getMaxCrashingDays() != null && operation.getMaxCrashingDays() >= durationDays) {
            return ResponseEntity.badRequest().body(
                "Maksymalne skrócenie (" + operation.getMaxCrashingDays() +
                " dni) musi być mniejsze niż czas trwania operacji (" + durationDays + " dni)."
            );
        }

        Operation saved = operationRepository.save(operation);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/operations/{id}/crash/{crashDays}")
    public ResponseEntity<?> crashOperation(@PathVariable Long id, @PathVariable int crashDays) {
        Optional<Operation> opt = operationRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.badRequest().body("Operacja o ID " + id + " nie istnieje.");
        }
        Operation op = opt.get();
        int max = op.getMaxCrashingDays() != null ? op.getMaxCrashingDays() : 0;
        if (crashDays < 0 || crashDays > max) {
            return ResponseEntity.badRequest().body(
                "Liczba dni skracania musi być między 0 a " + max + ".");
        }
        op.setCrashedDays(crashDays);
        operationRepository.save(op);
        return ResponseEntity.ok(op);
    }

    @DeleteMapping("/operations/{id}")
    public ResponseEntity<?> deleteOperation(@PathVariable Long id) {
        Optional<Operation> toDelete = operationRepository.findById(id);
        if (toDelete.isEmpty()) {
            return ResponseEntity.badRequest().body("Operacja o ID " + id + " nie istnieje.");
        }
        Operation deleted = toDelete.get();
        String deletedUuid = deleted.getUuid();

        // Poprzednicy usuwanej operacji — ich UUID trafi do następników
        List<String> inheritedPreds = parsePredecessorValues(deleted.getPredecessorIds());

        operationRepository.deleteById(id);

        // Dla każdej operacji, która miała usuwaną jako poprzednika:
        // 1. usuń UUID usuwanej operacji z jej predecessorIds
        // 2. dodaj (bez duplikatów) poprzedniki usuwanej operacji
        if (deletedUuid != null) {
            List<Operation> remaining = operationRepository.findAll();
            for (Operation op : remaining) {
                List<String> preds = parsePredecessorValues(op.getPredecessorIds());
                if (preds.remove(deletedUuid)) {
                    // Ta operacja była bezpośrednim następnikiem usuwanej — wstrzyknij poprzedniki
                    for (String inherited : inheritedPreds) {
                        if (!preds.contains(inherited)) {
                            preds.add(inherited);
                        }
                    }
                    op.setPredecessorIds(preds.isEmpty() ? null : String.join(",", preds));
                    operationRepository.save(op);
                }
            }
        }
        return ResponseEntity.ok("Usunięto operację o ID " + id);
    }

    @DeleteMapping("/operations")
    public ResponseEntity<?> deleteAllOperations() {
        operationRepository.deleteAll();
        return ResponseEntity.ok("Usunięto wszystkie operacje.");
    }

    // Eksport — backend generuje plik JSON do pobrania
    @GetMapping("/operations/export")
    public ResponseEntity<byte[]> exportOperations() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            byte[] jsonBytes = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(operationRepository.findAll());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDisposition(ContentDisposition.attachment()
                    .filename("harmonogram.json").build());

            return ResponseEntity.ok().headers(headers).body(jsonBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // Import — backend odbiera plik, parsuje JSON i zapisuje do bazy
    @PostMapping(value = "/operations/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importOperations(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Plik jest pusty.");
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            List<Operation> operations = mapper.readValue(
                    file.getInputStream(), new TypeReference<List<Operation>>() {});

            // 1. Przypisz UUID operacjom, które go nie mają
            //    Zbuduj mapy: jsonId → uuid  i  numer porządkowy → uuid
            Map<Long, String> jsonIdToUuid = new HashMap<>();
            for (Operation op : operations) {
                if (op.getUuid() == null || op.getUuid().isBlank()) {
                    op.setUuid(UUID.randomUUID().toString());
                }
                if (op.getId() != null) {
                    jsonIdToUuid.put(op.getId(), op.getUuid());
                }
            }

            // Sortuj wg ID z JSON dla mapowania porządkowego
            List<Operation> sortedByJsonId = new ArrayList<>(operations);
            sortedByJsonId.sort(Comparator.comparing(op -> op.getId() != null ? op.getId() : Long.MAX_VALUE));
            Map<Long, String> ordinalToUuid = new HashMap<>();
            for (int i = 0; i < sortedByJsonId.size(); i++) {
                ordinalToUuid.put((long) (i + 1), sortedByJsonId.get(i).getUuid());
            }

            // 2. Konwertuj predecessorIds z liczb całkowitych na UUID
            for (Operation op : operations) {
                List<String> preds = parsePredecessorValues(op.getPredecessorIds());
                boolean hasIntegerPreds = preds.stream().anyMatch(s -> s.matches("\\d+"));
                if (hasIntegerPreds) {
                    List<String> converted = new ArrayList<>();
                    for (String val : preds) {
                        if (isUuidFormat(val)) {
                            converted.add(val);
                        } else if (val.matches("\\d+")) {
                            long pid = Long.parseLong(val);
                            // Szukaj najpierw w jsonId, potem w numerach porządkowych
                            String uuid = jsonIdToUuid.containsKey(pid)
                                    ? jsonIdToUuid.get(pid)
                                    : ordinalToUuid.get(pid);
                            if (uuid != null) converted.add(uuid);
                        }
                    }
                    op.setPredecessorIds(converted.isEmpty() ? null : String.join(",", converted));
                }
            }

            operationRepository.deleteAll();
            for (Operation op : operations) {
                op.setId(null);
            }
            List<Operation> saved = operationRepository.saveAll(operations);
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Błąd parsowania pliku: " + e.getMessage());
        }
    }

    // ======================== POMOCNICY UUID ========================

    /** Parsuje predecessorIds na listę wartości (UUID lub int) */
    private List<String> parsePredecessorValues(String predecessorIdsStr) {
        List<String> result = new ArrayList<>();
        if (predecessorIdsStr == null || predecessorIdsStr.isBlank()) return result;
        for (String s : predecessorIdsStr.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /** Sprawdza czy string wygląda jak UUID */
    private boolean isUuidFormat(String s) {
        return s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /**
     * Rozwiązuje predecessorIds na listę operacji poprzedzających.
     * Obsługuje: UUID (nowy format), DB ID (zgodny), numery porządkowe (stary format).
     */
    private List<Operation> resolvePredecessorOps(
            String predecessorIdsStr,
            Map<String, Operation> uuidToOp,
            Map<Long, Operation> dbIdToOp,
            Map<Long, Operation> ordinalToOp) {
        List<Operation> result = new ArrayList<>();
        for (String val : parsePredecessorValues(predecessorIdsStr)) {
            Operation found = null;
            if (isUuidFormat(val)) {
                found = uuidToOp.get(val);
            } else if (val.matches("\\d+")) {
                long num = Long.parseLong(val);
                found = dbIdToOp.containsKey(num) ? dbIdToOp.get(num) : ordinalToOp.get(num);
            }
            if (found != null && !result.contains(found)) result.add(found);
        }
        return result;
    }

    /** Buduje mapę uuid → operacja */
    private Map<String, Operation> buildUuidMap(List<Operation> operations) {
        Map<String, Operation> map = new HashMap<>();
        for (Operation op : operations) {
            if (op.getUuid() != null) map.put(op.getUuid(), op);
        }
        return map;
    }

    /** Buduje mapę numer porządkowy (1,2,3...) → operacja, sortując wg ID */
    private Map<Long, Operation> buildOrdinalMap(List<Operation> operations) {
        List<Operation> sorted = new ArrayList<>(operations);
        sorted.sort(Comparator.comparing(Operation::getId));
        Map<Long, Operation> map = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            map.put((long) (i + 1), sorted.get(i));
        }
        return map;
    }

    // ======================== GANTT ENDPOINTS ========================

    // Wykres Gantta — backend oblicza pozycje i kolory pasków
    @GetMapping("/operations/gantt")
    public ResponseEntity<?> getGanttChart() {
        List<Operation> operations = operationRepository.findAll();
        if (operations.isEmpty()) {
            return ResponseEntity.ok(new GanttChartDTO(null, null, 0, List.of()));
        }

        // Sortuj po startTime
        operations.sort(Comparator.comparing(Operation::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // Mapy do rozwiązywania poprzedników
        Map<String, Operation> uuidToOp = buildUuidMap(operations);
        Map<Long, Operation> dbIdToOp = new HashMap<>();
        for (Operation op : operations) dbIdToOp.put(op.getId(), op);
        Map<Long, Operation> ordinalToOp = buildOrdinalMap(operations);

        // Oblicz granice projektu
        LocalDateTime projectStart = operations.stream()
                .map(Operation::getStartTime).filter(t -> t != null)
                .min(Comparator.naturalOrder()).orElse(LocalDateTime.now());
        LocalDateTime projectEnd = operations.stream()
                .map(Operation::getEffectiveEndTime).filter(t -> t != null)
                .max(Comparator.naturalOrder()).orElse(projectStart.plusDays(1));

        double totalHours = Duration.between(projectStart, projectEnd).toMinutes() / 60.0;
        double totalDays = Math.max(totalHours / 24.0, 0.01);

        String[] colors = {
            "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
            "#BA68C8", "#4DD0E1", "#FFD54F", "#F06292",
            "#AED581", "#7986CB", "#FF8A65", "#A1887F"
        };

        List<GanttBarDTO> bars = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            if (op.getStartTime() == null || op.getEffectiveEndTime() == null) continue;

            double startOffsetHours = Duration.between(projectStart, op.getStartTime()).toMinutes() / 60.0;
            double durationHours = Duration.between(op.getStartTime(), op.getEffectiveEndTime()).toMinutes() / 60.0;

            GanttBarDTO bar = new GanttBarDTO();
            bar.setOperationId(op.getId());
            bar.setName(op.getName());
            bar.setStartTime(op.getStartTime());
            bar.setEndTime(op.getEffectiveEndTime());
            bar.setStartOffsetDays(startOffsetHours / 24.0);
            bar.setDurationDays(durationHours / 24.0);
            bar.setWorkerCount(op.getWorkerCount() != null ? op.getWorkerCount() : 0);
            bar.setResources(op.getResources());
            bar.setColor(colors[i % colors.length]);

            List<Long> predDbIds = new ArrayList<>();
            for (Operation pred : resolvePredecessorOps(op.getPredecessorIds(), uuidToOp, dbIdToOp, ordinalToOp)) {
                predDbIds.add(pred.getId());
            }
            bar.setPredecessorIds(predDbIds);

            bars.add(bar);
        }

        return ResponseEntity.ok(new GanttChartDTO(projectStart, projectEnd, totalDays, bars));
    }

    // Wykres Gantta — najpóźniejsze terminy rozpoczęcia (backward pass CPM)
    @GetMapping("/operations/gantt-late")
    public ResponseEntity<?> getGanttChartLate() {
        List<Operation> operations = operationRepository.findAll();
        if (operations.isEmpty()) {
            return ResponseEntity.ok(new GanttChartDTO(null, null, 0, List.of()));
        }

        // Mapy do rozwiązywania poprzedników
        Map<String, Operation> uuidToOp = buildUuidMap(operations);
        Map<Long, Operation> dbIdToOp = new HashMap<>();
        for (Operation op : operations) dbIdToOp.put(op.getId(), op);
        Map<Long, Operation> ordinalToOp = buildOrdinalMap(operations);

        // Oblicz granice projektu (gantt-late)
        LocalDateTime projectStart = operations.stream()
                .map(Operation::getStartTime).filter(t -> t != null)
                .min(Comparator.naturalOrder()).orElse(LocalDateTime.now());
        LocalDateTime projectEnd = operations.stream()
                .map(Operation::getEffectiveEndTime).filter(t -> t != null)
                .max(Comparator.naturalOrder()).orElse(projectStart.plusDays(1));
        Map<String, Set<String>> successors = new HashMap<>();
        for (Operation op : operations) {
            if (op.getUuid() != null) successors.put(op.getUuid(), new HashSet<>());
        }
        for (Operation op : operations) {
            for (Operation pred : resolvePredecessorOps(op.getPredecessorIds(), uuidToOp, dbIdToOp, ordinalToOp)) {
                if (pred.getUuid() != null && op.getUuid() != null) {
                    successors.get(pred.getUuid()).add(op.getUuid());
                }
            }
        }

        // Backward pass — oblicz LF i LS po UUID
        Map<String, LocalDateTime> lateFinish = new HashMap<>();
        Map<String, LocalDateTime> lateStart = new HashMap<>();
        Set<String> computed = new HashSet<>();
        for (Operation op : operations) {
            if (op.getUuid() != null) {
                computeLateFinishByUuid(op.getUuid(), uuidToOp, successors, lateFinish, lateStart, computed, projectEnd);
            }
        }

        double totalHours = Duration.between(projectStart, projectEnd).toMinutes() / 60.0;
        double totalDays = Math.max(totalHours / 24.0, 0.01);

        // Sortuj po LS
        List<Operation> sortedOps = new ArrayList<>(operations);
        sortedOps.sort(Comparator.comparing(op ->
            op.getUuid() != null ? lateStart.getOrDefault(op.getUuid(), projectEnd) : projectEnd));

        String[] colors = {
            "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
            "#BA68C8", "#4DD0E1", "#FFD54F", "#F06292",
            "#AED581", "#7986CB", "#FF8A65", "#A1887F"
        };

        List<GanttBarDTO> bars = new ArrayList<>();
        for (int i = 0; i < sortedOps.size(); i++) {
            Operation op = sortedOps.get(i);
            if (op.getStartTime() == null || op.getEndTime() == null || op.getUuid() == null) continue;

            LocalDateTime ls = lateStart.get(op.getUuid());
            LocalDateTime lf = lateFinish.get(op.getUuid());
            if (ls == null || lf == null) continue;

            double startOffsetHours = Duration.between(projectStart, ls).toMinutes() / 60.0;
            double durationHours = Duration.between(ls, lf).toMinutes() / 60.0;

            GanttBarDTO bar = new GanttBarDTO();
            bar.setOperationId(op.getId());
            bar.setName(op.getName());
            bar.setStartTime(ls);
            bar.setEndTime(lf);
            bar.setStartOffsetDays(startOffsetHours / 24.0);
            bar.setDurationDays(durationHours / 24.0);
            bar.setWorkerCount(op.getWorkerCount() != null ? op.getWorkerCount() : 0);
            bar.setResources(op.getResources());
            bar.setColor(colors[i % colors.length]);

            List<Long> predDbIds = new ArrayList<>();
            for (Operation pred : resolvePredecessorOps(op.getPredecessorIds(), uuidToOp, dbIdToOp, ordinalToOp)) {
                predDbIds.add(pred.getId());
            }
            bar.setPredecessorIds(predDbIds);

            bars.add(bar);
        }

        return ResponseEntity.ok(new GanttChartDTO(projectStart, projectEnd, totalDays, bars));
    }

    /**
     * Backward pass CPM — rekurencyjne obliczanie LF i LS po UUID.
     * LF(i) = min{ LS(j) } dla następników j, lub projectEnd jeśli brak następników.
     */
    private void computeLateFinishByUuid(
            String uuid,
            Map<String, Operation> uuidToOp,
            Map<String, Set<String>> successors,
            Map<String, LocalDateTime> lateFinish,
            Map<String, LocalDateTime> lateStart,
            Set<String> computed,
            LocalDateTime projectEnd) {
        if (computed.contains(uuid)) return;

        Operation op = uuidToOp.get(uuid);
        if (op == null || op.getStartTime() == null || op.getEffectiveEndTime() == null) {
            computed.add(uuid);
            return;
        }

        Duration duration = Duration.between(op.getStartTime(), op.getEffectiveEndTime());
        Set<String> succs = successors.getOrDefault(uuid, Set.of());

        if (succs.isEmpty()) {
            lateFinish.put(uuid, projectEnd);
        } else {
            for (String succUuid : succs) {
                computeLateFinishByUuid(succUuid, uuidToOp, successors, lateFinish, lateStart, computed, projectEnd);
            }
            LocalDateTime minSuccStart = succs.stream()
                    .map(lateStart::get).filter(t -> t != null)
                    .min(Comparator.naturalOrder()).orElse(projectEnd);
            lateFinish.put(uuid, minSuccStart);
        }

        lateStart.put(uuid, lateFinish.get(uuid).minus(duration));
        computed.add(uuid);
    }

    @GetMapping("/hello")
    public String sayHello() {
        return "Serwer działa!";
    }

    @GetMapping("/test-add")
    public String addTestOperation() {
        Operation op = new Operation();
        op.setName("Testowy Montaż");
        op.setStartTime(LocalDateTime.now());
        op.setEndTime(LocalDateTime.now().plusDays(1));
        op.setWorkerCount(2);
        operationRepository.save(op);
        return "Dodano testową operację!";
    }
}
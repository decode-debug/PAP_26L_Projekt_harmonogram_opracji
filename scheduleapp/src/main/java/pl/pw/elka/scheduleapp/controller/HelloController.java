package pl.pw.elka.scheduleapp.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @DeleteMapping("/operations/{id}")
    public ResponseEntity<?> deleteOperation(@PathVariable Long id) {
        if (!operationRepository.existsById(id)) {
            return ResponseEntity.badRequest().body("Operacja o ID " + id + " nie istnieje.");
        }
        operationRepository.deleteById(id);
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

    /**
     * Rozwiązuje predecessorIds — obsługuje zarówno rzeczywiste ID z bazy,
     * jak i numery porządkowe (1, 2, 3...) z importu JSON.
     */
    private List<Long> resolvePredecessorIds(String predecessorIdsStr, Map<Long, Operation> opMap, Map<Long, Long> ordinalToActualId) {
        List<Long> resolved = new ArrayList<>();
        if (predecessorIdsStr == null || predecessorIdsStr.isBlank()) return resolved;
        for (String pidStr : predecessorIdsStr.split(",")) {
            String trimmed = pidStr.trim();
            if (trimmed.isEmpty()) continue;
            long pid = Long.parseLong(trimmed);
            if (opMap.containsKey(pid)) {
                // Rzeczywiste ID z bazy
                resolved.add(pid);
            } else if (ordinalToActualId.containsKey(pid)) {
                // Numer porządkowy z importu → mapuj na rzeczywiste ID
                resolved.add(ordinalToActualId.get(pid));
            }
        }
        return resolved;
    }

    /**
     * Tworzy mapę: numer porządkowy (1, 2, 3...) → rzeczywiste ID operacji.
     * Operacje sortowane po ID (auto-increment zachowuje kolejność importu).
     */
    private Map<Long, Long> buildOrdinalMapping(List<Operation> operations) {
        List<Operation> sorted = new ArrayList<>(operations);
        sorted.sort(Comparator.comparing(Operation::getId));
        Map<Long, Long> ordinalToActualId = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            ordinalToActualId.put((long) (i + 1), sorted.get(i).getId());
        }
        return ordinalToActualId;
    }

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

        // Mapa ID -> operacja i mapowanie porządkowe
        Map<Long, Operation> opMap = new HashMap<>();
        for (Operation op : operations) opMap.put(op.getId(), op);
        Map<Long, Long> ordinalToActualId = buildOrdinalMapping(operations);

        // Oblicz granice projektu
        LocalDateTime projectStart = operations.stream()
                .map(Operation::getStartTime)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime projectEnd = operations.stream()
                .map(Operation::getEndTime)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(projectStart.plusDays(1));

        double totalHours = Duration.between(projectStart, projectEnd).toMinutes() / 60.0;
        double totalDays = totalHours / 24.0;
        if (totalDays < 0.01) totalDays = 1;

        // Paleta kolorów
        String[] colors = {
            "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
            "#BA68C8", "#4DD0E1", "#FFD54F", "#F06292",
            "#AED581", "#7986CB", "#FF8A65", "#A1887F"
        };

        List<GanttBarDTO> bars = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            if (op.getStartTime() == null || op.getEndTime() == null) continue;

            double startOffsetHours = Duration.between(projectStart, op.getStartTime()).toMinutes() / 60.0;
            double durationHours = Duration.between(op.getStartTime(), op.getEndTime()).toMinutes() / 60.0;

            GanttBarDTO bar = new GanttBarDTO();
            bar.setOperationId(op.getId());
            bar.setName(op.getName());
            bar.setStartTime(op.getStartTime());
            bar.setEndTime(op.getEndTime());
            bar.setStartOffsetDays(startOffsetHours / 24.0);
            bar.setDurationDays(durationHours / 24.0);
            bar.setWorkerCount(op.getWorkerCount() != null ? op.getWorkerCount() : 0);
            bar.setResources(op.getResources());
            bar.setColor(colors[i % colors.length]);
            bar.setPredecessorIds(resolvePredecessorIds(op.getPredecessorIds(), opMap, ordinalToActualId));

            bars.add(bar);
        }

        GanttChartDTO chart = new GanttChartDTO(projectStart, projectEnd, totalDays, bars);
        return ResponseEntity.ok(chart);
    }

    // Wykres Gantta — najpóźniejsze terminy rozpoczęcia (backward pass CPM)
    @GetMapping("/operations/gantt-late")
    public ResponseEntity<?> getGanttChartLate() {
        List<Operation> operations = operationRepository.findAll();
        if (operations.isEmpty()) {
            return ResponseEntity.ok(new GanttChartDTO(null, null, 0, List.of()));
        }

        // Mapa ID -> operacja i mapowanie porządkowe
        Map<Long, Operation> opMap = new HashMap<>();
        for (Operation op : operations) {
            opMap.put(op.getId(), op);
        }
        Map<Long, Long> ordinalToActualId = buildOrdinalMapping(operations);

        // Oblicz granice projektu (z najwcześniejszych terminów)
        LocalDateTime projectStart = operations.stream()
                .map(Operation::getStartTime)
                .filter(t -> t != null)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.now());

        LocalDateTime projectEnd = operations.stream()
                .map(Operation::getEndTime)
                .filter(t -> t != null)
                .max(Comparator.naturalOrder())
                .orElse(projectStart.plusDays(1));

        // Zbuduj graf następników (odwrotność predecessorIds) z rozwiązanymi ID
        Map<Long, Set<Long>> successors = new HashMap<>();
        for (Operation op : operations) {
            successors.put(op.getId(), new HashSet<>());
        }
        for (Operation op : operations) {
            List<Long> resolvedPreds = resolvePredecessorIds(op.getPredecessorIds(), opMap, ordinalToActualId);
            for (Long predId : resolvedPreds) {
                successors.get(predId).add(op.getId());
            }
        }

        // Backward pass: oblicz najpóźniejsze czasy zakończenia (LF) i rozpoczęcia (LS)
        // LF(i) = min{ LS(j) : j jest następnikiem i }, lub projectEnd jeśli brak następników
        // LS(i) = LF(i) - duration(i)
        Map<Long, LocalDateTime> lateFinish = new HashMap<>();
        Map<Long, LocalDateTime> lateStart = new HashMap<>();

        // Przetwarzamy operacje w odwrotnej kolejności topologicznej (rekurencja z memoizacją)
        Set<Long> computed = new HashSet<>();
        for (Operation op : operations) {
            computeLateFinish(op.getId(), opMap, successors, lateFinish, lateStart, computed, projectEnd);
        }

        // Teraz budujemy paski Gantta z nowymi czasami
        double totalHours = Duration.between(projectStart, projectEnd).toMinutes() / 60.0;
        double totalDays = totalHours / 24.0;
        if (totalDays < 0.01) totalDays = 1;

        // Sortujemy operacje po ich nowym LS (od najwcześniejszego)
        List<Operation> sortedOps = new ArrayList<>(operations);
        sortedOps.sort(Comparator.comparing(op -> lateStart.getOrDefault(op.getId(), projectEnd)));

        // Paleta kolorów
        String[] colors = {
            "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
            "#BA68C8", "#4DD0E1", "#FFD54F", "#F06292",
            "#AED581", "#7986CB", "#FF8A65", "#A1887F"
        };

        List<GanttBarDTO> bars = new ArrayList<>();
        for (int i = 0; i < sortedOps.size(); i++) {
            Operation op = sortedOps.get(i);
            if (op.getStartTime() == null || op.getEndTime() == null) continue;

            LocalDateTime ls = lateStart.get(op.getId());
            LocalDateTime lf = lateFinish.get(op.getId());
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
            bar.setPredecessorIds(resolvePredecessorIds(op.getPredecessorIds(), opMap, ordinalToActualId));

            bars.add(bar);
        }

        GanttChartDTO chart = new GanttChartDTO(projectStart, projectEnd, totalDays, bars);
        return ResponseEntity.ok(chart);
    }

    /**
     * Rekurencyjne obliczanie LF i LS operacji (backward pass).
     */
    private void computeLateFinish(Long opId,
                                    Map<Long, Operation> opMap,
                                    Map<Long, Set<Long>> successors,
                                    Map<Long, LocalDateTime> lateFinish,
                                    Map<Long, LocalDateTime> lateStart,
                                    Set<Long> computed,
                                    LocalDateTime projectEnd) {
        if (computed.contains(opId)) return;

        Operation op = opMap.get(opId);
        if (op == null || op.getStartTime() == null || op.getEndTime() == null) {
            computed.add(opId);
            return;
        }

        // Czas trwania operacji (zachowujemy oryginalny)
        Duration duration = Duration.between(op.getStartTime(), op.getEndTime());

        Set<Long> succs = successors.getOrDefault(opId, Set.of());

        if (succs.isEmpty()) {
            // Brak następników — LF = koniec projektu
            lateFinish.put(opId, projectEnd);
        } else {
            // Najpierw oblicz LS wszystkich następników
            for (Long succId : succs) {
                computeLateFinish(succId, opMap, successors, lateFinish, lateStart, computed, projectEnd);
            }
            // LF(i) = min{ LS(j) } dla następników j
            LocalDateTime minSuccStart = succs.stream()
                    .map(lateStart::get)
                    .filter(t -> t != null)
                    .min(Comparator.naturalOrder())
                    .orElse(projectEnd);
            lateFinish.put(opId, minSuccStart);
        }

        // LS(i) = LF(i) - duration
        lateStart.put(opId, lateFinish.get(opId).minus(duration));
        computed.add(opId);
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
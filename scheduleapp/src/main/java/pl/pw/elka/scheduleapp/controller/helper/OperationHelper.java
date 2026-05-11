package pl.pw.elka.scheduleapp.controller.helper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import pl.pw.elka.scheduleapp.dto.GanttBarDTO;
import pl.pw.elka.scheduleapp.model.Operation;

@Component
public class OperationHelper {

    public static final String[] GANTT_COLORS = {
        "#4FC3F7", "#81C784", "#FFB74D", "#E57373",
        "#BA68C8", "#4DD0E1", "#FFD54F", "#F06292",
        "#AED581", "#7986CB", "#FF8A65", "#A1887F"
    };

    /** Returns the UUID of the currently authenticated user from the JWT. */
    public String currentUserUuid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getPrincipal() == null) {
            return null;
        }
        return (String) authentication.getPrincipal();
    }

    public ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    /** Parsuje predecessorIds na listę wartości (UUID lub int) */
    public List<String> parsePredecessorValues(String predecessorIdsStr) {
        List<String> result = new ArrayList<>();
        if (predecessorIdsStr == null || predecessorIdsStr.isBlank()) return result;
        for (String s : predecessorIdsStr.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    /** Sprawdza czy string wygląda jak UUID */
    public boolean isUuidFormat(String s) {
        return s.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /**
     * Rozwiązuje predecessorIds na listę operacji poprzedzających.
     * Obsługuje: UUID, DB ID, numery porządkowe.
     */
    public List<Operation> resolvePredecessorOps(
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
    public Map<String, Operation> buildUuidMap(List<Operation> operations) {
        Map<String, Operation> map = new HashMap<>();
        for (Operation op : operations) {
            if (op.getUuid() != null) map.put(op.getUuid(), op);
        }
        return map;
    }

    /** Buduje mapę id → operacja */
    public Map<Long, Operation> buildDbIdMap(List<Operation> operations) {
        Map<Long, Operation> map = new HashMap<>();
        for (Operation op : operations) map.put(op.getId(), op);
        return map;
    }

    /** Buduje numer porządkowy (1,2,3...) → operacja, sortując wg ID */
    public Map<Long, Operation> buildOrdinalMap(List<Operation> operations) {
        List<Operation> sorted = new ArrayList<>(operations);
        sorted.sort(Comparator.comparing(Operation::getId));
        Map<Long, Operation> map = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            map.put((long) (i + 1), sorted.get(i));
        }
        return map;
    }

    public LocalDateTime computeProjectStart(List<Operation> operations) {
        return operations.stream()
                .filter(op -> !Boolean.TRUE.equals(op.getAsap()))
                .map(Operation::getStartTime).filter(t -> t != null)
                .min(Comparator.naturalOrder()).orElse(LocalDateTime.now());
    }

    public LocalDateTime computeProjectEnd(List<Operation> operations,
            LocalDateTime projectStart, Map<String, LocalDateTime[]> asapTimes) {
        LocalDateTime projectEnd = projectStart.plusDays(1);
        for (Operation op : operations) {
            LocalDateTime end;
            if (Boolean.TRUE.equals(op.getAsap()) && op.getUuid() != null) {
                LocalDateTime[] times = asapTimes.get(op.getUuid());
                end = times != null ? times[1] : null;
            } else {
                end = op.getEffectiveEndTime();
            }
            if (end != null && end.isAfter(projectEnd)) projectEnd = end;
        }
        return projectEnd;
    }

    public GanttBarDTO buildGanttBar(Operation op, LocalDateTime startT, LocalDateTime endT,
            LocalDateTime projectStart, String color,
            Map<String, Operation> uuidToOp, Map<Long, Operation> dbIdToOp, Map<Long, Operation> ordinalToOp) {
        double startOffsetHours = Duration.between(projectStart, startT).toMinutes() / 60.0;
        double durationHours = Duration.between(startT, endT).toMinutes() / 60.0;

        GanttBarDTO bar = new GanttBarDTO();
        bar.setOperationId(op.getId());
        bar.setName(op.getName());
        bar.setStartTime(startT);
        bar.setEndTime(endT);
        bar.setStartOffsetDays(startOffsetHours / 24.0);
        bar.setDurationDays(durationHours / 24.0);
        bar.setWorkerCount(op.getWorkerCount() != null ? op.getWorkerCount() : 0);
        bar.setResources(op.getResources());
        bar.setColor(color);

        List<Long> predDbIds = new ArrayList<>();
        for (Operation pred : resolvePredecessorOps(op.getPredecessorIds(), uuidToOp, dbIdToOp, ordinalToOp)) {
            predDbIds.add(pred.getId());
        }
        bar.setPredecessorIds(predDbIds);
        return bar;
    }

    /**
     * Oblicza efektywne czasy startów i zakończeń operacji ASAP.
     * Zwraca mapę uuid → [effectiveStart, effectiveEnd].
     */
    public Map<String, LocalDateTime[]> computeAsapTimes(
            List<Operation> operations,
            Map<String, Operation> uuidToOp,
            Map<Long, Operation> dbIdToOp,
            Map<Long, Operation> ordinalToOp,
            LocalDateTime projectStart) {

        Map<String, LocalDateTime[]> result = new HashMap<>();
        Set<String> resolved = new HashSet<>();

        for (Operation op : operations) {
            if (!Boolean.TRUE.equals(op.getAsap()) && op.getUuid() != null
                    && op.getStartTime() != null && op.getEffectiveEndTime() != null) {
                result.put(op.getUuid(), new LocalDateTime[]{op.getStartTime(), op.getEffectiveEndTime()});
                resolved.add(op.getUuid());
            }
        }

        List<Operation> asapOps = new ArrayList<>();
        for (Operation op : operations) {
            if (Boolean.TRUE.equals(op.getAsap()) && op.getUuid() != null) {
                asapOps.add(op);
            }
        }

        boolean progress = true;
        while (progress && !asapOps.isEmpty()) {
            progress = false;
            Iterator<Operation> it = asapOps.iterator();
            while (it.hasNext()) {
                Operation op = it.next();
                List<Operation> preds = resolvePredecessorOps(op.getPredecessorIds(), uuidToOp, dbIdToOp, ordinalToOp);

                boolean allResolved = true;
                for (Operation pred : preds) {
                    if (pred.getUuid() != null && !resolved.contains(pred.getUuid())) {
                        allResolved = false;
                        break;
                    }
                }

                if (allResolved) {
                    LocalDateTime start = projectStart;
                    for (Operation pred : preds) {
                        if (pred.getUuid() != null && result.containsKey(pred.getUuid())) {
                            LocalDateTime predEnd = result.get(pred.getUuid())[1];
                            if (predEnd != null && predEnd.isAfter(start)) {
                                start = predEnd;
                            }
                        }
                    }
                    double durHours = op.getAsapDurationHours() != null ? op.getAsapDurationHours() : 0;
                    long durMinutes = (long) (durHours * 60);
                    int crashed = op.getCrashedDays() != null ? op.getCrashedDays() : 0;
                    LocalDateTime end = start.plusMinutes(durMinutes).minusDays(crashed);
                    result.put(op.getUuid(), new LocalDateTime[]{start, end});
                    resolved.add(op.getUuid());
                    it.remove();
                    progress = true;
                }
            }
        }
        return result;
    }

    /**
     * Backward pass CPM — rekurencyjne obliczanie LF i LS po UUID.
     */
    public void computeLateFinishByUuid(
            String uuid,
            Map<String, Operation> uuidToOp,
            Map<String, Set<String>> successors,
            Map<String, LocalDateTime> lateFinish,
            Map<String, LocalDateTime> lateStart,
            Set<String> computed,
            LocalDateTime projectEnd,
            Map<String, LocalDateTime[]> asapTimes) {
        if (computed.contains(uuid)) return;

        Operation op = uuidToOp.get(uuid);
        if (op == null) { computed.add(uuid); return; }

        Duration duration;
        if (Boolean.TRUE.equals(op.getAsap())) {
            LocalDateTime[] times = asapTimes.get(uuid);
            if (times == null) { computed.add(uuid); return; }
            duration = Duration.between(times[0], times[1]);
        } else {
            if (op.getStartTime() == null || op.getEffectiveEndTime() == null) { computed.add(uuid); return; }
            duration = Duration.between(op.getStartTime(), op.getEffectiveEndTime());
        }

        Set<String> succs = successors.getOrDefault(uuid, Set.of());

        if (succs.isEmpty()) {
            lateFinish.put(uuid, projectEnd);
        } else {
            for (String succUuid : succs) {
                computeLateFinishByUuid(succUuid, uuidToOp, successors, lateFinish, lateStart, computed, projectEnd, asapTimes);
            }
            LocalDateTime minSuccStart = succs.stream()
                    .map(lateStart::get).filter(t -> t != null)
                    .min(Comparator.naturalOrder()).orElse(projectEnd);
            lateFinish.put(uuid, minSuccStart);
        }

        lateStart.put(uuid, lateFinish.get(uuid).minus(duration));
        computed.add(uuid);
    }

    public String operationLabel(Operation op) {
        if (op.getName() != null && !op.getName().isBlank()) {
            return op.getName();
        }
        if (op.getUuid() != null && !op.getUuid().isBlank()) {
            return op.getUuid();
        }
        return "operacja bez nazwy";
    }

    public String buildMergeMessage(List<String> addedNames, List<String> skippedNames) {
        List<String> parts = new ArrayList<>();
        if (!addedNames.isEmpty()) {
            parts.add("Dodano operacje: " + String.join(", ", addedNames) + ".");
        }
        if (!skippedNames.isEmpty()) {
            parts.add("Pominięto już istniejące operacje: " + String.join(", ", skippedNames) + ".");
        }
        if (parts.isEmpty()) {
            return "Nie znaleziono operacji do dodania.";
        }
        return String.join(" ", parts);
    }
}

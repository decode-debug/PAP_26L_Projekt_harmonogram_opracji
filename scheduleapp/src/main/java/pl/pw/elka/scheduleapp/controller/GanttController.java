package pl.pw.elka.scheduleapp.controller;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.pw.elka.scheduleapp.controller.helper.OperationHelper;
import pl.pw.elka.scheduleapp.dto.GanttBarDTO;
import pl.pw.elka.scheduleapp.dto.GanttChartDTO;
import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
@RequestMapping("/api/operations")
public class GanttController {

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private OperationHelper helper;

    @GetMapping("/gantt")
    public ResponseEntity<?> getGanttChart() {
        List<Operation> operations = operationRepository.findByUserId(helper.currentUserUuid());
        if (operations.isEmpty()) {
            return ResponseEntity.ok(new GanttChartDTO(null, null, 0, List.of()));
        }

        operations.sort(Comparator.comparing(Operation::getStartTime,
                Comparator.nullsLast(Comparator.naturalOrder())));

        Map<String, Operation> uuidToOp = helper.buildUuidMap(operations);
        Map<Long, Operation> dbIdToOp = helper.buildDbIdMap(operations);
        Map<Long, Operation> ordinalToOp = helper.buildOrdinalMap(operations);

        LocalDateTime projectStart = helper.computeProjectStart(operations);
        Map<String, LocalDateTime[]> asapTimes = helper.computeAsapTimes(operations, uuidToOp, dbIdToOp, ordinalToOp, projectStart);
        LocalDateTime projectEnd = helper.computeProjectEnd(operations, projectStart, asapTimes);

        double totalDays = Math.max(Duration.between(projectStart, projectEnd).toMinutes() / 60.0 / 24.0, 0.01);

        List<GanttBarDTO> bars = new ArrayList<>();
        for (int i = 0; i < operations.size(); i++) {
            Operation op = operations.get(i);
            LocalDateTime startT, endT;
            if (Boolean.TRUE.equals(op.getAsap()) && op.getUuid() != null) {
                LocalDateTime[] times = asapTimes.get(op.getUuid());
                if (times == null) continue;
                startT = times[0];
                endT = times[1];
            } else {
                if (op.getStartTime() == null || op.getEffectiveEndTime() == null) continue;
                startT = op.getStartTime();
                endT = op.getEffectiveEndTime();
            }
            bars.add(helper.buildGanttBar(op, startT, endT, projectStart, OperationHelper.GANTT_COLORS[i % OperationHelper.GANTT_COLORS.length], uuidToOp, dbIdToOp, ordinalToOp));
        }

        return ResponseEntity.ok(new GanttChartDTO(projectStart, projectEnd, totalDays, bars));
    }

    @GetMapping("/gantt-late")
    public ResponseEntity<?> getGanttChartLate() {
        List<Operation> operations = operationRepository.findByUserId(helper.currentUserUuid());
        if (operations.isEmpty()) {
            return ResponseEntity.ok(new GanttChartDTO(null, null, 0, List.of()));
        }

        Map<String, Operation> uuidToOp = helper.buildUuidMap(operations);
        Map<Long, Operation> dbIdToOp = helper.buildDbIdMap(operations);
        Map<Long, Operation> ordinalToOp = helper.buildOrdinalMap(operations);

        LocalDateTime projectStart = helper.computeProjectStart(operations);
        Map<String, LocalDateTime[]> asapTimes = helper.computeAsapTimes(operations, uuidToOp, dbIdToOp, ordinalToOp, projectStart);
        final LocalDateTime projectEnd = helper.computeProjectEnd(operations, projectStart, asapTimes);

        Map<String, Set<String>> successors = new HashMap<>();
        for (Operation op : operations) {
            if (op.getUuid() != null) successors.put(op.getUuid(), new HashSet<>());
        }
        for (Operation op : operations) {
            for (Operation pred : helper.resolvePredecessorOps(op.getPredecessorIds(), uuidToOp, dbIdToOp, ordinalToOp)) {
                if (pred.getUuid() != null && op.getUuid() != null) {
                    successors.get(pred.getUuid()).add(op.getUuid());
                }
            }
        }

        Map<String, LocalDateTime> lateFinish = new HashMap<>();
        Map<String, LocalDateTime> lateStart = new HashMap<>();
        Set<String> computed = new HashSet<>();
        for (Operation op : operations) {
            if (op.getUuid() != null) {
                helper.computeLateFinishByUuid(op.getUuid(), uuidToOp, successors, lateFinish, lateStart, computed, projectEnd, asapTimes);
            }
        }

        double totalDays = Math.max(Duration.between(projectStart, projectEnd).toMinutes() / 60.0 / 24.0, 0.01);

        List<Operation> sortedOps = new ArrayList<>(operations);
        sortedOps.sort(Comparator.comparing(op ->
            op.getUuid() != null ? lateStart.getOrDefault(op.getUuid(), projectEnd) : projectEnd));

        List<GanttBarDTO> bars = new ArrayList<>();
        for (int i = 0; i < sortedOps.size(); i++) {
            Operation op = sortedOps.get(i);
            if (op.getUuid() == null) continue;

            LocalDateTime ls = lateStart.get(op.getUuid());
            LocalDateTime lf = lateFinish.get(op.getUuid());
            if (ls == null || lf == null) continue;

            bars.add(helper.buildGanttBar(op, ls, lf, projectStart, OperationHelper.GANTT_COLORS[i % OperationHelper.GANTT_COLORS.length], uuidToOp, dbIdToOp, ordinalToOp));
        }

        return ResponseEntity.ok(new GanttChartDTO(projectStart, projectEnd, totalDays, bars));
    }
}

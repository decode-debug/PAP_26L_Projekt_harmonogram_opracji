package pl.pw.elka.scheduleapp.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.pw.elka.scheduleapp.controller.helper.OperationHelper;
import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
@RequestMapping("/api")
public class OperationController {

    @Autowired
    private OperationRepository operationRepository;

    @Autowired
    private OperationHelper helper;

    @GetMapping("/operations")
    public List<Operation> getAllOperations() {
        return operationRepository.findByUserId(helper.currentUserUuid());
    }

    @PostMapping("/operations")
    public ResponseEntity<?> createOperation(@RequestBody Operation operation) {
        if (operation.getName() == null || operation.getName().isBlank()) {
            return ResponseEntity.badRequest().body("Nazwa operacji nie może być pusta.");
        }

        if (Boolean.TRUE.equals(operation.getAsap())) {
            if (operation.getAsapDurationHours() == null || operation.getAsapDurationHours() <= 0) {
                return ResponseEntity.badRequest().body("Czas trwania operacji ASAP musi być większy od 0.");
            }
            operation.setStartTime(null);
            operation.setEndTime(null);
            double durationDays = operation.getAsapDurationHours() / 24.0;
            if (operation.getMaxCrashingDays() != null && operation.getMaxCrashingDays() >= durationDays) {
                return ResponseEntity.badRequest().body(
                    "Maksymalne skrócenie (" + operation.getMaxCrashingDays() +
                    " dni) musi być mniejsze niż czas trwania operacji (" +
                    String.format("%.2f", durationDays) + " dni)."
                );
            }
        } else {
            if (operation.getStartTime() == null || operation.getEndTime() == null) {
                return ResponseEntity.badRequest().body("Czas rozpoczęcia i zakończenia muszą być podane.");
            }
            if (!operation.getEndTime().isAfter(operation.getStartTime())) {
                return ResponseEntity.badRequest().body("Czas zakończenia musi być późniejszy niż czas rozpoczęcia.");
            }
            long durationDays = operation.getDurationInDays();
            if (operation.getMaxCrashingDays() != null && operation.getMaxCrashingDays() >= durationDays) {
                return ResponseEntity.badRequest().body(
                    "Maksymalne skrócenie (" + operation.getMaxCrashingDays() +
                    " dni) musi być mniejsze niż czas trwania operacji (" + durationDays + " dni)."
                );
            }
        }

        if (operation.getWorkerCount() == null || operation.getWorkerCount() < 1) {
            return ResponseEntity.badRequest().body("Liczba pracowników musi wynosić co najmniej 1.");
        }

        operation.setUserId(helper.currentUserUuid());
        Operation saved = operationRepository.save(operation);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/operations/{id}/crash/{crashDays}")
    public ResponseEntity<?> crashOperation(@PathVariable Long id, @PathVariable int crashDays) {
        Optional<Operation> opt = operationRepository.findByIdAndUserId(id, helper.currentUserUuid());
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
        String userId = helper.currentUserUuid();
        Optional<Operation> toDelete = operationRepository.findByIdAndUserId(id, userId);
        if (toDelete.isEmpty()) {
            return ResponseEntity.badRequest().body("Operacja o ID " + id + " nie istnieje.");
        }
        Operation deleted = toDelete.get();
        String deletedUuid = deleted.getUuid();

        List<String> inheritedPreds = helper.parsePredecessorValues(deleted.getPredecessorIds());

        operationRepository.deleteById(id);

        if (deletedUuid != null) {
            List<Operation> remaining = operationRepository.findByUserId(userId);
            for (Operation op : remaining) {
                List<String> preds = helper.parsePredecessorValues(op.getPredecessorIds());
                if (preds.remove(deletedUuid)) {
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
        operationRepository.deleteByUserId(helper.currentUserUuid());
        return ResponseEntity.ok("Usunięto wszystkie operacje.");
    }
}

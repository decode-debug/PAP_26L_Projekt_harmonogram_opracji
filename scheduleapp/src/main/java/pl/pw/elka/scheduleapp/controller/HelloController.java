package pl.pw.elka.scheduleapp.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
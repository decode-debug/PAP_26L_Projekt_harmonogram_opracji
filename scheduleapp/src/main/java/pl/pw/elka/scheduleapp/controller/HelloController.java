package pl.pw.elka.scheduleapp.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
@CrossOrigin
public class HelloController {

    @Autowired
    private OperationRepository operationRepository;

    @GetMapping("/hello")
    public String sayHello() {
        return "Serwer działa! Spróbuj wejść na /test-add aby dodać dane.";
    }

    @GetMapping("/test-add")
    public String addTestOperation() {
    Operation op = new Operation();
    op.setName("Montaż końcowy");
    op.setStartTime(LocalDateTime.now());
    op.setEndTime(LocalDateTime.now().plusDays(3)); // Operacja trwa 3 dni
    op.setWorkerCount(5);
    op.setResources("Hala A, Suwnica");
    op.setTotalCost(1500.0);
    op.setCrashingCostPerDay(200.0); // Skrócenie o dzień kosztuje 200 PLN

    operationRepository.save(op);
    return "Dodano operację z zasobami. Czas trwania: " + op.getDurationInHours() + "h";
    }

    @GetMapping("/operations")
    public List<Operation> getAllOperations() {
        return operationRepository.findAll(); // Zwróci listę wszystkich operacji jako JSON
    }
}
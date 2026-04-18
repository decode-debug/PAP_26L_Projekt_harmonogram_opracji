package pl.pw.elka.scheduleapp.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
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
        op.setName("Montaż silnika");
        op.setStartTime(LocalDateTime.now());
        op.setEndTime(LocalDateTime.now().plusHours(2));
        op.setCost(500.0);
        op.setDurationInMinutes(120);

        operationRepository.save(op); // To zapisuje do bazy H2!
        return "Dodano operację: " + op.getName();
    }

    @GetMapping("/operations")
    public List<Operation> getAllOperations() {
        return operationRepository.findAll(); // Zwróci listę wszystkich operacji jako JSON
    }
}
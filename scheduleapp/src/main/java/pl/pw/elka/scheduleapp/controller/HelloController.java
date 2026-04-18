package pl.pw.elka.scheduleapp.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin; // Zmienione na gwiazdkę, by mieć PostMapping i RequestBody
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pl.pw.elka.scheduleapp.model.Operation;
import pl.pw.elka.scheduleapp.repository.OperationRepository;

@RestController
@RequestMapping("/api") // Wszystkie adresy będą mieć teraz przedrostek /api
@CrossOrigin(origins = "*") // Pozwala Reactowi na dostęp
public class HelloController {

    @Autowired
    private OperationRepository operationRepository;

    // Pobieranie wszystkich operacji (wywoływane przez React przy starcie)
    @GetMapping("/operations")
    public List<Operation> getAllOperations() {
        return operationRepository.findAll();
    }

    // Odbieranie danych z formularza React
    @PostMapping("/operations")
    public Operation createOperation(@RequestBody Operation operation) {
        // @RequestBody mówi Springowi: "weź JSON-a z Reacta i zamień go na obiekt Operation"
        return operationRepository.save(operation);
    }

    // Twoje stare metody testowe (teraz pod adresem /api/hello i /api/test-add)
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
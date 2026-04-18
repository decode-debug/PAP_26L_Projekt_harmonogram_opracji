package pl.pw.elka.scheduleapp.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/hello")
    public String sayHello() {
        return "Serwer Spring Boot działa na Javie 25! Projekt harmonogramu startuje.";
    }
}
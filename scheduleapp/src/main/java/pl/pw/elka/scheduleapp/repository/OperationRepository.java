package pl.pw.elka.scheduleapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pl.pw.elka.scheduleapp.model.Operation;

@Repository
public interface OperationRepository extends JpaRepository<Operation, Long> {
    // Dzięki dziedziczeniu po JpaRepository masz od razu metody:
    // save(operation) - zapisz/edytuj
    // findAll() - pobierz wszystkie
    // deleteById(id) - usuń
    // findById(id) - znajdź jedną
}
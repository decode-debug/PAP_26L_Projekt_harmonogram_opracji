package pl.pw.elka.scheduleapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import pl.pw.elka.scheduleapp.model.Operation;

@Repository
public interface OperationRepository extends JpaRepository<Operation, Long> {
    // Tu na razie nic nie musisz pisać - Spring sam wygeneruje metody save(), findAll(), itp.
}
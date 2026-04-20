package pl.pw.elka.scheduleapp.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import pl.pw.elka.scheduleapp.model.Operation;

@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:operation-repository-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class OperationRepositoryTest {

    @Autowired
    private OperationRepository operationRepository;

    @Test
    void shouldPersistOperationAndGenerateUuidOnInsert() {
        Operation operation = new Operation();
        operation.setName("Cięcie");
        operation.setStartTime(LocalDateTime.of(2026, 4, 19, 8, 0));
        operation.setEndTime(LocalDateTime.of(2026, 4, 20, 8, 0));
        operation.setWorkerCount(3);
        operation.setResources("Piła");

        Operation saved = operationRepository.saveAndFlush(operation);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUuid()).isNotBlank();
    }

    @Test
    void shouldPersistPredecessorIdsAndCrashingFields() {
        Operation predecessor = new Operation();
        predecessor.setName("Przygotowanie");
        predecessor.setStartTime(LocalDateTime.of(2026, 4, 18, 8, 0));
        predecessor.setEndTime(LocalDateTime.of(2026, 4, 19, 8, 0));
        predecessor.setWorkerCount(1);
        predecessor = operationRepository.saveAndFlush(predecessor);

        Operation operation = new Operation();
        operation.setName("Montaż");
        operation.setStartTime(LocalDateTime.of(2026, 4, 19, 9, 0));
        operation.setEndTime(LocalDateTime.of(2026, 4, 21, 9, 0));
        operation.setWorkerCount(2);
        operation.setPredecessorIds(predecessor.getUuid());
        operation.setMaxCrashingDays(1);
        operation.setCrashedDays(1);

        Operation saved = operationRepository.saveAndFlush(operation);
        Operation reloaded = operationRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getPredecessorIds()).isEqualTo(predecessor.getUuid());
        assertThat(reloaded.getCrashedDays()).isEqualTo(1);
        assertThat(reloaded.getEffectiveEndTime())
                .isEqualTo(LocalDateTime.of(2026, 4, 20, 9, 0));
    }

    // ======= DODATKOWE TESTY CRUD =======

    /** Odczyt po ID zwraca pusty Optional dla nieistniejącego rekordu. */
    @Test
    void shouldReturnEmptyOptionalForUnknownId() {
        Optional<Operation> result = operationRepository.findById(Long.MAX_VALUE);

        assertThat(result).isEmpty();
    }

    /** findAll zwraca wszystkie zapisane operacje. */
    @Test
    void shouldReturnAllSavedOperations() {
        Operation op1 = buildFixed("Alpha", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0));
        Operation op2 = buildFixed("Beta",  LocalDateTime.of(2026, 4, 21, 8, 0), LocalDateTime.of(2026, 4, 22, 8, 0));
        operationRepository.saveAll(List.of(op1, op2));

        List<Operation> all = operationRepository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(Operation::getName).containsExactlyInAnyOrder("Alpha", "Beta");
    }

    /** Aktualizacja istniejącej operacji jest trwała po saveAndFlush. */
    @Test
    void shouldUpdateExistingOperation() {
        Operation op = buildFixed("Pierwotna", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0));
        op = operationRepository.saveAndFlush(op);

        op.setName("Zaktualizowana");
        op.setWorkerCount(10);
        operationRepository.saveAndFlush(op);

        Operation reloaded = operationRepository.findById(op.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Zaktualizowana");
        assertThat(reloaded.getWorkerCount()).isEqualTo(10);
    }

    /** Usunięcie po ID powoduje, że findById zwraca puste. */
    @Test
    void shouldDeleteOperationById() {
        Operation op = buildFixed("DoUsunięcia", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0));
        op = operationRepository.saveAndFlush(op);
        Long id = op.getId();

        operationRepository.deleteById(id);
        operationRepository.flush();

        assertThat(operationRepository.findById(id)).isEmpty();
    }

    /** Usunięcie wszystkich operacji czyści tabelę. */
    @Test
    void shouldDeleteAllOperations() {
        operationRepository.save(buildFixed("A", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0)));
        operationRepository.save(buildFixed("B", LocalDateTime.of(2026, 4, 21, 8, 0), LocalDateTime.of(2026, 4, 22, 8, 0)));

        operationRepository.deleteAll();

        assertThat(operationRepository.findAll()).isEmpty();
    }

    /** Operacja ASAP jest persystowana razem z asapDurationHours. */
    @Test
    void shouldPersistAsapOperation() {
        Operation op = new Operation();
        op.setName("ASAP-Test");
        op.setAsap(true);
        op.setAsapDurationHours(9.0);
        op.setWorkerCount(2);

        Operation saved = operationRepository.saveAndFlush(op);
        Operation reloaded = operationRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getAsap()).isTrue();
        assertThat(reloaded.getAsapDurationHours()).isEqualTo(9.0);
        assertThat(reloaded.getStartTime()).isNull();
        assertThat(reloaded.getEndTime()).isNull();
        assertThat(reloaded.getDurationInHours()).isEqualTo(9);
        assertThat(reloaded.getDurationInDays()).isEqualTo(1);
    }

    /** Wiele operacji ASAP z różnymi czasami trwania są persystowane poprawnie. */
    @Test
    void shouldPersistMultipleAsapOperationsWithVariousDurations() {
        double[] hours = {0.5, 1.0, 8.5, 9.0, 12.0, 24.0, 48.0};

        for (double h : hours) {
            Operation op = new Operation();
            op.setName("ASAP-" + h);
            op.setAsap(true);
            op.setAsapDurationHours(h);
            op.setWorkerCount(1);
            operationRepository.save(op);
        }
        operationRepository.flush();

        List<Operation> all = operationRepository.findAll();
        assertThat(all).hasSize(hours.length);
        for (Operation op : all) {
            assertThat(op.getAsapDurationHours()).isGreaterThan(0);
        }
    }

    /** UUID jest unikatowy dla różnych operacji. */
    @Test
    void shouldGenerateUniqueUuidsForDifferentOperations() {
        Operation op1 = buildFixed("Op1", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0));
        Operation op2 = buildFixed("Op2", LocalDateTime.of(2026, 4, 21, 8, 0), LocalDateTime.of(2026, 4, 22, 8, 0));
        op1 = operationRepository.saveAndFlush(op1);
        op2 = operationRepository.saveAndFlush(op2);

        assertThat(op1.getUuid()).isNotEqualTo(op2.getUuid());
    }

    /** Persist i reload z wieloma poprzednikami (UUID rozdzielone przecinkami). */
    @Test
    void shouldPersistMultiplePredecessorIds() {
        Operation a = buildFixed("A", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0));
        Operation b = buildFixed("B", LocalDateTime.of(2026, 4, 20, 8, 0), LocalDateTime.of(2026, 4, 21, 8, 0));
        a = operationRepository.saveAndFlush(a);
        b = operationRepository.saveAndFlush(b);

        Operation c = buildFixed("C", LocalDateTime.of(2026, 4, 21, 8, 0), LocalDateTime.of(2026, 4, 22, 8, 0));
        c.setPredecessorIds(a.getUuid() + "," + b.getUuid());
        c = operationRepository.saveAndFlush(c);

        Operation reloaded = operationRepository.findById(c.getId()).orElseThrow();
        assertThat(reloaded.getPredecessorIds())
                .contains(a.getUuid())
                .contains(b.getUuid());
    }

    // ======= HELPER =======

    private Operation buildFixed(String name, LocalDateTime start, LocalDateTime end) {
        Operation op = new Operation();
        op.setName(name);
        op.setStartTime(start);
        op.setEndTime(end);
        op.setWorkerCount(1);
        return op;
    }
}

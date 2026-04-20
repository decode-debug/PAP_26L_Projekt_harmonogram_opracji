package pl.pw.elka.scheduleapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

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
}

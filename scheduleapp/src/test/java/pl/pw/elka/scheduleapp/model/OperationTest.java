package pl.pw.elka.scheduleapp.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class OperationTest {

    @Test
    void shouldCalculateDurationForStandardOperationWithPartialDayRoundedUp() {
        Operation operation = new Operation();
        operation.setStartTime(LocalDateTime.of(2026, 4, 19, 8, 0));
        operation.setEndTime(LocalDateTime.of(2026, 4, 20, 10, 30));

        assertThat(operation.getDurationInHours()).isEqualTo(26);
        assertThat(operation.getDurationInDays()).isEqualTo(2);
    }

    @Test
    void shouldCalculateDurationForAsapOperationFromDeclaredHours() {
        Operation operation = new Operation();
        operation.setAsap(true);
        operation.setAsapDurationHours(25.5);

        assertThat(operation.getDurationInHours()).isEqualTo(25);
        assertThat(operation.getDurationInDays()).isEqualTo(2);
    }

    @Test
    void shouldReturnZeroDurationWhenDatesAreMissing() {
        Operation operation = new Operation();

        assertThat(operation.getDurationInHours()).isZero();
        assertThat(operation.getDurationInDays()).isZero();
    }

    @Test
    void shouldSubtractCrashedDaysFromEffectiveEndTime() {
        Operation operation = new Operation();
        operation.setEndTime(LocalDateTime.of(2026, 4, 24, 16, 0));
        operation.setCrashedDays(2);

        assertThat(operation.getEffectiveEndTime())
                .isEqualTo(LocalDateTime.of(2026, 4, 22, 16, 0));
    }

    @Test
    void shouldKeepEffectiveEndTimeUnchangedWithoutCrashing() {
        Operation operation = new Operation();
        operation.setEndTime(LocalDateTime.of(2026, 4, 24, 16, 0));

        assertThat(operation.getEffectiveEndTime())
                .isEqualTo(LocalDateTime.of(2026, 4, 24, 16, 0));
    }

    @Test
    void shouldGenerateUuidWhenMissing() {
        Operation operation = new Operation();

        operation.generateUuid();

        assertThat(operation.getUuid()).isNotBlank();
    }

    @Test
    void shouldNotOverrideExistingUuid() {
        Operation operation = new Operation();
        operation.setUuid("existing-uuid");

        operation.generateUuid();

        assertThat(operation.getUuid()).isEqualTo("existing-uuid");
    }
}

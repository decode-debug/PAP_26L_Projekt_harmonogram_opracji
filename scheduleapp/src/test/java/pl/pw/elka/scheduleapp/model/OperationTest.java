package pl.pw.elka.scheduleapp.model;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

    // ======= TESTY PRAWIDŁOWYCH WARTOŚCI GODZIN I DNI =======

    /**
     * Sprawdza, że każda poprawna wartość będąca wielokrotnością 0.5h
     * (step z formularza) daje spodziewane getDurationInHours / getDurationInDays.
     * Format CsvSource: asapHours, expectedFlooredHours, expectedCeilDays
     */
    @ParameterizedTest(name = "{0}h → {1}h floor, {2} dni ceil")
    @CsvSource({
        "0.5,  0, 1",   // pół godziny — poniżej 1h po floor, 1 dzień ceil
        "1.0,  1, 1",
        "8.5,  8, 1",   // ósma i pół — mieści się w 1 dniu (< 24h)
        "9.0,  9, 1",   // 9 godzin — była błędnie odrzucana przez formularz
        "12.0, 12, 1",
        "23.5, 23, 1",
        "24.0, 24, 1",  // dokładnie 1 dzień
        "25.0, 25, 2",  // 1h ponad 1 dzień → 2 dni ceil
        "48.0, 48, 2",
        "49.5, 49, 3",
    })
    void asapDurationHoursToHoursAndDays(double asapHours, long expectedHours, long expectedDays) {
        Operation op = new Operation();
        op.setAsap(true);
        op.setAsapDurationHours(asapHours);

        assertThat(op.getDurationInHours())
                .as("getDurationInHours() dla %.1fh".formatted(asapHours))
                .isEqualTo(expectedHours);
        assertThat(op.getDurationInDays())
                .as("getDurationInDays() dla %.1fh".formatted(asapHours))
                .isEqualTo(expectedDays);
    }

    /** Sprawdza getDurationInDays dla operacji ze stałymi datami — pełne i niepełne dni. */
    @ParameterizedTest(name = "start={0}, end={1} → {2} dni")
    @MethodSource("fixedDateDurationCases")
    void fixedDateDurationInDays(LocalDateTime start, LocalDateTime end, long expectedDays) {
        Operation op = new Operation();
        op.setStartTime(start);
        op.setEndTime(end);

        assertThat(op.getDurationInDays()).isEqualTo(expectedDays);
    }

    static Stream<Arguments> fixedDateDurationCases() {
        return Stream.of(
            // Dokładnie 1 dzień (ta sama godzina) → 1 dzień
            Arguments.of(
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 8, 0),
                1L),
            // Ten sam dzień, koniec w późniejszej godzinie → 1 dzień (ceil)
            // DAYS.between(20.04, 20.04)=0, 16:00 > 08:00 → days++ → 1
            Arguments.of(
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 20, 16, 0),
                1L),
            // 2 dni i kilka godzin → 3 dni (ceil)
            Arguments.of(
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 22, 12, 0),
                3L),
            // Dokładnie 2 dni, ta sama godzina → 2 dni
            Arguments.of(
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 22, 8, 0),
                2L),
            // Następny dzień, wcześniejsza godzina → 1 dzień (brak dodatkowej ułamkowej)
            // DAYS.between(20.04, 21.04)=1, 06:00 < 08:00 → brak ++ → 1
            Arguments.of(
                LocalDateTime.of(2026, 4, 20, 8, 0),
                LocalDateTime.of(2026, 4, 21, 6, 0),
                1L)
        );
    }

    /** getDurationInHours zwraca rzeczywistą liczbę godzin między datami (bez zaokrąglenia). */
    @ParameterizedTest(name = "{0} → {1}h")
    @CsvSource({
        "8,  24",   // start 08:00 +24h
        "9,  47",   // start 09:00 → end po 47h to 08:00 (+2 dni -1h)
    })
    void fixedDateDurationInHours(int startHour, long expectedHours) {
        Operation op = new Operation();
        op.setStartTime(LocalDateTime.of(2026, 4, 20, startHour, 0));
        op.setEndTime(op.getStartTime().plusHours(expectedHours));

        assertThat(op.getDurationInHours()).isEqualTo(expectedHours);
    }

    /** Sprawdza, że zwracane godziny są wyłącznie FLOOR (nie round, nie ceil). */
    @Test
    void asapDurationHoursIsFlooredNotRounded() {
        Operation op = new Operation();
        op.setAsap(true);
        op.setAsapDurationHours(9.9);

        // floor(9.9) = 9, NIE 10
        assertThat(op.getDurationInHours()).isEqualTo(9);
        // ceil(9.9 / 24) = 1
        assertThat(op.getDurationInDays()).isEqualTo(1);
    }

    /** Sprawdza efektywny koniec przy zerowym skróceniu (brak crashedDays). */
    @Test
    void effectiveEndTimeWithNullCrashedDaysEqualsEndTime() {
        Operation op = new Operation();
        LocalDateTime end = LocalDateTime.of(2026, 4, 25, 10, 0);
        op.setEndTime(end);
        op.setCrashedDays(null);

        assertThat(op.getEffectiveEndTime()).isEqualTo(end);
    }

    /** Sprawdza efektywny koniec przy skróceniu o 1 dzień. */
    @Test
    void effectiveEndTimeWithOneDayCrash() {
        Operation op = new Operation();
        op.setEndTime(LocalDateTime.of(2026, 4, 25, 10, 0));
        op.setCrashedDays(1);

        assertThat(op.getEffectiveEndTime())
                .isEqualTo(LocalDateTime.of(2026, 4, 24, 10, 0));
    }

    /** getDurationInHours / getDurationInDays zwracają 0, gdy asap=true, ale brak asapDurationHours. */
    @Test
    void asapWithoutDurationHoursReturnsZero() {
        Operation op = new Operation();
        op.setAsap(true);
        op.setAsapDurationHours(null);

        assertThat(op.getDurationInHours()).isZero();
        assertThat(op.getDurationInDays()).isZero();
    }

    /** getEffectiveEndTime zwraca null, gdy endTime jest null. */
    @Test
    void effectiveEndTimeIsNullWhenEndTimeIsNull() {
        Operation op = new Operation();
        op.setCrashedDays(3);

        assertThat(op.getEffectiveEndTime()).isNull();
    }

    /** UUID jest poprawnym UUID po generateUuid(). */
    @Test
    void generatedUuidMatchesUuidFormat() {
        Operation op = new Operation();
        op.generateUuid();

        assertThat(op.getUuid())
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    /** Każde wywołanie generateUuid() bez istniejącego UUID generuje unikalne wartości. */
    @Test
    void generateUuidProducesUniqueValues() {
        Operation op1 = new Operation();
        Operation op2 = new Operation();
        op1.generateUuid();
        op2.generateUuid();

        assertThat(op1.getUuid()).isNotEqualTo(op2.getUuid());
    }
}

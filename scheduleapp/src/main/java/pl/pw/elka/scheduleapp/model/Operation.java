package pl.pw.elka.scheduleapp.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "operations")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stabilny UUID — używany jako klucz referencji poprzedników */
    @Column(name = "uuid", unique = true)
    private String uuid;

    @PrePersist
    public void generateUuid() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString();
        }
    }

    private String name;

    // Poprawka: Dodajemy leniwe parsowanie, aby Java nie wyrzuciła błędu,
    // jeśli sekundy zostaną przesłane lub pominięte
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm[:ss]")
    private LocalDateTime endTime;

    private Integer workerCount;
    private String resources;

    private Double totalCost;
    private Double crashingCostPerDay;
    private Integer maxCrashingDays;

    /** Aktualnie zastosowane skrócenie (dni) — przechowywane w bazie */
    private Integer crashedDays;

    /** Tryb ASAP — operacja startuje jak najwcześniej (po poprzednikach) */
    private Boolean asap;

    /** Czas trwania operacji ASAP w godzinach (używany gdy asap=true) */
    private Double asapDurationHours;

    // Operacje poprzedzające — przechowywane jako tekst "1,3,5"
    private String predecessorIds;

    // Metoda pomocnicza
    public long getDurationInHours() {
        if (Boolean.TRUE.equals(asap) && asapDurationHours != null) {
            return (long) Math.floor(asapDurationHours);
        }
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toHours();
        }
        return 0;
    }

    // Czas trwania w dniach — obliczany, nie zapisywany w bazie
    @Transient
    public long getDurationInDays() {
        if (Boolean.TRUE.equals(asap) && asapDurationHours != null) {
            return (long) Math.ceil(asapDurationHours / 24.0);
        }
        if (startTime != null && endTime != null) {
            long days = ChronoUnit.DAYS.between(startTime.toLocalDate(), endTime.toLocalDate());
            // Zaokrąglamy w górę jeśli jest część dnia
            if (endTime.toLocalTime().isAfter(startTime.toLocalTime())) {
                days++;
            }
            return Math.max(days, 0);
        }
        return 0;
    }

    /** Efektywny czas zakończenia po skróceniu (crashing) — tylko dla operacji ze stałymi datami */
    @Transient
    public LocalDateTime getEffectiveEndTime() {
        if (endTime == null) return null;
        int crashed = crashedDays != null ? crashedDays : 0;
        return crashed > 0 ? endTime.minusDays(crashed) : endTime;
    }
}
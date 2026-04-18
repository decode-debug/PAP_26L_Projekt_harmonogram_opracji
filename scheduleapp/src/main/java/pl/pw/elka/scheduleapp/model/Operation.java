package pl.pw.elka.scheduleapp.model;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    // Metoda pomocnicza
    public long getDurationInHours() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toHours();
        }
        return 0;
    }

    // Czas trwania w dniach — obliczany, nie zapisywany w bazie
    @Transient
    public long getDurationInDays() {
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
}
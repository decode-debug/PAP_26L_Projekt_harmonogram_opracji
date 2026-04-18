package pl.pw.elka.scheduleapp.model;

import java.time.Duration;
import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // Zasoby i pracownicy
    private Integer workerCount;
    private String resources; // Tu możesz wpisać np. "Tokarka, Wiertarka"

    // Finanse i optymalizacja
    private Double totalCost;
    private Double crashingCostPerDay; // Koszt skrócenia operacji o 1 dzień

    // Metoda pomocnicza do wyliczania długości w godzinach
    public long getDurationInHours() {
        if (startTime != null && endTime != null) {
            return Duration.between(startTime, endTime).toHours();
        }
        return 0;
    }
}
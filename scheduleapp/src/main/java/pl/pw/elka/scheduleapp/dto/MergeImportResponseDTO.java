package pl.pw.elka.scheduleapp.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pl.pw.elka.scheduleapp.model.Operation;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class MergeImportResponseDTO {
    private List<Operation> addedOperations;
    private List<String> addedNames;
    private List<String> skippedNames;
    private String message;
}

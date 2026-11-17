package fr.medislot.platform;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

/** Erreur rattachée à un champ du formulaire : le front l'affiche sous ce champ. */
public class FieldProblem extends RuntimeException {
    private final HttpStatus status;
    private final String field;

    public FieldProblem(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public ResponseEntity<ProblemDetail> response() {
        var problem = ProblemDetail.forStatusAndDetail(status, getMessage());
        problem.setProperty("errors", List.of(Map.of("field", field, "message", getMessage())));
        return ResponseEntity.status(status).body(problem);
    }
}

package fr.medislot.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class Errors {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ProblemDetail> status(ResponseStatusException e) {
        var p =
                ProblemDetail.forStatusAndDetail(
                        e.getStatusCode(),
                        e.getReason() == null ? "Demande refusée" : e.getReason());
        return ResponseEntity.status(e.getStatusCode()).body(p);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        var p =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "Vérifiez les données saisies.");
        p.setProperty(
                "errors",
                e.getBindingResult().getFieldErrors().stream()
                        .map(
                                f ->
                                        java.util.Map.of(
                                                "field",
                                                f.getField(),
                                                "message",
                                                String.valueOf(f.getDefaultMessage())))
                        .toList());
        return ResponseEntity.badRequest().body(p);
    }

    // Base injoignable : rien n'a été écrit.
    @ExceptionHandler({
        org.springframework.transaction.CannotCreateTransactionException.class,
        org.springframework.dao.DataAccessResourceFailureException.class
    })
    public ResponseEntity<ProblemDetail> database() {
        return ResponseEntity.status(503)
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "Base de données momentanément indisponible."));
    }

    // lat=abc par exemple : on nomme le paramètre sans renvoyer la valeur reçue.
    @ExceptionHandler(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> type(
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.BAD_REQUEST,
                                "Paramètre invalide : " + e.getName() + "."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict() {
        return ResponseEntity.status(409)
                .body(
                        ProblemDetail.forStatusAndDetail(
                                HttpStatus.CONFLICT,
                                "La demande entre en conflit avec les données actuelles."));
    }
}

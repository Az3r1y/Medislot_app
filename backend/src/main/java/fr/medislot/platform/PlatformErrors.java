package fr.medislot.platform;

import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = PlatformErrors.class)
public class PlatformErrors {
    @ExceptionHandler(FieldProblem.class)
    public ResponseEntity<ProblemDetail> field(FieldProblem problem) {
        return problem.response();
    }
}

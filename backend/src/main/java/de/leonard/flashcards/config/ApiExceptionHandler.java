package de.leonard.flashcards.config;

import de.leonard.flashcards.document.DocumentNotFoundException;
import de.leonard.flashcards.document.InvalidUploadException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Uebersetzt fachliche Ausnahmen in RFC-9457-Antworten, damit das Frontend
 * eine verwertbare Fehlermeldung anzeigen kann statt eines HTML-Stacktraces.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleNotFound(DocumentNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidUploadException.class)
    public ProblemDetail handleInvalidUpload(InvalidUploadException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        log.info("Upload abgelehnt, Datei zu gross: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "Die Datei ist groesser als das erlaubte Limit von 25 MB.");
    }
}

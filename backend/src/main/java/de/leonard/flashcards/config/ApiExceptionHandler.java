package de.leonard.flashcards.config;

import java.util.stream.Collectors;

import de.leonard.flashcards.deck.CardNotFoundException;
import de.leonard.flashcards.deck.DeckNotFoundException;
import de.leonard.flashcards.deck.InvalidDeckException;
import de.leonard.flashcards.document.DocumentNotFoundException;
import de.leonard.flashcards.document.InvalidUploadException;
import de.leonard.flashcards.generation.GenerationJobNotFoundException;
import de.leonard.flashcards.generation.InvalidGenerationRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

    @ExceptionHandler({
            DocumentNotFoundException.class,
            DeckNotFoundException.class,
            CardNotFoundException.class,
            GenerationJobNotFoundException.class
    })
    public ProblemDetail handleNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler({
            InvalidUploadException.class,
            InvalidDeckException.class,
            InvalidGenerationRequestException.class
    })
    public ProblemDetail handleInvalidRequest(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Bean-Validation-Fehler als eine lesbare Zeile, sonst kommt im Frontend nichts Brauchbares an. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationErrors(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .distinct()
                .collect(Collectors.joining(" "));
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, detail.isBlank() ? "Ungueltige Eingabe." : detail);
    }

    /** Unbekannte Enum-Werte (etwa language: "fr") landen hier. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException e) {
        Throwable cause = e.getMostSpecificCause();
        String detail = cause instanceof IllegalArgumentException
                ? cause.getMessage()
                : "Der Anfragekoerper konnte nicht gelesen werden.";
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException e) {
        log.info("Upload abgelehnt, Datei zu gross: {}", e.getMessage());
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE, "Die Datei ist groesser als das erlaubte Limit von 25 MB.");
    }
}

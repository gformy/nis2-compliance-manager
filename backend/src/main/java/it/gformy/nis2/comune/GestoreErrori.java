package it.gformy.nis2.comune;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Tutti gli errori escono in formato Problem Details (RFC 9457).
 * La classe base gestisce gia' validazione (400), JSON malformato e metodi non ammessi;
 * qui si aggiungono gli errori che arrivano da Oracle, senza mai esporre il messaggio
 * originale del database.
 */
@RestControllerAdvice
public class GestoreErrori extends ResponseEntityExceptionHandler {

    /** Errori di validazione: si aggiunge l'elenco "campo -> messaggio" per il form del frontend. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException eccezione,
            HttpHeaders intestazioni, HttpStatusCode stato, WebRequest richiesta) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
        Map<String, String> errori = new LinkedHashMap<>();
        for (FieldError errore : eccezione.getBindingResult().getFieldErrors()) {
            errori.putIfAbsent(errore.getField(), errore.getDefaultMessage());
        }
        problema.setProperty("errori", errori);
        return ResponseEntity.badRequest().body(problema);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicato(DuplicateKeyException eccezione) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Esiste gia' un elemento con lo stesso identificativo");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail vincoloViolato(DataIntegrityViolationException eccezione) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "L'operazione viola un vincolo di integrita' del database");
    }
}

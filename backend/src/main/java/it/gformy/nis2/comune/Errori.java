package it.gformy.nis2.comune;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Errori HTTP ricorrenti, gia' in formato Problem Details. */
public final class Errori {

    private Errori() {
    }

    public static ErrorResponseException conflitto(String dettaglio) {
        return new ErrorResponseException(HttpStatus.CONFLICT,
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, dettaglio), null);
    }

    /** 400 con la stessa forma degli errori di validazione: {"errori": {campo: messaggio}}. */
    public static ErrorResponseException nonValido(String campo, String messaggio) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
        problema.setProperty("errori", java.util.Map.of(campo, messaggio));
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problema, null);
    }

    public static ErrorResponseException nonTrovato(String descrizione) {
        return new ErrorResponseException(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, descrizione + " non trovato"), null);
    }
}

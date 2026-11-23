package it.gformy.nis2.comune;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Errori HTTP ricorrenti, gia' in formato Problem Details. */
public final class Errori {

    private Errori() {
    }

    public static ErrorResponseException nonTrovato(String descrizione) {
        return new ErrorResponseException(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, descrizione + " non trovato"), null);
    }
}

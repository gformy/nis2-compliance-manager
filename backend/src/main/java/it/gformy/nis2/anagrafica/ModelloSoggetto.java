package it.gformy.nis2.anagrafica;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Tipi scambiati dall'API dell'anagrafica. Sono record: immutabili, senza
 * logica, separati dalle tabelle (le righe del database non escono mai cosi' come sono).
 */
public final class ModelloSoggetto {

    private ModelloSoggetto() {
    }

    /** Valori ammessi dal vincolo CHECK su TipoSoggettoNIS.codice. */
    public enum TipoSoggetto { ESSENZIALE, IMPORTANTE }

    /** Riga dell'elenco. */
    public record SoggettoSintesi(long id, String codiceFiscale, String denominazione, TipoSoggetto tipoSoggetto,
                                  long organizzazioneId, String organizzazione) {
    }

    /** Anagrafica completa, come la restituisce l'API. */
    public record Soggetto(
            long id,
            String codiceFiscale,
            String denominazione,
            String indirizzoSedeLegale,
            String pec,
            String emailFunzionale,
            String telefono,
            BigDecimal fatturato,
            BigDecimal bilancio,
            Integer numeroDipendenti,
            TipoSoggetto tipoSoggetto,
            LocalDateTime dataCreazione) {
    }

    /**
     * Dati modificabili. I vincoli rispecchiano le colonne e i CHECK di SoggettoNIS,
     * cosi' l'errore arriva al form prima di arrivare a Oracle.
     * Fatturato e bilancio sono facoltativi: la PA ne e' esente.
     */
    public record DatiSoggetto(
            @NotBlank @Size(max = 255) String denominazione,
            @NotBlank @Size(max = 255) String indirizzoSedeLegale,
            @NotBlank @Email @Size(max = 255) String pec,
            @Email @Size(max = 255) String emailFunzionale,
            @Pattern(regexp = "[0-9 +]{6,30}", message = "deve contenere solo cifre, spazi e +") String telefono,
            @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal fatturato,
            @PositiveOrZero @Digits(integer = 13, fraction = 2) BigDecimal bilancio,
            @PositiveOrZero Integer numeroDipendenti,
            @NotNull TipoSoggetto tipoSoggetto) {
    }

    /** Creazione: il codice fiscale si sceglie una volta sola e non si modifica. */
    public record NuovoSoggetto(
            @NotBlank
            @Pattern(regexp = "[0-9]{11}|[A-Z0-9]{16}",
                     message = "deve essere una partita IVA di 11 cifre o un codice fiscale di 16 caratteri")
            String codiceFiscale,
            @NotNull @Valid DatiSoggetto dati,
            /** Solo per l'amministratore della piattaforma; gli altri creano nella propria organizzazione. */
            Long organizzazioneId) {
    }

    /** Una versione dell'anagrafica, dalla vista SoggettoNIS_Versioni del PW. */
    public record VersioneSoggetto(
            int versione,
            String denominazione,
            String indirizzoSedeLegale,
            String pec,
            String emailFunzionale,
            String telefono,
            BigDecimal fatturato,
            BigDecimal bilancio,
            Integer numeroDipendenti,
            LocalDateTime validoDa,
            LocalDateTime validoA,
            /* Chi ha creato questa versione modificando la precedente; null per la prima. */
            String modificataDa) {
    }
}

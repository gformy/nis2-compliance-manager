package it.gformy.nis2.fornitori;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Tipi dell'API di fornitori e dipendenze. */
public final class ModelloFornitori {

    private ModelloFornitori() {
    }

    /** Dipendenza.criterio_rilevanza (art. 18): 1 = fornitura ICT, 2 = fornitura non fungibile. */
    public enum CriterioRilevanza {
        ICT(1), NON_FUNGIBILE(2);

        private final int codiceAcn;

        CriterioRilevanza(int codiceAcn) {
            this.codiceAcn = codiceAcn;
        }

        public int codiceAcn() {
            return codiceAcn;
        }
    }

    public enum StatoDipendenza { ATTIVA, CESSATA }

    public record CodiceCpv(String codice, String descrizione) {
    }

    public record Fornitore(
            long id,
            String denominazione,
            String codiceFiscale,
            String paeseSedeLegale,
            List<CodiceCpv> cpv,
            LocalDateTime dataCreazione) {
    }

    /** Dati modificabili del fornitore. Il codice fiscale e' l'identita' e non cambia. */
    public record DatiFornitore(
            @NotBlank @Size(max = 255) String denominazione,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "codice ISO del paese, due lettere maiuscole")
            String paeseSedeLegale,
            @NotNull List<@NotNull @Pattern(regexp = "[0-9]{8}", message = "codice CPV di 8 cifre") String> cpv) {
    }

    public record NuovoFornitore(
            @NotBlank @Pattern(regexp = "[A-Z0-9]{2,16}", message = "da 2 a 16 lettere maiuscole o cifre")
            String codiceFiscale,
            @NotNull @Valid DatiFornitore dati) {

        /** Per un fornitore italiano: partita IVA di 11 cifre o codice fiscale di 16 caratteri. */
        @JsonIgnore
        @AssertTrue(message = "per un fornitore italiano serve una partita IVA di 11 cifre o un codice fiscale di 16 caratteri")
        public boolean isCodiceFiscaleItaliano() {
            if (dati == null || !"IT".equals(dati.paeseSedeLegale()) || codiceFiscale == null) {
                return true;
            }
            return codiceFiscale.matches("[0-9]{11}|[A-Z0-9]{16}");
        }
    }

    public record VersioneFornitore(
            int versione,
            String denominazione,
            String paeseSedeLegale,
            LocalDateTime validoDa,
            LocalDateTime validoA,
            String modificataDa) {
    }

    public record Dipendenza(
            long id,
            long fornitoreId,
            String fornitoreDenominazione,
            long elementoCriticoId,
            String elementoNome,
            String elementoTipo,
            CriterioRilevanza criterioRilevanza,
            String tipologiaFornitura,
            String referenteContatto,
            StatoDipendenza stato) {
    }

    public record NuovaDipendenza(
            @NotNull Long fornitoreId,
            @NotNull Long elementoCriticoId,
            @NotNull CriterioRilevanza criterioRilevanza,
            @Size(max = 255) String tipologiaFornitura,
            @Size(max = 255) String referenteContatto) {
    }

    public record DatiDipendenza(
            @NotNull CriterioRilevanza criterioRilevanza,
            @Size(max = 255) String tipologiaFornitura,
            @Size(max = 255) String referenteContatto,
            @NotNull StatoDipendenza stato) {
    }

    public record VersioneDipendenza(
            int versione,
            CriterioRilevanza criterioRilevanza,
            String tipologiaFornitura,
            StatoDipendenza stato,
            LocalDateTime validoDa,
            LocalDateTime validoA,
            String modificataDa) {
    }

    /**
     * Riga del profilo ACN per i fornitori rilevanti (art. 18): i cinque campi
     * richiesti dal portale, calcolati dalle dipendenze attive del soggetto.
     */
    public record FornitoreRilevante(
            String denominazione,
            String codiceFiscale,
            String paeseSedeLegale,
            List<String> codiciCpv,
            List<Integer> criteriRilevanza) {
    }
}

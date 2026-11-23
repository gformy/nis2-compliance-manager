package it.gformy.nis2.conformita;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tipi dell'API di conformita' alle misure di sicurezza ACN. */
public final class ModelloConformita {

    private ModelloConformita() {
    }

    /** StatoAdempimentoRequisito.stato (vincolo CHECK del PW). */
    public enum StatoAdempimento { NON_ATTUATO, PARZIALMENTE_ATTUATO, ATTUATO, NON_APPLICABILE }

    /** Un requisito applicabile al soggetto; stato null = non ancora valutato. */
    public record Requisito(
            long id,
            String codice,
            int numeroPunto,
            String testo,
            String misuraCodice,
            String misuraCategoria,
            String misuraDescrizione,
            StatoAdempimento stato,
            LocalDate dataValutazione,
            String note) {
    }

    public record DatiAdempimento(@NotNull StatoAdempimento stato, @Size(max = 1000) String note) {
    }

    public record Conteggi(int totale, int attuati, int parziali, int nonAttuati, int nonApplicabili, int nonValutati) {

        /**
         * Percentuale di attuazione: un requisito attuato vale 1, uno parzialmente
         * attuato 0,5; i non applicabili non entrano nel calcolo.
         */
        public double percentualeAttuazione() {
            int base = totale - nonApplicabili;
            return base == 0 ? 0 : Math.round((attuati + parziali * 0.5) * 1000.0 / base) / 10.0;
        }
    }

    public record RiepilogoMisura(String codice, String categoria, String descrizione, Conteggi conteggi,
                                  double percentualeAttuazione) {
    }

    public record Riepilogo(Conteggi complessivo, double percentualeAttuazione, List<RiepilogoMisura> misure) {
    }

    public record VersioneAdempimento(
            int versione,
            StatoAdempimento stato,
            LocalDate dataValutazione,
            LocalDateTime validoDa,
            LocalDateTime validoA,
            String modificataDa) {
    }
}

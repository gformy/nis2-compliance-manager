package it.gformy.nis2.formazione;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Piano di formazione (PR.AT-01, PR.AT-02) e registro delle partecipazioni. */
public final class ModelloFormazione {

    private ModelloFormazione() {
    }

    /** Organi di amministrazione e direttivi, ruoli specializzati (es. amministratori di sistema), altro personale. */
    public enum Categoria { ORGANO, RUOLO_SPECIALIZZATO, PERSONALE }

    public enum Destinatari { TUTTI, ORGANO, RUOLO_SPECIALIZZATO }

    public enum Esito { SUPERATA, NON_SUPERATA, NON_PREVISTA }

    public enum StatoAttivita {
        PIANIFICATA,
        SVOLTA,
        /** Data prevista passata senza partecipazioni nel registro. */
        DA_REGISTRARE
    }

    public record Persona(long id, String nome, Categoria categoria, String ruolo, LocalDate dataCessazione,
                          LocalDate ultimaFormazione, boolean inRegola) {
    }

    public record Attivita(long id, String titolo, String contenuti, Destinatari destinatari, LocalDate dataPrevista,
                           BigDecimal durataOre, String modalitaVerifica, int partecipanti, StatoAttivita stato) {
    }

    public record Partecipazione(long id, long attivitaId, String attivita, Destinatari destinatari, long personaId,
                                 String persona, LocalDate dataSvolgimento, Esito esito, String note,
                                 LocalDateTime registrataIl, String registrataDa) {
    }

    public record Copertura(Categoria categoria, int persone, int formate, double percentuale) {
    }

    public record Riepilogo(List<Copertura> copertura, int attivitaPianificate, int attivitaDaRegistrare) {
    }

    public record Formazione(Riepilogo riepilogo, List<Persona> persone, List<Attivita> attivita,
                             List<Partecipazione> registro) {
    }

    // ------------------------------------------------------------------ input

    public record DatiPersona(@NotBlank @Size(max = 255) String nome, @NotNull Categoria categoria,
                              @Size(max = 100) String ruolo) {
    }

    public record DatiCessazione(@NotNull @PastOrPresent LocalDate data) {
    }

    public record DatiAttivita(
            @NotBlank @Size(max = 200) String titolo,
            @NotBlank @Size(max = 2000) String contenuti,
            @NotNull Destinatari destinatari,
            @NotNull LocalDate dataPrevista,
            @Positive BigDecimal durataOre,
            @Size(max = 500) String modalitaVerifica) {
    }

    public record DatiPartecipazioni(
            @NotEmpty @Size(max = 500) List<Long> persone,
            @NotNull @PastOrPresent LocalDate data,
            @NotNull Esito esito,
            @Size(max = 500) String note) {
    }
}

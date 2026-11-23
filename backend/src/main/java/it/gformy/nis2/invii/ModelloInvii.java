package it.gformy.nis2.invii;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tipi dell'API degli invii alla piattaforma ACN e del profilo dichiarato. */
public final class ModelloInvii {

    private ModelloInvii() {
    }

    /** InvioACN.tipo_invio (vincolo CHECK del PW). */
    public enum TipoInvio { REGISTRAZIONE, AGGIORNAMENTO_ANNUALE, AGGIORNAMENTO_CONTINUO, CATEGORIZZAZIONE }

    /** Sezioni del profilo estraibili in CSV. */
    public enum Sezione { ELEMENTI, FORNITORI, ADEMPIMENTI }

    public record Invio(long id, TipoInvio tipo, Instant dataInvio, int periodoRiferimento, String registratoDa,
                        String note) {
    }

    public record DatiInvio(
            @NotNull TipoInvio tipo,
            @NotNull @Min(2024) @Max(2100) Integer periodoRiferimento,
            @Size(max = 500) String note) {
    }

    public record Anagrafica(String denominazione, String indirizzoSedeLegale, String pec, BigDecimal fatturato,
                             Long numeroDipendenti) {
    }

    public record ElementoProfilo(long id, String nome, String tipo) {
    }

    public record FornitoreProfilo(long dipendenzaId, String fornitore, String codiceFiscale, String elemento,
                                   String criterioRilevanza, String tipologiaFornitura) {
    }

    public record AdempimentoProfilo(String requisito, String stato, LocalDate dataValutazione) {
    }

    /** Il profilo cosi' come risultava all'istante dell'invio. */
    public record Profilo(
            Invio invio,
            Anagrafica anagrafica,
            List<ElementoProfilo> elementi,
            List<FornitoreProfilo> fornitori,
            List<AdempimentoProfilo> adempimenti) {
    }

    public record Modifica<T>(T prima, T dopo) {
    }

    public record Differenze<T>(List<T> aggiunti, List<T> rimossi, List<Modifica<T>> modificati) {

        public boolean vuote() {
            return aggiunti.isEmpty() && rimossi.isEmpty() && modificati.isEmpty();
        }
    }

    public record CampoModificato(String campo, Object prima, Object dopo) {
    }

    public record Confronto(
            Invio da,
            Invio a,
            List<CampoModificato> anagrafica,
            Differenze<ElementoProfilo> elementi,
            Differenze<FornitoreProfilo> fornitori,
            Differenze<AdempimentoProfilo> adempimenti) {
    }

    public record Estrazione(Instant dataOra, String utente, String dettaglio) {
    }
}

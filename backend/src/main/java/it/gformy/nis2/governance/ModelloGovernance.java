package it.gformy.nis2.governance;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Politiche, piani e altri documenti che gli organi di amministrazione approvano
 * (GV.PO-01 punto 3, Linee guida ACN - Guida alla lettura, Appendice C) e riesaminano
 * almeno ogni anno (GV.PO-02 punto 1).
 */
public final class ModelloGovernance {

    private ModelloGovernance() {
    }

    public enum Categoria { POLITICA, DOCUMENTO }

    public enum StatoDocumento {
        MANCANTE,
        DA_APPROVARE,
        VALIDO,
        /** Riesame da fare entro 30 giorni. */
        RIESAME_IN_SCADENZA,
        RIESAME_SCADUTO
    }

    public record Organo(long id, String nome, String ruolo) {
    }

    public record FileDocumento(long id, String nomeFile, String contentType, long dimensione, String sha256,
                                String versioneDocumento, Instant caricatoIl, String caricatoDa) {
    }

    public record Documento(
            long id,
            String versione,
            LocalDate dataApprovazione,
            Organo approvatoDa,
            LocalDate dataUltimoRiesame,
            int periodicitaRiesameMesi,
            String note,
            LocalDate prossimoRiesame,
            FileDocumento ultimoFile) {
    }

    public record Voce(String codice, String nome, Categoria categoria, String ambito, StatoDocumento stato,
                       Documento documento) {
    }

    public record Riepilogo(int totale, int validi, int inScadenza, int scaduti, int daApprovare, int mancanti,
                            double percentualeInRegola) {
    }

    public record Governance(Riepilogo riepilogo, List<Voce> voci) {
    }

    public record VersioneDocumento(int versione, String versioneDocumento, LocalDate dataApprovazione,
                                    String approvatoDa, LocalDate dataUltimoRiesame, LocalDateTime validoDa,
                                    LocalDateTime validoA, String modificataDa) {
    }

    // ------------------------------------------------------------------ input

    public record DatiOrgano(@NotBlank @Size(max = 255) String nome, @NotBlank @Size(max = 100) String ruolo) {
    }

    public record DatiNuovoDocumento(
            @NotBlank String tipo,
            @Size(max = 20) String versione,
            @Min(1) @Max(12) Integer periodicitaRiesameMesi,
            @Size(max = 500) String note) {
    }

    public record DatiDocumento(
            @Size(max = 20) String versione,
            @NotNull @Min(1) @Max(12) Integer periodicitaRiesameMesi,
            @Size(max = 500) String note) {
    }

    public record DatiApprovazione(@NotNull @PastOrPresent LocalDate data, @NotNull Long organoId) {
    }

    public record DatiRiesame(@NotNull @PastOrPresent LocalDate data) {
    }
}

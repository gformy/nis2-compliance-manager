package it.gformy.nis2.organizzazioni;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Organizzazioni clienti della piattaforma, piani e amministratori. */
public final class ModelloOrganizzazioni {

    private ModelloOrganizzazioni() {
    }

    public enum TipoOrganizzazione { CLIENTE_DIRETTO, RIVENDITORE }

    public enum StatoOrganizzazione { ATTIVA, SOSPESA }

    public record Piano(String codice, String nome, Integer maxSoggetti, BigDecimal prezzoMensileSoggetto) {
    }

    public record Amministratore(String utente, java.time.LocalDateTime dataAssegnazione, String assegnatoDa) {
    }

    public record Organizzazione(
            long id,
            String nome,
            String codiceFiscale,
            TipoOrganizzazione tipo,
            StatoOrganizzazione stato,
            Piano piano,
            int soggetti,
            BigDecimal costoMensile,
            String riferimentoContratto,
            LocalDate dataContratto,
            boolean conLogo,
            List<Amministratore> amministratori) {
    }

    public record DatiOrganizzazione(
            @NotBlank @Size(max = 255) String nome,
            @NotBlank @Pattern(regexp = "[0-9]{11}|[A-Z0-9]{16}",
                    message = "deve essere una partita IVA di 11 cifre o un codice fiscale di 16 caratteri")
            String codiceFiscale,
            @NotNull TipoOrganizzazione tipo,
            @NotBlank String piano,
            StatoOrganizzazione stato,
            @Size(max = 100) String riferimentoContratto,
            @PastOrPresent LocalDate dataContratto) {
    }
}

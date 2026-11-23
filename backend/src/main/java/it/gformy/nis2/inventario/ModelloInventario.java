package it.gformy.nis2.inventario;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Tipi dell'API dell'inventario: asset, servizi e loro collegamenti. */
public final class ModelloInventario {

    private ModelloInventario() {
    }

    /** ElementoCritico.tipo: discrimina le due sottoclassi della gerarchia ISA. */
    public enum TipoElemento { ASSET, SERVIZIO }

    /** Asset.tipo_asset. */
    public enum TipoAsset { HARDWARE, SOFTWARE, FLUSSO_RETE, SERVIZIO_FORNITORE }

    /** ElementoCritico.stato: un elemento non si cancella, si dismette. */
    public enum StatoElemento { ATTIVO, DISMESSO }

    /** AssetServizio.stato. */
    public enum StatoCollegamento { ATTIVA, CESSATA }

    /**
     * Stati membri dell'Unione europea, codici ISO 3166-1 alpha-2 come nella
     * colonna ServizioStatoMembro.stato_membro (la Grecia e' GR, non EL).
     */
    public enum StatoMembroUE {
        AT, BE, BG, CY, CZ, DE, DK, EE, ES, FI, FR, GR, HR, HU, IE, IT, LT, LU, LV, MT, NL, PL, PT, RO, SE, SI, SK
    }

    public record Asset(
            long id,
            String nome,
            TipoAsset tipoAsset,
            boolean rilevante,
            StatoElemento stato,
            LocalDateTime dataInizioValidita,
            LocalDateTime dataFineValidita) {
    }

    /** "rilevante" corrisponde ai sistemi informativi e di rete rilevanti delle specifiche ACN. */
    public record DatiAsset(
            @NotBlank @Size(max = 255) String nome,
            @NotNull TipoAsset tipoAsset,
            boolean rilevante) {
    }

    public record Servizio(
            long id,
            String nome,
            String descrizione,
            String categoriaRilevanza,
            List<StatoMembroUE> statiMembro,
            StatoElemento stato,
            LocalDateTime dataInizioValidita,
            LocalDateTime dataFineValidita) {
    }

    /** La categoria di rilevanza resta testo libero finche' ACN non pubblica il modello definitivo. */
    public record DatiServizio(
            @NotBlank @Size(max = 255) String nome,
            @Size(max = 500) String descrizione,
            @Size(max = 50) String categoriaRilevanza,
            @NotNull List<@NotNull StatoMembroUE> statiMembro) {
    }

    public record Collegamento(
            long assetId,
            String assetNome,
            long servizioId,
            String servizioNome,
            StatoCollegamento stato) {
    }

    public record VersioneElemento(
            int versione,
            String nome,
            StatoElemento stato,
            LocalDateTime validoDa,
            LocalDateTime validoA,
            String modificataDa) {
    }
}

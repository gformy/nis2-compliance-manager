package it.gformy.nis2.incidenti;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Tipi dell'API degli incidenti significativi (D.Lgs. 138/2024, art. 25). Gli istanti sono in UTC. */
public final class ModelloIncidenti {

    private ModelloIncidenti() {
    }

    public enum Gravita { BASSA, MEDIA, ALTA, CRITICA }

    public enum StatoIncidente { APERTO, CHIUSO }

    /** NotificaIncidente.tipo (vincolo CHECK del PW). */
    public enum TipoNotifica { PRE_NOTIFICA, NOTIFICA, RELAZIONE_INTERMEDIA, RELAZIONE_FINALE, RELAZIONE_MENSILE }

    public enum TipoIoc { IP, HASH, DOMINIO, URL, EMAIL }

    public enum FaseTimeline { RILEVAMENTO, INVESTIGAZIONE, CONTENIMENTO, ERADICAZIONE, RIPRISTINO }

    public enum FaseRisposta { CONTENIMENTO, ERADICAZIONE }

    public enum StatoScadenza {
        /** Il termine non decorre ancora (es. relazione finale prima della notifica). */
        IN_ATTESA,
        IN_TEMPO,
        /** Resta meno di un quarto del tempo concesso. */
        IN_SCADENZA,
        SCADUTA,
        TRASMESSA,
        TRASMESSA_IN_RITARDO
    }

    public record TipoIncidente(long id, String codice, String descrizione) {
    }

    public record Catalogo(List<TipoIncidente> tipi, List<String> categorie) {
    }

    public record Scadenza(TipoNotifica tipo, Instant scadenza, StatoScadenza stato, Instant trasmessaIl) {
    }

    public record SintesiIncidente(
            long id,
            String titolo,
            String tipoIncidente,
            String categoria,
            Gravita gravita,
            StatoIncidente stato,
            Instant dataEvidenza,
            Scadenza prossimaScadenza) {
    }

    public record ElementoCoinvolto(long id, String nome, String tipo) {
    }

    public record Notifica(long id, TipoNotifica tipo, Instant dataTrasmissione, String contenuto, String registrataDa) {
    }

    public record Ioc(long id, TipoIoc tipo, String valore, Instant dataRilevazione) {
    }

    public record EventoTimeline(long id, Instant dataOra, FaseTimeline fase, String descrizione) {
    }

    public record Attivita(long id, FaseRisposta fase, String descrizione, String motivazione, String esito,
                           Instant dataEsecuzione) {
    }

    public record Incidente(
            long id,
            String titolo,
            String descrizione,
            TipoIncidente tipo,
            String categoria,
            Gravita gravita,
            String rootCause,
            StatoIncidente stato,
            Instant dataEvidenza,
            Instant dataChiusura,
            Instant dataRegistrazione,
            String registratoDa,
            List<ElementoCoinvolto> elementi,
            List<Notifica> notifiche,
            List<Scadenza> scadenze,
            List<Ioc> ioc,
            List<EventoTimeline> timeline,
            List<Attivita> attivita) {
    }

    // ------------------------------------------------------------------ input

    public record DatiIncidente(
            @NotBlank @Size(max = 200) String titolo,
            @Size(max = 2000) String descrizione,
            @NotBlank String tipoIncidente,
            String categoria,
            Gravita gravita,
            @NotNull @PastOrPresent Instant dataEvidenza,
            @Size(max = 500) String rootCause,
            List<Long> elementiCritici) {

        public List<Long> elementi() {
            return elementiCritici == null ? List.of() : elementiCritici.stream().distinct().toList();
        }
    }

    public record DatiNotifica(
            @NotNull TipoNotifica tipo,
            @NotNull @PastOrPresent Instant dataTrasmissione,
            @Size(max = 20000) String contenuto) {
    }

    public record DatiIoc(@NotNull TipoIoc tipo, @NotBlank @Size(max = 500) String valore) {

        private static final Pattern IPV4 = Pattern.compile(
                "((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)");
        private static final Pattern IPV6 = Pattern.compile("[0-9a-fA-F:]{2,39}");
        private static final Pattern HASH = Pattern.compile("[0-9a-fA-F]{32}|[0-9a-fA-F]{40}|[0-9a-fA-F]{64}");
        private static final Pattern DOMINIO = Pattern.compile(
                "(?=.{1,253}$)([a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,63}");
        private static final Pattern URL = Pattern.compile("(https?|ftp)://\\S+");
        private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

        /** Il valore deve avere il formato del tipo: un IoC malformato non serve a chi lo riceve. */
        @AssertTrue(message = "il valore non ha il formato atteso per il tipo indicato")
        public boolean isValoreCoerente() {
            if (tipo == null || valore == null) {
                return true;
            }
            String v = valore.strip();
            return switch (tipo) {
                case IP -> IPV4.matcher(v).matches() || (v.contains(":") && IPV6.matcher(v).matches());
                case HASH -> HASH.matcher(v).matches();
                case DOMINIO -> DOMINIO.matcher(v).matches();
                case URL -> URL.matcher(v).matches();
                case EMAIL -> EMAIL.matcher(v).matches();
            };
        }
    }

    public record DatiEvento(
            @NotNull @PastOrPresent Instant dataOra,
            @NotNull FaseTimeline fase,
            @NotBlank @Size(max = 500) String descrizione) {
    }

    public record DatiAttivita(
            @NotNull FaseRisposta fase,
            @NotBlank @Size(max = 500) String descrizione,
            @Size(max = 500) String motivazione,
            @Size(max = 255) String esito,
            @PastOrPresent Instant dataEsecuzione) {
    }
}

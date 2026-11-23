package it.gformy.nis2.invii;

import java.net.URI;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.invii.ModelloInvii.Confronto;
import it.gformy.nis2.invii.ModelloInvii.DatiInvio;
import it.gformy.nis2.invii.ModelloInvii.Estrazione;
import it.gformy.nis2.invii.ModelloInvii.Invio;
import it.gformy.nis2.invii.ModelloInvii.Profilo;
import it.gformy.nis2.invii.ModelloInvii.Sezione;
import jakarta.validation.Valid;

/**
 * Invii alla piattaforma ACN: registrazione, profilo dichiarato, confronto ed estrazione CSV.
 *
 * Registrare un invio spetta al referente NIS (o all'amministratore); leggere ed
 * estrarre a chiunque veda il soggetto, revisori compresi. Un invio non si modifica
 * e non si cancella: anche l'utente applicativo ha solo SELECT e INSERT su InvioACN.
 */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/invii")
public class InvioController {

    private final InvioRepository repository;
    private final TransazioneAuditata transazioneAuditata;

    public InvioController(InvioRepository repository, TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Invio> elenco(@PathVariable long soggettoId) {
        return repository.elenco(soggettoId);
    }

    /** Registra l'invio appena fatto sul portale ACN: il profilo si fotografa in questo istante. */
    @PostMapping
    @PreAuthorize("@accessoSoggetti.puoInviare(#soggettoId)")
    public ResponseEntity<Invio> registra(@PathVariable long soggettoId, @Valid @RequestBody DatiInvio dati) {
        long id = transazioneAuditata.esegui(() -> repository.inserisci(soggettoId, dati));
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/invii/" + id))
                .body(trova(soggettoId, id));
    }

    @GetMapping("/{id}/profilo")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Profilo profilo(@PathVariable long soggettoId, @PathVariable long id) {
        Invio invio = trova(soggettoId, id);
        return new Profilo(invio, repository.anagrafica(id), repository.elementi(id), repository.fornitori(id),
                repository.adempimenti(id));
    }

    @GetMapping("/confronto")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Confronto confronto(@PathVariable long soggettoId, @RequestParam long da, @RequestParam long a) {
        return Confronti.confronta(profilo(soggettoId, da), profilo(soggettoId, a));
    }

    /** CSV di una sezione del profilo; ogni estrazione resta tracciata in LogEstrazione. */
    @GetMapping("/{id}/csv/{sezione}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public ResponseEntity<byte[]> csv(@PathVariable long soggettoId, @PathVariable long id,
                                      @PathVariable String sezione) {
        Sezione scelta = sezione(sezione);
        Profilo profilo = profilo(soggettoId, id);
        byte[] contenuto = switch (scelta) {
            case ELEMENTI -> Csv.documento(List.of("id", "tipo", "nome"),
                    profilo.elementi().stream().map(e -> List.<Object>of(e.id(), e.tipo(), e.nome())).toList());
            case FORNITORI -> Csv.documento(
                    List.of("fornitore", "codice_fiscale", "elemento", "criterio_rilevanza", "tipologia_fornitura"),
                    profilo.fornitori().stream().map(f -> java.util.Arrays.<Object>asList(f.fornitore(),
                            f.codiceFiscale(), f.elemento(), f.criterioRilevanza(), f.tipologiaFornitura())).toList());
            case ADEMPIMENTI -> Csv.documento(List.of("requisito", "stato", "data_valutazione"),
                    profilo.adempimenti().stream().map(r -> java.util.Arrays.<Object>asList(r.requisito(),
                            r.stato(), r.dataValutazione())).toList());
        };
        int righe = switch (scelta) {
            case ELEMENTI -> profilo.elementi().size();
            case FORNITORI -> profilo.fornitori().size();
            case ADEMPIMENTI -> profilo.adempimenti().size();
        };
        transazioneAuditata.esegui(() -> {
            repository.registraEstrazione(id, "sezione=" + scelta.name().toLowerCase(Locale.ROOT) + ";righe=" + righe);
            return null;
        });
        String nomeFile = "invio-" + id + "-" + scelta.name().toLowerCase(Locale.ROOT) + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nomeFile).build()
                        .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(contenuto);
    }

    @GetMapping("/{id}/estrazioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Estrazione> estrazioni(@PathVariable long soggettoId, @PathVariable long id) {
        trova(soggettoId, id);
        return repository.estrazioni(id);
    }

    private Invio trova(long soggettoId, long id) {
        return repository.trova(soggettoId, id).orElseThrow(() -> Errori.nonTrovato("Invio " + id));
    }

    private static Sezione sezione(String valore) {
        try {
            return Sezione.valueOf(valore.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw Errori.nonTrovato("Sezione " + valore);
        }
    }
}

package it.gformy.nis2.inventario;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.inventario.ModelloInventario.Collegamento;
import it.gformy.nis2.inventario.ModelloInventario.DatiServizio;
import it.gformy.nis2.inventario.ModelloInventario.Servizio;
import it.gformy.nis2.inventario.ModelloInventario.TipoElemento;
import it.gformy.nis2.inventario.ModelloInventario.VersioneElemento;
import jakarta.validation.Valid;

/** Servizi erogati dal soggetto e asset da cui dipendono. */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/servizi")
public class ServizioController {

    private final InventarioRepository repository;
    private final ControlloElementi controllo;
    private final TransazioneAuditata transazioneAuditata;

    public ServizioController(InventarioRepository repository, ControlloElementi controllo,
                              TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.controllo = controllo;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Servizio> elenco(@PathVariable long soggettoId) {
        return repository.elencoServizi(soggettoId);
    }

    @PostMapping
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Servizio> crea(@PathVariable long soggettoId, @Valid @RequestBody DatiServizio dati) {
        long id = transazioneAuditata.esegui(() -> repository.inserisciServizio(soggettoId, dati));
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/servizi/" + id))
                .body(trova(soggettoId, id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Servizio dettaglio(@PathVariable long soggettoId, @PathVariable long id) {
        return trova(soggettoId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Servizio aggiorna(@PathVariable long soggettoId, @PathVariable long id,
                             @Valid @RequestBody DatiServizio dati) {
        transazioneAuditata.esegui(() -> {
            controllo.attivo(soggettoId, id, TipoElemento.SERVIZIO);
            repository.aggiornaServizio(id, dati);
            return null;
        });
        return trova(soggettoId, id);
    }

    @PostMapping("/{id}/dismissione")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Servizio dismetti(@PathVariable long soggettoId, @PathVariable long id) {
        transazioneAuditata.esegui(() -> {
            controllo.attivo(soggettoId, id, TipoElemento.SERVIZIO);
            repository.dismetti(id);
            return null;
        });
        return trova(soggettoId, id);
    }

    @GetMapping("/{id}/versioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<VersioneElemento> versioni(@PathVariable long soggettoId, @PathVariable long id) {
        controllo.esistente(soggettoId, id, TipoElemento.SERVIZIO);
        return repository.versioni(id);
    }

    @GetMapping("/{id}/asset")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Collegamento> asset(@PathVariable long soggettoId, @PathVariable long id) {
        controllo.esistente(soggettoId, id, TipoElemento.SERVIZIO);
        return repository.collegamentiDelServizio(id);
    }

    /** Collega un asset al servizio: entrambi devono essere attivi e dello stesso soggetto. */
    @PutMapping("/{id}/asset/{assetId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public List<Collegamento> collega(@PathVariable long soggettoId, @PathVariable long id,
                                      @PathVariable long assetId) {
        transazioneAuditata.esegui(() -> {
            controllo.attivo(soggettoId, id, TipoElemento.SERVIZIO);
            controllo.attivo(soggettoId, assetId, TipoElemento.ASSET);
            repository.collega(assetId, id);
            return null;
        });
        return repository.collegamentiDelServizio(id);
    }

    /** Il collegamento non si cancella: cessa, e il trigger del PW ne conserva la storia. */
    @DeleteMapping("/{id}/asset/{assetId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public List<Collegamento> cessa(@PathVariable long soggettoId, @PathVariable long id,
                                    @PathVariable long assetId) {
        boolean cessato = transazioneAuditata.esegui(() -> {
            controllo.esistente(soggettoId, id, TipoElemento.SERVIZIO);
            controllo.esistente(soggettoId, assetId, TipoElemento.ASSET);
            return repository.cessa(assetId, id);
        });
        if (!cessato) {
            throw Errori.nonTrovato("Collegamento attivo tra asset " + assetId + " e servizio " + id);
        }
        return repository.collegamentiDelServizio(id);
    }

    private Servizio trova(long soggettoId, long id) {
        return repository.trovaServizio(soggettoId, id).orElseThrow(() -> Errori.nonTrovato("Servizio " + id));
    }
}

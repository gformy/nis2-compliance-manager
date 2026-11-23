package it.gformy.nis2.inventario;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.inventario.ModelloInventario.Asset;
import it.gformy.nis2.inventario.ModelloInventario.Collegamento;
import it.gformy.nis2.inventario.ModelloInventario.DatiAsset;
import it.gformy.nis2.inventario.ModelloInventario.TipoElemento;
import it.gformy.nis2.inventario.ModelloInventario.VersioneElemento;
import jakarta.validation.Valid;

/** Asset del soggetto: hardware, software, flussi di rete, servizi di fornitori. */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/asset")
public class AssetController {

    private final InventarioRepository repository;
    private final ControlloElementi controllo;
    private final TransazioneAuditata transazioneAuditata;

    public AssetController(InventarioRepository repository, ControlloElementi controllo,
                           TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.controllo = controllo;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Asset> elenco(@PathVariable long soggettoId) {
        return repository.elencoAsset(soggettoId);
    }

    @PostMapping
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Asset> crea(@PathVariable long soggettoId, @Valid @RequestBody DatiAsset dati) {
        long id = transazioneAuditata.esegui(() -> repository.inserisciAsset(soggettoId, dati));
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/asset/" + id))
                .body(trova(soggettoId, id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Asset dettaglio(@PathVariable long soggettoId, @PathVariable long id) {
        return trova(soggettoId, id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Asset aggiorna(@PathVariable long soggettoId, @PathVariable long id, @Valid @RequestBody DatiAsset dati) {
        transazioneAuditata.esegui(() -> {
            controllo.attivo(soggettoId, id, TipoElemento.ASSET);
            repository.aggiornaAsset(id, dati);
            return null;
        });
        return trova(soggettoId, id);
    }

    /** Al posto del DELETE: l'asset resta nello storico e nelle ricostruzioni AS OF. */
    @PostMapping("/{id}/dismissione")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Asset dismetti(@PathVariable long soggettoId, @PathVariable long id) {
        transazioneAuditata.esegui(() -> {
            controllo.attivo(soggettoId, id, TipoElemento.ASSET);
            repository.dismetti(id);
            return null;
        });
        return trova(soggettoId, id);
    }

    @GetMapping("/{id}/versioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<VersioneElemento> versioni(@PathVariable long soggettoId, @PathVariable long id) {
        controllo.esistente(soggettoId, id, TipoElemento.ASSET);
        return repository.versioni(id);
    }

    @GetMapping("/{id}/servizi")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Collegamento> servizi(@PathVariable long soggettoId, @PathVariable long id) {
        controllo.esistente(soggettoId, id, TipoElemento.ASSET);
        return repository.collegamentiDellAsset(id);
    }

    private Asset trova(long soggettoId, long id) {
        return repository.trovaAsset(soggettoId, id).orElseThrow(() -> Errori.nonTrovato("Asset " + id));
    }
}

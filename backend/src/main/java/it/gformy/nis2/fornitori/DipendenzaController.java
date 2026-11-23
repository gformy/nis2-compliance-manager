package it.gformy.nis2.fornitori;

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
import it.gformy.nis2.fornitori.ModelloFornitori.DatiDipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.Dipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.FornitoreRilevante;
import it.gformy.nis2.fornitori.ModelloFornitori.NuovaDipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.StatoDipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.VersioneDipendenza;
import it.gformy.nis2.inventario.InventarioRepository;
import it.gformy.nis2.inventario.ModelloInventario.StatoElemento;
import jakarta.validation.Valid;

/** Dipendenze del soggetto da fornitori terzi e loro sintesi per il profilo ACN. */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}")
public class DipendenzaController {

    private final DipendenzaRepository repository;
    private final FornitoreRepository fornitori;
    private final InventarioRepository inventario;
    private final TransazioneAuditata transazioneAuditata;

    public DipendenzaController(DipendenzaRepository repository, FornitoreRepository fornitori,
                                InventarioRepository inventario, TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.fornitori = fornitori;
        this.inventario = inventario;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping("/dipendenze")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Dipendenza> elenco(@PathVariable long soggettoId) {
        return repository.elenco(soggettoId);
    }

    @PostMapping("/dipendenze")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Dipendenza> crea(@PathVariable long soggettoId, @Valid @RequestBody NuovaDipendenza nuova) {
        long id = transazioneAuditata.esegui(() -> {
            elementoAttivo(soggettoId, nuova.elementoCriticoId());
            fornitori.trova(nuova.fornitoreId())
                    .orElseThrow(() -> Errori.nonTrovato("Fornitore " + nuova.fornitoreId()));
            return repository.inserisci(nuova);
        });
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/dipendenze/" + id))
                .body(trova(soggettoId, id));
    }

    @GetMapping("/dipendenze/{id}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Dipendenza dettaglio(@PathVariable long soggettoId, @PathVariable long id) {
        return trova(soggettoId, id);
    }

    /** Cambia criterio, tipologia e referente, oppure fa cessare o riattiva la dipendenza. */
    @PutMapping("/dipendenze/{id}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Dipendenza aggiorna(@PathVariable long soggettoId, @PathVariable long id,
                               @Valid @RequestBody DatiDipendenza dati) {
        transazioneAuditata.esegui(() -> {
            Dipendenza attuale = trova(soggettoId, id);
            if (dati.stato() == StatoDipendenza.ATTIVA) {
                elementoAttivo(soggettoId, attuale.elementoCriticoId());
            }
            repository.aggiorna(id, dati);
            return null;
        });
        return trova(soggettoId, id);
    }

    @GetMapping("/dipendenze/{id}/versioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<VersioneDipendenza> versioni(@PathVariable long soggettoId, @PathVariable long id) {
        trova(soggettoId, id);
        return repository.versioni(id);
    }

    /** I cinque campi del portale ACN per ogni fornitore rilevante (art. 18). */
    @GetMapping("/fornitori-rilevanti")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<FornitoreRilevante> fornitoriRilevanti(@PathVariable long soggettoId) {
        return repository.fornitoriRilevanti(soggettoId);
    }

    private Dipendenza trova(long soggettoId, long id) {
        return repository.trova(soggettoId, id).orElseThrow(() -> Errori.nonTrovato("Dipendenza " + id));
    }

    /** L'elemento deve essere del soggetto (altrimenti 404) e attivo (altrimenti 409). */
    private void elementoAttivo(long soggettoId, long elementoId) {
        StatoElemento stato = inventario.statoElemento(soggettoId, elementoId)
                .orElseThrow(() -> Errori.nonTrovato("Elemento critico " + elementoId));
        if (stato == StatoElemento.DISMESSO) {
            throw Errori.conflitto("L'elemento critico " + elementoId + " e' dismesso");
        }
    }
}

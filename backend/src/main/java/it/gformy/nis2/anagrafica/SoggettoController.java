package it.gformy.nis2.anagrafica;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.anagrafica.ModelloSoggetto.DatiSoggetto;
import it.gformy.nis2.anagrafica.ModelloSoggetto.NuovoSoggetto;
import it.gformy.nis2.anagrafica.ModelloSoggetto.Soggetto;
import it.gformy.nis2.anagrafica.ModelloSoggetto.SoggettoSintesi;
import it.gformy.nis2.anagrafica.ModelloSoggetto.VersioneSoggetto;
import it.gformy.nis2.accesso.AccessoSoggetti;
import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import jakarta.validation.Valid;

/**
 * API dell'anagrafica del soggetto NIS.
 *
 * Ogni utente vede solo i soggetti a cui e' assegnato (tabella UtenteSoggetto);
 * l'amministratore li vede tutti. I controlli sono in {@link AccessoSoggetti}.
 */
@RestController
@RequestMapping("/api/v1/soggetti")
public class SoggettoController {

    private final SoggettoRepository repository;
    private final TransazioneAuditata transazioneAuditata;
    private final AccessoSoggetti accesso;

    public SoggettoController(SoggettoRepository repository, TransazioneAuditata transazioneAuditata,
                              AccessoSoggetti accesso) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
        this.accesso = accesso;
    }

    @GetMapping
    public List<SoggettoSintesi> elenco() {
        return accesso.amministratore() ? repository.elenco() : repository.elencoPerUtente(accesso.utente());
    }

    @GetMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#id)")
    public Soggetto dettaglio(@PathVariable long id) {
        return repository.trova(id).orElseThrow(() -> nonTrovato(id));
    }

    @PostMapping
    @PreAuthorize("hasRole('AMMINISTRATORE')")
    public ResponseEntity<Soggetto> crea(@Valid @RequestBody NuovoSoggetto nuovo) {
        long id = transazioneAuditata.esegui(() -> repository.inserisci(nuovo.codiceFiscale(), nuovo.dati()));
        Soggetto creato = repository.trova(id).orElseThrow();
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + id)).body(creato);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#id)")
    public Soggetto aggiorna(@PathVariable long id, @Valid @RequestBody DatiSoggetto dati) {
        boolean aggiornato = transazioneAuditata.esegui(() -> repository.aggiorna(id, dati));
        if (!aggiornato) {
            throw nonTrovato(id);
        }
        return repository.trova(id).orElseThrow();
    }

    @GetMapping("/{id}/versioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#id)")
    public List<VersioneSoggetto> versioni(@PathVariable long id) {
        List<VersioneSoggetto> versioni = repository.versioni(id);
        if (versioni.isEmpty()) {
            throw nonTrovato(id);
        }
        return versioni;
    }

    private static ErrorResponseException nonTrovato(long id) {
        return Errori.nonTrovato("Soggetto " + id);
    }
}

package it.gformy.nis2.anagrafica;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
import it.gformy.nis2.audit.TransazioneAuditata;
import jakarta.validation.Valid;

/**
 * API dell'anagrafica del soggetto NIS.
 *
 * Nella tappa 2 ogni utente autenticato vede tutti i soggetti; dalla tappa 3
 * l'accesso sara' limitato ai soggetti assegnati all'utente (tabella UtenteSoggetto).
 */
@RestController
@RequestMapping("/api/v1/soggetti")
public class SoggettoController {

    private final SoggettoRepository repository;
    private final TransazioneAuditata transazioneAuditata;

    public SoggettoController(SoggettoRepository repository, TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping
    public List<SoggettoSintesi> elenco() {
        return repository.elenco();
    }

    @GetMapping("/{id}")
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
    public Soggetto aggiorna(@PathVariable long id, @Valid @RequestBody DatiSoggetto dati) {
        boolean aggiornato = transazioneAuditata.esegui(() -> repository.aggiorna(id, dati));
        if (!aggiornato) {
            throw nonTrovato(id);
        }
        return repository.trova(id).orElseThrow();
    }

    @GetMapping("/{id}/versioni")
    public List<VersioneSoggetto> versioni(@PathVariable long id) {
        List<VersioneSoggetto> versioni = repository.versioni(id);
        if (versioni.isEmpty()) {
            throw nonTrovato(id);
        }
        return versioni;
    }

    private static ErrorResponseException nonTrovato(long id) {
        return new ErrorResponseException(HttpStatus.NOT_FOUND,
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Soggetto " + id + " non trovato"), null);
    }
}

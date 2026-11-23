package it.gformy.nis2.fornitori;

import java.net.URI;
import java.util.List;
import java.util.Map;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.fornitori.ModelloFornitori.CodiceCpv;
import it.gformy.nis2.fornitori.ModelloFornitori.DatiFornitore;
import it.gformy.nis2.fornitori.ModelloFornitori.Fornitore;
import it.gformy.nis2.fornitori.ModelloFornitori.NuovoFornitore;
import it.gformy.nis2.fornitori.ModelloFornitori.VersioneFornitore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Anagrafica dei fornitori, condivisa da tutti i soggetti.
 *
 * Tutti gli utenti autenticati la consultano per scegliere il fornitore di una
 * dipendenza; solo l'amministratore crea o modifica un fornitore, perche' una
 * modifica si vedrebbe in tutti i soggetti che lo usano. Sapere che un fornitore
 * esiste nel catalogo non dice quali soggetti ne dipendono: quello resta nelle
 * dipendenze, visibili solo dentro il proprio soggetto.
 */
@RestController
public class FornitoreController {

    private final FornitoreRepository repository;
    private final TransazioneAuditata transazioneAuditata;

    public FornitoreController(FornitoreRepository repository, TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping("/api/v1/fornitori")
    public List<Fornitore> cerca(@RequestParam(defaultValue = "") @Size(max = 100) String q) {
        return repository.cerca(q.trim());
    }

    @GetMapping("/api/v1/fornitori/{id}")
    public Fornitore dettaglio(@PathVariable long id) {
        return trova(id);
    }

    @PostMapping("/api/v1/fornitori")
    @PreAuthorize("hasRole('AMMINISTRATORE')")
    public ResponseEntity<Fornitore> crea(@Valid @RequestBody NuovoFornitore nuovo) {
        verificaCpv(nuovo.dati().cpv());
        long id = transazioneAuditata.esegui(() -> repository.inserisci(nuovo.codiceFiscale(), nuovo.dati()));
        return ResponseEntity.created(URI.create("/api/v1/fornitori/" + id)).body(trova(id));
    }

    @PutMapping("/api/v1/fornitori/{id}")
    @PreAuthorize("hasRole('AMMINISTRATORE')")
    public Fornitore aggiorna(@PathVariable long id, @Valid @RequestBody DatiFornitore dati) {
        verificaCpv(dati.cpv());
        boolean aggiornato = transazioneAuditata.esegui(() -> repository.aggiorna(id, dati));
        if (!aggiornato) {
            throw Errori.nonTrovato("Fornitore " + id);
        }
        return trova(id);
    }

    @GetMapping("/api/v1/fornitori/{id}/versioni")
    public List<VersioneFornitore> versioni(@PathVariable long id) {
        trova(id);
        return repository.versioni(id);
    }

    @GetMapping("/api/v1/cpv")
    public List<CodiceCpv> catalogoCpv() {
        return repository.catalogoCpv();
    }

    private Fornitore trova(long id) {
        return repository.trova(id).orElseThrow(() -> Errori.nonTrovato("Fornitore " + id));
    }

    private void verificaCpv(List<String> codici) {
        List<String> sconosciuti = repository.codiciSconosciuti(codici);
        if (!sconosciuti.isEmpty()) {
            ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Dati non validi");
            problema.setProperty("errori", Map.of("cpv", "codici non presenti nel catalogo: " + sconosciuti));
            throw new ErrorResponseException(HttpStatus.BAD_REQUEST, problema, null);
        }
    }
}

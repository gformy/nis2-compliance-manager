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
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository;
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository.Quota;
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
    private final OrganizzazioneRepository organizzazioni;

    /** Organizzazione del gestore della piattaforma (V12). */
    static final long ORGANIZZAZIONE_PIATTAFORMA = 1;

    public SoggettoController(SoggettoRepository repository, TransazioneAuditata transazioneAuditata,
                              AccessoSoggetti accesso, OrganizzazioneRepository organizzazioni) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
        this.accesso = accesso;
        this.organizzazioni = organizzazioni;
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

    /**
     * Crea un soggetto nell'organizzazione indicata (amministratore della piattaforma) o in quella
     * dell'amministratore di organizzazione, entro la quota del piano.
     */
    @PostMapping
    @PreAuthorize("hasRole('AMMINISTRATORE') or @accessoSoggetti.amministratoreOrganizzazione()")
    public ResponseEntity<Soggetto> crea(@Valid @RequestBody NuovoSoggetto nuovo) {
        long organizzazione = accesso.amministratore()
                ? (nuovo.organizzazioneId() != null ? nuovo.organizzazioneId() : ORGANIZZAZIONE_PIATTAFORMA)
                : accesso.organizzazioneUtente().orElseThrow();
        if (!accesso.amministratore() && nuovo.organizzazioneId() != null && nuovo.organizzazioneId() != organizzazione) {
            throw Errori.nonTrovato("Organizzazione " + nuovo.organizzazioneId());
        }
        long id = transazioneAuditata.esegui(() -> {
            Quota quota = organizzazioni.trova(organizzazione).map(o -> organizzazioni.bloccaQuota(organizzazione))
                    .orElseThrow(() -> Errori.nonValido("organizzazioneId", "organizzazione inesistente"));
            if (quota.piena()) {
                throw Errori.conflitto("Il piano dell'organizzazione consente al massimo " + quota.massimo()
                        + " soggetti: per aggiungerne altri serve un piano superiore");
            }
            return repository.inserisci(nuovo.codiceFiscale(), nuovo.dati(), organizzazione);
        });
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

    /** Cosa puo' fare l'utente su questo soggetto: il frontend mostra i comandi di conseguenza. */
    public record Permessi(boolean modificare, boolean inviare, boolean gestireAccessi) {
    }

    @GetMapping("/{id}/permessi")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#id)")
    public Permessi permessi(@PathVariable long id) {
        return new Permessi(accesso.puoModificare(id), accesso.puoInviare(id), accesso.puoGestireAccessi(id));
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

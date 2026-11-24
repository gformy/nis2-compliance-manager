package it.gformy.nis2.accesso;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.accesso.AssegnazioneRepository.Assegnazione;
import it.gformy.nis2.anagrafica.SoggettoRepository;
import it.gformy.nis2.comune.Errori;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Gestione di chi lavora su un soggetto. Solo l'amministratore della piattaforma.
 * Ogni cambiamento finisce nel log applicativo con l'autore: chi ha dato o tolto un accesso.
 */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/utenti")
public class AssegnazioniController {

    /** Formato degli username di Keycloak; impedisce anche di iniettare a capo nei log. */
    public static final String FORMATO_UTENTE = "[a-zA-Z0-9._@-]{1,64}";

    public record NuovoRuolo(@NotNull RuoloSoggetto ruolo) {
    }

    private static final Logger LOG = LoggerFactory.getLogger(AssegnazioniController.class);

    private final AssegnazioneRepository assegnazioni;
    private final SoggettoRepository soggetti;
    private final AccessoSoggetti accesso;

    public AssegnazioniController(AssegnazioneRepository assegnazioni, SoggettoRepository soggetti,
                                  AccessoSoggetti accesso) {
        this.assegnazioni = assegnazioni;
        this.soggetti = soggetti;
        this.accesso = accesso;
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoGestireAccessi(#soggettoId)")
    public List<Assegnazione> elenco(@PathVariable long soggettoId) {
        soggettoEsistente(soggettoId);
        return assegnazioni.perSoggetto(soggettoId);
    }

    @PutMapping("/{utente}")
    @PreAuthorize("@accessoSoggetti.puoGestireAccessi(#soggettoId)")
    public List<Assegnazione> assegna(@PathVariable long soggettoId,
                                      @PathVariable @Pattern(regexp = FORMATO_UTENTE) String utente,
                                      @Valid @RequestBody NuovoRuolo richiesta) {
        soggettoEsistente(soggettoId);
        assegnazioni.assegna(utente, soggettoId, richiesta.ruolo(), accesso.utente());
        LOG.info("Accesso concesso: utente={} soggetto={} ruolo={} da={}",
                utente, soggettoId, richiesta.ruolo(), accesso.utente());
        return assegnazioni.perSoggetto(soggettoId);
    }

    @DeleteMapping("/{utente}")
    @PreAuthorize("@accessoSoggetti.puoGestireAccessi(#soggettoId)")
    public ResponseEntity<Void> rimuovi(@PathVariable long soggettoId,
                                        @PathVariable @Pattern(regexp = FORMATO_UTENTE) String utente) {
        if (!assegnazioni.rimuovi(utente, soggettoId)) {
            throw Errori.nonTrovato("Assegnazione di " + utente);
        }
        LOG.info("Accesso revocato: utente={} soggetto={} da={}", utente, soggettoId, accesso.utente());
        return ResponseEntity.noContent().build();
    }

    private void soggettoEsistente(long soggettoId) {
        if (!soggetti.esiste(soggettoId)) {
            throw Errori.nonTrovato("Soggetto " + soggettoId);
        }
    }
}

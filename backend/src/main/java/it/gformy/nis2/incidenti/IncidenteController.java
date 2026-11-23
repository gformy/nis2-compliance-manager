package it.gformy.nis2.incidenti;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import it.gformy.nis2.incidenti.IncidenteRepository.Testata;
import it.gformy.nis2.incidenti.ModelloIncidenti.Catalogo;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiAttivita;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiEvento;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiIoc;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiNotifica;
import it.gformy.nis2.incidenti.ModelloIncidenti.Incidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.Notifica;
import it.gformy.nis2.incidenti.ModelloIncidenti.SintesiIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.StatoIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoNotifica;
import it.gformy.nis2.inventario.InventarioRepository;
import jakarta.validation.Valid;

/**
 * Registro degli incidenti significativi del soggetto, con le scadenze di notifica al CSIRT Italia.
 *
 * Regole:
 * <ul>
 *   <li>il tipo IS-x deve essere applicabile al tipo di soggetto (IS-4 solo essenziali);</li>
 *   <li>gli elementi coinvolti devono appartenere al soggetto;</li>
 *   <li>la data di evidenza fa decorrere i termini: dopo la prima trasmissione non si cambia;</li>
 *   <li>un incidente chiuso accetta ancora notifiche e relazioni, non altre modifiche.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/incidenti")
public class IncidenteController {

    private final IncidenteRepository repository;
    private final InventarioRepository inventario;
    private final TransazioneAuditata transazioneAuditata;
    private final Clock orologio;

    public IncidenteController(IncidenteRepository repository, InventarioRepository inventario,
                               TransazioneAuditata transazioneAuditata, Clock orologio) {
        this.repository = repository;
        this.inventario = inventario;
        this.transazioneAuditata = transazioneAuditata;
        this.orologio = orologio;
    }

    @GetMapping("/catalogo")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Catalogo catalogo(@PathVariable long soggettoId) {
        return new Catalogo(repository.tipiApplicabili(soggettoId), repository.categorie());
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<SintesiIncidente> elenco(@PathVariable long soggettoId) {
        Instant adesso = orologio.instant();
        return repository.elenco(soggettoId).stream()
                .map(t -> new SintesiIncidente(t.id(), t.titolo(), t.tipo().codice(), t.categoria(), t.gravita(),
                        t.stato(), t.dataEvidenza(),
                        Scadenze.prossima(Scadenze.calcola(t.dataEvidenza(), repository.notifiche(t.id()), adesso))
                                .orElse(null)))
                .toList();
    }

    @PostMapping
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Incidente> crea(@PathVariable long soggettoId, @Valid @RequestBody DatiIncidente dati) {
        long id = transazioneAuditata.esegui(() -> {
            long tipo = tipo(soggettoId, dati.tipoIncidente());
            Long categoria = categoria(dati.categoria());
            verificaElementi(soggettoId, dati.elementi());
            long nuovo = repository.inserisci(soggettoId, tipo, categoria, dati);
            repository.sostituisciElementi(nuovo, dati.elementi());
            return nuovo;
        });
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/incidenti/" + id))
                .body(dettaglio(soggettoId, id));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Incidente dettaglio(@PathVariable long soggettoId, @PathVariable long id) {
        Testata t = trova(soggettoId, id);
        List<Notifica> notifiche = repository.notifiche(id);
        return new Incidente(t.id(), t.titolo(), t.descrizione(), t.tipo(), t.categoria(), t.gravita(),
                t.rootCause(), t.stato(), t.dataEvidenza(), t.dataChiusura(), t.dataRegistrazione(),
                t.registratoDa(), repository.elementi(id), notifiche,
                Scadenze.calcola(t.dataEvidenza(), notifiche, orologio.instant()),
                repository.ioc(id), repository.timeline(id), repository.attivita(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente aggiorna(@PathVariable long soggettoId, @PathVariable long id,
                              @Valid @RequestBody DatiIncidente dati) {
        transazioneAuditata.esegui(() -> {
            Testata t = aperto(soggettoId, id);
            if (!t.dataEvidenza().equals(dati.dataEvidenza().truncatedTo(ChronoUnit.MICROS))
                    && !repository.notifiche(id).isEmpty()) {
                throw Errori.conflitto("La data di evidenza non si puo' cambiare dopo la prima trasmissione "
                        + "al CSIRT: da quella data decorrono i termini di notifica");
            }
            long tipo = tipo(soggettoId, dati.tipoIncidente());
            Long categoria = categoria(dati.categoria());
            verificaElementi(soggettoId, dati.elementi());
            repository.aggiorna(id, tipo, categoria, dati);
            repository.sostituisciElementi(id, dati.elementi());
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    @PostMapping("/{id}/chiusura")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente chiudi(@PathVariable long soggettoId, @PathVariable long id) {
        transazioneAuditata.esegui(() -> {
            aperto(soggettoId, id);
            repository.chiudi(id, orologio.instant());
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    /** Registra una trasmissione al CSIRT Italia gia' avvenuta (pre-notifica, notifica, relazioni). */
    @PostMapping("/{id}/notifiche")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente notifica(@PathVariable long soggettoId, @PathVariable long id,
                              @Valid @RequestBody DatiNotifica dati) {
        transazioneAuditata.esegui(() -> {
            Testata t = trova(soggettoId, id);
            if (dati.dataTrasmissione().isBefore(t.dataEvidenza())) {
                throw Errori.nonValido("dataTrasmissione", "non puo' precedere la data di evidenza dell'incidente");
            }
            boolean relazione = dati.tipo() != TipoNotifica.PRE_NOTIFICA && dati.tipo() != TipoNotifica.NOTIFICA;
            if (relazione && repository.notifiche(id).stream().noneMatch(n -> n.tipo() == TipoNotifica.NOTIFICA)) {
                throw Errori.conflitto("Le relazioni seguono la notifica: registrare prima la notifica");
            }
            repository.aggiungiNotifica(id, dati);
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    @PostMapping("/{id}/ioc")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente aggiungiIoc(@PathVariable long soggettoId, @PathVariable long id,
                                 @Valid @RequestBody DatiIoc dati) {
        transazioneAuditata.esegui(() -> {
            aperto(soggettoId, id);
            repository.aggiungiIoc(id, dati, orologio.instant());
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    @DeleteMapping("/{id}/ioc/{iocId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente rimuoviIoc(@PathVariable long soggettoId, @PathVariable long id, @PathVariable long iocId) {
        transazioneAuditata.esegui(() -> {
            aperto(soggettoId, id);
            if (!repository.rimuoviIoc(id, iocId)) {
                throw Errori.nonTrovato("Indicatore " + iocId);
            }
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    @PostMapping("/{id}/timeline")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente aggiungiEvento(@PathVariable long soggettoId, @PathVariable long id,
                                    @Valid @RequestBody DatiEvento dati) {
        transazioneAuditata.esegui(() -> {
            aperto(soggettoId, id);
            repository.aggiungiEvento(id, dati);
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    @PostMapping("/{id}/attivita")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Incidente aggiungiAttivita(@PathVariable long soggettoId, @PathVariable long id,
                                      @Valid @RequestBody DatiAttivita dati) {
        transazioneAuditata.esegui(() -> {
            aperto(soggettoId, id);
            Instant quando = dati.dataEsecuzione() != null ? dati.dataEsecuzione() : orologio.instant();
            repository.aggiungiAttivita(id, dati, quando);
            return null;
        });
        return dettaglio(soggettoId, id);
    }

    // ---------------------------------------------------------------- controlli

    private Testata trova(long soggettoId, long id) {
        return repository.trova(soggettoId, id).orElseThrow(() -> Errori.nonTrovato("Incidente " + id));
    }

    private Testata aperto(long soggettoId, long id) {
        Testata t = trova(soggettoId, id);
        if (t.stato() == StatoIncidente.CHIUSO) {
            throw Errori.conflitto("L'incidente " + id + " e' chiuso: si possono solo registrare notifiche");
        }
        return t;
    }

    private long tipo(long soggettoId, String codice) {
        return repository.tipiApplicabili(soggettoId).stream()
                .filter(t -> t.codice().equals(codice))
                .map(TipoIncidente::id)
                .findFirst()
                .orElseThrow(() -> Errori.nonValido("tipoIncidente",
                        "tipo non applicabile a questo soggetto: " + codice));
    }

    private Long categoria(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        return repository.categoria(nome)
                .orElseThrow(() -> Errori.nonValido("categoria", "categoria sconosciuta: " + nome));
    }

    private void verificaElementi(long soggettoId, List<Long> elementi) {
        for (long elemento : elementi) {
            inventario.statoElemento(soggettoId, elemento)
                    .orElseThrow(() -> Errori.nonTrovato("Elemento critico " + elemento));
        }
    }
}

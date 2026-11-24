package it.gformy.nis2.organizzazioni;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.gformy.nis2.accesso.AccessoSoggetti;
import it.gformy.nis2.accesso.AssegnazioniController;
import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.DatiOrganizzazione;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.Organizzazione;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.Piano;
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository.Logo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

/**
 * Organizzazioni clienti della piattaforma.
 *
 * Il gestore della piattaforma (ruolo AMMINISTRATORE) crea le organizzazioni, assegna il
 * piano e le sospende. Gli amministratori di un'organizzazione vedono solo la propria,
 * aggiungono colleghi e caricano il logo usato nei report.
 */
@RestController
@Validated
public class OrganizzazioneController {

    private static final Logger LOG = LoggerFactory.getLogger(OrganizzazioneController.class);
    private static final int LOGO_MASSIMO = 512 * 1024;

    private final OrganizzazioneRepository repository;
    private final AccessoSoggetti accesso;
    private final TransazioneAuditata transazioneAuditata;

    public OrganizzazioneController(OrganizzazioneRepository repository, AccessoSoggetti accesso,
                                    TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.accesso = accesso;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping("/api/v1/piani")
    public List<Piano> piani() {
        return repository.piani();
    }

    @GetMapping("/api/v1/organizzazioni")
    public List<Organizzazione> elenco() {
        if (accesso.amministratore()) {
            return repository.elenco();
        }
        return accesso.organizzazioneUtente().flatMap(repository::trova).map(List::of).orElse(List.of());
    }

    @PostMapping("/api/v1/organizzazioni")
    @PreAuthorize("hasRole('AMMINISTRATORE')")
    public ResponseEntity<Organizzazione> crea(@Valid @RequestBody DatiOrganizzazione dati) {
        long id = transazioneAuditata.esegui(() -> repository.inserisci(dati, piano(dati.piano())));
        LOG.info("Organizzazione creata: id={} piano={} da={}", id, dati.piano(), accesso.utente());
        return ResponseEntity.created(URI.create("/api/v1/organizzazioni/" + id)).body(trova(id));
    }

    @GetMapping("/api/v1/organizzazioni/{id}")
    @PreAuthorize("@accessoSoggetti.puoGestireOrganizzazione(#id)")
    public Organizzazione dettaglio(@PathVariable long id) {
        return trova(id);
    }

    /** Piano, stato e contratto li decide il gestore della piattaforma. */
    @PutMapping("/api/v1/organizzazioni/{id}")
    @PreAuthorize("hasRole('AMMINISTRATORE')")
    public Organizzazione aggiorna(@PathVariable long id, @Valid @RequestBody DatiOrganizzazione dati) {
        Organizzazione attuale = trova(id);
        int piano = piano(dati.piano());
        Integer massimo = repository.piani().stream().filter(p -> p.codice().equals(dati.piano()))
                .findFirst().map(Piano::maxSoggetti).orElse(null);
        if (massimo != null && attuale.soggetti() > massimo) {
            throw Errori.conflitto("Il piano " + dati.piano() + " consente al massimo " + massimo
                    + " soggetti, l'organizzazione ne ha " + attuale.soggetti());
        }
        transazioneAuditata.esegui(() -> {
            repository.aggiorna(id, dati, piano);
            return null;
        });
        LOG.info("Organizzazione aggiornata: id={} piano={} stato={} da={}", id, dati.piano(), dati.stato(),
                accesso.utente());
        return trova(id);
    }

    @PutMapping("/api/v1/organizzazioni/{id}/amministratori/{utente}")
    @PreAuthorize("@accessoSoggetti.puoGestireOrganizzazione(#id)")
    public Organizzazione aggiungiAmministratore(@PathVariable long id,
            @PathVariable @Pattern(regexp = AssegnazioniController.FORMATO_UTENTE) String utente) {
        trova(id);
        transazioneAuditata.esegui(() -> {
            repository.aggiungiAmministratore(id, utente);
            return null;
        });
        LOG.info("Amministratore di organizzazione aggiunto: utente={} organizzazione={} da={}",
                utente, id, accesso.utente());
        return trova(id);
    }

    @DeleteMapping("/api/v1/organizzazioni/{id}/amministratori/{utente}")
    @PreAuthorize("@accessoSoggetti.puoGestireOrganizzazione(#id)")
    public Organizzazione rimuoviAmministratore(@PathVariable long id,
            @PathVariable @Pattern(regexp = AssegnazioniController.FORMATO_UTENTE) String utente) {
        if (utente.equals(accesso.utente()) && !accesso.amministratore()) {
            throw Errori.conflitto("Non puoi rimuovere te stesso: chiedi a un altro amministratore");
        }
        if (!repository.rimuoviAmministratore(id, utente)) {
            throw Errori.nonTrovato("Amministratore " + utente);
        }
        LOG.info("Amministratore di organizzazione rimosso: utente={} organizzazione={} da={}",
                utente, id, accesso.utente());
        return trova(id);
    }

    /** Logo per i report PDF: PNG o JPEG, al massimo 512 KB, verificato sui primi byte. */
    @PutMapping(path = "/api/v1/organizzazioni/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessoSoggetti.puoGestireOrganizzazione(#id)")
    public Organizzazione caricaLogo(@PathVariable long id, @RequestPart("file") MultipartFile file) {
        trova(id);
        byte[] contenuto;
        try {
            contenuto = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (contenuto.length == 0 || contenuto.length > LOGO_MASSIMO) {
            throw Errori.nonValido("file", "il logo deve essere tra 1 byte e 512 KB");
        }
        String tipo = tipoImmagine(contenuto);
        if (tipo == null) {
            throw Errori.nonValido("file", "il logo deve essere un'immagine PNG o JPEG");
        }
        transazioneAuditata.esegui(() -> {
            repository.salvaLogo(id, contenuto, tipo);
            return null;
        });
        return trova(id);
    }

    @GetMapping("/api/v1/organizzazioni/{id}/logo")
    @PreAuthorize("@accessoSoggetti.puoGestireOrganizzazione(#id)")
    public ResponseEntity<byte[]> logo(@PathVariable long id) {
        Logo logo = repository.logo(id).orElseThrow(() -> Errori.nonTrovato("Logo"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(logo.contenuto());
    }

    static String tipoImmagine(byte[] contenuto) {
        if (contenuto.length >= 8 && (contenuto[0] & 0xFF) == 0x89 && contenuto[1] == 'P' && contenuto[2] == 'N'
                && contenuto[3] == 'G') {
            return "image/png";
        }
        if (contenuto.length >= 3 && (contenuto[0] & 0xFF) == 0xFF && (contenuto[1] & 0xFF) == 0xD8
                && (contenuto[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    private Organizzazione trova(long id) {
        return repository.trova(id).orElseThrow(() -> Errori.nonTrovato("Organizzazione " + id));
    }

    private int piano(String codice) {
        return repository.pianoInVendita(codice)
                .orElseThrow(() -> Errori.nonValido("piano", "piano inesistente o non in vendita: " + codice));
    }
}

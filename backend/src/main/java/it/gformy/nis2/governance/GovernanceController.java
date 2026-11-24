package it.gformy.nis2.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.governance.FileCaricati.FileVerificato;
import it.gformy.nis2.governance.GovernanceRepository.Contenuto;
import it.gformy.nis2.governance.GovernanceRepository.Riga;
import it.gformy.nis2.governance.ModelloGovernance.Categoria;
import it.gformy.nis2.governance.ModelloGovernance.DatiApprovazione;
import it.gformy.nis2.governance.ModelloGovernance.DatiDocumento;
import it.gformy.nis2.governance.ModelloGovernance.DatiNuovoDocumento;
import it.gformy.nis2.governance.ModelloGovernance.DatiOrgano;
import it.gformy.nis2.governance.ModelloGovernance.DatiRiesame;
import it.gformy.nis2.governance.ModelloGovernance.Documento;
import it.gformy.nis2.governance.ModelloGovernance.FileDocumento;
import it.gformy.nis2.governance.ModelloGovernance.Governance;
import it.gformy.nis2.governance.ModelloGovernance.Organo;
import it.gformy.nis2.governance.ModelloGovernance.VersioneDocumento;
import it.gformy.nis2.governance.ModelloGovernance.Voce;
import jakarta.validation.Valid;

/**
 * Politiche, piani e organi di amministrazione del soggetto.
 *
 * Regole:
 * <ul>
 *   <li>un documento si approva solo dopo averne caricato il file, e solo da un organo del soggetto;</li>
 *   <li>caricare un nuovo file annulla l'approvazione: il nuovo contenuto va riapprovato;</li>
 *   <li>il riesame non puo' precedere l'approvazione;</li>
 *   <li>i file non si modificano ne' si cancellano; ogni scaricamento e' tracciato.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}")
public class GovernanceController {

    private static final ZoneId ITALIA = ZoneId.of("Europe/Rome");

    private final GovernanceRepository repository;
    private final TransazioneAuditata transazioneAuditata;
    private final Clock orologio;

    public GovernanceController(GovernanceRepository repository, TransazioneAuditata transazioneAuditata,
                                Clock orologio) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
        this.orologio = orologio;
    }

    // ---------------------------------------------------------------- organi di amministrazione

    @GetMapping("/organi")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Organo> organi(@PathVariable long soggettoId) {
        return repository.organi(soggettoId);
    }

    @PostMapping("/organi")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Organo> nuovoOrgano(@PathVariable long soggettoId, @Valid @RequestBody DatiOrgano dati) {
        long id = transazioneAuditata.esegui(() -> repository.inserisciOrgano(soggettoId, dati));
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/organi/" + id))
                .body(new Organo(id, dati.nome().strip(), dati.ruolo().strip()));
    }

    /** Un organo che ha approvato documenti non si elimina (409 dal vincolo di integrita'). */
    @DeleteMapping("/organi/{organoId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Void> eliminaOrgano(@PathVariable long soggettoId, @PathVariable long organoId) {
        transazioneAuditata.esegui(() -> {
            if (!repository.eliminaOrgano(soggettoId, organoId)) {
                throw Errori.nonTrovato("Organo " + organoId);
            }
            return null;
        });
        return ResponseEntity.noContent().build();
    }

    // ---------------------------------------------------------------- documenti

    @GetMapping("/documenti")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Governance documenti(@PathVariable long soggettoId) {
        Map<Long, FileDocumento> file = repository.ultimiFile(soggettoId);
        LocalDate oggi = LocalDate.now(orologio.withZone(ITALIA));
        List<Voce> voci = repository.righe(soggettoId).stream().map(r -> voce(r, file, oggi)).toList();
        return new Governance(StatoDocumenti.riepiloga(voci), voci);
    }

    @PostMapping("/documenti")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public ResponseEntity<Voce> nuovoDocumento(@PathVariable long soggettoId,
                                               @Valid @RequestBody DatiNuovoDocumento dati) {
        long id = transazioneAuditata.esegui(() -> {
            Riga tipo = repository.righe(soggettoId).stream()
                    .filter(r -> r.codice().equals(dati.tipo()))
                    .findFirst()
                    .orElseThrow(() -> Errori.nonValido("tipo", "documento non previsto per questo soggetto: "
                            + dati.tipo()));
            if (tipo.documentoId() != null) {
                throw Errori.conflitto("Il documento " + dati.tipo() + " esiste gia'");
            }
            return repository.inserisci(soggettoId, tipo.tipoId(), dati);
        });
        return ResponseEntity.created(URI.create("/api/v1/soggetti/" + soggettoId + "/documenti/" + id))
                .body(voce(soggettoId, id));
    }

    @PutMapping("/documenti/{documentoId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Voce aggiorna(@PathVariable long soggettoId, @PathVariable long documentoId,
                         @Valid @RequestBody DatiDocumento dati) {
        transazioneAuditata.esegui(() -> {
            trova(soggettoId, documentoId);
            repository.aggiorna(documentoId, dati);
            return null;
        });
        return voce(soggettoId, documentoId);
    }

    @PostMapping("/documenti/{documentoId}/approvazione")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Voce approva(@PathVariable long soggettoId, @PathVariable long documentoId,
                        @Valid @RequestBody DatiApprovazione dati) {
        transazioneAuditata.esegui(() -> {
            trova(soggettoId, documentoId);
            repository.organo(soggettoId, dati.organoId())
                    .orElseThrow(() -> Errori.nonTrovato("Organo " + dati.organoId()));
            if (repository.files(documentoId).isEmpty()) {
                throw Errori.conflitto("Carica il documento prima di registrarne l'approvazione");
            }
            repository.approva(documentoId, dati.data(), dati.organoId());
            return null;
        });
        return voce(soggettoId, documentoId);
    }

    @PostMapping("/documenti/{documentoId}/riesame")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Voce riesame(@PathVariable long soggettoId, @PathVariable long documentoId,
                        @Valid @RequestBody DatiRiesame dati) {
        transazioneAuditata.esegui(() -> {
            Riga riga = trova(soggettoId, documentoId);
            if (riga.dataApprovazione() == null) {
                throw Errori.conflitto("Il documento non e' ancora approvato: il riesame segue l'approvazione");
            }
            if (dati.data().isBefore(riga.dataApprovazione())) {
                throw Errori.nonValido("data", "il riesame non puo' precedere l'approvazione");
            }
            repository.riesame(documentoId, dati.data());
            return null;
        });
        return voce(soggettoId, documentoId);
    }

    @GetMapping("/documenti/{documentoId}/versioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<VersioneDocumento> versioni(@PathVariable long soggettoId, @PathVariable long documentoId) {
        trova(soggettoId, documentoId);
        return repository.versioni(documentoId);
    }

    // ---------------------------------------------------------------- file

    @PostMapping(path = "/documenti/{documentoId}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Voce carica(@PathVariable long soggettoId, @PathVariable long documentoId,
                       @RequestPart("file") MultipartFile file,
                       @RequestParam(name = "versione", required = false) String versione) {
        if (versione != null && versione.strip().length() > 20) {
            throw Errori.nonValido("versione", "al massimo 20 caratteri");
        }
        FileVerificato verificato = FileCaricati.verifica(file.getOriginalFilename(), byteDi(file));
        transazioneAuditata.esegui(() -> {
            Riga riga = trova(soggettoId, documentoId);
            String etichetta = versione != null && !versione.isBlank() ? versione : riga.versione();
            repository.inserisciFile(documentoId, etichetta, verificato);
            repository.revocaApprovazione(documentoId, versione);
            return null;
        });
        return voce(soggettoId, documentoId);
    }

    @GetMapping("/documenti/{documentoId}/file")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<FileDocumento> files(@PathVariable long soggettoId, @PathVariable long documentoId) {
        trova(soggettoId, documentoId);
        return repository.files(documentoId);
    }

    @GetMapping("/documenti/{documentoId}/file/{fileId}")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public ResponseEntity<byte[]> scarica(@PathVariable long soggettoId, @PathVariable long documentoId,
                                          @PathVariable long fileId) {
        trova(soggettoId, documentoId);
        Contenuto contenuto = repository.contenuto(documentoId, fileId)
                .orElseThrow(() -> Errori.nonTrovato("File " + fileId));
        transazioneAuditata.esegui(() -> {
            repository.registraConsultazione(documentoId, "file=" + fileId + ";sha256=" + contenuto.file().sha256());
            return null;
        });
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contenuto.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(contenuto.file().nomeFile(),
                                java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(contenuto.byteFile());
    }

    // ---------------------------------------------------------------- supporto

    private Riga trova(long soggettoId, long documentoId) {
        return repository.riga(soggettoId, documentoId)
                .orElseThrow(() -> Errori.nonTrovato("Documento " + documentoId));
    }

    private Voce voce(long soggettoId, long documentoId) {
        Riga riga = trova(soggettoId, documentoId);
        return voce(riga, repository.ultimiFile(soggettoId), LocalDate.now(orologio.withZone(ITALIA)));
    }

    private static Voce voce(Riga r, Map<Long, FileDocumento> file, LocalDate oggi) {
        Categoria categoria = r.ambito() != null ? Categoria.POLITICA : Categoria.DOCUMENTO;
        if (r.documentoId() == null) {
            return new Voce(r.codice(), r.nome(), categoria, r.ambito(),
                    StatoDocumenti.stato(false, null, oggi), null);
        }
        LocalDate prossimo = StatoDocumenti.prossimoRiesame(r.dataApprovazione(), r.dataUltimoRiesame(),
                r.periodicita());
        Documento documento = new Documento(r.documentoId(), r.versione(), r.dataApprovazione(), r.approvatoDa(),
                r.dataUltimoRiesame(), r.periodicita(), r.note(), prossimo, file.get(r.documentoId()));
        return new Voce(r.codice(), r.nome(), categoria, r.ambito(), StatoDocumenti.stato(true, prossimo, oggi),
                documento);
    }

    private static byte[] byteDi(MultipartFile file) {
        if (file.getSize() > FileCaricati.DIMENSIONE_MASSIMA) {
            throw Errori.nonValido("file", "il file supera i 10 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

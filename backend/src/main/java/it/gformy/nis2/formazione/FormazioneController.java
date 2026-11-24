package it.gformy.nis2.formazione;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import it.gformy.nis2.formazione.FormazioneRepository.RigaAttivita;
import it.gformy.nis2.formazione.FormazioneRepository.RigaPersona;
import it.gformy.nis2.formazione.ModelloFormazione.Attivita;
import it.gformy.nis2.formazione.ModelloFormazione.DatiAttivita;
import it.gformy.nis2.formazione.ModelloFormazione.DatiCessazione;
import it.gformy.nis2.formazione.ModelloFormazione.DatiPartecipazioni;
import it.gformy.nis2.formazione.ModelloFormazione.DatiPersona;
import it.gformy.nis2.formazione.ModelloFormazione.Formazione;
import it.gformy.nis2.formazione.ModelloFormazione.Partecipazione;
import it.gformy.nis2.formazione.ModelloFormazione.Persona;
import it.gformy.nis2.formazione.ModelloFormazione.Riepilogo;
import it.gformy.nis2.formazione.ModelloFormazione.StatoAttivita;
import it.gformy.nis2.formazione.RegoleFormazione.PersonaValutata;
import it.gformy.nis2.invii.Csv;
import jakarta.validation.Valid;

/**
 * Piano di formazione e registro (PR.AT-01, PR.AT-02).
 *
 * Ogni modifica restituisce lo stato completo della formazione del soggetto, cosi'
 * il frontend non deve ricalcolare coperture e stati.
 */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/formazione")
public class FormazioneController {

    private static final ZoneId ITALIA = ZoneId.of("Europe/Rome");

    private final FormazioneRepository repository;
    private final TransazioneAuditata transazioneAuditata;
    private final Clock orologio;

    public FormazioneController(FormazioneRepository repository, TransazioneAuditata transazioneAuditata,
                                Clock orologio) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
        this.orologio = orologio;
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Formazione formazione(@PathVariable long soggettoId) {
        LocalDate oggi = oggi();
        List<Partecipazione> registro = repository.registro(soggettoId);
        Map<Long, List<Partecipazione>> perPersona = registro.stream()
                .collect(Collectors.groupingBy(Partecipazione::personaId));

        List<Persona> persone = repository.persone(soggettoId).stream().map(p -> {
            LocalDate ultima = RegoleFormazione.ultimaFormazioneValida(p.categoria(),
                    perPersona.getOrDefault(p.id(), List.of()), oggi).orElse(null);
            return new Persona(p.id(), p.nome(), p.categoria(), p.ruolo(), p.dataCessazione(), ultima, ultima != null);
        }).toList();

        List<Attivita> attivita = repository.attivita(soggettoId).stream()
                .map(a -> new Attivita(a.id(), a.titolo(), a.contenuti(), a.destinatari(), a.dataPrevista(),
                        a.durataOre(), a.modalitaVerifica(), a.partecipanti(),
                        RegoleFormazione.statoAttivita(a.dataPrevista(), a.partecipanti(), oggi)))
                .toList();

        Riepilogo riepilogo = new Riepilogo(
                RegoleFormazione.copertura(persone.stream()
                        .map(p -> new PersonaValutata(p.categoria(), attiva(p.dataCessazione(), oggi), p.inRegola()))
                        .toList()),
                conta(attivita, StatoAttivita.PIANIFICATA),
                conta(attivita, StatoAttivita.DA_REGISTRARE));
        return new Formazione(riepilogo, persone, attivita, registro);
    }

    @PostMapping("/persone")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Formazione nuovaPersona(@PathVariable long soggettoId, @Valid @RequestBody DatiPersona dati) {
        transazioneAuditata.esegui(() -> {
            repository.inserisciPersona(soggettoId, dati);
            return null;
        });
        return formazione(soggettoId);
    }

    /** Chi lascia l'organizzazione non si cancella: il registro deve restare completo. */
    @PostMapping("/persone/{personaId}/cessazione")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Formazione cessa(@PathVariable long soggettoId, @PathVariable long personaId,
                            @Valid @RequestBody DatiCessazione dati) {
        transazioneAuditata.esegui(() -> {
            RigaPersona persona = persona(soggettoId, personaId);
            if (persona.dataCessazione() != null) {
                throw Errori.conflitto(persona.nome() + " e' gia' cessata");
            }
            repository.cessa(personaId, dati.data());
            return null;
        });
        return formazione(soggettoId);
    }

    @PostMapping("/attivita")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Formazione nuovaAttivita(@PathVariable long soggettoId, @Valid @RequestBody DatiAttivita dati) {
        transazioneAuditata.esegui(() -> repository.inserisciAttivita(soggettoId, dati));
        return formazione(soggettoId);
    }

    /** I destinatari e la verifica non cambiano dopo la prima partecipazione: il registro resterebbe incoerente. */
    @PutMapping("/attivita/{attivitaId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Formazione aggiornaAttivita(@PathVariable long soggettoId, @PathVariable long attivitaId,
                                       @Valid @RequestBody DatiAttivita dati) {
        transazioneAuditata.esegui(() -> {
            RigaAttivita attivita = attivita(soggettoId, attivitaId);
            boolean cambiaVerifica = (attivita.modalitaVerifica() == null)
                    != (dati.modalitaVerifica() == null || dati.modalitaVerifica().isBlank());
            if (attivita.partecipanti() > 0 && (attivita.destinatari() != dati.destinatari() || cambiaVerifica)) {
                throw Errori.conflitto("L'attivita' ha gia' partecipazioni registrate: destinatari e presenza "
                        + "della verifica non si possono cambiare");
            }
            repository.aggiornaAttivita(attivitaId, dati);
            return null;
        });
        return formazione(soggettoId);
    }

    /** Registra nel registro la partecipazione di una o piu' persone a un'attivita'. */
    @PostMapping("/attivita/{attivitaId}/partecipazioni")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Formazione partecipazioni(@PathVariable long soggettoId, @PathVariable long attivitaId,
                                     @Valid @RequestBody DatiPartecipazioni dati) {
        transazioneAuditata.esegui(() -> {
            RigaAttivita attivita = attivita(soggettoId, attivitaId);
            RegoleFormazione.esitoNonCoerente(attivita.modalitaVerifica(), dati.esito()).ifPresent(errore -> {
                throw Errori.nonValido("esito", errore);
            });
            for (long personaId : dati.persone().stream().distinct().toList()) {
                RigaPersona persona = persona(soggettoId, personaId);
                if (!RegoleFormazione.destinatariCompatibili(attivita.destinatari(), persona.categoria())) {
                    throw Errori.nonValido("persone", persona.nome() + " non rientra tra i destinatari dell'attivita'");
                }
                if (persona.dataCessazione() != null && dati.data().isAfter(persona.dataCessazione())) {
                    throw Errori.nonValido("persone", persona.nome() + " era gia' cessata in quella data");
                }
                repository.inserisciPartecipazione(attivitaId, personaId, dati.data(), dati.esito(), dati.note());
            }
            return null;
        });
        return formazione(soggettoId);
    }

    /** Registro in CSV per le verifiche ACN; l'estrazione resta in LogEstrazione. */
    @GetMapping("/registro.csv")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public ResponseEntity<byte[]> registroCsv(@PathVariable long soggettoId) {
        List<Partecipazione> registro = repository.registro(soggettoId);
        byte[] csv = Csv.documento(
                List.of("data", "persona", "attivita", "destinatari", "esito_verifica", "note", "registrata_da"),
                registro.stream().map(p -> Arrays.<Object>asList(p.dataSvolgimento(), p.persona(), p.attivita(),
                        p.destinatari().name(), p.esito().name(), p.note(), p.registrataDa())).toList());
        transazioneAuditata.esegui(() -> {
            repository.registraEstrazione(soggettoId, "registro_formazione;righe=" + registro.size());
            return null;
        });
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("registro-formazione-" + soggettoId + ".csv")
                                .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(csv);
    }

    private RigaPersona persona(long soggettoId, long personaId) {
        return repository.persona(soggettoId, personaId).orElseThrow(() -> Errori.nonTrovato("Persona " + personaId));
    }

    private RigaAttivita attivita(long soggettoId, long attivitaId) {
        return repository.attivita(soggettoId, attivitaId)
                .orElseThrow(() -> Errori.nonTrovato("Attivita' " + attivitaId));
    }

    private LocalDate oggi() {
        return LocalDate.now(orologio.withZone(ITALIA));
    }

    private static boolean attiva(LocalDate cessazione, LocalDate oggi) {
        return cessazione == null || cessazione.isAfter(oggi);
    }

    private static int conta(List<Attivita> attivita, StatoAttivita stato) {
        return (int) attivita.stream().filter(a -> a.stato() == stato).count();
    }
}

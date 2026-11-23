package it.gformy.nis2.conformita;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import it.gformy.nis2.audit.TransazioneAuditata;
import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.conformita.ModelloConformita.Conteggi;
import it.gformy.nis2.conformita.ModelloConformita.DatiAdempimento;
import it.gformy.nis2.conformita.ModelloConformita.Requisito;
import it.gformy.nis2.conformita.ModelloConformita.Riepilogo;
import it.gformy.nis2.conformita.ModelloConformita.RiepilogoMisura;
import it.gformy.nis2.conformita.ModelloConformita.VersioneAdempimento;
import jakarta.validation.Valid;

/** Stato di attuazione dei requisiti ACN applicabili al soggetto. */
@RestController
@RequestMapping("/api/v1/soggetti/{soggettoId}/adempimenti")
public class ConformitaController {

    private final ConformitaRepository repository;
    private final TransazioneAuditata transazioneAuditata;

    public ConformitaController(ConformitaRepository repository, TransazioneAuditata transazioneAuditata) {
        this.repository = repository;
        this.transazioneAuditata = transazioneAuditata;
    }

    @GetMapping
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<Requisito> requisiti(@PathVariable long soggettoId) {
        return repository.requisiti(soggettoId);
    }

    @GetMapping("/riepilogo")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public Riepilogo riepilogo(@PathVariable long soggettoId) {
        return riepiloga(repository.requisiti(soggettoId));
    }

    /** Valuta un requisito: solo se e' applicabile al tipo di soggetto (altrimenti 404). */
    @PutMapping("/{requisitoId}")
    @PreAuthorize("@accessoSoggetti.puoModificare(#soggettoId)")
    public Requisito valuta(@PathVariable long soggettoId, @PathVariable long requisitoId,
                            @Valid @RequestBody DatiAdempimento dati) {
        transazioneAuditata.esegui(() -> {
            trova(soggettoId, requisitoId);
            repository.valuta(soggettoId, requisitoId, dati);
            return null;
        });
        return trova(soggettoId, requisitoId);
    }

    @GetMapping("/{requisitoId}/versioni")
    @PreAuthorize("@accessoSoggetti.puoLeggere(#soggettoId)")
    public List<VersioneAdempimento> versioni(@PathVariable long soggettoId, @PathVariable long requisitoId) {
        trova(soggettoId, requisitoId);
        return repository.versioni(soggettoId, requisitoId);
    }

    private Requisito trova(long soggettoId, long requisitoId) {
        return repository.requisito(soggettoId, requisitoId)
                .orElseThrow(() -> Errori.nonTrovato("Requisito " + requisitoId + " per questo soggetto"));
    }

    static Riepilogo riepiloga(List<Requisito> requisiti) {
        Map<String, List<Requisito>> perMisura = new LinkedHashMap<>();
        for (Requisito r : requisiti) {
            perMisura.computeIfAbsent(r.misuraCodice(), k -> new java.util.ArrayList<>()).add(r);
        }
        List<RiepilogoMisura> misure = perMisura.values().stream()
                .map(lista -> {
                    Requisito primo = lista.getFirst();
                    Conteggi conteggi = conta(lista);
                    return new RiepilogoMisura(primo.misuraCodice(), primo.misuraCategoria(),
                            primo.misuraDescrizione(), conteggi, conteggi.percentualeAttuazione());
                })
                .toList();
        Conteggi complessivo = conta(requisiti);
        return new Riepilogo(complessivo, complessivo.percentualeAttuazione(), misure);
    }

    private static Conteggi conta(List<Requisito> requisiti) {
        int attuati = 0;
        int parziali = 0;
        int nonAttuati = 0;
        int nonApplicabili = 0;
        int nonValutati = 0;
        for (Requisito r : requisiti) {
            if (r.stato() == null) {
                nonValutati++;
                continue;
            }
            switch (r.stato()) {
                case ATTUATO -> attuati++;
                case PARZIALMENTE_ATTUATO -> parziali++;
                case NON_ATTUATO -> nonAttuati++;
                case NON_APPLICABILE -> nonApplicabili++;
            }
        }
        return new Conteggi(requisiti.size(), attuati, parziali, nonAttuati, nonApplicabili, nonValutati);
    }
}

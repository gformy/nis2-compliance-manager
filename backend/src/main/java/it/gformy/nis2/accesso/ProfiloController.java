package it.gformy.nis2.accesso;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;

import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.StatoOrganizzazione;
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository;
import org.springframework.web.bind.annotation.RestController;

/**
 * Chi sono e cosa posso fare: il frontend lo usa per mostrare o nascondere
 * i comandi. E' solo una comodita' per l'interfaccia: i permessi veri li
 * controlla sempre il backend a ogni richiesta.
 */
@RestController
public class ProfiloController {

    public record SoggettoAssegnato(long soggettoId, RuoloSoggetto ruolo) {
    }

    public record Profilo(String utente, boolean amministratore, List<SoggettoAssegnato> soggetti,
                          OrganizzazioneUtente organizzazione) {
    }

    /** Organizzazione che l'utente amministra, se ne ha una. */
    public record OrganizzazioneUtente(long id, String nome, boolean sospesa) {
    }

    private final AccessoSoggetti accesso;
    private final AssegnazioneRepository assegnazioni;
    private final OrganizzazioneRepository organizzazioni;

    public ProfiloController(AccessoSoggetti accesso, AssegnazioneRepository assegnazioni,
                             OrganizzazioneRepository organizzazioni) {
        this.accesso = accesso;
        this.assegnazioni = assegnazioni;
        this.organizzazioni = organizzazioni;
    }

    @GetMapping("/api/v1/me")
    public Profilo profilo() {
        String utente = accesso.utente();
        List<SoggettoAssegnato> soggetti = assegnazioni.perUtente(utente).stream()
                .map(a -> new SoggettoAssegnato(a.soggettoId(), a.ruolo()))
                .toList();
        OrganizzazioneUtente organizzazione = accesso.organizzazioneUtente()
                .flatMap(organizzazioni::trova)
                .map(o -> new OrganizzazioneUtente(o.id(), o.nome(), o.stato() == StatoOrganizzazione.SOSPESA))
                .orElse(null);
        return new Profilo(utente, accesso.amministratore(), soggetti, organizzazione);
    }
}

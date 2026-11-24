package it.gformy.nis2.accesso;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import it.gformy.nis2.comune.Errori;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.StatoOrganizzazione;
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository;
import it.gformy.nis2.organizzazioni.OrganizzazioneRepository.AppartenenzaSoggetto;

/**
 * Controllo di accesso per soggetto: il cuore dell'isolamento tra clienti.
 *
 * Si usa nelle annotazioni dei controller, per esempio
 * {@code @PreAuthorize("@accessoSoggetti.puoLeggere(#id)")}, cosi' il controllo
 * avviene prima di eseguire qualunque query sui dati del soggetto.
 *
 * Chi accede a un soggetto:
 * <ul>
 *   <li>l'amministratore della piattaforma (ruolo Keycloak AMMINISTRATORE), sempre;</li>
 *   <li>gli amministratori dell'organizzazione a cui il soggetto appartiene, come un referente NIS;</li>
 *   <li>gli utenti assegnati al soggetto, con il ruolo dell'assegnazione.</li>
 * </ul>
 * Se l'organizzazione e' sospesa i dati restano consultabili ma non modificabili.
 *
 * Un soggetto a cui l'utente non ha accesso risulta "non trovato" (404) e non
 * "vietato" (403): chi prova id a caso non scopre nemmeno quali soggetti esistono.
 * Un revisore che tenta una modifica riceve invece 403, perche' il soggetto lo vede gia'.
 */
@Component("accessoSoggetti")
public class AccessoSoggetti {

    public static final String RUOLO_AMMINISTRATORE = "ROLE_AMMINISTRATORE";

    private final AssegnazioneRepository assegnazioni;
    private final OrganizzazioneRepository organizzazioni;

    public AccessoSoggetti(AssegnazioneRepository assegnazioni, OrganizzazioneRepository organizzazioni) {
        this.assegnazioni = assegnazioni;
        this.organizzazioni = organizzazioni;
    }

    private record Accesso(boolean amministratoreOrganizzazione, RuoloSoggetto ruolo, boolean sospesa) {

        boolean puoModificare() {
            return !sospesa && (amministratoreOrganizzazione || ruolo.puoModificare());
        }

        boolean puoInviare() {
            return !sospesa && (amministratoreOrganizzazione || ruolo.puoInviare());
        }
    }

    public boolean puoLeggere(long soggettoId) {
        if (amministratore()) {
            return true;
        }
        accessoObbligatorio(soggettoId);
        return true;
    }

    public boolean puoModificare(long soggettoId) {
        return amministratore() || accessoObbligatorio(soggettoId).puoModificare();
    }

    public boolean puoInviare(long soggettoId) {
        return amministratore() || accessoObbligatorio(soggettoId).puoInviare();
    }

    /** Assegnare utenti a un soggetto: amministratore della piattaforma o della sua organizzazione. */
    public boolean puoGestireAccessi(long soggettoId) {
        if (amministratore()) {
            return true;
        }
        Accesso accesso = accessoObbligatorio(soggettoId);
        return accesso.amministratoreOrganizzazione() && !accesso.sospesa();
    }

    /** Vedere e gestire un'organizzazione: amministratore della piattaforma o suo membro. */
    public boolean puoGestireOrganizzazione(long organizzazioneId) {
        if (amministratore()) {
            return true;
        }
        if (organizzazioneUtente().filter(id -> id == organizzazioneId).isEmpty()) {
            throw Errori.nonTrovato("Organizzazione " + organizzazioneId);
        }
        return true;
    }

    public boolean amministratoreOrganizzazione() {
        return organizzazioneUtente().isPresent();
    }

    public Optional<Long> organizzazioneUtente() {
        return organizzazioni.organizzazioneDi(utente());
    }

    public Optional<RuoloSoggetto> ruolo(long soggettoId) {
        return assegnazioni.ruolo(utente(), soggettoId);
    }

    public boolean amministratore() {
        return autenticazione().getAuthorities().stream()
                .anyMatch(autorita -> RUOLO_AMMINISTRATORE.equals(autorita.getAuthority()));
    }

    public String utente() {
        return autenticazione().getName();
    }

    private Accesso accessoObbligatorio(long soggettoId) {
        AppartenenzaSoggetto appartenenza = organizzazioni.appartenenza(soggettoId)
                .orElseThrow(() -> Errori.nonTrovato("Soggetto " + soggettoId));
        boolean sospesa = appartenenza.stato() == StatoOrganizzazione.SOSPESA;
        boolean amministratoreOrganizzazione = organizzazioneUtente()
                .filter(id -> id == appartenenza.organizzazioneId()).isPresent();
        if (amministratoreOrganizzazione) {
            return new Accesso(true, RuoloSoggetto.REFERENTE_NIS, sospesa);
        }
        RuoloSoggetto ruolo = ruolo(soggettoId).orElseThrow(() -> Errori.nonTrovato("Soggetto " + soggettoId));
        return new Accesso(false, ruolo, sospesa);
    }

    private static Authentication autenticazione() {
        Authentication autenticazione = SecurityContextHolder.getContext().getAuthentication();
        if (autenticazione == null) {
            throw new IllegalStateException("Nessun utente autenticato");
        }
        return autenticazione;
    }
}

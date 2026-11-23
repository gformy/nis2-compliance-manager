package it.gformy.nis2.accesso;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import it.gformy.nis2.comune.Errori;

/**
 * Controllo di accesso per soggetto: il cuore dell'isolamento tra clienti.
 *
 * Si usa nelle annotazioni dei controller, per esempio
 * {@code @PreAuthorize("@accessoSoggetti.puoLeggere(#id)")}, cosi' il controllo
 * avviene prima di eseguire qualunque query sui dati del soggetto.
 *
 * Un soggetto a cui l'utente non e' assegnato risulta "non trovato" (404) e non
 * "vietato" (403): chi prova id a caso non scopre nemmeno quali soggetti esistono.
 * Un revisore che tenta una modifica riceve invece 403, perche' il soggetto lo vede gia'.
 */
@Component("accessoSoggetti")
public class AccessoSoggetti {

    public static final String RUOLO_AMMINISTRATORE = "ROLE_AMMINISTRATORE";

    private final AssegnazioneRepository assegnazioni;

    public AccessoSoggetti(AssegnazioneRepository assegnazioni) {
        this.assegnazioni = assegnazioni;
    }

    public boolean puoLeggere(long soggettoId) {
        if (amministratore()) {
            return true;
        }
        ruoloObbligatorio(soggettoId);
        return true;
    }

    public boolean puoModificare(long soggettoId) {
        if (amministratore()) {
            return true;
        }
        return ruoloObbligatorio(soggettoId).puoModificare();
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

    private RuoloSoggetto ruoloObbligatorio(long soggettoId) {
        return ruolo(soggettoId).orElseThrow(() -> Errori.nonTrovato("Soggetto " + soggettoId));
    }

    private static Authentication autenticazione() {
        Authentication autenticazione = SecurityContextHolder.getContext().getAuthentication();
        if (autenticazione == null) {
            throw new IllegalStateException("Nessun utente autenticato");
        }
        return autenticazione;
    }
}

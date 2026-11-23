package it.gformy.nis2.audit;

import java.util.function.Supplier;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Esegue una modifica in una transazione in cui Oracle sa chi e' l'utente.
 *
 * Il backend usa sempre lo stesso utente di database (nis2_app). Per far
 * registrare ai trigger del PW la persona reale, all'inizio della transazione
 * si imposta il CLIENT_IDENTIFIER della sessione con l'utente del token; la
 * funzione fn_utente_corrente (migrazione V4) lo legge e lo scrive in
 * LogEstrazione. Alla fine l'identificativo viene azzerato, perche' la
 * connessione torna nel pool e servira' a un'altra richiesta.
 */
@Component
public class TransazioneAuditata {

    /** Limite di Oracle per CLIENT_IDENTIFIER. */
    private static final int LUNGHEZZA_MASSIMA = 64;

    private final TransactionTemplate transazione;
    private final JdbcClient jdbc;

    public TransazioneAuditata(TransactionTemplate transazione, JdbcClient jdbc) {
        this.transazione = transazione;
        this.jdbc = jdbc;
    }

    public <T> T esegui(Supplier<T> operazione) {
        String utente = utenteCorrente();
        return transazione.execute(stato -> {
            jdbc.sql("BEGIN DBMS_SESSION.SET_IDENTIFIER(?); END;").param(utente).update();
            try {
                return operazione.get();
            } finally {
                jdbc.sql("BEGIN DBMS_SESSION.CLEAR_IDENTIFIER; END;").update();
            }
        });
    }

    static String utenteCorrente() {
        Authentication autenticazione = SecurityContextHolder.getContext().getAuthentication();
        if (autenticazione == null || autenticazione.getName() == null) {
            throw new IllegalStateException("Modifica senza utente autenticato");
        }
        String nome = autenticazione.getName();
        return nome.length() > LUNGHEZZA_MASSIMA ? nome.substring(0, LUNGHEZZA_MASSIMA) : nome;
    }
}

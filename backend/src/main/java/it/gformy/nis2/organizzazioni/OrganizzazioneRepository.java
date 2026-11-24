package it.gformy.nis2.organizzazioni;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.Amministratore;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.DatiOrganizzazione;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.Organizzazione;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.Piano;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.StatoOrganizzazione;
import it.gformy.nis2.organizzazioni.ModelloOrganizzazioni.TipoOrganizzazione;

/** Organizzazioni, piani e appartenenza degli utenti (V12). */
@Repository
public class OrganizzazioneRepository {

    /** Organizzazione e stato di un soggetto, per i controlli di accesso. */
    public record AppartenenzaSoggetto(long organizzazioneId, StatoOrganizzazione stato) {
    }

    public record Quota(Integer massimo, int usati) {
        public boolean piena() {
            return massimo != null && usati >= massimo;
        }
    }

    public record Logo(byte[] contenuto, String contentType) {
    }

    private static final String ORGANIZZAZIONI = """
            SELECT o.id, o.nome, o.codice_fiscale, o.tipo, o.stato, o.riferimento_contratto, o.data_contratto,
                   CASE WHEN o.logo IS NULL THEN 0 ELSE 1 END AS con_logo,
                   p.codice, p.nome AS piano_nome, p.max_soggetti, p.prezzo_mensile_soggetto,
                   (SELECT COUNT(*) FROM SoggettoNIS s WHERE s.organizzazione_id = o.id) AS soggetti
            FROM Organizzazione o JOIN PianoAbbonamento p ON p.id = o.piano_id
            """;

    private final JdbcClient jdbc;

    public OrganizzazioneRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Piano> piani() {
        return jdbc.sql("""
                SELECT codice, nome, max_soggetti, prezzo_mensile_soggetto
                FROM PianoAbbonamento WHERE in_vendita = 1 ORDER BY prezzo_mensile_soggetto DESC
                """)
                .query((rs, n) -> piano(rs, "nome")).list();
    }

    public Optional<Integer> pianoInVendita(String codice) {
        return jdbc.sql("SELECT id FROM PianoAbbonamento WHERE codice = ? AND in_vendita = 1")
                .param(codice).query(Integer.class).optional();
    }

    public List<Organizzazione> elenco() {
        return jdbc.sql(ORGANIZZAZIONI + " ORDER BY o.nome").query(this::organizzazione).list();
    }

    public Optional<Organizzazione> trova(long id) {
        return jdbc.sql(ORGANIZZAZIONI + " WHERE o.id = ?").param(id).query(this::organizzazione).optional();
    }

    public long inserisci(DatiOrganizzazione dati, int pianoId) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO Organizzazione (nome, codice_fiscale, tipo, piano_id, stato, riferimento_contratto,
                                            data_contratto)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(dati.nome().strip(), dati.codiceFiscale(), dati.tipo().name(), pianoId,
                        (dati.stato() == null ? StatoOrganizzazione.ATTIVA : dati.stato()).name(),
                        vuotoNull(dati.riferimentoContratto()),
                        dati.dataContratto() == null ? null : Date.valueOf(dati.dataContratto()))
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    public void aggiorna(long id, DatiOrganizzazione dati, int pianoId) {
        jdbc.sql("""
                UPDATE Organizzazione SET nome = ?, codice_fiscale = ?, tipo = ?, piano_id = ?, stato = ?,
                                          riferimento_contratto = ?, data_contratto = ?
                WHERE id = ?
                """)
                .params(dati.nome().strip(), dati.codiceFiscale(), dati.tipo().name(), pianoId,
                        (dati.stato() == null ? StatoOrganizzazione.ATTIVA : dati.stato()).name(),
                        vuotoNull(dati.riferimentoContratto()),
                        dati.dataContratto() == null ? null : Date.valueOf(dati.dataContratto()), id)
                .update();
    }

    // ---------------------------------------------------------------- appartenenza e quota

    public Optional<Long> organizzazioneDi(String utente) {
        return jdbc.sql("SELECT organizzazione_id FROM UtenteOrganizzazione WHERE utente = ?")
                .param(utente).query(Long.class).optional();
    }

    public Optional<AppartenenzaSoggetto> appartenenza(long soggettoId) {
        return jdbc.sql("""
                SELECT s.organizzazione_id, o.stato FROM SoggettoNIS s
                JOIN Organizzazione o ON o.id = s.organizzazione_id WHERE s.id = ?
                """)
                .param(soggettoId)
                .query((rs, n) -> new AppartenenzaSoggetto(rs.getLong("organizzazione_id"),
                        StatoOrganizzazione.valueOf(rs.getString("stato"))))
                .optional();
    }

    /** Blocca la riga dell'organizzazione fino a fine transazione e restituisce la quota usata. */
    public Quota bloccaQuota(long organizzazioneId) {
        return jdbc.sql("""
                SELECT p.max_soggetti,
                       (SELECT COUNT(*) FROM SoggettoNIS s WHERE s.organizzazione_id = o.id) AS usati
                FROM Organizzazione o JOIN PianoAbbonamento p ON p.id = o.piano_id
                WHERE o.id = ? FOR UPDATE OF o.id
                """)
                .param(organizzazioneId)
                .query((rs, n) -> new Quota(intero(rs, "max_soggetti"), rs.getInt("usati")))
                .single();
    }

    public List<Amministratore> amministratori(long organizzazioneId) {
        return jdbc.sql("""
                SELECT utente, data_assegnazione, assegnato_da FROM UtenteOrganizzazione
                WHERE organizzazione_id = ? ORDER BY utente
                """)
                .param(organizzazioneId)
                .query((rs, n) -> new Amministratore(rs.getString("utente"),
                        rs.getObject("data_assegnazione", LocalDateTime.class), rs.getString("assegnato_da")))
                .list();
    }

    public void aggiungiAmministratore(long organizzazioneId, String utente) {
        jdbc.sql("""
                MERGE INTO UtenteOrganizzazione u
                USING (SELECT ? AS utente, ? AS organizzazione_id FROM dual) s ON (u.utente = s.utente)
                WHEN NOT MATCHED THEN INSERT (utente, organizzazione_id) VALUES (s.utente, s.organizzazione_id)
                WHEN MATCHED THEN UPDATE SET organizzazione_id = s.organizzazione_id
                """)
                .params(utente, organizzazioneId).update();
    }

    public boolean rimuoviAmministratore(long organizzazioneId, String utente) {
        return jdbc.sql("DELETE FROM UtenteOrganizzazione WHERE utente = ? AND organizzazione_id = ?")
                .params(utente, organizzazioneId).update() == 1;
    }

    // ---------------------------------------------------------------- logo

    public Optional<Logo> logo(long organizzazioneId) {
        return jdbc.sql("SELECT logo, logo_content_type FROM Organizzazione WHERE id = ? AND logo IS NOT NULL")
                .param(organizzazioneId)
                .query((rs, n) -> new Logo(rs.getBytes("logo"), rs.getString("logo_content_type")))
                .optional();
    }

    public void salvaLogo(long organizzazioneId, byte[] contenuto, String contentType) {
        jdbc.sql("UPDATE Organizzazione SET logo = ?, logo_content_type = ? WHERE id = ?")
                .params(contenuto, contentType, organizzazioneId).update();
    }

    // ---------------------------------------------------------------- conversioni

    private Organizzazione organizzazione(ResultSet rs, int n) throws SQLException {
        long id = rs.getLong("id");
        Piano piano = piano(rs, "piano_nome");
        int soggetti = rs.getInt("soggetti");
        Date contratto = rs.getDate("data_contratto");
        return new Organizzazione(id, rs.getString("nome"), rs.getString("codice_fiscale"),
                TipoOrganizzazione.valueOf(rs.getString("tipo")), StatoOrganizzazione.valueOf(rs.getString("stato")),
                piano, soggetti, piano.prezzoMensileSoggetto().multiply(BigDecimal.valueOf(soggetti)),
                rs.getString("riferimento_contratto"), contratto == null ? null : contratto.toLocalDate(),
                rs.getInt("con_logo") == 1, amministratori(id));
    }

    private static Piano piano(ResultSet rs, String colonnaNome) throws SQLException {
        return new Piano(rs.getString("codice"), rs.getString(colonnaNome), intero(rs, "max_soggetti"),
                rs.getBigDecimal("prezzo_mensile_soggetto"));
    }

    private static Integer intero(ResultSet rs, String colonna) throws SQLException {
        int valore = rs.getInt(colonna);
        return rs.wasNull() ? null : valore;
    }

    private static String vuotoNull(String valore) {
        return valore == null || valore.isBlank() ? null : valore.strip();
    }
}

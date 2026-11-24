package it.gformy.nis2.anagrafica;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.anagrafica.ModelloSoggetto.DatiSoggetto;
import it.gformy.nis2.anagrafica.ModelloSoggetto.Soggetto;
import it.gformy.nis2.anagrafica.ModelloSoggetto.SoggettoSintesi;
import it.gformy.nis2.anagrafica.ModelloSoggetto.TipoSoggetto;
import it.gformy.nis2.anagrafica.ModelloSoggetto.VersioneSoggetto;

/**
 * Accesso a SoggettoNIS con SQL esplicito: le query sono quelle che si
 * scriverebbero in SQL Developer, con parametri sempre in bind (niente
 * concatenazioni, quindi niente SQL injection).
 */
@Repository
public class SoggettoRepository {

    private final JdbcClient jdbc;

    public SoggettoRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SINTESI = """
            SELECT DISTINCT s.id, s.codice_fiscale, s.denominazione, t.codice AS tipo_soggetto,
                   o.id AS organizzazione_id, o.nome AS organizzazione
            FROM SoggettoNIS s
            JOIN TipoSoggettoNIS t ON t.id = s.tipo_soggetto_id
            JOIN Organizzazione o ON o.id = s.organizzazione_id
            """;

    public List<SoggettoSintesi> elenco() {
        return jdbc.sql(SINTESI + " ORDER BY s.denominazione").query(SoggettoRepository::sintesi).list();
    }

    /**
     * Solo i soggetti a cui l'utente e' assegnato o che appartengono all'organizzazione
     * che amministra: il filtro e' nella query, non nel codice Java.
     */
    public List<SoggettoSintesi> elencoPerUtente(String utente) {
        return jdbc.sql(SINTESI + """
                WHERE s.id IN (SELECT us.soggetto_nis_id FROM UtenteSoggetto us WHERE us.utente = ?)
                   OR s.organizzazione_id IN (SELECT uo.organizzazione_id FROM UtenteOrganizzazione uo
                                              WHERE uo.utente = ?)
                ORDER BY s.denominazione
                """)
                .params(utente, utente)
                .query(SoggettoRepository::sintesi)
                .list();
    }

    private static SoggettoSintesi sintesi(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new SoggettoSintesi(
                rs.getLong("id"),
                rs.getString("codice_fiscale"),
                rs.getString("denominazione"),
                TipoSoggetto.valueOf(rs.getString("tipo_soggetto")),
                rs.getLong("organizzazione_id"),
                rs.getString("organizzazione"));
    }

    public boolean esiste(long id) {
        return jdbc.sql("SELECT COUNT(*) FROM SoggettoNIS WHERE id = ?").param(id).query(Integer.class).single() > 0;
    }

    public Optional<Soggetto> trova(long id) {
        return jdbc.sql("""
                SELECT s.id, s.codice_fiscale, s.denominazione, s.indirizzo_sede_legale, s.pec,
                       s.email_funzionale, s.telefono, s.fatturato, s.bilancio, s.n_dipendenti,
                       t.codice AS tipo_soggetto, s.data_creazione
                FROM SoggettoNIS s
                JOIN TipoSoggettoNIS t ON t.id = s.tipo_soggetto_id
                WHERE s.id = ?
                """)
                .param(id)
                .query(SoggettoRepository::soggetto)
                .optional();
    }

    public long inserisci(String codiceFiscale, DatiSoggetto dati, long organizzazioneId) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO SoggettoNIS (codice_fiscale, denominazione, indirizzo_sede_legale, pec,
                                         email_funzionale, telefono, fatturato, bilancio, n_dipendenti,
                                         tipo_soggetto_id, organizzazione_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, (SELECT id FROM TipoSoggettoNIS WHERE codice = ?), ?)
                """)
                .params(codiceFiscale, dati.denominazione(), dati.indirizzoSedeLegale(), dati.pec(),
                        dati.emailFunzionale(), dati.telefono(), dati.fatturato(), dati.bilancio(),
                        dati.numeroDipendenti(), dati.tipoSoggetto().name(), organizzazioneId)
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    /** Il trigger trg_storico_soggettonis del PW salva la versione precedente. */
    public boolean aggiorna(long id, DatiSoggetto dati) {
        int righe = jdbc.sql("""
                UPDATE SoggettoNIS
                SET denominazione = ?, indirizzo_sede_legale = ?, pec = ?, email_funzionale = ?,
                    telefono = ?, fatturato = ?, bilancio = ?, n_dipendenti = ?,
                    tipo_soggetto_id = (SELECT id FROM TipoSoggettoNIS WHERE codice = ?)
                WHERE id = ?
                """)
                .params(dati.denominazione(), dati.indirizzoSedeLegale(), dati.pec(), dati.emailFunzionale(),
                        dati.telefono(), dati.fatturato(), dati.bilancio(), dati.numeroDipendenti(),
                        dati.tipoSoggetto().name(), id)
                .update();
        return righe == 1;
    }

    /**
     * Versioni dalla vista del PW, dalla piu' recente. L'autore della versione N
     * e' l'utente registrato in LogEstrazione quando si e' chiusa la versione N-1.
     */
    public List<VersioneSoggetto> versioni(long id) {
        return jdbc.sql("""
                SELECT v.versione, v.denominazione, v.indirizzo_sede_legale, v.pec, v.email_funzionale,
                       v.telefono, v.fatturato, v.bilancio, v.n_dipendenti, v.valido_da, v.valido_a,
                       (SELECT l.utente
                        FROM SoggettoNISStorico st
                        JOIN LogEstrazione l ON l.id = st.log_estrazione_id
                        WHERE st.soggetto_nis_id = v.soggetto_nis_id
                          AND st.versione = v.versione - 1) AS modificata_da
                FROM SoggettoNIS_Versioni v
                WHERE v.soggetto_nis_id = ?
                ORDER BY v.versione DESC
                """)
                .param(id)
                .query((rs, n) -> new VersioneSoggetto(
                        rs.getInt("versione"),
                        rs.getString("denominazione"),
                        rs.getString("indirizzo_sede_legale"),
                        rs.getString("pec"),
                        rs.getString("email_funzionale"),
                        rs.getString("telefono"),
                        rs.getBigDecimal("fatturato"),
                        rs.getBigDecimal("bilancio"),
                        intero(rs, "n_dipendenti"),
                        dataOra(rs, "valido_da"),
                        dataOra(rs, "valido_a"),
                        rs.getString("modificata_da")))
                .list();
    }

    private static Soggetto soggetto(ResultSet rs, int riga) throws SQLException {
        return new Soggetto(
                rs.getLong("id"),
                rs.getString("codice_fiscale"),
                rs.getString("denominazione"),
                rs.getString("indirizzo_sede_legale"),
                rs.getString("pec"),
                rs.getString("email_funzionale"),
                rs.getString("telefono"),
                rs.getBigDecimal("fatturato"),
                rs.getBigDecimal("bilancio"),
                intero(rs, "n_dipendenti"),
                TipoSoggetto.valueOf(rs.getString("tipo_soggetto")),
                dataOra(rs, "data_creazione"));
    }

    private static Integer intero(ResultSet rs, String colonna) throws SQLException {
        BigDecimal valore = rs.getBigDecimal(colonna);
        return valore == null ? null : valore.intValueExact();
    }

    private static LocalDateTime dataOra(ResultSet rs, String colonna) throws SQLException {
        Timestamp valore = rs.getTimestamp(colonna);
        return valore == null ? null : valore.toLocalDateTime();
    }
}

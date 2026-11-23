package it.gformy.nis2.accesso;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Lettura e scrittura di UtenteSoggetto. */
@Repository
public class AssegnazioneRepository {

    public record Assegnazione(String utente, long soggettoId, RuoloSoggetto ruolo,
                               LocalDateTime dataAssegnazione, String assegnatoDa) {
    }

    private final JdbcClient jdbc;

    public AssegnazioneRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<RuoloSoggetto> ruolo(String utente, long soggettoId) {
        return jdbc.sql("SELECT ruolo FROM UtenteSoggetto WHERE utente = ? AND soggetto_nis_id = ?")
                .params(utente, soggettoId)
                .query((rs, n) -> RuoloSoggetto.valueOf(rs.getString("ruolo")))
                .optional();
    }

    public List<Assegnazione> perUtente(String utente) {
        return jdbc.sql("""
                SELECT utente, soggetto_nis_id, ruolo, data_assegnazione, assegnato_da
                FROM UtenteSoggetto WHERE utente = ? ORDER BY soggetto_nis_id
                """)
                .param(utente)
                .query((rs, n) -> assegnazione(rs))
                .list();
    }

    public List<Assegnazione> perSoggetto(long soggettoId) {
        return jdbc.sql("""
                SELECT utente, soggetto_nis_id, ruolo, data_assegnazione, assegnato_da
                FROM UtenteSoggetto WHERE soggetto_nis_id = ? ORDER BY utente
                """)
                .param(soggettoId)
                .query((rs, n) -> assegnazione(rs))
                .list();
    }

    /** Crea l'assegnazione o ne cambia il ruolo. */
    public void assegna(String utente, long soggettoId, RuoloSoggetto ruolo, String assegnatoDa) {
        jdbc.sql("""
                MERGE INTO UtenteSoggetto t
                USING (SELECT ? AS utente, ? AS soggetto_nis_id FROM dual) s
                ON (t.utente = s.utente AND t.soggetto_nis_id = s.soggetto_nis_id)
                WHEN MATCHED THEN UPDATE SET ruolo = ?, assegnato_da = ?, data_assegnazione = SYSTIMESTAMP
                WHEN NOT MATCHED THEN INSERT (utente, soggetto_nis_id, ruolo, assegnato_da)
                     VALUES (s.utente, s.soggetto_nis_id, ?, ?)
                """)
                .params(utente, soggettoId, ruolo.name(), assegnatoDa, ruolo.name(), assegnatoDa)
                .update();
    }

    public boolean rimuovi(String utente, long soggettoId) {
        return jdbc.sql("DELETE FROM UtenteSoggetto WHERE utente = ? AND soggetto_nis_id = ?")
                .params(utente, soggettoId)
                .update() == 1;
    }

    private static Assegnazione assegnazione(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Assegnazione(
                rs.getString("utente"),
                rs.getLong("soggetto_nis_id"),
                RuoloSoggetto.valueOf(rs.getString("ruolo")),
                rs.getTimestamp("data_assegnazione").toLocalDateTime(),
                rs.getString("assegnato_da"));
    }
}

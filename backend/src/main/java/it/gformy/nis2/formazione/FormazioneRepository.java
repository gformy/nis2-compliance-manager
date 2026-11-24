package it.gformy.nis2.formazione;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.formazione.ModelloFormazione.Categoria;
import it.gformy.nis2.formazione.ModelloFormazione.DatiAttivita;
import it.gformy.nis2.formazione.ModelloFormazione.DatiPersona;
import it.gformy.nis2.formazione.ModelloFormazione.Destinatari;
import it.gformy.nis2.formazione.ModelloFormazione.Esito;
import it.gformy.nis2.formazione.ModelloFormazione.Partecipazione;

/** Persone, attivita' del piano e registro delle partecipazioni (V11). */
@Repository
public class FormazioneRepository {

    public record RigaPersona(long id, String nome, Categoria categoria, String ruolo, LocalDate dataCessazione) {
    }

    public record RigaAttivita(long id, String titolo, String contenuti, Destinatari destinatari,
                               LocalDate dataPrevista, java.math.BigDecimal durataOre, String modalitaVerifica,
                               int partecipanti) {
    }

    private static final String PERSONE = """
            SELECT id, nome, categoria, ruolo, data_cessazione FROM PersonaFormazione
            """;

    private static final String ATTIVITA = """
            SELECT a.id, a.titolo, a.contenuti, a.destinatari, a.data_prevista, a.durata_ore, a.modalita_verifica,
                   (SELECT COUNT(*) FROM PartecipazioneFormazione p WHERE p.attivita_id = a.id) AS partecipanti
            FROM AttivitaFormativa a
            """;

    private final JdbcClient jdbc;

    public FormazioneRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- persone

    public List<RigaPersona> persone(long soggettoId) {
        return jdbc.sql(PERSONE + " WHERE soggetto_nis_id = ? ORDER BY categoria, nome")
                .param(soggettoId).query(FormazioneRepository::persona).list();
    }

    public Optional<RigaPersona> persona(long soggettoId, long personaId) {
        return jdbc.sql(PERSONE + " WHERE id = ? AND soggetto_nis_id = ?")
                .params(personaId, soggettoId).query(FormazioneRepository::persona).optional();
    }

    public void inserisciPersona(long soggettoId, DatiPersona dati) {
        jdbc.sql("INSERT INTO PersonaFormazione (soggetto_nis_id, nome, categoria, ruolo) VALUES (?, ?, ?, ?)")
                .params(soggettoId, dati.nome().strip(), dati.categoria().name(), vuotoNull(dati.ruolo()))
                .update();
    }

    public void cessa(long personaId, LocalDate data) {
        jdbc.sql("UPDATE PersonaFormazione SET data_cessazione = ? WHERE id = ?")
                .params(Date.valueOf(data), personaId).update();
    }

    // ---------------------------------------------------------------- attivita'

    public List<RigaAttivita> attivita(long soggettoId) {
        return jdbc.sql(ATTIVITA + " WHERE a.soggetto_nis_id = ? ORDER BY a.data_prevista DESC, a.id DESC")
                .param(soggettoId).query(FormazioneRepository::attivita).list();
    }

    public Optional<RigaAttivita> attivita(long soggettoId, long attivitaId) {
        return jdbc.sql(ATTIVITA + " WHERE a.id = ? AND a.soggetto_nis_id = ?")
                .params(attivitaId, soggettoId).query(FormazioneRepository::attivita).optional();
    }

    public long inserisciAttivita(long soggettoId, DatiAttivita dati) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO AttivitaFormativa (soggetto_nis_id, titolo, contenuti, destinatari, data_prevista,
                                               durata_ore, modalita_verifica)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(soggettoId, dati.titolo().strip(), dati.contenuti().strip(), dati.destinatari().name(),
                        Date.valueOf(dati.dataPrevista()), dati.durataOre(), vuotoNull(dati.modalitaVerifica()))
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    public void aggiornaAttivita(long attivitaId, DatiAttivita dati) {
        jdbc.sql("""
                UPDATE AttivitaFormativa SET titolo = ?, contenuti = ?, destinatari = ?, data_prevista = ?,
                                             durata_ore = ?, modalita_verifica = ?
                WHERE id = ?
                """)
                .params(dati.titolo().strip(), dati.contenuti().strip(), dati.destinatari().name(),
                        Date.valueOf(dati.dataPrevista()), dati.durataOre(), vuotoNull(dati.modalitaVerifica()),
                        attivitaId)
                .update();
    }

    // ---------------------------------------------------------------- registro

    public List<Partecipazione> registro(long soggettoId) {
        return jdbc.sql("""
                SELECT p.id, p.attivita_id, a.titolo, a.destinatari, p.persona_id, pe.nome, p.data_svolgimento,
                       p.esito_verifica, p.note, p.registrata_il, p.registrata_da
                FROM PartecipazioneFormazione p
                JOIN AttivitaFormativa a ON a.id = p.attivita_id
                JOIN PersonaFormazione pe ON pe.id = p.persona_id
                WHERE a.soggetto_nis_id = ?
                ORDER BY p.data_svolgimento DESC, pe.nome, p.id DESC
                """)
                .param(soggettoId)
                .query((rs, n) -> new Partecipazione(rs.getLong("id"), rs.getLong("attivita_id"),
                        rs.getString("titolo"), Destinatari.valueOf(rs.getString("destinatari")),
                        rs.getLong("persona_id"), rs.getString("nome"),
                        rs.getDate("data_svolgimento").toLocalDate(), Esito.valueOf(rs.getString("esito_verifica")),
                        rs.getString("note"), rs.getObject("registrata_il", LocalDateTime.class),
                        rs.getString("registrata_da")))
                .list();
    }

    public void inserisciPartecipazione(long attivitaId, long personaId, LocalDate data, Esito esito, String note) {
        jdbc.sql("""
                INSERT INTO PartecipazioneFormazione (attivita_id, persona_id, data_svolgimento, esito_verifica, note)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(attivitaId, personaId, Date.valueOf(data), esito.name(), vuotoNull(note))
                .update();
    }

    public void registraEstrazione(long soggettoId, String dettaglio) {
        jdbc.sql("""
                INSERT INTO LogEstrazione (registro_tabella_id, record_id, utente, tipo_operazione, snapshot_dati)
                VALUES ((SELECT id FROM RegistroTabelle WHERE nome_tabella = 'PartecipazioneFormazione'), ?,
                        fn_utente_corrente, 'ESTRAZIONE_CSV', ?)
                """)
                .params(soggettoId, dettaglio).update();
    }

    // ---------------------------------------------------------------- conversioni

    private static RigaPersona persona(ResultSet rs, int n) throws SQLException {
        Date cessazione = rs.getDate("data_cessazione");
        return new RigaPersona(rs.getLong("id"), rs.getString("nome"), Categoria.valueOf(rs.getString("categoria")),
                rs.getString("ruolo"), cessazione == null ? null : cessazione.toLocalDate());
    }

    private static RigaAttivita attivita(ResultSet rs, int n) throws SQLException {
        return new RigaAttivita(rs.getLong("id"), rs.getString("titolo"), rs.getString("contenuti"),
                Destinatari.valueOf(rs.getString("destinatari")), rs.getDate("data_prevista").toLocalDate(),
                rs.getBigDecimal("durata_ore"), rs.getString("modalita_verifica"), rs.getInt("partecipanti"));
    }

    private static String vuotoNull(String valore) {
        return valore == null || valore.isBlank() ? null : valore.strip();
    }
}

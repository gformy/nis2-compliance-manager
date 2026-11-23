package it.gformy.nis2.incidenti;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.incidenti.ModelloIncidenti.Attivita;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiAttivita;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiEvento;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiIoc;
import it.gformy.nis2.incidenti.ModelloIncidenti.DatiNotifica;
import it.gformy.nis2.incidenti.ModelloIncidenti.ElementoCoinvolto;
import it.gformy.nis2.incidenti.ModelloIncidenti.EventoTimeline;
import it.gformy.nis2.incidenti.ModelloIncidenti.FaseRisposta;
import it.gformy.nis2.incidenti.ModelloIncidenti.FaseTimeline;
import it.gformy.nis2.incidenti.ModelloIncidenti.Gravita;
import it.gformy.nis2.incidenti.ModelloIncidenti.Ioc;
import it.gformy.nis2.incidenti.ModelloIncidenti.Notifica;
import it.gformy.nis2.incidenti.ModelloIncidenti.StatoIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoIncidente;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoIoc;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoNotifica;

/**
 * Accesso alle tabelle degli incidenti del PW. I TIMESTAMP sono in UTC (vedi V8):
 * si leggono come LocalDateTime e si convertono in Instant senza passare dal fuso della JVM.
 */
@Repository
public class IncidenteRepository {

    /** Riga base dell'incidente, prima di aggiungere elementi, notifiche e cronologia. */
    public record Testata(
            long id, long soggettoId, String titolo, String descrizione, TipoIncidente tipo, String categoria,
            Gravita gravita, String rootCause, StatoIncidente stato, Instant dataEvidenza, Instant dataChiusura,
            Instant dataRegistrazione, String registratoDa) {
    }

    private static final String TESTATA = """
            SELECT i.id, i.soggetto_nis_id, i.titolo, i.descrizione, i.gravita, i.root_cause, i.stato,
                   i.data_evidenza, i.data_chiusura, i.data_registrazione, i.registrato_da,
                   ti.id AS tipo_id, ti.codice AS tipo_codice, ti.descrizione AS tipo_descrizione,
                   c.nome AS categoria
            FROM Incidente i
            JOIN TipoIncidente ti ON ti.id = i.tipo_incidente_id
            LEFT JOIN CategoriaIncidente c ON c.id = i.categoria_id
            """;

    private final JdbcClient jdbc;

    public IncidenteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- cataloghi

    /** Tipi IS-x applicabili al tipo del soggetto e in vigore oggi (IS-4 solo per gli essenziali). */
    public List<TipoIncidente> tipiApplicabili(long soggettoId) {
        return jdbc.sql("""
                SELECT ti.id, ti.codice, ti.descrizione
                FROM SoggettoNIS s
                JOIN IncidenteApplicabilita ia ON ia.tipo_soggetto_id = s.tipo_soggetto_id
                JOIN TipoIncidente ti ON ti.id = ia.tipo_incidente_id
                WHERE s.id = ?
                  AND ti.data_inizio_validita <= CURRENT_DATE
                  AND (ti.data_fine_validita IS NULL OR ti.data_fine_validita > CURRENT_DATE)
                ORDER BY ti.codice
                """)
                .param(soggettoId)
                .query((rs, n) -> new TipoIncidente(rs.getLong("id"), rs.getString("codice"),
                        rs.getString("descrizione")))
                .list();
    }

    public List<String> categorie() {
        return jdbc.sql("SELECT nome FROM CategoriaIncidente ORDER BY nome").query(String.class).list();
    }

    public Optional<Long> categoria(String nome) {
        return jdbc.sql("SELECT id FROM CategoriaIncidente WHERE nome = ?").param(nome).query(Long.class).optional();
    }

    // ---------------------------------------------------------------- incidente

    public List<Testata> elenco(long soggettoId) {
        return jdbc.sql(TESTATA + " WHERE i.soggetto_nis_id = ? ORDER BY i.data_evidenza DESC, i.id DESC")
                .param(soggettoId)
                .query(IncidenteRepository::testata)
                .list();
    }

    /** L'incidente solo se appartiene al soggetto: protegge dagli id di altri soggetti nell'URL. */
    public Optional<Testata> trova(long soggettoId, long id) {
        return jdbc.sql(TESTATA + " WHERE i.id = ? AND i.soggetto_nis_id = ?")
                .params(id, soggettoId)
                .query(IncidenteRepository::testata)
                .optional();
    }

    public long inserisci(long soggettoId, long tipoId, Long categoriaId, DatiIncidente dati) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO Incidente (tipo_incidente_id, soggetto_nis_id, categoria_id, data_evidenza, gravita,
                                       root_cause, titolo, descrizione)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(tipoId, soggettoId, categoriaId, utc(dati.dataEvidenza()), nome(dati.gravita()),
                        dati.rootCause(), dati.titolo().strip(), dati.descrizione())
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    public void aggiorna(long id, long tipoId, Long categoriaId, DatiIncidente dati) {
        jdbc.sql("""
                UPDATE Incidente SET tipo_incidente_id = ?, categoria_id = ?, data_evidenza = ?, gravita = ?,
                                     root_cause = ?, titolo = ?, descrizione = ?
                WHERE id = ?
                """)
                .params(tipoId, categoriaId, utc(dati.dataEvidenza()), nome(dati.gravita()), dati.rootCause(),
                        dati.titolo().strip(), dati.descrizione(), id)
                .update();
    }

    public void chiudi(long id, Instant quando) {
        jdbc.sql("UPDATE Incidente SET stato = 'CHIUSO', data_chiusura = ? WHERE id = ?")
                .params(utc(quando), id)
                .update();
    }

    // ---------------------------------------------------------------- elementi coinvolti

    public List<ElementoCoinvolto> elementi(long incidenteId) {
        return jdbc.sql("""
                SELECT ec.id, ec.nome, ec.tipo
                FROM IncidenteElementoCritico iec
                JOIN ElementoCritico ec ON ec.id = iec.elemento_critico_id
                WHERE iec.incidente_id = ?
                ORDER BY ec.tipo, ec.nome
                """)
                .param(incidenteId)
                .query((rs, n) -> new ElementoCoinvolto(rs.getLong("id"), rs.getString("nome"), rs.getString("tipo")))
                .list();
    }

    public void sostituisciElementi(long incidenteId, List<Long> elementi) {
        jdbc.sql("DELETE FROM IncidenteElementoCritico WHERE incidente_id = ?").param(incidenteId).update();
        for (long elemento : elementi) {
            jdbc.sql("INSERT INTO IncidenteElementoCritico (incidente_id, elemento_critico_id) VALUES (?, ?)")
                    .params(incidenteId, elemento)
                    .update();
        }
    }

    // ---------------------------------------------------------------- notifiche

    public List<Notifica> notifiche(long incidenteId) {
        return jdbc.sql("""
                SELECT id, tipo, data_trasmissione, contenuto, registrata_da
                FROM NotificaIncidente WHERE incidente_id = ? ORDER BY data_trasmissione, id
                """)
                .param(incidenteId)
                .query((rs, n) -> new Notifica(rs.getLong("id"), TipoNotifica.valueOf(rs.getString("tipo")),
                        istante(rs, "data_trasmissione"), rs.getString("contenuto"), rs.getString("registrata_da")))
                .list();
    }

    public void aggiungiNotifica(long incidenteId, DatiNotifica dati) {
        jdbc.sql("""
                INSERT INTO NotificaIncidente (incidente_id, tipo, data_trasmissione, contenuto)
                VALUES (?, ?, ?, ?)
                """)
                .params(incidenteId, dati.tipo().name(), utc(dati.dataTrasmissione()), dati.contenuto())
                .update();
    }

    // ---------------------------------------------------------------- IoC, cronologia, risposta

    public List<Ioc> ioc(long incidenteId) {
        return jdbc.sql("""
                SELECT id, tipo, valore, data_rilevazione
                FROM IndicatoreCompromissione WHERE incidente_id = ? ORDER BY data_rilevazione, id
                """)
                .param(incidenteId)
                .query((rs, n) -> new Ioc(rs.getLong("id"), TipoIoc.valueOf(rs.getString("tipo")),
                        rs.getString("valore"), istante(rs, "data_rilevazione")))
                .list();
    }

    public void aggiungiIoc(long incidenteId, DatiIoc dati, Instant quando) {
        jdbc.sql("""
                INSERT INTO IndicatoreCompromissione (incidente_id, tipo, valore, data_rilevazione)
                VALUES (?, ?, ?, ?)
                """)
                .params(incidenteId, dati.tipo().name(), dati.valore().strip(), utc(quando))
                .update();
    }

    /** Rimuove l'IoC solo se appartiene all'incidente; restituisce false altrimenti. */
    public boolean rimuoviIoc(long incidenteId, long iocId) {
        return jdbc.sql("DELETE FROM IndicatoreCompromissione WHERE id = ? AND incidente_id = ?")
                .params(iocId, incidenteId)
                .update() == 1;
    }

    public List<EventoTimeline> timeline(long incidenteId) {
        return jdbc.sql("""
                SELECT id, data_ora, fase, descrizione
                FROM TimelineEvento WHERE incidente_id = ? ORDER BY data_ora, id
                """)
                .param(incidenteId)
                .query((rs, n) -> new EventoTimeline(rs.getLong("id"), istante(rs, "data_ora"),
                        FaseTimeline.valueOf(rs.getString("fase")), rs.getString("descrizione")))
                .list();
    }

    public void aggiungiEvento(long incidenteId, DatiEvento dati) {
        jdbc.sql("INSERT INTO TimelineEvento (incidente_id, data_ora, fase, descrizione) VALUES (?, ?, ?, ?)")
                .params(incidenteId, utc(dati.dataOra()), dati.fase().name(), dati.descrizione().strip())
                .update();
    }

    public List<Attivita> attivita(long incidenteId) {
        return jdbc.sql("""
                SELECT id, fase, descrizione, motivazione, esito, data_esecuzione
                FROM AttivitaRisposta WHERE incidente_id = ? ORDER BY data_esecuzione, id
                """)
                .param(incidenteId)
                .query((rs, n) -> new Attivita(rs.getLong("id"), FaseRisposta.valueOf(rs.getString("fase")),
                        rs.getString("descrizione"), rs.getString("motivazione"), rs.getString("esito"),
                        istante(rs, "data_esecuzione")))
                .list();
    }

    public void aggiungiAttivita(long incidenteId, DatiAttivita dati, Instant quando) {
        jdbc.sql("""
                INSERT INTO AttivitaRisposta (incidente_id, fase, descrizione, motivazione, esito, data_esecuzione)
                VALUES (?, ?, ?, ?, ?, ?)
                """)
                .params(incidenteId, dati.fase().name(), dati.descrizione().strip(), dati.motivazione(),
                        dati.esito(), utc(quando))
                .update();
    }

    // ---------------------------------------------------------------- conversioni

    private static Testata testata(ResultSet rs, int n) throws SQLException {
        String gravita = rs.getString("gravita");
        return new Testata(
                rs.getLong("id"),
                rs.getLong("soggetto_nis_id"),
                rs.getString("titolo"),
                rs.getString("descrizione"),
                new TipoIncidente(rs.getLong("tipo_id"), rs.getString("tipo_codice"), rs.getString("tipo_descrizione")),
                rs.getString("categoria"),
                gravita == null ? null : Gravita.valueOf(gravita),
                rs.getString("root_cause"),
                StatoIncidente.valueOf(rs.getString("stato")),
                istante(rs, "data_evidenza"),
                istante(rs, "data_chiusura"),
                istante(rs, "data_registrazione"),
                rs.getString("registrato_da"));
    }

    static Instant istante(ResultSet rs, String colonna) throws SQLException {
        LocalDateTime valore = rs.getObject(colonna, LocalDateTime.class);
        return valore == null ? null : valore.toInstant(ZoneOffset.UTC);
    }

    static LocalDateTime utc(Instant istante) {
        return istante == null ? null : LocalDateTime.ofInstant(istante, ZoneOffset.UTC);
    }

    private static String nome(Enum<?> valore) {
        return valore == null ? null : valore.name();
    }
}

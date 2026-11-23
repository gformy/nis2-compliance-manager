package it.gformy.nis2.invii;

import java.sql.Date;
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

import it.gformy.nis2.invii.ModelloInvii.AdempimentoProfilo;
import it.gformy.nis2.invii.ModelloInvii.Anagrafica;
import it.gformy.nis2.invii.ModelloInvii.DatiInvio;
import it.gformy.nis2.invii.ModelloInvii.ElementoProfilo;
import it.gformy.nis2.invii.ModelloInvii.Estrazione;
import it.gformy.nis2.invii.ModelloInvii.FornitoreProfilo;
import it.gformy.nis2.invii.ModelloInvii.Invio;
import it.gformy.nis2.invii.ModelloInvii.TipoInvio;

/**
 * Invii ACN e ricostruzione del profilo dalle viste ProfiloACN_*_AsOf del PW.
 *
 * Il profilo dichiarato comprende gli elementi ATTIVI e le dipendenze ATTIVE
 * all'istante dell'invio: un elemento dismesso prima dell'invio non era piu' dichiarato.
 */
@Repository
public class InvioRepository {

    private final JdbcClient jdbc;

    public InvioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Invio> elenco(long soggettoId) {
        return jdbc.sql("""
                SELECT id, tipo_invio, data_invio, periodo_riferimento, registrato_da, note
                FROM InvioACN WHERE soggetto_nis_id = ? ORDER BY data_invio DESC, id DESC
                """)
                .param(soggettoId)
                .query(InvioRepository::invio)
                .list();
    }

    public Optional<Invio> trova(long soggettoId, long id) {
        return jdbc.sql("""
                SELECT id, tipo_invio, data_invio, periodo_riferimento, registrato_da, note
                FROM InvioACN WHERE id = ? AND soggetto_nis_id = ?
                """)
                .params(id, soggettoId)
                .query(InvioRepository::invio)
                .optional();
    }

    public long inserisci(long soggettoId, DatiInvio dati) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO InvioACN (soggetto_nis_id, tipo_invio, periodo_riferimento, note) VALUES (?, ?, ?, ?)")
                .params(soggettoId, dati.tipo().name(), dati.periodoRiferimento(), dati.note())
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    // ---------------------------------------------------------------- profilo AS OF

    public Anagrafica anagrafica(long invioId) {
        return jdbc.sql("""
                SELECT denominazione, indirizzo_sede_legale, pec, fatturato, n_dipendenti
                FROM ProfiloACN_Anagrafica_AsOf WHERE invio_id = ?
                """)
                .param(invioId)
                .query((rs, n) -> new Anagrafica(rs.getString("denominazione"), rs.getString("indirizzo_sede_legale"),
                        rs.getString("pec"), rs.getBigDecimal("fatturato"),
                        rs.getObject("n_dipendenti") == null ? null : rs.getLong("n_dipendenti")))
                .optional()
                .orElse(new Anagrafica(null, null, null, null, null));
    }

    public List<ElementoProfilo> elementi(long invioId) {
        return jdbc.sql("""
                SELECT elemento_critico_id, nome, tipo
                FROM ProfiloACN_ElementiCritici_AsOf
                WHERE invio_id = ? AND stato_al_momento_invio = 'ATTIVO'
                ORDER BY tipo, nome
                """)
                .param(invioId)
                .query((rs, n) -> new ElementoProfilo(rs.getLong("elemento_critico_id"), rs.getString("nome"),
                        rs.getString("tipo")))
                .list();
    }

    public List<FornitoreProfilo> fornitori(long invioId) {
        return jdbc.sql("""
                SELECT dipendenza_id, denominazione, fornitore_codice_fiscale, elemento_nome,
                       criterio_rilevanza, tipologia_fornitura
                FROM ProfiloACN_FornitoriRilevanti_AsOf
                WHERE invio_id = ? AND stato_al_momento_invio = 'ATTIVA'
                ORDER BY denominazione, elemento_nome
                """)
                .param(invioId)
                .query((rs, n) -> new FornitoreProfilo(rs.getLong("dipendenza_id"), rs.getString("denominazione"),
                        rs.getString("fornitore_codice_fiscale"), rs.getString("elemento_nome"),
                        rs.getString("criterio_rilevanza"), rs.getString("tipologia_fornitura")))
                .list();
    }

    public List<AdempimentoProfilo> adempimenti(long invioId) {
        return jdbc.sql("""
                SELECT requisito, stato_al_momento_invio, data_valutazione
                FROM ProfiloACN_Adempimento_AsOf WHERE invio_id = ? ORDER BY requisito
                """)
                .param(invioId)
                .query((rs, n) -> {
                    Date data = rs.getDate("data_valutazione");
                    return new AdempimentoProfilo(rs.getString("requisito"), rs.getString("stato_al_momento_invio"),
                            data == null ? null : data.toLocalDate());
                })
                .list();
    }

    // ---------------------------------------------------------------- audit delle estrazioni

    /** Traccia l'estrazione in LogEstrazione, con l'utente della transazione auditata. */
    public void registraEstrazione(long invioId, String dettaglio) {
        jdbc.sql("""
                INSERT INTO LogEstrazione (registro_tabella_id, record_id, utente, tipo_operazione, snapshot_dati)
                VALUES ((SELECT id FROM RegistroTabelle WHERE nome_tabella = 'InvioACN'), ?,
                        fn_utente_corrente, 'ESTRAZIONE_CSV', ?)
                """)
                .params(invioId, dettaglio)
                .update();
    }

    public List<Estrazione> estrazioni(long invioId) {
        return jdbc.sql("""
                SELECT l.data_ora, l.utente, TO_CHAR(DBMS_LOB.SUBSTR(l.snapshot_dati, 500, 1)) AS dettaglio
                FROM LogEstrazione l JOIN RegistroTabelle r ON r.id = l.registro_tabella_id
                WHERE r.nome_tabella = 'InvioACN' AND l.record_id = ? AND l.tipo_operazione = 'ESTRAZIONE_CSV'
                ORDER BY l.data_ora DESC, l.id DESC
                """)
                .param(invioId)
                .query((rs, n) -> new Estrazione(istante(rs, "data_ora"), rs.getString("utente"),
                        rs.getString("dettaglio")))
                .list();
    }

    private static Invio invio(ResultSet rs, int n) throws SQLException {
        return new Invio(rs.getLong("id"), TipoInvio.valueOf(rs.getString("tipo_invio")), istante(rs, "data_invio"),
                rs.getInt("periodo_riferimento"), rs.getString("registrato_da"), rs.getString("note"));
    }

    private static Instant istante(ResultSet rs, String colonna) throws SQLException {
        LocalDateTime valore = rs.getObject(colonna, LocalDateTime.class);
        return valore == null ? null : valore.toInstant(ZoneOffset.UTC);
    }
}

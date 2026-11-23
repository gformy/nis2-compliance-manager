package it.gformy.nis2.inventario;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.inventario.ModelloInventario.Asset;
import it.gformy.nis2.inventario.ModelloInventario.Collegamento;
import it.gformy.nis2.inventario.ModelloInventario.DatiAsset;
import it.gformy.nis2.inventario.ModelloInventario.DatiServizio;
import it.gformy.nis2.inventario.ModelloInventario.Servizio;
import it.gformy.nis2.inventario.ModelloInventario.StatoCollegamento;
import it.gformy.nis2.inventario.ModelloInventario.StatoElemento;
import it.gformy.nis2.inventario.ModelloInventario.StatoMembroUE;
import it.gformy.nis2.inventario.ModelloInventario.TipoAsset;
import it.gformy.nis2.inventario.ModelloInventario.TipoElemento;
import it.gformy.nis2.inventario.ModelloInventario.VersioneElemento;

/**
 * Accesso alla gerarchia ElementoCritico -> Asset / Servizio del PW.
 *
 * Regole che arrivano dallo schema e che qui si rispettano:
 * - si inserisce dalle viste AssetCompleto e ServizioCompleto (trigger INSTEAD OF),
 *   che creano insieme la riga padre e quella figlia con lo stesso id;
 * - non si cancella: si dismette con stato = 'DISMESSO' e il trigger salva la versione;
 * - ogni query porta il soggetto: un id di un altro soggetto risulta inesistente.
 */
@Repository
public class InventarioRepository {

    private static final String COLONNE_ASSET = """
            SELECT ec.id, ec.nome, a.tipo_asset, a.rilevante, ec.stato,
                   ec.data_inizio_validita, ec.data_fine_validita
            FROM ElementoCritico ec
            JOIN Asset a ON a.id = ec.id
            """;

    private static final String COLONNE_SERVIZIO = """
            SELECT ec.id, ec.nome, s.descrizione, s.categoria_rilevanza, ec.stato,
                   ec.data_inizio_validita, ec.data_fine_validita
            FROM ElementoCritico ec
            JOIN Servizio s ON s.id = ec.id
            """;

    private final JdbcClient jdbc;

    public InventarioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- elemento critico

    /** Stato dell'elemento se appartiene al soggetto ed e' del tipo atteso. */
    public Optional<StatoElemento> statoElemento(long soggettoId, long id, TipoElemento tipo) {
        return jdbc.sql("SELECT stato FROM ElementoCritico WHERE id = ? AND soggetto_nis_id = ? AND tipo = ?")
                .params(id, soggettoId, tipo.name())
                .query((rs, n) -> StatoElemento.valueOf(rs.getString("stato")))
                .optional();
    }

    public void rinomina(long id, String nome) {
        jdbc.sql("UPDATE ElementoCritico SET nome = ? WHERE id = ?").params(nome, id).update();
    }

    /** Dismissione al posto del DELETE (vietato dai trigger del PW); i collegamenti attivi cessano. */
    public void dismetti(long id) {
        jdbc.sql("""
                UPDATE ElementoCritico SET stato = 'DISMESSO', data_fine_validita = SYSTIMESTAMP
                WHERE id = ? AND stato = 'ATTIVO'
                """).param(id).update();
        jdbc.sql("""
                UPDATE AssetServizio SET stato = 'CESSATA'
                WHERE (asset_id = ? OR servizio_id = ?) AND stato = 'ATTIVA'
                """).params(id, id).update();
    }

    /** Versioni dalla vista ElementoCritico_Versioni; l'autore viene da LogEstrazione. */
    public List<VersioneElemento> versioni(long id) {
        return jdbc.sql("""
                SELECT v.versione, v.nome, v.stato, v.valido_da, v.valido_a,
                       (SELECT l.utente
                        FROM ElementoCriticoStorico st
                        JOIN LogEstrazione l ON l.id = st.log_estrazione_id
                        WHERE st.elemento_critico_id = v.elemento_critico_id
                          AND st.versione = v.versione - 1) AS modificata_da
                FROM ElementoCritico_Versioni v
                WHERE v.elemento_critico_id = ?
                ORDER BY v.versione DESC
                """)
                .param(id)
                .query((rs, n) -> new VersioneElemento(
                        rs.getInt("versione"),
                        rs.getString("nome"),
                        StatoElemento.valueOf(rs.getString("stato")),
                        dataOra(rs, "valido_da"),
                        dataOra(rs, "valido_a"),
                        rs.getString("modificata_da")))
                .list();
    }

    /**
     * Id dell'elemento appena inserito dalla vista: la sequenza seq_elemento_critico
     * e' stata usata nella stessa sessione, quindi CURRVAL restituisce proprio quel valore.
     * Va chiamato nella stessa transazione dell'inserimento.
     */
    private long idAppenaCreato() {
        return jdbc.sql("SELECT seq_elemento_critico.CURRVAL FROM dual").query(Long.class).single();
    }

    // ---------------------------------------------------------------- asset

    public List<Asset> elencoAsset(long soggettoId) {
        return jdbc.sql(COLONNE_ASSET + " WHERE ec.soggetto_nis_id = ? ORDER BY ec.stato, ec.nome")
                .param(soggettoId)
                .query(InventarioRepository::asset)
                .list();
    }

    public Optional<Asset> trovaAsset(long soggettoId, long id) {
        return jdbc.sql(COLONNE_ASSET + " WHERE ec.id = ? AND ec.soggetto_nis_id = ?")
                .params(id, soggettoId)
                .query(InventarioRepository::asset)
                .optional();
    }

    public long inserisciAsset(long soggettoId, DatiAsset dati) {
        jdbc.sql("""
                INSERT INTO AssetCompleto (nome, soggetto_nis_id, tipo_asset, rilevante)
                VALUES (?, ?, ?, ?)
                """)
                .params(dati.nome(), soggettoId, dati.tipoAsset().name(), dati.rilevante() ? 1 : 0)
                .update();
        return idAppenaCreato();
    }

    public void aggiornaAsset(long id, DatiAsset dati) {
        rinomina(id, dati.nome());
        jdbc.sql("UPDATE Asset SET tipo_asset = ?, rilevante = ? WHERE id = ?")
                .params(dati.tipoAsset().name(), dati.rilevante() ? 1 : 0, id)
                .update();
    }

    // ---------------------------------------------------------------- servizi

    /** Due query in tutto (servizi + stati membri), non una per servizio. */
    public List<Servizio> elencoServizi(long soggettoId) {
        Map<Long, List<StatoMembroUE>> stati = new HashMap<>();
        jdbc.sql("""
                SELECT ssm.servizio_id, ssm.stato_membro
                FROM ServizioStatoMembro ssm
                JOIN ElementoCritico ec ON ec.id = ssm.servizio_id
                WHERE ec.soggetto_nis_id = ?
                ORDER BY ssm.stato_membro
                """)
                .param(soggettoId)
                .query((ResultSet rs) -> {
                    stati.computeIfAbsent(rs.getLong("servizio_id"), k -> new ArrayList<>())
                            .add(StatoMembroUE.valueOf(rs.getString("stato_membro")));
                });
        return jdbc.sql(COLONNE_SERVIZIO + " WHERE ec.soggetto_nis_id = ? ORDER BY ec.stato, ec.nome")
                .param(soggettoId)
                .query((rs, n) -> servizio(rs, stati.getOrDefault(rs.getLong("id"), List.of())))
                .list();
    }

    public Optional<Servizio> trovaServizio(long soggettoId, long id) {
        List<StatoMembroUE> stati = statiMembro(id);
        return jdbc.sql(COLONNE_SERVIZIO + " WHERE ec.id = ? AND ec.soggetto_nis_id = ?")
                .params(id, soggettoId)
                .query((rs, n) -> servizio(rs, stati))
                .optional();
    }

    public long inserisciServizio(long soggettoId, DatiServizio dati) {
        jdbc.sql("""
                INSERT INTO ServizioCompleto (nome, soggetto_nis_id, descrizione, categoria_rilevanza)
                VALUES (?, ?, ?, ?)
                """)
                .params(dati.nome(), soggettoId, dati.descrizione(), dati.categoriaRilevanza())
                .update();
        long id = idAppenaCreato();
        sostituisciStatiMembro(id, dati.statiMembro());
        return id;
    }

    public void aggiornaServizio(long id, DatiServizio dati) {
        rinomina(id, dati.nome());
        jdbc.sql("UPDATE Servizio SET descrizione = ?, categoria_rilevanza = ? WHERE id = ?")
                .params(dati.descrizione(), dati.categoriaRilevanza(), id)
                .update();
        sostituisciStatiMembro(id, dati.statiMembro());
    }

    private List<StatoMembroUE> statiMembro(long servizioId) {
        return jdbc.sql("SELECT stato_membro FROM ServizioStatoMembro WHERE servizio_id = ? ORDER BY stato_membro")
                .param(servizioId)
                .query((rs, n) -> StatoMembroUE.valueOf(rs.getString("stato_membro")))
                .list();
    }

    private void sostituisciStatiMembro(long servizioId, List<StatoMembroUE> stati) {
        jdbc.sql("DELETE FROM ServizioStatoMembro WHERE servizio_id = ?").param(servizioId).update();
        for (StatoMembroUE stato : stati.stream().distinct().toList()) {
            jdbc.sql("INSERT INTO ServizioStatoMembro (servizio_id, stato_membro) VALUES (?, ?)")
                    .params(servizioId, stato.name())
                    .update();
        }
    }

    // ---------------------------------------------------------------- collegamenti

    private static final String COLONNE_COLLEGAMENTO = """
            SELECT r.asset_id, ea.nome AS asset_nome, r.servizio_id, es.nome AS servizio_nome, r.stato
            FROM AssetServizio r
            JOIN ElementoCritico ea ON ea.id = r.asset_id
            JOIN ElementoCritico es ON es.id = r.servizio_id
            """;

    public List<Collegamento> collegamentiDelServizio(long servizioId) {
        return jdbc.sql(COLONNE_COLLEGAMENTO + " WHERE r.servizio_id = ? ORDER BY r.stato, ea.nome")
                .param(servizioId)
                .query(InventarioRepository::collegamento)
                .list();
    }

    public List<Collegamento> collegamentiDellAsset(long assetId) {
        return jdbc.sql(COLONNE_COLLEGAMENTO + " WHERE r.asset_id = ? ORDER BY r.stato, es.nome")
                .param(assetId)
                .query(InventarioRepository::collegamento)
                .list();
    }

    /** Crea il collegamento o lo riattiva: la riattivazione passa dal trigger di storico. */
    public void collega(long assetId, long servizioId) {
        jdbc.sql("""
                MERGE INTO AssetServizio t
                USING (SELECT ? AS asset_id, ? AS servizio_id FROM dual) s
                ON (t.asset_id = s.asset_id AND t.servizio_id = s.servizio_id)
                WHEN MATCHED THEN UPDATE SET stato = 'ATTIVA' WHERE t.stato <> 'ATTIVA'
                WHEN NOT MATCHED THEN INSERT (asset_id, servizio_id) VALUES (s.asset_id, s.servizio_id)
                """)
                .params(assetId, servizioId)
                .update();
    }

    public boolean cessa(long assetId, long servizioId) {
        return jdbc.sql("""
                UPDATE AssetServizio SET stato = 'CESSATA'
                WHERE asset_id = ? AND servizio_id = ? AND stato = 'ATTIVA'
                """)
                .params(assetId, servizioId)
                .update() == 1;
    }

    // ---------------------------------------------------------------- mapping

    private static Asset asset(ResultSet rs, int riga) throws SQLException {
        return new Asset(
                rs.getLong("id"),
                rs.getString("nome"),
                TipoAsset.valueOf(rs.getString("tipo_asset")),
                rs.getInt("rilevante") == 1,
                StatoElemento.valueOf(rs.getString("stato")),
                dataOra(rs, "data_inizio_validita"),
                dataOra(rs, "data_fine_validita"));
    }

    private static Servizio servizio(ResultSet rs, List<StatoMembroUE> stati) throws SQLException {
        return new Servizio(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("descrizione"),
                rs.getString("categoria_rilevanza"),
                stati,
                StatoElemento.valueOf(rs.getString("stato")),
                dataOra(rs, "data_inizio_validita"),
                dataOra(rs, "data_fine_validita"));
    }

    private static Collegamento collegamento(ResultSet rs, int riga) throws SQLException {
        return new Collegamento(
                rs.getLong("asset_id"),
                rs.getString("asset_nome"),
                rs.getLong("servizio_id"),
                rs.getString("servizio_nome"),
                StatoCollegamento.valueOf(rs.getString("stato")));
    }

    private static LocalDateTime dataOra(ResultSet rs, String colonna) throws SQLException {
        Timestamp valore = rs.getTimestamp(colonna);
        return valore == null ? null : valore.toLocalDateTime();
    }
}

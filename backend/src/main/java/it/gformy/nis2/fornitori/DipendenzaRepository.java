package it.gformy.nis2.fornitori;

import static it.gformy.nis2.fornitori.FornitoreRepository.dataOra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.fornitori.ModelloFornitori.CodiceCpv;
import it.gformy.nis2.fornitori.ModelloFornitori.CriterioRilevanza;
import it.gformy.nis2.fornitori.ModelloFornitori.DatiDipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.Dipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.FornitoreRilevante;
import it.gformy.nis2.fornitori.ModelloFornitori.NuovaDipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.StatoDipendenza;
import it.gformy.nis2.fornitori.ModelloFornitori.VersioneDipendenza;

/**
 * Dipendenze del soggetto da fornitori. Una dipendenza appartiene al soggetto
 * dell'elemento critico a cui si riferisce: ogni query filtra passando da ElementoCritico.
 */
@Repository
public class DipendenzaRepository {

    private static final String COLONNE = """
            SELECT d.id, d.fornitore_id, f.denominazione AS fornitore_denominazione,
                   d.elemento_critico_id, ec.nome AS elemento_nome, ec.tipo AS elemento_tipo,
                   d.criterio_rilevanza, d.tipologia_fornitura, d.referente_contatto, d.stato
            FROM Dipendenza d
            JOIN ElementoCritico ec ON ec.id = d.elemento_critico_id
            JOIN Fornitore f ON f.id = d.fornitore_id
            """;

    private final JdbcClient jdbc;
    private final FornitoreRepository fornitori;

    public DipendenzaRepository(JdbcClient jdbc, FornitoreRepository fornitori) {
        this.jdbc = jdbc;
        this.fornitori = fornitori;
    }

    public List<Dipendenza> elenco(long soggettoId) {
        return jdbc.sql(COLONNE + " WHERE ec.soggetto_nis_id = ? ORDER BY d.stato, f.denominazione, ec.nome")
                .param(soggettoId)
                .query(DipendenzaRepository::dipendenza)
                .list();
    }

    public Optional<Dipendenza> trova(long soggettoId, long id) {
        return jdbc.sql(COLONNE + " WHERE d.id = ? AND ec.soggetto_nis_id = ?")
                .params(id, soggettoId)
                .query(DipendenzaRepository::dipendenza)
                .optional();
    }

    public long inserisci(NuovaDipendenza nuova) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO Dipendenza (fornitore_id, elemento_critico_id, criterio_rilevanza,
                                        tipologia_fornitura, referente_contatto)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(nuova.fornitoreId(), nuova.elementoCriticoId(), nuova.criterioRilevanza().name(),
                        nuova.tipologiaFornitura(), nuova.referenteContatto())
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    /** Il trigger trg_storico_dipendenza conserva criterio, tipologia, referente e stato precedenti. */
    public void aggiorna(long id, DatiDipendenza dati) {
        jdbc.sql("""
                UPDATE Dipendenza
                SET criterio_rilevanza = ?, tipologia_fornitura = ?, referente_contatto = ?, stato = ?
                WHERE id = ?
                """)
                .params(dati.criterioRilevanza().name(), dati.tipologiaFornitura(), dati.referenteContatto(),
                        dati.stato().name(), id)
                .update();
    }

    /** Versioni dalla vista Dipendenza_Versioni (corretta in V3 con data_creazione). */
    public List<VersioneDipendenza> versioni(long id) {
        return jdbc.sql("""
                SELECT v.versione, v.criterio_rilevanza, v.tipologia_fornitura, v.stato, v.valido_da, v.valido_a,
                       (SELECT l.utente FROM DipendenzaStorico st JOIN LogEstrazione l ON l.id = st.log_estrazione_id
                        WHERE st.dipendenza_id = v.dipendenza_id AND st.versione = v.versione - 1) AS modificata_da
                FROM Dipendenza_Versioni v
                WHERE v.dipendenza_id = ?
                ORDER BY v.versione DESC
                """)
                .param(id)
                .query((rs, n) -> new VersioneDipendenza(
                        rs.getInt("versione"),
                        CriterioRilevanza.valueOf(rs.getString("criterio_rilevanza")),
                        rs.getString("tipologia_fornitura"),
                        StatoDipendenza.valueOf(rs.getString("stato")),
                        dataOra(rs, "valido_da"),
                        dataOra(rs, "valido_a"),
                        rs.getString("modificata_da")))
                .list();
    }

    /**
     * Fornitori rilevanti del soggetto nel formato del portale ACN (art. 18):
     * denominazione, codice fiscale, paese, codici CPV e criterio (1 = ICT, 2 = non fungibile).
     * Contano solo le dipendenze attive; se un fornitore e' sia ICT sia non fungibile
     * per elementi diversi, compaiono entrambi i criteri.
     */
    public List<FornitoreRilevante> fornitoriRilevanti(long soggettoId) {
        record Riga(long id, String denominazione, String codiceFiscale, String paese, String criteri) {
        }
        List<Riga> righe = jdbc.sql("""
                SELECT f.id, f.denominazione, f.codice_fiscale, f.paese_sede_legale,
                       LISTAGG(DISTINCT CASE d.criterio_rilevanza WHEN 'ICT' THEN '1' ELSE '2' END, ',')
                           WITHIN GROUP (ORDER BY CASE d.criterio_rilevanza WHEN 'ICT' THEN '1' ELSE '2' END) AS criteri
                FROM Dipendenza d
                JOIN ElementoCritico ec ON ec.id = d.elemento_critico_id
                JOIN Fornitore f ON f.id = d.fornitore_id
                WHERE ec.soggetto_nis_id = ? AND d.stato = 'ATTIVA'
                GROUP BY f.id, f.denominazione, f.codice_fiscale, f.paese_sede_legale
                ORDER BY f.denominazione
                """)
                .param(soggettoId)
                .query((rs, n) -> new Riga(rs.getLong("id"), rs.getString("denominazione"),
                        rs.getString("codice_fiscale"), rs.getString("paese_sede_legale"), rs.getString("criteri")))
                .list();
        Map<Long, List<CodiceCpv>> cpv = fornitori.cpvPerFornitori(righe.stream().map(Riga::id).toList());
        return righe.stream()
                .map(r -> new FornitoreRilevante(
                        r.denominazione(), r.codiceFiscale(), r.paese(),
                        cpv.getOrDefault(r.id(), List.of()).stream().map(CodiceCpv::codice).toList(),
                        Arrays.stream(r.criteri().split(",")).map(Integer::valueOf).toList()))
                .toList();
    }

    private static Dipendenza dipendenza(ResultSet rs, int riga) throws SQLException {
        return new Dipendenza(
                rs.getLong("id"),
                rs.getLong("fornitore_id"),
                rs.getString("fornitore_denominazione"),
                rs.getLong("elemento_critico_id"),
                rs.getString("elemento_nome"),
                rs.getString("elemento_tipo"),
                CriterioRilevanza.valueOf(rs.getString("criterio_rilevanza")),
                rs.getString("tipologia_fornitura"),
                rs.getString("referente_contatto"),
                StatoDipendenza.valueOf(rs.getString("stato")));
    }
}

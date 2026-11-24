package it.gformy.nis2.governance;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.governance.FileCaricati.FileVerificato;
import it.gformy.nis2.governance.ModelloGovernance.DatiDocumento;
import it.gformy.nis2.governance.ModelloGovernance.DatiNuovoDocumento;
import it.gformy.nis2.governance.ModelloGovernance.DatiOrgano;
import it.gformy.nis2.governance.ModelloGovernance.FileDocumento;
import it.gformy.nis2.governance.ModelloGovernance.Organo;
import it.gformy.nis2.governance.ModelloGovernance.VersioneDocumento;

/** Accesso a DocumentoGovernance (PW), al suo storico, ai file (V10) e agli organi di amministrazione. */
@Repository
public class GovernanceRepository {

    /** Riga del catalogo applicabile al soggetto, con l'eventuale documento. */
    public record Riga(
            long tipoId, String codice, String nome, String ambito,
            Long documentoId, String versione, LocalDate dataApprovazione, Organo approvatoDa,
            LocalDate dataUltimoRiesame, Integer periodicita, String note) {
    }

    public record Contenuto(FileDocumento file, byte[] byteFile) {
    }

    private static final String RIGHE = """
            SELECT td.id AS tipo_id, td.codice, td.nome, td.ambito_politica,
                   d.id AS documento_id, d.versione, d.data_approvazione, d.data_ultimo_riesame,
                   d.periodicita_riesame_mesi, d.note,
                   o.id AS organo_id, o.nome AS organo_nome, o.ruolo AS organo_ruolo
            FROM SoggettoNIS s
            JOIN DocumentoGovernanceApplicabilita a ON a.tipo_soggetto_id = s.tipo_soggetto_id
            JOIN TipoDocumentoGovernance td ON td.id = a.tipo_documento_id
            LEFT JOIN DocumentoGovernance d ON d.tipo_documento_id = td.id AND d.soggetto_nis_id = s.id
            LEFT JOIN OrganoAmministrazione o ON o.id = d.approvato_da
            WHERE s.id = ?
            """;

    private static final String FILE = """
            SELECT f.id, f.documento_id, f.nome_file, f.content_type, f.dimensione, f.sha256,
                   f.versione_documento, f.caricato_il, f.caricato_da
            FROM DocumentoGovernanceFile f
            """;

    private final JdbcClient jdbc;

    public GovernanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- organi di amministrazione

    public List<Organo> organi(long soggettoId) {
        return jdbc.sql("SELECT id, nome, ruolo FROM OrganoAmministrazione WHERE soggetto_nis_id = ? ORDER BY nome")
                .param(soggettoId)
                .query((rs, n) -> new Organo(rs.getLong("id"), rs.getString("nome"), rs.getString("ruolo")))
                .list();
    }

    public Optional<Organo> organo(long soggettoId, long organoId) {
        return jdbc.sql("SELECT id, nome, ruolo FROM OrganoAmministrazione WHERE id = ? AND soggetto_nis_id = ?")
                .params(organoId, soggettoId)
                .query((rs, n) -> new Organo(rs.getLong("id"), rs.getString("nome"), rs.getString("ruolo")))
                .optional();
    }

    public long inserisciOrgano(long soggettoId, DatiOrgano dati) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO OrganoAmministrazione (soggetto_nis_id, nome, ruolo) VALUES (?, ?, ?)")
                .params(soggettoId, dati.nome().strip(), dati.ruolo().strip())
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    public boolean eliminaOrgano(long soggettoId, long organoId) {
        return jdbc.sql("DELETE FROM OrganoAmministrazione WHERE id = ? AND soggetto_nis_id = ?")
                .params(organoId, soggettoId)
                .update() == 1;
    }

    // ---------------------------------------------------------------- documenti

    /** Politiche prima (in ordine di ambito a-p), poi gli altri documenti in ordine di codice. */
    public List<Riga> righe(long soggettoId) {
        return jdbc.sql(RIGHE + """
                ORDER BY CASE WHEN td.ambito_politica IS NULL THEN 1 ELSE 0 END, td.ambito_politica, td.codice
                """)
                .param(soggettoId)
                .query(GovernanceRepository::riga)
                .list();
    }

    public Optional<Riga> riga(long soggettoId, long documentoId) {
        return jdbc.sql(RIGHE + " AND d.id = ?")
                .params(soggettoId, documentoId)
                .query(GovernanceRepository::riga)
                .optional();
    }

    /** Ultimo file caricato per ogni documento del soggetto. */
    public Map<Long, FileDocumento> ultimiFile(long soggettoId) {
        Map<Long, FileDocumento> ultimi = new HashMap<>();
        jdbc.sql(FILE + """
                JOIN DocumentoGovernance d ON d.id = f.documento_id
                WHERE d.soggetto_nis_id = ?
                  AND f.id = (SELECT MAX(f2.id) FROM DocumentoGovernanceFile f2 WHERE f2.documento_id = f.documento_id)
                """)
                .param(soggettoId)
                .query(rs -> {
                    ultimi.put(rs.getLong("documento_id"), file(rs));
                });
        return ultimi;
    }

    public long inserisci(long soggettoId, long tipoId, DatiNuovoDocumento dati) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO DocumentoGovernance (soggetto_nis_id, tipo_documento_id, versione,
                                                 periodicita_riesame_mesi, note)
                VALUES (?, ?, ?, ?, ?)
                """)
                .params(soggettoId, tipoId, vuotoNull(dati.versione()),
                        dati.periodicitaRiesameMesi() == null ? 12 : dati.periodicitaRiesameMesi(),
                        vuotoNull(dati.note()))
                .update(chiave, "ID");
        return chiave.getKeyAs(Number.class).longValue();
    }

    public void aggiorna(long documentoId, DatiDocumento dati) {
        jdbc.sql("UPDATE DocumentoGovernance SET versione = ?, periodicita_riesame_mesi = ?, note = ? WHERE id = ?")
                .params(vuotoNull(dati.versione()), dati.periodicitaRiesameMesi(), vuotoNull(dati.note()), documentoId)
                .update();
    }

    /** L'approvazione vale anche come primo riesame. Il trigger del PW storicizza lo stato precedente. */
    public void approva(long documentoId, LocalDate data, long organoId) {
        jdbc.sql("""
                UPDATE DocumentoGovernance SET data_approvazione = ?, approvato_da = ?, data_ultimo_riesame = ?
                WHERE id = ?
                """)
                .params(Date.valueOf(data), organoId, Date.valueOf(data), documentoId)
                .update();
    }

    public void riesame(long documentoId, LocalDate data) {
        jdbc.sql("UPDATE DocumentoGovernance SET data_ultimo_riesame = ? WHERE id = ?")
                .params(Date.valueOf(data), documentoId)
                .update();
    }

    /** Un nuovo contenuto va riapprovato: si azzera l'approvazione (lo storico conserva la precedente). */
    public void revocaApprovazione(long documentoId, String nuovaVersione) {
        jdbc.sql("""
                UPDATE DocumentoGovernance
                SET data_approvazione = NULL, approvato_da = NULL, data_ultimo_riesame = NULL,
                    versione = NVL(?, versione)
                WHERE id = ?
                """)
                .params(vuotoNull(nuovaVersione), documentoId)
                .update();
    }

    /** Storico delle approvazioni e dei riesami, dalla piu' vecchia alla corrente. */
    public List<VersioneDocumento> versioni(long documentoId) {
        record Riga(int versione, String versioneDocumento, LocalDate approvazione, String organo,
                    LocalDate riesame, LocalDateTime validoDa, LocalDateTime validoA, String chiusaDa) {
        }
        List<Riga> righe = jdbc.sql("""
                SELECT st.versione, NULL AS versione_documento, st.data_approvazione, st.data_ultimo_riesame,
                       st.valido_da, st.valido_a, o.nome AS organo, l.utente AS chiusa_da
                FROM DocumentoGovernanceStorico st
                JOIN LogEstrazione l ON l.id = st.log_estrazione_id
                LEFT JOIN OrganoAmministrazione o ON o.id = st.approvato_da
                WHERE st.documento_id = ?
                UNION ALL
                SELECT (SELECT NVL(MAX(versione), 0) + 1 FROM DocumentoGovernanceStorico WHERE documento_id = d.id),
                       d.versione, d.data_approvazione, d.data_ultimo_riesame,
                       NVL((SELECT MAX(valido_a) FROM DocumentoGovernanceStorico WHERE documento_id = d.id),
                           d.data_creazione),
                       NULL, o.nome, NULL
                FROM DocumentoGovernance d
                LEFT JOIN OrganoAmministrazione o ON o.id = d.approvato_da
                WHERE d.id = ?
                ORDER BY 1
                """)
                .params(documentoId, documentoId)
                .query((rs, n) -> new Riga(rs.getInt("versione"), rs.getString("versione_documento"),
                        data(rs, "data_approvazione"), rs.getString("organo"), data(rs, "data_ultimo_riesame"),
                        ora(rs, "valido_da"), ora(rs, "valido_a"), rs.getString("chiusa_da")))
                .list();
        List<VersioneDocumento> versioni = new ArrayList<>();
        String autore = null;
        for (Riga r : righe) {
            // Chi ha prodotto la versione N e' chi ha chiuso la N-1 (traccia in LogEstrazione).
            versioni.add(new VersioneDocumento(r.versione(), r.versioneDocumento(), r.approvazione(), r.organo(),
                    r.riesame(), r.validoDa(), r.validoA(), autore));
            autore = r.chiusaDa();
        }
        return versioni;
    }

    // ---------------------------------------------------------------- file

    public List<FileDocumento> files(long documentoId) {
        return jdbc.sql(FILE + " WHERE f.documento_id = ? ORDER BY f.id DESC")
                .param(documentoId)
                .query((rs, n) -> file(rs))
                .list();
    }

    public Optional<Contenuto> contenuto(long documentoId, long fileId) {
        return jdbc.sql(FILE.replace("f.caricato_da", "f.caricato_da, f.contenuto")
                        + " WHERE f.id = ? AND f.documento_id = ?")
                .params(fileId, documentoId)
                .query((rs, n) -> new Contenuto(file(rs), rs.getBytes("contenuto")))
                .optional();
    }

    public void inserisciFile(long documentoId, String versione, FileVerificato file) {
        jdbc.sql("""
                INSERT INTO DocumentoGovernanceFile (documento_id, versione_documento, nome_file, content_type,
                                                     dimensione, sha256, contenuto)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .params(documentoId, vuotoNull(versione), file.nome(), file.contentType(), file.contenuto().length,
                        file.sha256(), file.contenuto())
                .update();
    }

    /** Ogni scaricamento resta in LogEstrazione come consultazione del documento. */
    public void registraConsultazione(long documentoId, String dettaglio) {
        jdbc.sql("""
                INSERT INTO LogEstrazione (registro_tabella_id, record_id, utente, tipo_operazione, snapshot_dati)
                VALUES ((SELECT id FROM RegistroTabelle WHERE nome_tabella = 'DocumentoGovernance'), ?,
                        fn_utente_corrente, 'CONSULTAZIONE', ?)
                """)
                .params(documentoId, dettaglio)
                .update();
    }

    // ---------------------------------------------------------------- conversioni

    private static Riga riga(ResultSet rs, int n) throws SQLException {
        long documento = rs.getLong("documento_id");
        boolean esiste = !rs.wasNull();
        long organo = rs.getLong("organo_id");
        Organo approvatoDa = rs.wasNull() ? null
                : new Organo(organo, rs.getString("organo_nome"), rs.getString("organo_ruolo"));
        return new Riga(rs.getLong("tipo_id"), rs.getString("codice"), rs.getString("nome"),
                rs.getString("ambito_politica"),
                esiste ? documento : null, rs.getString("versione"), data(rs, "data_approvazione"), approvatoDa,
                data(rs, "data_ultimo_riesame"),
                esiste ? rs.getInt("periodicita_riesame_mesi") : null, rs.getString("note"));
    }

    private static FileDocumento file(ResultSet rs) throws SQLException {
        return new FileDocumento(rs.getLong("id"), rs.getString("nome_file"), rs.getString("content_type"),
                rs.getLong("dimensione"), rs.getString("sha256"), rs.getString("versione_documento"),
                rs.getObject("caricato_il", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getString("caricato_da"));
    }

    private static LocalDate data(ResultSet rs, String colonna) throws SQLException {
        Date valore = rs.getDate(colonna);
        return valore == null ? null : valore.toLocalDate();
    }

    private static LocalDateTime ora(ResultSet rs, String colonna) throws SQLException {
        Timestamp valore = rs.getTimestamp(colonna);
        return valore == null ? null : valore.toLocalDateTime();
    }

    private static String vuotoNull(String valore) {
        return valore == null || valore.isBlank() ? null : valore.strip();
    }
}

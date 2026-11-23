package it.gformy.nis2.conformita;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.conformita.ModelloConformita.DatiAdempimento;
import it.gformy.nis2.conformita.ModelloConformita.Requisito;
import it.gformy.nis2.conformita.ModelloConformita.StatoAdempimento;
import it.gformy.nis2.conformita.ModelloConformita.VersioneAdempimento;

/**
 * Requisiti applicabili e loro stato di adempimento.
 *
 * L'applicabilita' viene dal PW: MisuraApplicabilita lega ogni requisito ai tipi
 * di soggetto (essenziale, importante) e RequisitoMisura ha una validita' nel
 * tempo legata alla versione della determinazione ACN.
 */
@Repository
public class ConformitaRepository {

    private static final String REQUISITI_APPLICABILI = """
            SELECT rm.id, rm.codice, rm.numero_punto, rm.testo,
                   m.codice AS misura_codice, m.categoria AS misura_categoria, m.descrizione AS misura_descrizione,
                   sar.stato, sar.data_valutazione, sar.note
            FROM SoggettoNIS s
            JOIN MisuraApplicabilita ma ON ma.tipo_soggetto_id = s.tipo_soggetto_id
            JOIN RequisitoMisura rm ON rm.id = ma.requisito_id
                                   AND rm.data_inizio_validita <= CURRENT_DATE
                                   AND (rm.data_fine_validita IS NULL OR rm.data_fine_validita > CURRENT_DATE)
            JOIN MisuraSicurezza m ON m.id = rm.misura_id
            LEFT JOIN StatoAdempimentoRequisito sar ON sar.requisito_id = rm.id AND sar.soggetto_nis_id = s.id
            WHERE s.id = ?
            """;

    private final JdbcClient jdbc;

    public ConformitaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Requisito> requisiti(long soggettoId) {
        return jdbc.sql(REQUISITI_APPLICABILI + " ORDER BY m.codice, rm.numero_punto")
                .param(soggettoId)
                .query((rs, n) -> {
                    String stato = rs.getString("stato");
                    Date data = rs.getDate("data_valutazione");
                    return new Requisito(
                            rs.getLong("id"),
                            rs.getString("codice"),
                            rs.getInt("numero_punto"),
                            rs.getString("testo"),
                            rs.getString("misura_codice"),
                            rs.getString("misura_categoria"),
                            rs.getString("misura_descrizione"),
                            stato == null ? null : StatoAdempimento.valueOf(stato),
                            data == null ? null : data.toLocalDate(),
                            rs.getString("note"));
                })
                .list();
    }

    public Optional<Requisito> requisito(long soggettoId, long requisitoId) {
        return requisiti(soggettoId).stream().filter(r -> r.id() == requisitoId).findFirst();
    }

    /**
     * Prima valutazione o aggiornamento. In aggiornamento il trigger
     * trg_storico_statoadempimento del PW salva la valutazione precedente.
     */
    public void valuta(long soggettoId, long requisitoId, DatiAdempimento dati) {
        jdbc.sql("""
                MERGE INTO StatoAdempimentoRequisito t
                USING (SELECT ? AS requisito_id, ? AS soggetto_nis_id FROM dual) s
                ON (t.requisito_id = s.requisito_id AND t.soggetto_nis_id = s.soggetto_nis_id)
                WHEN MATCHED THEN UPDATE SET stato = ?, note = ?, data_valutazione = CURRENT_DATE
                WHEN NOT MATCHED THEN INSERT (requisito_id, soggetto_nis_id, stato, note)
                     VALUES (s.requisito_id, s.soggetto_nis_id, ?, ?)
                """)
                .params(requisitoId, soggettoId, dati.stato().name(), dati.note(), dati.stato().name(), dati.note())
                .update();
    }

    /** Versioni dalla vista StatoAdempimentoRequisito_Versioni (corretta in V3). */
    public List<VersioneAdempimento> versioni(long soggettoId, long requisitoId) {
        return jdbc.sql("""
                SELECT v.versione, v.stato, v.data_valutazione, v.valido_da, v.valido_a,
                       (SELECT l.utente FROM StatoAdempimentoRequisitoStorico st
                        JOIN LogEstrazione l ON l.id = st.log_estrazione_id
                        WHERE st.stato_adempimento_id = v.stato_adempimento_id
                          AND st.versione = v.versione - 1) AS modificata_da
                FROM StatoAdempimentoRequisito_Versioni v
                JOIN StatoAdempimentoRequisito sar ON sar.id = v.stato_adempimento_id
                WHERE sar.soggetto_nis_id = ? AND sar.requisito_id = ?
                ORDER BY v.versione DESC
                """)
                .params(soggettoId, requisitoId)
                .query((rs, n) -> {
                    Timestamp validoA = rs.getTimestamp("valido_a");
                    return new VersioneAdempimento(
                            rs.getInt("versione"),
                            StatoAdempimento.valueOf(rs.getString("stato")),
                            rs.getDate("data_valutazione").toLocalDate(),
                            rs.getTimestamp("valido_da").toLocalDateTime(),
                            validoA == null ? null : validoA.toLocalDateTime(),
                            rs.getString("modificata_da"));
                })
                .list();
    }
}

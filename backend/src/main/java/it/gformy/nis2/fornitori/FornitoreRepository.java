package it.gformy.nis2.fornitori;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import it.gformy.nis2.fornitori.ModelloFornitori.CodiceCpv;
import it.gformy.nis2.fornitori.ModelloFornitori.DatiFornitore;
import it.gformy.nis2.fornitori.ModelloFornitori.Fornitore;
import it.gformy.nis2.fornitori.ModelloFornitori.VersioneFornitore;

/** Anagrafica dei fornitori (condivisa tra i soggetti) e catalogo CPV. */
@Repository
public class FornitoreRepository {

    private static final int RISULTATI_MASSIMI = 50;

    private final JdbcClient jdbc;

    public FornitoreRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Ricerca per denominazione o codice fiscale. I caratteri jolly di LIKE
     * scritti dall'utente (% e _) vengono neutralizzati con ESCAPE.
     */
    public List<Fornitore> cerca(String testo) {
        String filtro = "%" + testo.toUpperCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<Fornitore> fornitori = jdbc.sql("""
                SELECT id, denominazione, codice_fiscale, paese_sede_legale, data_creazione
                FROM Fornitore
                WHERE UPPER(denominazione) LIKE :filtro ESCAPE '\\' OR codice_fiscale LIKE :filtro ESCAPE '\\'
                ORDER BY denominazione
                FETCH FIRST :massimo ROWS ONLY
                """)
                .param("filtro", filtro)
                .param("massimo", RISULTATI_MASSIMI)
                .query((rs, n) -> fornitore(rs, List.of()))
                .list();
        Map<Long, List<CodiceCpv>> cpv = cpvPerFornitori(fornitori.stream().map(Fornitore::id).toList());
        return fornitori.stream()
                .map(f -> new Fornitore(f.id(), f.denominazione(), f.codiceFiscale(), f.paeseSedeLegale(),
                        cpv.getOrDefault(f.id(), List.of()), f.dataCreazione()))
                .toList();
    }

    public Optional<Fornitore> trova(long id) {
        List<CodiceCpv> cpv = cpvPerFornitori(List.of(id)).getOrDefault(id, List.of());
        return jdbc.sql("""
                SELECT id, denominazione, codice_fiscale, paese_sede_legale, data_creazione
                FROM Fornitore WHERE id = ?
                """)
                .param(id)
                .query((rs, n) -> fornitore(rs, cpv))
                .optional();
    }

    public long inserisci(String codiceFiscale, DatiFornitore dati) {
        KeyHolder chiave = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO Fornitore (denominazione, codice_fiscale, paese_sede_legale) VALUES (?, ?, ?)")
                .params(dati.denominazione(), codiceFiscale, dati.paeseSedeLegale())
                .update(chiave, "ID");
        long id = chiave.getKeyAs(Number.class).longValue();
        sostituisciCpv(id, dati.cpv());
        return id;
    }

    /** Il trigger trg_storico_fornitore del PW conserva denominazione e paese precedenti. */
    public boolean aggiorna(long id, DatiFornitore dati) {
        int righe = jdbc.sql("UPDATE Fornitore SET denominazione = ?, paese_sede_legale = ? WHERE id = ?")
                .params(dati.denominazione(), dati.paeseSedeLegale(), id)
                .update();
        if (righe == 1) {
            sostituisciCpv(id, dati.cpv());
        }
        return righe == 1;
    }

    /** Nel PW non c'e' una vista Fornitore_Versioni: qui la si ricostruisce allo stesso modo. */
    public List<VersioneFornitore> versioni(long id) {
        return jdbc.sql("""
                SELECT v.versione, v.denominazione, v.paese_sede_legale, v.valido_da, v.valido_a,
                       (SELECT l.utente FROM FornitoreStorico st JOIN LogEstrazione l ON l.id = st.log_estrazione_id
                        WHERE st.fornitore_id = :id AND st.versione = v.versione - 1) AS modificata_da
                FROM (
                    SELECT versione, denominazione, paese_sede_legale, valido_da, valido_a
                    FROM FornitoreStorico WHERE fornitore_id = :id
                    UNION ALL
                    SELECT NVL((SELECT MAX(versione) + 1 FROM FornitoreStorico WHERE fornitore_id = f.id), 1),
                           f.denominazione, f.paese_sede_legale,
                           NVL((SELECT MAX(valido_a) FROM FornitoreStorico WHERE fornitore_id = f.id), f.data_creazione),
                           NULL
                    FROM Fornitore f WHERE f.id = :id
                ) v
                ORDER BY v.versione DESC
                """)
                .param("id", id)
                .query((rs, n) -> new VersioneFornitore(
                        rs.getInt("versione"),
                        rs.getString("denominazione"),
                        rs.getString("paese_sede_legale"),
                        dataOra(rs, "valido_da"),
                        dataOra(rs, "valido_a"),
                        rs.getString("modificata_da")))
                .list();
    }

    public List<CodiceCpv> catalogoCpv() {
        return jdbc.sql("SELECT codice, descrizione FROM CodiceCPV ORDER BY codice")
                .query((rs, n) -> new CodiceCpv(rs.getString("codice"), rs.getString("descrizione")))
                .list();
    }

    public Map<Long, List<CodiceCpv>> cpvPerFornitori(Collection<Long> fornitori) {
        Map<Long, List<CodiceCpv>> risultato = new HashMap<>();
        if (fornitori.isEmpty()) {
            return risultato;
        }
        jdbc.sql("""
                SELECT fc.fornitore_id, c.codice, c.descrizione
                FROM FornitoreCPV fc JOIN CodiceCPV c ON c.id = fc.cpv_id
                WHERE fc.fornitore_id IN (:fornitori)
                ORDER BY c.codice
                """)
                .param("fornitori", fornitori)
                .query((ResultSet rs) -> {
                    risultato.computeIfAbsent(rs.getLong("fornitore_id"), k -> new ArrayList<>())
                            .add(new CodiceCpv(rs.getString("codice"), rs.getString("descrizione")));
                });
        return risultato;
    }

    /** I codici sconosciuti al catalogo non vengono inseriti: lo segnala il controller con un 400. */
    public List<String> codiciSconosciuti(List<String> codici) {
        List<String> noti = catalogoCpv().stream().map(CodiceCpv::codice).toList();
        return codici.stream().filter(c -> !noti.contains(c)).distinct().toList();
    }

    private void sostituisciCpv(long fornitoreId, List<String> codici) {
        jdbc.sql("DELETE FROM FornitoreCPV WHERE fornitore_id = ?").param(fornitoreId).update();
        for (String codice : codici.stream().distinct().toList()) {
            jdbc.sql("""
                    INSERT INTO FornitoreCPV (fornitore_id, cpv_id)
                    SELECT ?, id FROM CodiceCPV WHERE codice = ?
                    """)
                    .params(fornitoreId, codice)
                    .update();
        }
    }

    private static Fornitore fornitore(ResultSet rs, List<CodiceCpv> cpv) throws SQLException {
        return new Fornitore(
                rs.getLong("id"),
                rs.getString("denominazione"),
                rs.getString("codice_fiscale"),
                rs.getString("paese_sede_legale"),
                cpv,
                dataOra(rs, "data_creazione"));
    }

    static LocalDateTime dataOra(ResultSet rs, String colonna) throws SQLException {
        Timestamp valore = rs.getTimestamp(colonna);
        return valore == null ? null : valore.toLocalDateTime();
    }
}

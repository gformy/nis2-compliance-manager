package it.gformy.nis2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Verifica che lo schema del PW, migrato con Flyway, si comporti come previsto
 * quando lo usa il backend attraverso l'utente applicativo.
 */
class SchemaPw19Test extends OracleTestBase {

    @Test
    void leTabelleDelPwSonoTutteVisibiliAllUtenteApplicativo() {
        Integer tabelle = jdbc.queryForObject("""
                SELECT COUNT(*) FROM all_tables
                WHERE owner = UPPER(?) AND LOWER(table_name) <> 'flyway_schema_history'
                """, Integer.class, PROPRIETARIO);

        assertThat(tabelle).isEqualTo(47);
    }

    @Test
    void lAuditRegistraLaPersonaENonLUtenteTecnico() {
        long soggetto = nuovoSoggetto();

        String utenteRegistrato = transazione.execute(stato -> {
            jdbc.update("BEGIN DBMS_SESSION.SET_IDENTIFIER(?); END;", "mario.rossi");
            try {
                jdbc.update("UPDATE SoggettoNIS SET telefono = '0622222222' WHERE id = ?", soggetto);
                return jdbc.queryForObject("""
                        SELECT l.utente
                        FROM LogEstrazione l
                        JOIN RegistroTabelle r ON r.id = l.registro_tabella_id
                        WHERE r.nome_tabella = 'SoggettoNIS' AND l.record_id = ?
                        """, String.class, soggetto);
            } finally {
                jdbc.update("BEGIN DBMS_SESSION.CLEAR_IDENTIFIER; END;");
            }
        });

        assertThat(utenteRegistrato).isEqualTo("mario.rossi");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM SoggettoNISStorico WHERE soggetto_nis_id = ?", Integer.class, soggetto))
                .isEqualTo(1);
    }

    @Test
    void senzaIdentificativoLAuditRicadeSullUtenteDiDatabase() {
        long soggetto = nuovoSoggetto();

        jdbc.update("UPDATE SoggettoNIS SET telefono = '0633333333' WHERE id = ?", soggetto);

        String utenteRegistrato = jdbc.queryForObject("""
                SELECT l.utente
                FROM LogEstrazione l
                JOIN RegistroTabelle r ON r.id = l.registro_tabella_id
                WHERE r.nome_tabella = 'SoggettoNIS' AND l.record_id = ?
                """, String.class, soggetto);
        assertThat(utenteRegistrato).isEqualToIgnoringCase(APPLICATIVO);
    }

    @Test
    void ilDeleteDiUnElementoCriticoEBloccatoDaiPermessiEDalTrigger() {
        long asset = nuovoAsset(nuovoSoggetto(), "Server di test");

        // L'utente applicativo non ha proprio il permesso di DELETE
        // (ORA-41900 da Oracle 23ai, ORA-01031 nelle versioni precedenti)
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ElementoCritico WHERE id = ?", asset))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");

        // Anche il proprietario viene fermato dal trigger del PW
        assertThatThrownBy(() -> jdbcProprietario().update("DELETE FROM ElementoCritico WHERE id = ?", asset))
                .hasMessageContaining("ORA-20001");
    }

    @Test
    void ilProfiloAsOfNonMostraFornitoriAggiuntiDopoLInvio() {
        long soggetto = nuovoSoggetto();
        long asset = nuovoAsset(soggetto, "Server ERP");
        long invioPrecedente = nuovoInvio(soggetto);

        String codiceFiscale = codiceFiscaleCasuale();
        jdbc.update("""
                INSERT INTO Fornitore (denominazione, codice_fiscale, paese_sede_legale)
                VALUES ('Fornitore Cloud S.r.l.', ?, 'IT')
                """, codiceFiscale);
        long fornitore = jdbc.queryForObject(
                "SELECT id FROM Fornitore WHERE codice_fiscale = ?", Long.class, codiceFiscale);
        jdbc.update("""
                INSERT INTO Dipendenza (fornitore_id, elemento_critico_id, criterio_rilevanza)
                VALUES (?, ?, 'ICT')
                """, fornitore, asset);

        long invioSuccessivo = nuovoInvio(soggetto);

        assertThat(fornitoriDichiarati(invioPrecedente)).isZero();
        assertThat(fornitoriDichiarati(invioSuccessivo)).isEqualTo(1);
    }

    private long nuovoInvio(long soggettoId) {
        jdbc.update("""
                INSERT INTO InvioACN (soggetto_nis_id, tipo_invio, periodo_riferimento)
                VALUES (?, 'AGGIORNAMENTO_CONTINUO', 2026)
                """, soggettoId);
        return jdbc.queryForObject(
                "SELECT MAX(id) FROM InvioACN WHERE soggetto_nis_id = ?", Long.class, soggettoId);
    }

    private int fornitoriDichiarati(long invioId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ProfiloACN_FornitoriRilevanti_AsOf WHERE invio_id = ?",
                Integer.class, invioId);
    }
}

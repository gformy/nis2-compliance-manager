package it.gformy.nis2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

/** Il profilo demo applica i dati dimostrativi senza errori. */
@ActiveProfiles("demo")
class DatiDemoTest extends OracleTestBase {

    @Test
    void ilProfiloDemoCaricaISoggettiDiEsempio() {
        Integer soggetti = jdbc.queryForObject(
                "SELECT COUNT(*) FROM SoggettoNIS WHERE codice_fiscale IN ('01234567890', '80012345678')",
                Integer.class);

        assertThat(soggetti).isEqualTo(2);
    }

    @Test
    void ilProfiloDemoCaricaLInventarioDellAziendaAlfa() {
        Integer asset = jdbc.queryForObject("""
                SELECT COUNT(*) FROM AssetCompleto a JOIN SoggettoNIS s ON s.id = a.soggetto_nis_id
                WHERE s.codice_fiscale = '01234567890'
                """, Integer.class);
        Integer collegamenti = jdbc.queryForObject("""
                SELECT COUNT(*) FROM AssetServizio r JOIN ElementoCritico ec ON ec.id = r.asset_id
                JOIN SoggettoNIS s ON s.id = ec.soggetto_nis_id
                WHERE s.codice_fiscale = '01234567890'
                """, Integer.class);

        assertThat(asset).isEqualTo(4);
        assertThat(collegamenti).isEqualTo(4);
    }

    @Test
    void ilProfiloDemoCaricaGliIncidentiDellAziendaAlfa() {
        Integer incidenti = jdbc.queryForObject("""
                SELECT COUNT(*) FROM Incidente i JOIN SoggettoNIS s ON s.id = i.soggetto_nis_id
                WHERE s.codice_fiscale = '01234567890'
                """, Integer.class);
        Integer notifiche = jdbc.queryForObject("""
                SELECT COUNT(*) FROM NotificaIncidente n JOIN Incidente i ON i.id = n.incidente_id
                WHERE i.titolo = 'Phishing mirato all''ufficio acquisti'
                """, Integer.class);

        assertThat(incidenti).isEqualTo(2);
        assertThat(notifiche).isEqualTo(3);
    }

    @Test
    void ilPrimoInvioDemoDichiaraLInventarioDellAziendaAlfa() {
        Integer elementi = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ProfiloACN_ElementiCritici_AsOf p
                JOIN SoggettoNIS s ON s.id = p.soggetto_nis_id
                WHERE s.codice_fiscale = '01234567890' AND p.stato_al_momento_invio = 'ATTIVO'
                """, Integer.class);

        assertThat(elementi).isEqualTo(6);
    }

    @Test
    void ilProfiloDemoMostraDocumentiInStatiDiversi() {
        List<String> codici = jdbc.queryForList("""
                SELECT td.codice FROM DocumentoGovernance d
                JOIN TipoDocumentoGovernance td ON td.id = d.tipo_documento_id
                JOIN SoggettoNIS s ON s.id = d.soggetto_nis_id
                JOIN DocumentoGovernanceFile f ON f.documento_id = d.id
                WHERE s.codice_fiscale = '01234567890' ORDER BY td.codice
                """, String.class);

        assertThat(codici).containsExactly("GV.PO-01-a", "GV.PO-01-p", "PR.AT-01", "RS.MA-01");
    }
}

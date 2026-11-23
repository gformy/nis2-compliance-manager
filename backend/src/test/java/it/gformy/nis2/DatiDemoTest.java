package it.gformy.nis2;

import static org.assertj.core.api.Assertions.assertThat;

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
}

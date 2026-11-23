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
}

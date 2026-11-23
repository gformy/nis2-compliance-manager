package it.gformy.nis2;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Base dei test di integrazione: un Oracle Database Free vero in container,
 * condiviso da tutte le classi di test, con la stessa configurazione a due
 * utenti dell'ambiente reale (proprietario per Flyway, applicativo per il backend).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TokenDiTest.class)
public abstract class OracleTestBase {

    static final String PROPRIETARIO = "nis2_owner";
    static final String PASSWORD_PROPRIETARIO = "Proprietario_Test_1";
    static final String APPLICATIVO = "nis2_app";
    static final String PASSWORD_APPLICATIVO = "Applicativo_Test_1";

    static final OracleContainer ORACLE = new OracleContainer("gvenzl/oracle-free:23-slim-faststart")
            .withUsername(PROPRIETARIO)
            .withPassword(PASSWORD_PROPRIETARIO)
            .withEnv("NIS2_APP_USER", APPLICATIVO)
            .withEnv("NIS2_APP_PASSWORD", PASSWORD_APPLICATIVO)
            .withCopyFileToContainer(
                    MountableFile.forHostPath("../infra/oracle/init/01_utente_applicativo.sh", 0755),
                    "/container-entrypoint-initdb.d/01_utente_applicativo.sh");

    static {
        ORACLE.start();
    }

    @DynamicPropertySource
    static void proprietaDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", ORACLE::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APPLICATIVO);
        registry.add("spring.datasource.password", () -> PASSWORD_APPLICATIVO);
        registry.add("spring.flyway.url", ORACLE::getJdbcUrl);
        registry.add("spring.flyway.user", () -> PROPRIETARIO);
        registry.add("spring.flyway.password", () -> PASSWORD_PROPRIETARIO);
    }

    /** Connessione dell'applicazione: utente nis2_app. */
    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected TransactionTemplate transazione;

    /** Connessione del proprietario, solo per verificare cio' che l'applicazione non puo' fare. */
    protected JdbcTemplate jdbcProprietario() {
        return new JdbcTemplate(new DriverManagerDataSource(
                ORACLE.getJdbcUrl(), PROPRIETARIO, PASSWORD_PROPRIETARIO));
    }

    protected static String codiceFiscaleCasuale() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    protected long nuovoSoggetto() {
        String codiceFiscale = codiceFiscaleCasuale();
        jdbc.update("""
                INSERT INTO SoggettoNIS (codice_fiscale, denominazione, indirizzo_sede_legale,
                                         pec, telefono, tipo_soggetto_id)
                VALUES (?, 'Soggetto di test', 'Via Roma 1, Roma', 'test@pec.it', '0611111111',
                        (SELECT id FROM TipoSoggettoNIS WHERE codice = 'ESSENZIALE'))
                """, codiceFiscale);
        return jdbc.queryForObject(
                "SELECT id FROM SoggettoNIS WHERE codice_fiscale = ?", Long.class, codiceFiscale);
    }

    /** Inserisce un asset dalla vista ISA, come prevede il PW. */
    protected long nuovoAsset(long soggettoId, String nome) {
        jdbc.update("""
                INSERT INTO AssetCompleto (nome, soggetto_nis_id, tipo_asset, rilevante)
                VALUES (?, ?, 'HARDWARE', 1)
                """, nome, soggettoId);
        return jdbc.queryForObject(
                "SELECT id FROM ElementoCritico WHERE nome = ? AND soggetto_nis_id = ?",
                Long.class, nome, soggettoId);
    }
}

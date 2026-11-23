package it.gformy.nis2.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;

import it.gformy.nis2.OracleTestBase;
import it.gformy.nis2.TokenDiTest;

/**
 * Test dell'API attraverso HTTP vero: server avviato su una porta casuale,
 * token JWT, Oracle in container. Verifica il comportamento che vede il frontend.
 */
class SoggettoApiTest extends OracleTestBase {

    private static final String OPERATORE = TokenDiTest.token("luca.verdi");
    private static final String AMMINISTRATORE = TokenDiTest.token("anna.neri", "AMMINISTRATORE");

    @Value("${local.server.port}")
    private int porta;

    private RestClient client;

    record Risposta(int stato, String corpo) {
        <T> T json(String percorso) {
            return JsonPath.read(corpo, percorso);
        }
    }

    @BeforeEach
    void preparaClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + porta).build();
    }

    @Test
    void senzaTokenLApiRisponde401MaLoStatoDiSaluteEPubblico() {
        assertThat(chiama(HttpMethod.GET, "/api/v1/soggetti", null, null).stato()).isEqualTo(401);
        assertThat(chiama(HttpMethod.GET, "/actuator/health", null, null).stato()).isEqualTo(200);
    }

    @Test
    void unUtenteAutenticatoLeggeElencoEDettaglio() {
        long id = nuovoSoggetto();

        Risposta elenco = chiama(HttpMethod.GET, "/api/v1/soggetti", OPERATORE, null);
        Risposta dettaglio = chiama(HttpMethod.GET, "/api/v1/soggetti/" + id, OPERATORE, null);

        assertThat(elenco.stato()).isEqualTo(200);
        assertThat(elenco.<List<Integer>>json("$[*].id")).contains((int) id);
        assertThat(dettaglio.stato()).isEqualTo(200);
        assertThat(dettaglio.<String>json("$.pec")).isEqualTo("test@pec.it");
        assertThat(dettaglio.<String>json("$.tipoSoggetto")).isEqualTo("ESSENZIALE");
    }

    @Test
    void laModificaCreaUnaNuovaVersioneFirmataDaChiLHaFatta() {
        long id = nuovoSoggetto();

        Risposta modifica = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id,
                TokenDiTest.token("giulia.bianchi"), datiSoggetto("Soggetto rinominato", "nuova@pec.it"));
        Risposta versioni = chiama(HttpMethod.GET, "/api/v1/soggetti/" + id + "/versioni", OPERATORE, null);

        assertThat(modifica.stato()).isEqualTo(200);
        assertThat(modifica.<String>json("$.denominazione")).isEqualTo("Soggetto rinominato");
        assertThat(versioni.<List<Integer>>json("$[*].versione")).containsExactly(2, 1);
        assertThat(versioni.<String>json("$[0].modificataDa")).isEqualTo("giulia.bianchi");
        assertThat(versioni.<String>json("$[1].denominazione")).isEqualTo("Soggetto di test");
        assertThat(versioni.<Object>json("$[1].modificataDa")).isNull();
    }

    @Test
    void soloLAmministratoreCreaUnSoggettoEIlCodiceFiscaleEUnico() {
        String codiceFiscale = "12345" + (System.nanoTime() % 1_000_000 + 1_000_000);
        String nuovo = """
                {"codiceFiscale": "%s", "dati": %s}
                """.formatted(codiceFiscale.substring(0, 11), datiSoggetto("Nuovo Ente", "ente@pec.it"));

        Risposta daOperatore = chiama(HttpMethod.POST, "/api/v1/soggetti", OPERATORE, nuovo);
        Risposta daAmministratore = chiama(HttpMethod.POST, "/api/v1/soggetti", AMMINISTRATORE, nuovo);
        Risposta duplicato = chiama(HttpMethod.POST, "/api/v1/soggetti", AMMINISTRATORE, nuovo);

        assertThat(daOperatore.stato()).isEqualTo(403);
        assertThat(daAmministratore.stato()).isEqualTo(201);
        assertThat(daAmministratore.<String>json("$.denominazione")).isEqualTo("Nuovo Ente");
        assertThat(duplicato.stato()).isEqualTo(409);
    }

    @Test
    void datiNonValidiRestituiscono400ConIlCampoSbagliato() {
        long id = nuovoSoggetto();

        Risposta risposta = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id, OPERATORE,
                datiSoggetto("Soggetto", "non-e-una-pec"));

        assertThat(risposta.stato()).isEqualTo(400);
        assertThat(risposta.<String>json("$.errori.pec")).isNotBlank();
    }

    @Test
    void unSoggettoInesistenteRestituisce404() {
        assertThat(chiama(HttpMethod.GET, "/api/v1/soggetti/999999999", OPERATORE, null).stato()).isEqualTo(404);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/999999999", OPERATORE,
                datiSoggetto("X", "x@pec.it")).stato()).isEqualTo(404);
    }

    private static String datiSoggetto(String denominazione, String pec) {
        return """
                {
                  "denominazione": "%s",
                  "indirizzoSedeLegale": "Via Roma 1, Roma",
                  "pec": "%s",
                  "emailFunzionale": null,
                  "telefono": "06 1234567",
                  "fatturato": null,
                  "bilancio": null,
                  "numeroDipendenti": 120,
                  "tipoSoggetto": "ESSENZIALE"
                }
                """.formatted(denominazione, pec);
    }

    private Risposta chiama(HttpMethod metodo, String percorso, String token, String corpoJson) {
        RestClient.RequestBodySpec richiesta = client.method(metodo).uri(percorso);
        if (token != null) {
            richiesta.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (corpoJson != null) {
            richiesta.contentType(MediaType.APPLICATION_JSON).body(corpoJson);
        }
        return richiesta.exchange((req, res) -> new Risposta(
                res.getStatusCode().value(),
                new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    }
}

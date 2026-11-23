package it.gformy.nis2;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import com.jayway.jsonpath.JsonPath;

/** Base per i test che chiamano l'API via HTTP con un token JWT. */
public abstract class ApiTestBase extends OracleTestBase {

    @Value("${local.server.port}")
    private int porta;

    private RestClient client;

    public record Risposta(int stato, String corpo) {
        public <T> T json(String percorso) {
            return JsonPath.read(corpo, percorso);
        }
    }

    @BeforeEach
    void preparaClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + porta).build();
    }

    protected Risposta chiama(HttpMethod metodo, String percorso, String token, String corpoJson) {
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

    protected Risposta get(String percorso, String token) {
        return chiama(HttpMethod.GET, percorso, token, null);
    }

    protected static String datiSoggetto(String denominazione, String pec) {
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
}

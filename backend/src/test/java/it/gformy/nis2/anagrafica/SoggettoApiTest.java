package it.gformy.nis2.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/**
 * Test dell'API attraverso HTTP vero: server avviato su una porta casuale,
 * token JWT, Oracle in container. Verifica il comportamento che vede il frontend.
 */
class SoggettoApiTest extends ApiTestBase {

    private static final String OPERATORE = TokenDiTest.token("luca.verdi");
    private static final String AMMINISTRATORE = TokenDiTest.token("anna.neri", "AMMINISTRATORE");

    @Test
    void senzaTokenLApiRisponde401MaLoStatoDiSaluteEPubblico() {
        assertThat(get("/api/v1/soggetti", null).stato()).isEqualTo(401);
        assertThat(get("/actuator/health", null).stato()).isEqualTo(200);
    }

    @Test
    void unUtenteAssegnatoLeggeElencoEDettaglio() {
        long id = nuovoSoggetto();
        assegna("luca.verdi", id, "OPERATORE");

        Risposta elenco = get("/api/v1/soggetti", OPERATORE);
        Risposta dettaglio = get("/api/v1/soggetti/" + id, OPERATORE);

        assertThat(elenco.stato()).isEqualTo(200);
        assertThat(elenco.<List<Integer>>json("$[*].id")).contains((int) id);
        assertThat(dettaglio.stato()).isEqualTo(200);
        assertThat(dettaglio.<String>json("$.pec")).isEqualTo("test@pec.it");
        assertThat(dettaglio.<String>json("$.tipoSoggetto")).isEqualTo("ESSENZIALE");
    }

    @Test
    void laModificaCreaUnaNuovaVersioneFirmataDaChiLHaFatta() {
        long id = nuovoSoggetto();
        assegna("giulia.bianchi", id, "OPERATORE");
        assegna("luca.verdi", id, "REVISORE");

        Risposta modifica = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id,
                TokenDiTest.token("giulia.bianchi"), datiSoggetto("Soggetto rinominato", "nuova@pec.it"));
        Risposta versioni = get("/api/v1/soggetti/" + id + "/versioni", OPERATORE);

        assertThat(modifica.stato()).isEqualTo(200);
        assertThat(modifica.<String>json("$.denominazione")).isEqualTo("Soggetto rinominato");
        assertThat(versioni.<List<Integer>>json("$[*].versione")).containsExactly(2, 1);
        assertThat(versioni.<String>json("$[0].modificataDa")).isEqualTo("giulia.bianchi");
        assertThat(versioni.<String>json("$[1].denominazione")).isEqualTo("Soggetto di test");
        assertThat(versioni.<Object>json("$[1].modificataDa")).isNull();
    }

    @Test
    void soloLAmministratoreCreaUnSoggettoEIlCodiceFiscaleEUnico() {
        String codiceFiscale = String.valueOf(10_000_000_000L + System.nanoTime() % 89_999_999_999L);
        String nuovo = """
                {"codiceFiscale": "%s", "dati": %s}
                """.formatted(codiceFiscale, datiSoggetto("Nuovo Ente", "ente@pec.it"));

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
        assegna("luca.verdi", id, "OPERATORE");

        Risposta risposta = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id, OPERATORE,
                datiSoggetto("Soggetto", "non-e-una-pec"));

        assertThat(risposta.stato()).isEqualTo(400);
        assertThat(risposta.<String>json("$.errori.pec")).isNotBlank();
    }

    @Test
    void unSoggettoInesistenteRestituisce404AncheAllAmministratore() {
        assertThat(get("/api/v1/soggetti/999999999", OPERATORE).stato()).isEqualTo(404);
        assertThat(get("/api/v1/soggetti/999999999", AMMINISTRATORE).stato()).isEqualTo(404);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/999999999", AMMINISTRATORE,
                datiSoggetto("X", "x@pec.it")).stato()).isEqualTo(404);
    }
}

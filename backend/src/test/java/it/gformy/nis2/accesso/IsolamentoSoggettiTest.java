package it.gformy.nis2.accesso;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/**
 * Isolamento tra soggetti: il controllo contro BOLA/IDOR, primo rischio della
 * OWASP API Security Top 10. Ogni test e' un tentativo di vedere o cambiare
 * i dati di un soggetto a cui l'utente non e' assegnato.
 */
class IsolamentoSoggettiTest extends ApiTestBase {

    private static final String MARIO = TokenDiTest.token("mario.alfa");
    private static final String RITA_REVISORE = TokenDiTest.token("rita.revisore");
    private static final String AMMINISTRATORE = TokenDiTest.token("anna.neri", "AMMINISTRATORE");

    @Test
    void nellElencoCompaionoSoloISoggettiAssegnati() {
        long suo = nuovoSoggetto();
        long altrui = nuovoSoggetto();
        assegna("mario.alfa", suo, "OPERATORE");

        List<Integer> visibili = get("/api/v1/soggetti", MARIO).json("$[*].id");

        assertThat(visibili).contains((int) suo).doesNotContain((int) altrui);
    }

    @Test
    void cambiandoLIdNellUrlNonSiVedeNeSiModificaUnSoggettoAltrui() {
        long suo = nuovoSoggetto();
        long altrui = nuovoSoggetto();
        assegna("mario.alfa", suo, "OPERATORE");

        assertThat(get("/api/v1/soggetti/" + altrui, MARIO).stato()).isEqualTo(404);
        assertThat(get("/api/v1/soggetti/" + altrui + "/versioni", MARIO).stato()).isEqualTo(404);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/" + altrui, MARIO,
                datiSoggetto("Preso da Mario", "mario@pec.it")).stato()).isEqualTo(404);

        assertThat(denominazione(altrui)).isEqualTo("Soggetto di test");
    }

    @Test
    void laRispostaPerUnSoggettoAltruiEUgualeAQuellaPerUnoInesistente() {
        long altrui = nuovoSoggetto();

        Risposta esistente = get("/api/v1/soggetti/" + altrui, MARIO);
        Risposta inesistente = get("/api/v1/soggetti/999999998", MARIO);

        assertThat(esistente.stato()).isEqualTo(inesistente.stato()).isEqualTo(404);
    }

    @Test
    void ilRevisoreLeggeMaNonModifica() {
        long id = nuovoSoggetto();
        assegna("rita.revisore", id, "REVISORE");

        assertThat(get("/api/v1/soggetti/" + id, RITA_REVISORE).stato()).isEqualTo(200);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id, RITA_REVISORE,
                datiSoggetto("Modificato dal revisore", "rev@pec.it")).stato()).isEqualTo(403);

        assertThat(denominazione(id)).isEqualTo("Soggetto di test");
    }

    @Test
    void lAmministratoreConcedeERevocaLAccesso() {
        long id = nuovoSoggetto();
        String percorso = "/api/v1/soggetti/" + id + "/utenti/mario.alfa";

        assertThat(get("/api/v1/soggetti/" + id, AMMINISTRATORE).stato()).isEqualTo(200);

        Risposta concesso = chiama(HttpMethod.PUT, percorso, AMMINISTRATORE, "{\"ruolo\": \"OPERATORE\"}");
        assertThat(concesso.stato()).isEqualTo(200);
        assertThat(concesso.<List<String>>json("$[*].assegnatoDa")).containsExactly("anna.neri");
        assertThat(get("/api/v1/soggetti/" + id, MARIO).stato()).isEqualTo(200);

        assertThat(chiama(HttpMethod.DELETE, percorso, AMMINISTRATORE, null).stato()).isEqualTo(204);
        assertThat(get("/api/v1/soggetti/" + id, MARIO).stato()).isEqualTo(404);
    }

    @Test
    void unUtenteNonPuoAssegnarsiDaSoloUnRuoloPiuAlto() {
        long id = nuovoSoggetto();
        assegna("mario.alfa", id, "REVISORE");

        Risposta risposta = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id + "/utenti/mario.alfa",
                MARIO, "{\"ruolo\": \"REFERENTE_NIS\"}");

        assertThat(risposta.stato()).isEqualTo(403);
        assertThat(get("/api/v1/me", MARIO).<List<String>>json("$.soggetti[?(@.soggettoId == " + id + ")].ruolo"))
                .containsExactly("REVISORE");
    }

    @Test
    void unNomeUtenteNonValidoVieneRifiutato() {
        long id = nuovoSoggetto();

        Risposta risposta = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + id + "/utenti/utente!",
                AMMINISTRATORE, "{\"ruolo\": \"OPERATORE\"}");

        assertThat(risposta.stato()).isEqualTo(400);
    }

    @Test
    void ilProfiloElencaRuoloESoggettiDellUtente() {
        long id = nuovoSoggetto();
        assegna("rita.revisore", id, "REVISORE");

        Risposta profilo = get("/api/v1/me", RITA_REVISORE);

        assertThat(profilo.<String>json("$.utente")).isEqualTo("rita.revisore");
        assertThat(profilo.<Boolean>json("$.amministratore")).isFalse();
        assertThat(profilo.<List<Integer>>json("$.soggetti[*].soggettoId")).contains((int) id);
    }
}

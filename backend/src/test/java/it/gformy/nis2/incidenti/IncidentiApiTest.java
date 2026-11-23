package it.gformy.nis2.incidenti;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Registro degli incidenti: tipi applicabili, scadenze, notifiche e isolamento tra soggetti. */
class IncidentiApiTest extends ApiTestBase {

    private static final String GIORGIO = TokenDiTest.token("giorgio.referente");
    private static final String LIA_REVISORE = TokenDiTest.token("lia.revisore");
    private static final String ESTRANEO = TokenDiTest.token("mario.estraneo");

    private long soggettoDiGiorgio() {
        long id = nuovoSoggetto();
        assegna("giorgio.referente", id, "REFERENTE_NIS");
        assegna("lia.revisore", id, "REVISORE");
        return id;
    }

    private static String ore(long ore) {
        return Instant.now().minus(Duration.ofHours(ore)).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String incidente(String tipo, String evidenza, long... elementi) {
        StringBuilder ids = new StringBuilder();
        for (long e : elementi) {
            ids.append(ids.isEmpty() ? "" : ",").append(e);
        }
        return """
                {"titolo": "Ransomware sul file server", "descrizione": "Cifratura delle cartelle condivise",
                 "tipoIncidente": "%s", "categoria": "RANSOMWARE", "gravita": "ALTA",
                 "dataEvidenza": "%s", "elementiCritici": [%s]}
                """.formatted(tipo, evidenza, ids);
    }

    private Risposta crea(long soggetto, String corpo) {
        return chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/incidenti", GIORGIO, corpo);
    }

    private Risposta notifica(long soggetto, long incidente, String tipo, String quando) {
        return chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/incidenti/" + incidente + "/notifiche",
                GIORGIO, "{\"tipo\": \"%s\", \"dataTrasmissione\": \"%s\", \"contenuto\": \"testo\"}"
                        .formatted(tipo, quando));
    }

    @Test
    void ilCatalogoDiUnEssenzialeComprendeIs4() {
        long soggetto = soggettoDiGiorgio();

        Risposta catalogo = get("/api/v1/soggetti/" + soggetto + "/incidenti/catalogo", GIORGIO);

        assertThat(catalogo.<List<String>>json("$.tipi[*].codice")).containsExactly("IS-1", "IS-2", "IS-3", "IS-4");
        assertThat(catalogo.<List<String>>json("$.categorie")).contains("RANSOMWARE", "PHISHING");
    }

    @Test
    void unIncidenteAppenaRegistratoHaLaPreNotificaComeProssimaScadenza() {
        long soggetto = soggettoDiGiorgio();
        long server = nuovoAsset(soggetto, "File server");

        Risposta creato = crea(soggetto, incidente("IS-1", ore(2), server));
        Risposta elenco = get("/api/v1/soggetti/" + soggetto + "/incidenti", GIORGIO);

        assertThat(creato.stato()).isEqualTo(201);
        assertThat(creato.<String>json("$.registratoDa")).isEqualTo("giorgio.referente");
        assertThat(creato.<List<String>>json("$.elementi[*].nome")).containsExactly("File server");
        assertThat(creato.<List<String>>json("$.scadenze[*].stato"))
                .containsExactly("IN_TEMPO", "IN_TEMPO", "IN_ATTESA");
        assertThat(elenco.<String>json("$[0].prossimaScadenza.tipo")).isEqualTo("PRE_NOTIFICA");
    }

    @Test
    void leNotificheSiRegistranoInOrdineEUnaSolaVolta() {
        long soggetto = soggettoDiGiorgio();
        long id = crea(soggetto, incidente("IS-3", ore(30))).<Integer>json("$.id");

        Risposta relazioneTroppoPresto = notifica(soggetto, id, "RELAZIONE_FINALE", ore(1));
        notifica(soggetto, id, "PRE_NOTIFICA", ore(20));
        Risposta doppia = notifica(soggetto, id, "PRE_NOTIFICA", ore(19));
        Risposta conNotifica = notifica(soggetto, id, "NOTIFICA", ore(1));

        assertThat(relazioneTroppoPresto.stato()).isEqualTo(409);
        assertThat(doppia.stato()).isEqualTo(409);
        assertThat(conNotifica.<List<String>>json("$.scadenze[*].stato"))
                .containsExactly("TRASMESSA", "TRASMESSA", "IN_TEMPO");
    }

    @Test
    void dopoLaPrimaTrasmissioneLaDataDiEvidenzaNonCambia() {
        long soggetto = soggettoDiGiorgio();
        long id = crea(soggetto, incidente("IS-1", ore(5))).<Integer>json("$.id");
        notifica(soggetto, id, "PRE_NOTIFICA", ore(1));

        Risposta modifica = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/incidenti/" + id, GIORGIO,
                incidente("IS-1", ore(10)));

        assertThat(modifica.stato()).isEqualTo(409);
    }

    @Test
    void unIncidenteChiusoAccettaSoloNotifiche() {
        long soggetto = soggettoDiGiorgio();
        long id = crea(soggetto, incidente("IS-2", ore(3))).<Integer>json("$.id");
        String base = "/api/v1/soggetti/" + soggetto + "/incidenti/" + id;

        Risposta ioc = chiama(HttpMethod.POST, base + "/ioc", GIORGIO, "{\"tipo\": \"IP\", \"valore\": \"203.0.113.7\"}");
        Risposta chiuso = chiama(HttpMethod.POST, base + "/chiusura", GIORGIO, null);
        Risposta dopo = chiama(HttpMethod.POST, base + "/timeline", GIORGIO,
                "{\"dataOra\": \"%s\", \"fase\": \"RIPRISTINO\", \"descrizione\": \"Servizi ripristinati\"}"
                        .formatted(ore(1)));
        Risposta preNotifica = notifica(soggetto, id, "PRE_NOTIFICA", ore(1));

        assertThat(ioc.<List<String>>json("$.ioc[*].valore")).containsExactly("203.0.113.7");
        assertThat(chiuso.<String>json("$.stato")).isEqualTo("CHIUSO");
        assertThat(dopo.stato()).isEqualTo(409);
        assertThat(preNotifica.stato()).isEqualTo(200);
    }

    @Test
    void datiNonValidiRispondono400() {
        long soggetto = soggettoDiGiorgio();
        long id = crea(soggetto, incidente("IS-1", ore(3))).<Integer>json("$.id");

        Risposta tipoInesistente = crea(soggetto, incidente("IS-9", ore(1)));
        Risposta nelFuturo = crea(soggetto, incidente("IS-1", Instant.now().plus(Duration.ofHours(2)).toString()));
        Risposta iocMalformato = chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/incidenti/" + id + "/ioc",
                GIORGIO, "{\"tipo\": \"HASH\", \"valore\": \"non-un-hash\"}");

        assertThat(tipoInesistente.stato()).isEqualTo(400);
        assertThat(tipoInesistente.<String>json("$.errori.tipoIncidente")).contains("IS-9");
        assertThat(nelFuturo.stato()).isEqualTo(400);
        assertThat(iocMalformato.stato()).isEqualTo(400);
        assertThat(iocMalformato.<String>json("$.errori.valoreCoerente")).isNotBlank();
    }

    @Test
    void isolamentoTraSoggettiEPermessiDiScrittura() {
        long soggetto = soggettoDiGiorgio();
        long altro = nuovoSoggetto();
        long assetAltrui = nuovoAsset(altro, "Asset di un altro soggetto");
        long id = crea(soggetto, incidente("IS-1", ore(1))).<Integer>json("$.id");

        Risposta conAssetAltrui = crea(soggetto, incidente("IS-1", ore(1), assetAltrui));
        Risposta revisoreScrive = chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/incidenti",
                LIA_REVISORE, incidente("IS-1", ore(1)));
        Risposta revisoreLegge = get("/api/v1/soggetti/" + soggetto + "/incidenti/" + id, LIA_REVISORE);
        Risposta estraneoLegge = get("/api/v1/soggetti/" + soggetto + "/incidenti/" + id, ESTRANEO);

        assertThat(conAssetAltrui.stato()).isEqualTo(404);
        assertThat(revisoreScrive.stato()).isEqualTo(403);
        assertThat(revisoreLegge.stato()).isEqualTo(200);
        assertThat(estraneoLegge.stato()).isEqualTo(404);
    }
}

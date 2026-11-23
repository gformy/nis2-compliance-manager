package it.gformy.nis2.inventario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Inventario attraverso l'API: viste ISA, dismissione, storico, collegamenti e isolamento. */
class InventarioApiTest extends ApiTestBase {

    private static final String SARA = TokenDiTest.token("sara.operatrice");
    private static final String PIERO_REVISORE = TokenDiTest.token("piero.revisore");

    private static final String SERVER = """
            {"nome": "Server ERP", "tipoAsset": "HARDWARE", "rilevante": true}
            """;

    private long soggettoDiSara() {
        long id = nuovoSoggetto();
        assegna("sara.operatrice", id, "OPERATORE");
        return id;
    }

    private long creaAsset(long soggettoId, String json) {
        Risposta creato = chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggettoId + "/asset", SARA, json);
        assertThat(creato.stato()).isEqualTo(201);
        return creato.<Integer>json("$.id").longValue();
    }

    private long creaServizio(long soggettoId, String nome) {
        Risposta creato = chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggettoId + "/servizi", SARA, """
                {"nome": "%s", "descrizione": "Servizio di test", "categoriaRilevanza": null,
                 "statiMembro": ["IT", "FR"]}
                """.formatted(nome));
        assertThat(creato.stato()).isEqualTo(201);
        return creato.<Integer>json("$.id").longValue();
    }

    @Test
    void unAssetCreatoDallaVistaIsaHaLaRigaPadreELaRigaFiglia() {
        long soggetto = soggettoDiSara();

        long id = creaAsset(soggetto, SERVER);

        assertThat(jdbc.queryForObject("SELECT tipo FROM ElementoCritico WHERE id = ?", String.class, id))
                .isEqualTo("ASSET");
        assertThat(jdbc.queryForObject("SELECT tipo_asset FROM Asset WHERE id = ?", String.class, id))
                .isEqualTo("HARDWARE");
        Risposta elenco = get("/api/v1/soggetti/" + soggetto + "/asset", SARA);
        assertThat(elenco.<List<String>>json("$[*].nome")).containsExactly("Server ERP");
        assertThat(elenco.<List<Boolean>>json("$[*].rilevante")).containsExactly(true);
    }

    @Test
    void laDismissioneSostituisceIlDeleteEFinisceNelloStorico() {
        long soggetto = soggettoDiSara();
        long id = creaAsset(soggetto, SERVER);
        String percorso = "/api/v1/soggetti/" + soggetto + "/asset/" + id;

        Risposta dismesso = chiama(HttpMethod.POST, percorso + "/dismissione", SARA, null);
        Risposta versioni = get(percorso + "/versioni", SARA);

        assertThat(dismesso.stato()).isEqualTo(200);
        assertThat(dismesso.<String>json("$.stato")).isEqualTo("DISMESSO");
        assertThat(dismesso.<String>json("$.dataFineValidita")).isNotNull();
        assertThat(versioni.<List<String>>json("$[*].stato")).containsExactly("DISMESSO", "ATTIVO");
        assertThat(versioni.<String>json("$[0].modificataDa")).isEqualTo("sara.operatrice");

        // Un elemento dismesso non si modifica e non si dismette di nuovo
        assertThat(chiama(HttpMethod.PUT, percorso, SARA, SERVER).stato()).isEqualTo(409);
        assertThat(chiama(HttpMethod.POST, percorso + "/dismissione", SARA, null).stato()).isEqualTo(409);
        // E non esiste un DELETE
        assertThat(chiama(HttpMethod.DELETE, percorso, SARA, null).stato()).isEqualTo(405);
    }

    @Test
    void rinominareUnAssetCreaUnaVersioneModificareSoloIlTipoNo() {
        long soggetto = soggettoDiSara();
        long id = creaAsset(soggetto, SERVER);
        String percorso = "/api/v1/soggetti/" + soggetto + "/asset/" + id;

        chiama(HttpMethod.PUT, percorso, SARA, """
                {"nome": "Server ERP", "tipoAsset": "SOFTWARE", "rilevante": false}
                """);
        Risposta rinominato = chiama(HttpMethod.PUT, percorso, SARA, """
                {"nome": "Server ERP produzione", "tipoAsset": "SOFTWARE", "rilevante": false}
                """);

        assertThat(rinominato.<String>json("$.tipoAsset")).isEqualTo("SOFTWARE");
        // Il trigger del PW storicizza nome, soggetto e stato: il tipo e' un attributo della sottoclasse
        assertThat(get(percorso + "/versioni", SARA).<List<String>>json("$[*].nome"))
                .containsExactly("Server ERP produzione", "Server ERP");
    }

    @Test
    void unServizioHaGliStatiMembriEGliAssetCollegati() {
        long soggetto = soggettoDiSara();
        long asset = creaAsset(soggetto, SERVER);
        long servizio = creaServizio(soggetto, "Fatturazione");
        String percorso = "/api/v1/soggetti/" + soggetto + "/servizi/" + servizio;

        Risposta collegato = chiama(HttpMethod.PUT, percorso + "/asset/" + asset, SARA, null);
        Risposta cessato = chiama(HttpMethod.DELETE, percorso + "/asset/" + asset, SARA, null);
        Risposta riattivato = chiama(HttpMethod.PUT, percorso + "/asset/" + asset, SARA, null);

        assertThat(get(percorso, SARA).<List<String>>json("$.statiMembro")).containsExactly("FR", "IT");
        assertThat(collegato.<List<String>>json("$[*].stato")).containsExactly("ATTIVA");
        assertThat(cessato.<List<String>>json("$[*].stato")).containsExactly("CESSATA");
        assertThat(riattivato.<List<String>>json("$[*].stato")).containsExactly("ATTIVA");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM AssetServizioStorico st JOIN AssetServizio r ON r.id = st.asset_servizio_id
                WHERE r.asset_id = ? AND r.servizio_id = ?
                """, Integer.class, asset, servizio)).isEqualTo(2);
    }

    @Test
    void dismettereUnAssetFaCessareISuoiCollegamenti() {
        long soggetto = soggettoDiSara();
        long asset = creaAsset(soggetto, SERVER);
        long servizio = creaServizio(soggetto, "Portale");
        chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/servizi/" + servizio + "/asset/" + asset, SARA, null);

        chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/asset/" + asset + "/dismissione", SARA, null);

        assertThat(get("/api/v1/soggetti/" + soggetto + "/servizi/" + servizio + "/asset", SARA)
                .<List<String>>json("$[*].stato")).containsExactly("CESSATA");
    }

    @Test
    void unoStatoMembroNonValidoVieneRifiutato() {
        long soggetto = soggettoDiSara();

        Risposta risposta = chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/servizi", SARA, """
                {"nome": "Servizio", "statiMembro": ["IT", "US"]}
                """);

        assertThat(risposta.stato()).isEqualTo(400);
    }

    @Test
    void unAssetDiUnAltroSoggettoNonSiRaggiungeNeanchePassandoDalProprio() {
        long mio = soggettoDiSara();
        long altrui = nuovoSoggetto();
        long assetAltrui = nuovoAsset(altrui, "Server di un altro soggetto");
        long mioServizio = creaServizio(mio, "Servizio mio");

        // Il proprio soggetto nell'URL e l'id di un asset altrui
        assertThat(get("/api/v1/soggetti/" + mio + "/asset/" + assetAltrui, SARA).stato()).isEqualTo(404);
        assertThat(chiama(HttpMethod.POST, "/api/v1/soggetti/" + mio + "/asset/" + assetAltrui + "/dismissione",
                SARA, null).stato()).isEqualTo(404);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/" + mio + "/servizi/" + mioServizio + "/asset/" + assetAltrui,
                SARA, null).stato()).isEqualTo(404);
        // Il soggetto altrui nell'URL
        assertThat(get("/api/v1/soggetti/" + altrui + "/asset", SARA).stato()).isEqualTo(404);

        assertThat(jdbc.queryForObject("SELECT stato FROM ElementoCritico WHERE id = ?", String.class, assetAltrui))
                .isEqualTo("ATTIVO");
    }

    @Test
    void ilRevisoreConsultaLInventarioMaNonLoModifica() {
        long soggetto = soggettoDiSara();
        assegna("piero.revisore", soggetto, "REVISORE");
        long asset = creaAsset(soggetto, SERVER);

        assertThat(get("/api/v1/soggetti/" + soggetto + "/asset/" + asset, PIERO_REVISORE).stato()).isEqualTo(200);
        assertThat(chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/asset", PIERO_REVISORE, SERVER)
                .stato()).isEqualTo(403);
        assertThat(chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/asset/" + asset + "/dismissione",
                PIERO_REVISORE, null).stato()).isEqualTo(403);
    }
}

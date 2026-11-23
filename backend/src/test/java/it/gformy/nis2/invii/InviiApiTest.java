package it.gformy.nis2.invii;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Invii ACN: profilo ricostruito AS OF, confronto, CSV tracciato e permessi. */
class InviiApiTest extends ApiTestBase {

    private static final String GIORGIO = TokenDiTest.token("giorgio.referente");
    private static final String ORNELLA = TokenDiTest.token("ornella.operatore");
    private static final String LIA_REVISORE = TokenDiTest.token("lia.revisore");

    private long soggetto() {
        long id = nuovoSoggetto();
        assegna("giorgio.referente", id, "REFERENTE_NIS");
        assegna("ornella.operatore", id, "OPERATORE");
        assegna("lia.revisore", id, "REVISORE");
        return id;
    }

    private Risposta invia(long soggetto, String token) {
        return chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/invii", token,
                "{\"tipo\": \"AGGIORNAMENTO_ANNUALE\", \"periodoRiferimento\": 2026}");
    }

    private long invio(long soggetto) {
        return invia(soggetto, GIORGIO).<Integer>json("$.id");
    }

    @Test
    void ilProfiloDiUnInvioNonCambiaConLeModificheSuccessive() {
        long soggetto = soggetto();
        long server = nuovoAsset(soggetto, "Server di posta");
        long primo = invio(soggetto);

        jdbc.update("UPDATE ElementoCritico SET nome = 'Server di posta (cluster)' WHERE id = ?", server);
        nuovoAsset(soggetto, "Firewall perimetrale");
        long secondo = invio(soggetto);

        Risposta profiloPrimo = get("/api/v1/soggetti/" + soggetto + "/invii/" + primo + "/profilo", LIA_REVISORE);
        Risposta profiloSecondo = get("/api/v1/soggetti/" + soggetto + "/invii/" + secondo + "/profilo", GIORGIO);

        assertThat(profiloPrimo.<List<String>>json("$.elementi[*].nome")).containsExactly("Server di posta");
        assertThat(profiloPrimo.<String>json("$.invio.registratoDa")).isEqualTo("giorgio.referente");
        assertThat(profiloSecondo.<List<String>>json("$.elementi[*].nome"))
                .containsExactlyInAnyOrder("Server di posta (cluster)", "Firewall perimetrale");
    }

    @Test
    void ilConfrontoMostraAggiuntiRimossiEModificati() {
        long soggetto = soggetto();
        long server = nuovoAsset(soggetto, "Server");
        long vecchio = nuovoAsset(soggetto, "Vecchio NAS");
        long primo = invio(soggetto);

        jdbc.update("UPDATE ElementoCritico SET nome = 'Server applicativo' WHERE id = ?", server);
        jdbc.update("UPDATE ElementoCritico SET stato = 'DISMESSO', data_fine_validita = SYSTIMESTAMP WHERE id = ?",
                vecchio);
        nuovoAsset(soggetto, "Nuovo NAS");
        jdbc.update("UPDATE SoggettoNIS SET pec = 'nuova@pec.it' WHERE id = ?", soggetto);
        long secondo = invio(soggetto);

        Risposta confronto = get("/api/v1/soggetti/" + soggetto + "/invii/confronto?da=" + primo + "&a=" + secondo,
                GIORGIO);

        assertThat(confronto.<List<String>>json("$.elementi.aggiunti[*].nome")).containsExactly("Nuovo NAS");
        assertThat(confronto.<List<String>>json("$.elementi.rimossi[*].nome")).containsExactly("Vecchio NAS");
        assertThat(confronto.<List<String>>json("$.elementi.modificati[*].dopo.nome"))
                .containsExactly("Server applicativo");
        assertThat(confronto.<List<String>>json("$.anagrafica[*].campo")).containsExactly("pec");
    }

    @Test
    void lEstrazioneCsvNeutralizzaLeFormuleEVieneTracciata() {
        long soggetto = soggetto();
        nuovoAsset(soggetto, "=HYPERLINK(\"http://attaccante.example\")");
        long id = invio(soggetto);

        Risposta csv = get("/api/v1/soggetti/" + soggetto + "/invii/" + id + "/csv/elementi", LIA_REVISORE);
        Risposta estrazioni = get("/api/v1/soggetti/" + soggetto + "/invii/" + id + "/estrazioni", GIORGIO);

        assertThat(csv.stato()).isEqualTo(200);
        assertThat(csv.corpo()).startsWith("﻿id;tipo;nome\r\n");
        assertThat(csv.corpo()).contains(";ASSET;\"'=HYPERLINK(");
        assertThat(estrazioni.<List<String>>json("$[*].utente")).containsExactly("lia.revisore");
        assertThat(estrazioni.<String>json("$[0].dettaglio")).isEqualTo("sezione=elementi;righe=1");
        assertThat(csv.corpo().getBytes(StandardCharsets.UTF_8)).isNotEmpty();
    }

    @Test
    void soloIlReferenteRegistraGliInvii() {
        long soggetto = soggetto();

        assertThat(invia(soggetto, ORNELLA).stato()).isEqualTo(403);
        assertThat(invia(soggetto, LIA_REVISORE).stato()).isEqualTo(403);
        assertThat(invia(soggetto, GIORGIO).stato()).isEqualTo(201);
    }

    @Test
    void unInvioDiUnAltroSoggettoNonSiVedeNeSiConfronta() {
        long mio = soggetto();
        long altro = soggetto();
        long invioAltrui = invio(altro);
        long mioInvio = invio(mio);

        Risposta profilo = get("/api/v1/soggetti/" + mio + "/invii/" + invioAltrui + "/profilo", GIORGIO);
        Risposta confronto = get("/api/v1/soggetti/" + mio + "/invii/confronto?da=" + mioInvio + "&a=" + invioAltrui,
                GIORGIO);
        Risposta sezione = get("/api/v1/soggetti/" + mio + "/invii/" + mioInvio + "/csv/password", GIORGIO);

        assertThat(profilo.stato()).isEqualTo(404);
        assertThat(confronto.stato()).isEqualTo(404);
        assertThat(sezione.stato()).isEqualTo(404);
    }

    @Test
    void lUtenteApplicativoNonPuoCorreggereNeCancellareInviiETracce() {
        long soggetto = soggetto();
        long id = invio(soggetto);

        assertThatThrownBy(() -> jdbc.update("UPDATE InvioACN SET periodo_riferimento = 2020 WHERE id = ?", id))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM InvioACN WHERE id = ?", id))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM LogEstrazione"))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");
    }
}

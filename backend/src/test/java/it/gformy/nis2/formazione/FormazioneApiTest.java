package it.gformy.nis2.formazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Piano di formazione e registro: coperture, coerenza del registro, isolamento e permessi. */
class FormazioneApiTest extends ApiTestBase {

    private static final String GIORGIO = TokenDiTest.token("giorgio.referente");
    private static final String LIA_REVISORE = TokenDiTest.token("lia.revisore");

    private long soggetto() {
        long id = nuovoSoggetto();
        assegna("giorgio.referente", id, "REFERENTE_NIS");
        assegna("lia.revisore", id, "REVISORE");
        return id;
    }

    private String base(long soggetto) {
        return "/api/v1/soggetti/" + soggetto + "/formazione";
    }

    private Risposta persona(long soggetto, String nome, String categoria) {
        return chiama(HttpMethod.POST, base(soggetto) + "/persone", GIORGIO,
                "{\"nome\": \"%s\", \"categoria\": \"%s\", \"ruolo\": \"ruolo\"}".formatted(nome, categoria));
    }

    private Risposta attivita(long soggetto, String titolo, String destinatari, String verifica) {
        return chiama(HttpMethod.POST, base(soggetto) + "/attivita", GIORGIO, """
                {"titolo": "%s", "contenuti": "Phishing, password, segnalazione degli eventi",
                 "destinatari": "%s", "dataPrevista": "%s", "durataOre": 2, "modalitaVerifica": %s}
                """.formatted(titolo, destinatari, LocalDate.now().minusDays(7), verifica == null ? "null" : "\"" + verifica + "\""));
    }

    private static long idDi(Risposta r, String percorso, String nome) {
        List<Integer> ids = r.json(percorso.formatted(nome));
        return ids.get(0);
    }

    private Risposta partecipa(long soggetto, long attivita, String esito, long... persone) {
        StringBuilder ids = new StringBuilder();
        for (long p : persone) {
            ids.append(ids.isEmpty() ? "" : ",").append(p);
        }
        return chiama(HttpMethod.POST, base(soggetto) + "/attivita/" + attivita + "/partecipazioni", GIORGIO,
                "{\"persone\": [%s], \"data\": \"%s\", \"esito\": \"%s\"}".formatted(ids, LocalDate.now(), esito));
    }

    @Test
    void laCoperturaSiAggiornaConIlRegistroEIRuoliSpecializzatiServeLaFormazioneDedicata() {
        long soggetto = soggetto();
        persona(soggetto, "Anna CdA", "ORGANO");
        persona(soggetto, "Bruno Sistemista", "RUOLO_SPECIALIZZATO");
        Risposta stato = persona(soggetto, "Carla Impiegata", "PERSONALE");
        long anna = idDi(stato, "$.persone[?(@.nome == '%s')].id", "Anna CdA");
        long bruno = idDi(stato, "$.persone[?(@.nome == '%s')].id", "Bruno Sistemista");
        long carla = idDi(stato, "$.persone[?(@.nome == '%s')].id", "Carla Impiegata");
        Risposta conCorso = attivita(soggetto, "Consapevolezza", "TUTTI", "Questionario finale");
        long corso = idDi(conCorso, "$.attivita[?(@.titolo == '%s')].id", "Consapevolezza");

        Risposta dopo = partecipa(soggetto, corso, "SUPERATA", anna, bruno, carla);

        assertThat(conCorso.<List<String>>json("$.attivita[*].stato")).containsExactly("DA_REGISTRARE");
        assertThat(dopo.<List<Double>>json("$.riepilogo.copertura[*].percentuale")).containsExactly(100.0, 0.0, 100.0);
        assertThat(dopo.<List<Boolean>>json("$.persone[?(@.nome == 'Bruno Sistemista')].inRegola")).containsExactly(false);
        assertThat(dopo.<List<String>>json("$.registro[*].registrataDa")).containsOnly("giorgio.referente");
        assertThat(dopo.<List<String>>json("$.attivita[*].stato")).containsExactly("SVOLTA");
    }

    @Test
    void ilRegistroRifiutaEsitiDestinatariEDuplicatiIncoerenti() {
        long soggetto = soggetto();
        Risposta stato = persona(soggetto, "Dario", "PERSONALE");
        long dario = idDi(stato, "$.persone[?(@.nome == '%s')].id", "Dario");
        long perOrgani = idDi(attivita(soggetto, "Per il CdA", "ORGANO", null), "$.attivita[?(@.titolo == '%s')].id", "Per il CdA");
        long conTest = idDi(attivita(soggetto, "Con test", "TUTTI", "Test"), "$.attivita[?(@.titolo == '%s')].id", "Con test");

        Risposta destinatarioSbagliato = partecipa(soggetto, perOrgani, "NON_PREVISTA", dario);
        Risposta esitoSbagliato = partecipa(soggetto, conTest, "NON_PREVISTA", dario);
        Risposta prima = partecipa(soggetto, conTest, "NON_SUPERATA", dario);
        Risposta doppia = partecipa(soggetto, conTest, "SUPERATA", dario);
        Risposta cambioDestinatari = chiama(HttpMethod.PUT, base(soggetto) + "/attivita/" + conTest, GIORGIO, """
                {"titolo": "Con test", "contenuti": "x", "destinatari": "ORGANO", "dataPrevista": "%s",
                 "modalitaVerifica": "Test"}
                """.formatted(LocalDate.now()));

        assertThat(destinatarioSbagliato.stato()).isEqualTo(400);
        assertThat(esitoSbagliato.<String>json("$.errori.esito")).contains("verifica");
        assertThat(prima.<List<Boolean>>json("$.persone[*].inRegola")).containsExactly(false);
        assertThat(doppia.stato()).isEqualTo(409);
        assertThat(cambioDestinatari.stato()).isEqualTo(409);
    }

    @Test
    void unaPersonaCessataEsceDallaCoperturaMaRestaNelRegistro() {
        long soggetto = soggetto();
        Risposta stato = persona(soggetto, "Elena", "PERSONALE");
        long elena = idDi(stato, "$.persone[?(@.nome == '%s')].id", "Elena");
        long corso = idDi(attivita(soggetto, "Base", "TUTTI", null), "$.attivita[?(@.titolo == '%s')].id", "Base");
        partecipa(soggetto, corso, "NON_PREVISTA", elena);

        Risposta cessata = chiama(HttpMethod.POST, base(soggetto) + "/persone/" + elena + "/cessazione", GIORGIO,
                "{\"data\": \"%s\"}".formatted(LocalDate.now()));

        assertThat(cessata.<Integer>json("$.riepilogo.copertura[2].persone")).isZero();
        assertThat(cessata.<List<String>>json("$.registro[*].persona")).containsExactly("Elena");
    }

    @Test
    void ilCsvDelRegistroETracciatoEIlRegistroNonSiModifica() {
        long soggetto = soggetto();
        Risposta stato = persona(soggetto, "=CMD()", "PERSONALE");
        long persona = idDi(stato, "$.persone[?(@.nome == '%s')].id", "=CMD()");
        long corso = idDi(attivita(soggetto, "Base", "TUTTI", null), "$.attivita[?(@.titolo == '%s')].id", "Base");
        partecipa(soggetto, corso, "NON_PREVISTA", persona);

        Risposta csv = get(base(soggetto) + "/registro.csv", LIA_REVISORE);
        Integer estrazioni = jdbc.queryForObject("""
                SELECT COUNT(*) FROM LogEstrazione l JOIN RegistroTabelle r ON r.id = l.registro_tabella_id
                WHERE r.nome_tabella = 'PartecipazioneFormazione' AND l.record_id = ? AND l.utente = 'lia.revisore'
                """, Integer.class, soggetto);

        assertThat(csv.corpo()).contains(";\"'=CMD()\";Base;TUTTI;NON_PREVISTA;");
        assertThat(estrazioni).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM PartecipazioneFormazione WHERE persona_id = ?", persona))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");
    }

    @Test
    void isolamentoTraSoggettiERevisoreInSolaLettura() {
        long soggetto = soggetto();
        long altro = nuovoSoggetto();
        assegna("giorgio.referente", altro, "REFERENTE_NIS");
        long estranea = idDi(persona(altro, "Estranea", "PERSONALE"), "$.persone[?(@.nome == '%s')].id", "Estranea");
        long corso = idDi(attivita(soggetto, "Base", "TUTTI", null), "$.attivita[?(@.titolo == '%s')].id", "Base");

        Risposta conPersonaAltrui = partecipa(soggetto, corso, "NON_PREVISTA", estranea);
        Risposta revisoreScrive = chiama(HttpMethod.POST, base(soggetto) + "/persone", LIA_REVISORE,
                "{\"nome\": \"X\", \"categoria\": \"PERSONALE\"}");
        Risposta revisoreLegge = get(base(soggetto), LIA_REVISORE);

        assertThat(conPersonaAltrui.stato()).isEqualTo(404);
        assertThat(revisoreScrive.stato()).isEqualTo(403);
        assertThat(revisoreLegge.stato()).isEqualTo(200);
    }
}

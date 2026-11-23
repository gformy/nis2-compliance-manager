package it.gformy.nis2.conformita;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Valutazione dei requisiti ACN applicabili e riepilogo di attuazione. */
class ConformitaApiTest extends ApiTestBase {

    private static final String GIORGIO = TokenDiTest.token("giorgio.referente");
    private static final String LIA_REVISORE = TokenDiTest.token("lia.revisore");

    private long soggettoDiGiorgio() {
        long id = nuovoSoggetto();
        assegna("giorgio.referente", id, "REFERENTE_NIS");
        return id;
    }

    private long requisito(String codice) {
        return jdbc.queryForObject("SELECT id FROM RequisitoMisura WHERE codice = ?", Long.class, codice);
    }

    private Risposta valuta(long soggetto, String codice, String stato, String token) {
        return chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/adempimenti/" + requisito(codice), token,
                "{\"stato\": \"%s\", \"note\": \"valutazione %s\"}".formatted(stato, stato));
    }

    @Test
    void unSoggettoVedeIRequisitiApplicabiliAncoraDaValutare() {
        long soggetto = soggettoDiGiorgio();

        Risposta requisiti = get("/api/v1/soggetti/" + soggetto + "/adempimenti", GIORGIO);

        assertThat(requisiti.<List<String>>json("$[*].codice"))
                .contains("GV.SC-04.1", "ID.AM-01.1", "ID.AM-04.1", "GV.OC-04.1", "RS.MA-01.1");
        assertThat(requisiti.<List<Object>>json("$[?(@.codice == 'ID.AM-01.1')].stato")).containsOnlyNulls();
    }

    @Test
    void ogniNuovaValutazioneConservaLaPrecedenteNelloStorico() {
        long soggetto = soggettoDiGiorgio();

        valuta(soggetto, "RS.MA-01.1", "NON_ATTUATO", GIORGIO);
        Risposta aggiornata = valuta(soggetto, "RS.MA-01.1", "ATTUATO", GIORGIO);
        Risposta versioni = get("/api/v1/soggetti/" + soggetto + "/adempimenti/" + requisito("RS.MA-01.1")
                + "/versioni", GIORGIO);

        assertThat(aggiornata.stato()).isEqualTo(200);
        assertThat(aggiornata.<String>json("$.stato")).isEqualTo("ATTUATO");
        assertThat(versioni.<List<String>>json("$[*].stato")).containsExactly("ATTUATO", "NON_ATTUATO");
        assertThat(versioni.<String>json("$[0].modificataDa")).isEqualTo("giorgio.referente");
    }

    @Test
    void ilRiepilogoPesaIParzialiAMetaEsclusiINonApplicabili() {
        long soggetto = soggettoDiGiorgio();
        valuta(soggetto, "ID.AM-01.1", "ATTUATO", GIORGIO);
        valuta(soggetto, "ID.AM-04.1", "PARZIALMENTE_ATTUATO", GIORGIO);
        valuta(soggetto, "GV.SC-04.1", "NON_ATTUATO", GIORGIO);
        valuta(soggetto, "GV.OC-04.1", "NON_APPLICABILE", GIORGIO);

        Risposta riepilogo = get("/api/v1/soggetti/" + soggetto + "/adempimenti/riepilogo", GIORGIO);

        assertThat(riepilogo.<Integer>json("$.complessivo.totale")).isGreaterThanOrEqualTo(5);
        assertThat(riepilogo.<Integer>json("$.complessivo.attuati")).isEqualTo(1);
        assertThat(riepilogo.<Integer>json("$.complessivo.parziali")).isEqualTo(1);
        assertThat(riepilogo.<Integer>json("$.complessivo.nonApplicabili")).isEqualTo(1);
        // Con i 5 requisiti del catalogo: (1 + 0,5) / (5 - 1) = 37,5%
        assertThat(riepilogo.<Double>json("$.percentualeAttuazione")).isEqualTo(37.5);
        assertThat(riepilogo.<List<Double>>json("$.misure[?(@.codice == 'ID.AM-04')].percentualeAttuazione"))
                .containsExactly(50.0);
    }

    @Test
    void unRequisitoNonApplicabileAlTipoDiSoggettoNonSiValuta() {
        long requisitoSoloEssenziali = jdbcProprietario().queryForObject("""
                SELECT id FROM RequisitoMisura WHERE codice = 'ID.AM-01.1'""", Long.class);
        jdbcProprietario().update("""
                INSERT INTO RequisitoMisura (codice, misura_id, numero_punto, testo)
                SELECT 'ID.AM-01.9', misura_id, 9, 'Requisito di prova solo per gli essenziali'
                FROM RequisitoMisura WHERE id = ?""", requisitoSoloEssenziali);
        jdbcProprietario().update("""
                INSERT INTO MisuraApplicabilita (requisito_id, tipo_soggetto_id)
                SELECT rm.id, ts.id FROM RequisitoMisura rm, TipoSoggettoNIS ts
                WHERE rm.codice = 'ID.AM-01.9' AND ts.codice = 'ESSENZIALE'""");
        long importante = soggettoDiGiorgio();
        jdbc.update("""
                UPDATE SoggettoNIS SET tipo_soggetto_id = (SELECT id FROM TipoSoggettoNIS WHERE codice = 'IMPORTANTE')
                WHERE id = ?""", importante);

        Risposta elenco = get("/api/v1/soggetti/" + importante + "/adempimenti", GIORGIO);
        Risposta valutazione = valuta(importante, "ID.AM-01.9", "ATTUATO", GIORGIO);

        assertThat(elenco.<List<String>>json("$[*].codice")).doesNotContain("ID.AM-01.9");
        assertThat(valutazione.stato()).isEqualTo(404);
    }

    @Test
    void statoNonPrevistoRevisoreESoggettoAltruiVengonoRespinti() {
        long soggetto = soggettoDiGiorgio();
        assegna("lia.revisore", soggetto, "REVISORE");
        long altrui = nuovoSoggetto();

        assertThat(valuta(soggetto, "ID.AM-01.1", "FATTO", GIORGIO).stato()).isEqualTo(400);
        assertThat(valuta(soggetto, "ID.AM-01.1", "ATTUATO", LIA_REVISORE).stato()).isEqualTo(403);
        assertThat(valuta(altrui, "ID.AM-01.1", "ATTUATO", GIORGIO).stato()).isEqualTo(404);
        assertThat(get("/api/v1/soggetti/" + soggetto + "/adempimenti/riepilogo", LIA_REVISORE).stato())
                .isEqualTo(200);
    }
}

package it.gformy.nis2.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Politiche e piani: catalogo, ciclo di approvazione e riesame, file tracciati, permessi. */
class GovernanceApiTest extends ApiTestBase {

    private static final String GIORGIO = TokenDiTest.token("giorgio.referente");
    private static final String LIA_REVISORE = TokenDiTest.token("lia.revisore");
    private static final byte[] PDF = "%PDF-1.7\nPolitica di prova\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private long soggetto() {
        long id = nuovoSoggetto();
        assegna("giorgio.referente", id, "REFERENTE_NIS");
        assegna("lia.revisore", id, "REVISORE");
        return id;
    }

    private String base(long soggetto) {
        return "/api/v1/soggetti/" + soggetto;
    }

    private long organo(long soggetto) {
        return chiama(HttpMethod.POST, base(soggetto) + "/organi", GIORGIO,
                "{\"nome\": \"Consiglio di amministrazione\", \"ruolo\": \"Organo di amministrazione\"}")
                .<Integer>json("$.id");
    }

    private long documento(long soggetto, String tipo) {
        return chiama(HttpMethod.POST, base(soggetto) + "/documenti", GIORGIO,
                "{\"tipo\": \"" + tipo + "\", \"versione\": \"1.0\"}").<Integer>json("$.documento.id");
    }

    private Risposta approva(long soggetto, long documento, long organo, LocalDate data) {
        return chiama(HttpMethod.POST, base(soggetto) + "/documenti/" + documento + "/approvazione", GIORGIO,
                "{\"data\": \"%s\", \"organoId\": %d}".formatted(data, organo));
    }

    @Test
    void ilCatalogoComprendeLeSediciPoliticheEIDocumentiDaApprovare() {
        long soggetto = soggetto();

        Risposta elenco = get(base(soggetto) + "/documenti", LIA_REVISORE);

        assertThat(elenco.<List<String>>json("$.voci[?(@.categoria == 'POLITICA')].ambito"))
                .containsExactly("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n", "o", "p");
        assertThat(elenco.<List<String>>json("$.voci[*].codice"))
                .contains("GV.RR-02", "ID.RA-05", "PR.AT-01", "RS.MA-01");
        assertThat(elenco.<Integer>json("$.riepilogo.mancanti")).isEqualTo(elenco.<Integer>json("$.riepilogo.totale"));
    }

    @Test
    void cicloDiVitaApprovazioneNuovaVersioneERiapprovazione() {
        long soggetto = soggetto();
        long organo = organo(soggetto);
        long id = documento(soggetto, "GV.PO-01-a");
        String doc = base(soggetto) + "/documenti/" + id;

        Risposta senzaFile = approva(soggetto, id, organo, LocalDate.now());
        caricaFile(doc + "/file", GIORGIO, "politica-rischio.pdf", PDF);
        Risposta approvato = approva(soggetto, id, organo, LocalDate.now());
        Risposta nuovoFile = caricaFile(doc + "/file?versione=2.0", GIORGIO, "politica-rischio-v2.pdf", PDF);
        Risposta versioni = get(doc + "/versioni", GIORGIO);

        assertThat(senzaFile.stato()).isEqualTo(409);
        assertThat(approvato.<String>json("$.stato")).isEqualTo("VALIDO");
        assertThat(approvato.<String>json("$.documento.approvatoDa.nome")).isEqualTo("Consiglio di amministrazione");
        assertThat(approvato.<String>json("$.documento.prossimoRiesame"))
                .isEqualTo(LocalDate.now().plusMonths(12).toString());
        assertThat(nuovoFile.<String>json("$.stato")).isEqualTo("DA_APPROVARE");
        assertThat(nuovoFile.<String>json("$.documento.versione")).isEqualTo("2.0");
        assertThat(versioni.<List<String>>json("$[*].approvatoDa"))
                .containsExactly(null, "Consiglio di amministrazione", null);
        assertThat(versioni.<String>json("$[2].modificataDa")).isEqualTo("giorgio.referente");
    }

    @Test
    void unRiesameScadutoSiRinnovaConUnNuovoRiesame() {
        long soggetto = soggetto();
        long organo = organo(soggetto);
        long id = documento(soggetto, "RS.MA-01");
        String doc = base(soggetto) + "/documenti/" + id;
        caricaFile(doc + "/file", GIORGIO, "piano-incidenti.pdf", PDF);

        Risposta vecchio = approva(soggetto, id, organo, LocalDate.now().minusMonths(13));
        Risposta primaDellApprovazione = chiama(HttpMethod.POST, doc + "/riesame", GIORGIO,
                "{\"data\": \"%s\"}".formatted(LocalDate.now().minusMonths(14)));
        Risposta riesaminato = chiama(HttpMethod.POST, doc + "/riesame", GIORGIO,
                "{\"data\": \"%s\"}".formatted(LocalDate.now()));

        assertThat(vecchio.<String>json("$.stato")).isEqualTo("RIESAME_SCADUTO");
        assertThat(primaDellApprovazione.stato()).isEqualTo(400);
        assertThat(riesaminato.<String>json("$.stato")).isEqualTo("VALIDO");
    }

    @Test
    void iFileSiControllanoSiScaricanoIntattiEOgniScaricamentoETracciato() {
        long soggetto = soggetto();
        long id = documento(soggetto, "PR.AT-01");
        String doc = base(soggetto) + "/documenti/" + id;
        byte[] grande = Arrays.copyOf(PDF, 200_000);

        Risposta eseguibile = caricaFile(doc + "/file", GIORGIO, "piano.pdf", new byte[] {'M', 'Z', 1, 2});
        caricaFile(doc + "/file", GIORGIO, "piano-formazione.pdf", grande);
        long fileId = get(doc + "/file", GIORGIO).<Integer>json("$[0].id");
        byte[] scaricato = scaricaByte(doc + "/file/" + fileId, LIA_REVISORE);
        Integer consultazioni = jdbc.queryForObject("""
                SELECT COUNT(*) FROM LogEstrazione l JOIN RegistroTabelle r ON r.id = l.registro_tabella_id
                WHERE r.nome_tabella = 'DocumentoGovernance' AND l.record_id = ?
                  AND l.tipo_operazione = 'CONSULTAZIONE' AND l.utente = 'lia.revisore'
                """, Integer.class, id);

        assertThat(eseguibile.stato()).isEqualTo(400);
        assertThat(eseguibile.<String>json("$.errori.file")).contains("estensione");
        assertThat(scaricato).isEqualTo(grande);
        assertThat(consultazioni).isEqualTo(1);
    }

    @Test
    void organiEDocumentiSonoIsolatiPerSoggettoEIRevisoriNonScrivono() {
        long soggetto = soggetto();
        long altro = nuovoSoggetto();
        assegna("giorgio.referente", altro, "REFERENTE_NIS");
        long organoAltrui = organo(altro);
        long documentoAltrui = documento(altro, "GV.PO-01-b");
        long id = documento(soggetto, "GV.PO-01-b");
        caricaFile(base(soggetto) + "/documenti/" + id + "/file", GIORGIO, "p.pdf", PDF);

        Risposta conOrganoAltrui = approva(soggetto, id, organoAltrui, LocalDate.now());
        Risposta documentoDiAltri = get(base(soggetto) + "/documenti/" + documentoAltrui + "/file", GIORGIO);
        Risposta revisoreCarica = caricaFile(base(soggetto) + "/documenti/" + id + "/file", LIA_REVISORE, "p.pdf", PDF);
        Risposta doppio = chiama(HttpMethod.POST, base(soggetto) + "/documenti", GIORGIO, "{\"tipo\": \"GV.PO-01-b\"}");

        assertThat(conOrganoAltrui.stato()).isEqualTo(404);
        assertThat(documentoDiAltri.stato()).isEqualTo(404);
        assertThat(revisoreCarica.stato()).isEqualTo(403);
        assertThat(doppio.stato()).isEqualTo(409);
    }

    @Test
    void unOrganoCheHaApprovatoNonSiEliminaEIFileNonSiCancellano() {
        long soggetto = soggetto();
        long organo = organo(soggetto);
        long id = documento(soggetto, "ID.RA-05");
        caricaFile(base(soggetto) + "/documenti/" + id + "/file", GIORGIO, "rischio.pdf", PDF);
        approva(soggetto, id, organo, LocalDate.now());

        Risposta elimina = chiama(HttpMethod.DELETE, base(soggetto) + "/organi/" + organo, GIORGIO, null);

        assertThat(elimina.stato()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM DocumentoGovernanceFile WHERE documento_id = ?", id))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");
        assertThatThrownBy(() -> jdbc.update("UPDATE DocumentoGovernanceFile SET nome_file = 'x' WHERE documento_id = ?", id))
                .hasMessageMatching("(?s).*ORA-(41900|01031).*");
    }
}

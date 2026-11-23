package it.gformy.nis2.fornitori;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Anagrafica fornitori condivisa, dipendenze per soggetto e sintesi per il profilo ACN. */
class FornitoriApiTest extends ApiTestBase {

    private static final String AMMINISTRATORE = TokenDiTest.token("anna.neri", "AMMINISTRATORE");
    private static final String ELENA = TokenDiTest.token("elena.operatrice");
    private static final String OSVALDO_REVISORE = TokenDiTest.token("osvaldo.revisore");
    private static final AtomicLong PROGRESSIVO = new AtomicLong(20_000_000_000L);

    private static String partitaIva() {
        return String.valueOf(PROGRESSIVO.incrementAndGet()).substring(0, 11);
    }

    private long creaFornitore(String denominazione, String cpv) {
        Risposta creato = chiama(HttpMethod.POST, "/api/v1/fornitori", AMMINISTRATORE, """
                {"codiceFiscale": "%s", "dati": {"denominazione": "%s", "paeseSedeLegale": "IT", "cpv": [%s]}}
                """.formatted(partitaIva(), denominazione, cpv));
        assertThat(creato.stato()).isEqualTo(201);
        return creato.<Integer>json("$.id").longValue();
    }

    private long creaDipendenza(long soggetto, long fornitore, long elemento, String criterio) {
        Risposta creata = chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/dipendenze", ELENA, """
                {"fornitoreId": %d, "elementoCriticoId": %d, "criterioRilevanza": "%s",
                 "tipologiaFornitura": "Assistenza", "referenteContatto": "help@fornitore.example"}
                """.formatted(fornitore, elemento, criterio));
        assertThat(creata.stato()).isEqualTo(201);
        return creata.<Integer>json("$.id").longValue();
    }

    private long soggettoDiElena() {
        long id = nuovoSoggetto();
        assegna("elena.operatrice", id, "OPERATORE");
        return id;
    }

    @Test
    void soloLAmministratoreCreaEModificaIFornitori() {
        long id = creaFornitore("Cloud Test S.p.A.", "\"72000000\", \"72317000\"");

        assertThat(chiama(HttpMethod.POST, "/api/v1/fornitori", ELENA, """
                {"codiceFiscale": "%s", "dati": {"denominazione": "X", "paeseSedeLegale": "IT", "cpv": []}}
                """.formatted(partitaIva())).stato()).isEqualTo(403);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/fornitori/" + id, ELENA, """
                {"denominazione": "Rinominato", "paeseSedeLegale": "IT", "cpv": []}
                """).stato()).isEqualTo(403);

        Risposta letto = get("/api/v1/fornitori/" + id, ELENA);
        assertThat(letto.<List<String>>json("$.cpv[*].codice")).containsExactly("72000000", "72317000");
    }

    @Test
    void laModificaDelFornitoreVaNelloStoricoConLAutore() {
        long id = creaFornitore("Vecchio Nome S.r.l.", "");

        chiama(HttpMethod.PUT, "/api/v1/fornitori/" + id, AMMINISTRATORE, """
                {"denominazione": "Nuovo Nome S.r.l.", "paeseSedeLegale": "IT", "cpv": ["64200000"]}
                """);

        Risposta versioni = get("/api/v1/fornitori/" + id + "/versioni", ELENA);
        assertThat(versioni.<List<String>>json("$[*].denominazione")).containsExactly("Nuovo Nome S.r.l.", "Vecchio Nome S.r.l.");
        assertThat(versioni.<String>json("$[0].modificataDa")).isEqualTo("anna.neri");
    }

    @Test
    void codiceFiscaleECpvVengonoValidati() {
        Risposta italianoSbagliato = chiama(HttpMethod.POST, "/api/v1/fornitori", AMMINISTRATORE, """
                {"codiceFiscale": "ABC123", "dati": {"denominazione": "X", "paeseSedeLegale": "IT", "cpv": []}}
                """);
        Risposta esteroValido = chiama(HttpMethod.POST, "/api/v1/fornitori", AMMINISTRATORE, """
                {"codiceFiscale": "FR%s", "dati": {"denominazione": "Fournisseur SAS", "paeseSedeLegale": "FR", "cpv": []}}
                """.formatted(partitaIva()));
        Risposta cpvSconosciuto = chiama(HttpMethod.POST, "/api/v1/fornitori", AMMINISTRATORE, """
                {"codiceFiscale": "%s", "dati": {"denominazione": "Y", "paeseSedeLegale": "IT", "cpv": ["99999999"]}}
                """.formatted(partitaIva()));

        assertThat(italianoSbagliato.stato()).isEqualTo(400);
        assertThat(esteroValido.stato()).isEqualTo(201);
        assertThat(cpvSconosciuto.stato()).isEqualTo(400);
        assertThat(cpvSconosciuto.<String>json("$.errori.cpv")).contains("99999999");
    }

    @Test
    void laRicercaNonInterpretaICaratteriJollyScrittiDallUtente() {
        creaFornitore("Ricerca Speciale S.p.A.", "");

        assertThat(get("/api/v1/fornitori?q=speciale", ELENA).<List<String>>json("$[*].denominazione"))
                .contains("Ricerca Speciale S.p.A.");
        assertThat(get("/api/v1/fornitori?q=%25", ELENA).<List<Object>>json("$")).isEmpty();
    }

    @Test
    void unaDipendenzaSiCreaSoloSuUnElementoAttivoDelProprioSoggetto() {
        long mio = soggettoDiElena();
        long asset = nuovoAsset(mio, "Firewall perimetrale");
        long assetAltrui = nuovoAsset(nuovoSoggetto(), "Firewall altrui");
        long fornitore = creaFornitore("Sicurezza Reti S.r.l.", "\"32400000\"");

        creaDipendenza(mio, fornitore, asset, "ICT");

        Risposta duplicata = chiama(HttpMethod.POST, "/api/v1/soggetti/" + mio + "/dipendenze", ELENA, """
                {"fornitoreId": %d, "elementoCriticoId": %d, "criterioRilevanza": "NON_FUNGIBILE"}
                """.formatted(fornitore, asset));
        Risposta suAssetAltrui = chiama(HttpMethod.POST, "/api/v1/soggetti/" + mio + "/dipendenze", ELENA, """
                {"fornitoreId": %d, "elementoCriticoId": %d, "criterioRilevanza": "ICT"}
                """.formatted(fornitore, assetAltrui));
        jdbc.update("UPDATE ElementoCritico SET stato = 'DISMESSO' WHERE id = ?", asset);
        long altroFornitore = creaFornitore("Altro Fornitore S.r.l.", "");
        Risposta suAssetDismesso = chiama(HttpMethod.POST, "/api/v1/soggetti/" + mio + "/dipendenze", ELENA, """
                {"fornitoreId": %d, "elementoCriticoId": %d, "criterioRilevanza": "ICT"}
                """.formatted(altroFornitore, asset));

        assertThat(duplicata.stato()).isEqualTo(409);
        assertThat(suAssetAltrui.stato()).isEqualTo(404);
        assertThat(suAssetDismesso.stato()).isEqualTo(409);
    }

    @Test
    void cambiareIlCriterioCreaUnaVersioneEUnaDipendenzaAltruiNonSiTocca() {
        long mio = soggettoDiElena();
        long fornitore = creaFornitore("Software Gestionale S.p.A.", "\"48000000\"");
        long dipendenza = creaDipendenza(mio, fornitore, nuovoAsset(mio, "ERP"), "ICT");
        long dipendenzaAltrui = creaDipendenzaDiretta(fornitore, nuovoAsset(nuovoSoggetto(), "ERP altrui"));
        String modifica = """
                {"criterioRilevanza": "NON_FUNGIBILE", "tipologiaFornitura": "Unico fornitore del gestionale",
                 "referenteContatto": null, "stato": "ATTIVA"}
                """;

        Risposta aggiornata = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + mio + "/dipendenze/" + dipendenza, ELENA, modifica);
        Risposta versioni = get("/api/v1/soggetti/" + mio + "/dipendenze/" + dipendenza + "/versioni", ELENA);
        Risposta attraversoIlMio = chiama(HttpMethod.PUT,
                "/api/v1/soggetti/" + mio + "/dipendenze/" + dipendenzaAltrui, ELENA, modifica);

        assertThat(aggiornata.<String>json("$.criterioRilevanza")).isEqualTo("NON_FUNGIBILE");
        assertThat(versioni.<List<String>>json("$[*].criterioRilevanza")).containsExactly("NON_FUNGIBILE", "ICT");
        assertThat(versioni.<String>json("$[0].modificataDa")).isEqualTo("elena.operatrice");
        assertThat(attraversoIlMio.stato()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT criterio_rilevanza FROM Dipendenza WHERE id = ?", String.class,
                dipendenzaAltrui)).isEqualTo("ICT");
    }

    @Test
    void iFornitoriRilevantiRiportanoICinqueCampiDelPortaleAcn() {
        long soggetto = soggettoDiElena();
        long server = nuovoAsset(soggetto, "Server SCADA");
        long rete = nuovoAsset(soggetto, "Rete di controllo");
        long scada = creaFornitore("SCADA Fornitore S.p.A.", "\"48000000\", \"50312000\"");
        long cessato = creaFornitore("Fornitore Cessato S.r.l.", "");
        creaDipendenza(soggetto, scada, server, "ICT");
        creaDipendenza(soggetto, scada, rete, "NON_FUNGIBILE");
        long daCessare = creaDipendenza(soggetto, cessato, server, "ICT");
        chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/dipendenze/" + daCessare, ELENA, """
                {"criterioRilevanza": "ICT", "tipologiaFornitura": null, "referenteContatto": null, "stato": "CESSATA"}
                """);

        Risposta rilevanti = get("/api/v1/soggetti/" + soggetto + "/fornitori-rilevanti", ELENA);

        assertThat(rilevanti.<List<String>>json("$[*].denominazione")).containsExactly("SCADA Fornitore S.p.A.");
        assertThat(rilevanti.<String>json("$[0].paeseSedeLegale")).isEqualTo("IT");
        assertThat(rilevanti.<List<String>>json("$[0].codiciCpv")).containsExactly("48000000", "50312000");
        assertThat(rilevanti.<List<Integer>>json("$[0].criteriRilevanza")).containsExactly(1, 2);
    }

    @Test
    void ilRevisoreConsultaLeDipendenzeMaNonLeCrea() {
        long soggetto = soggettoDiElena();
        assegna("osvaldo.revisore", soggetto, "REVISORE");
        long fornitore = creaFornitore("Fornitore per revisore S.r.l.", "");
        long asset = nuovoAsset(soggetto, "Database clienti");
        creaDipendenza(soggetto, fornitore, asset, "ICT");

        assertThat(get("/api/v1/soggetti/" + soggetto + "/dipendenze", OSVALDO_REVISORE).<List<Object>>json("$"))
                .hasSize(1);
        assertThat(chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/dipendenze", OSVALDO_REVISORE, """
                {"fornitoreId": %d, "elementoCriticoId": %d, "criterioRilevanza": "ICT"}
                """.formatted(fornitore, asset)).stato()).isEqualTo(403);
    }

    private long creaDipendenzaDiretta(long fornitore, long elemento) {
        jdbc.update("""
                INSERT INTO Dipendenza (fornitore_id, elemento_critico_id, criterio_rilevanza)
                VALUES (?, ?, 'ICT')""", fornitore, elemento);
        return jdbc.queryForObject("SELECT id FROM Dipendenza WHERE fornitore_id = ? AND elemento_critico_id = ?",
                Long.class, fornitore, elemento);
    }
}

package it.gformy.nis2.organizzazioni;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Organizzazioni: quota del piano, isolamento tra organizzazioni, sospensione, logo. */
class OrganizzazioniApiTest extends ApiTestBase {

    private static final String GESTORE = TokenDiTest.token("gestore.piattaforma", "AMMINISTRATORE");

    private static String partitaIva() {
        return String.format("%011d", ThreadLocalRandom.current().nextLong(1, 99_999_999_999L));
    }

    private static String nomeUtente(String prefisso) {
        return prefisso + "." + ThreadLocalRandom.current().nextInt(1_000_000);
    }

    private long organizzazione(String piano) {
        return chiama(HttpMethod.POST, "/api/v1/organizzazioni", GESTORE, """
                {"nome": "Studio %s", "codiceFiscale": "%s", "tipo": "RIVENDITORE", "piano": "%s",
                 "riferimentoContratto": "RIV-2026-01"}
                """.formatted(piano, partitaIva(), piano)).<Integer>json("$.id");
    }

    private String amministratore(long organizzazione) {
        String utente = nomeUtente("admin.org");
        chiama(HttpMethod.PUT, "/api/v1/organizzazioni/" + organizzazione + "/amministratori/" + utente, GESTORE, null);
        return utente;
    }

    private Risposta creaSoggetto(String token) {
        return chiama(HttpMethod.POST, "/api/v1/soggetti", token, """
                {"codiceFiscale": "%s", "dati": %s}
                """.formatted(partitaIva(), datiSoggetto("Cliente dello studio", "cliente@pec.it")));
    }

    @Test
    void lAmministratoreDiOrganizzazioneCreaSoggettiEntroLaQuotaDelPiano() {
        long org = organizzazione("BASE");
        String admin = TokenDiTest.token(amministratore(org));

        Risposta primo = creaSoggetto(admin);
        Risposta secondo = creaSoggetto(admin);
        Risposta dettaglio = get("/api/v1/organizzazioni/" + org, admin);

        assertThat(primo.stato()).isEqualTo(201);
        assertThat(secondo.stato()).isEqualTo(409);
        assertThat(secondo.<String>json("$.detail")).contains("al massimo 1");
        assertThat(dettaglio.<Integer>json("$.soggetti")).isEqualTo(1);
        assertThat(dettaglio.<Number>json("$.costoMensile").doubleValue()).isEqualTo(59.0);
    }

    @Test
    void ilDatabaseRespingeISoggettiOltreLaQuotaAncheSenzaPassareDalBackend() {
        long org = organizzazione("BASE");
        creaSoggetto(TokenDiTest.token(amministratore(org)));

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO SoggettoNIS (codice_fiscale, denominazione, indirizzo_sede_legale, pec, telefono,
                                         tipo_soggetto_id, organizzazione_id)
                VALUES (?, 'Oltre quota', 'Via Roma 1', 'x@pec.it', '06', 1, ?)
                """, partitaIva(), org))
                .hasMessageContaining("ORA-20010");
    }

    @Test
    void leOrganizzazioniSonoIsolateNelBackendENelDatabase() {
        long studioA = organizzazione("PROFESSIONAL");
        long studioB = organizzazione("PROFESSIONAL");
        String utenteA = amministratore(studioA);
        String adminA = TokenDiTest.token(utenteA);
        String adminB = TokenDiTest.token(amministratore(studioB));
        long soggettoB = creaSoggetto(adminB).<Integer>json("$.id");
        long soggettoA = creaSoggetto(adminA).<Integer>json("$.id");

        Risposta leggeAltrui = get("/api/v1/soggetti/" + soggettoB, adminA);
        Risposta organizzazioneAltrui = get("/api/v1/organizzazioni/" + studioB, adminA);
        Risposta elenco = get("/api/v1/soggetti", adminA);
        Risposta assegnazioneIncrociata = chiama(HttpMethod.PUT,
                "/api/v1/soggetti/" + soggettoB + "/utenti/" + utenteA, GESTORE, "{\"ruolo\": \"REVISORE\"}");

        assertThat(leggeAltrui.stato()).isEqualTo(404);
        assertThat(organizzazioneAltrui.stato()).isEqualTo(404);
        assertThat(elenco.<List<Integer>>json("$[*].id")).containsExactly((int) soggettoA);
        assertThat(assegnazioneIncrociata.stato()).isEqualTo(409);
        assertThat(assegnazioneIncrociata.<String>json("$.detail")).contains("altra organizzazione");
    }

    @Test
    void lAmministratoreDiOrganizzazioneGestisceGliAccessiDeiSuoiClienti() {
        long org = organizzazione("PROFESSIONAL");
        String admin = TokenDiTest.token(amministratore(org));
        long soggetto = creaSoggetto(admin).<Integer>json("$.id");
        String cliente = nomeUtente("cliente");

        Risposta assegna = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/utenti/" + cliente, admin,
                "{\"ruolo\": \"OPERATORE\"}");
        Risposta clienteLegge = get("/api/v1/soggetti/" + soggetto, TokenDiTest.token(cliente));
        Risposta clienteAssegna = chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/utenti/altro.utente",
                TokenDiTest.token(cliente), "{\"ruolo\": \"OPERATORE\"}");
        Risposta profilo = get("/api/v1/me", admin);

        assertThat(assegna.stato()).isEqualTo(200);
        assertThat(clienteLegge.stato()).isEqualTo(200);
        assertThat(clienteAssegna.stato()).isEqualTo(403);
        assertThat(profilo.<Integer>json("$.organizzazione.id")).isEqualTo((int) org);
        assertThat(get("/api/v1/soggetti/" + soggetto + "/permessi", admin).<Boolean>json("$.gestireAccessi")).isTrue();
        assertThat(get("/api/v1/soggetti/" + soggetto + "/permessi", TokenDiTest.token(cliente))
                .<Boolean>json("$.gestireAccessi")).isFalse();
    }

    @Test
    void unaOrganizzazioneSospesaRestaConsultabileMaNonModificabile() {
        long org = organizzazione("PROFESSIONAL");
        String admin = TokenDiTest.token(amministratore(org));
        long soggetto = creaSoggetto(admin).<Integer>json("$.id");
        String operatore = nomeUtente("operatore");
        chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto + "/utenti/" + operatore, admin, "{\"ruolo\": \"OPERATORE\"}");

        String partita = get("/api/v1/organizzazioni/" + org, GESTORE).json("$.codiceFiscale");
        chiama(HttpMethod.PUT, "/api/v1/organizzazioni/" + org, GESTORE, """
                {"nome": "Studio sospeso", "codiceFiscale": "%s", "tipo": "RIVENDITORE", "piano": "PROFESSIONAL",
                 "stato": "SOSPESA"}
                """.formatted(partita));
        String datiNuovi = datiSoggetto("Nuovo nome", "nuovo@pec.it");

        assertThat(get("/api/v1/soggetti/" + soggetto, admin).stato()).isEqualTo(200);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto, admin, datiNuovi).stato()).isEqualTo(403);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto, TokenDiTest.token(operatore), datiNuovi)
                .stato()).isEqualTo(403);
        assertThat(chiama(HttpMethod.PUT, "/api/v1/soggetti/" + soggetto, GESTORE, datiNuovi).stato()).isEqualTo(200);
    }

    @Test
    void ilPianoNonScendeSottoISoggettiPresentiEIlLogoDeveEssereUnImmagine() {
        long org = organizzazione("PROFESSIONAL");
        String admin = TokenDiTest.token(amministratore(org));
        creaSoggetto(admin);
        creaSoggetto(admin);
        String partita = get("/api/v1/organizzazioni/" + org, GESTORE).json("$.codiceFiscale");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0};

        Risposta riduzione = chiama(HttpMethod.PUT, "/api/v1/organizzazioni/" + org, GESTORE, """
                {"nome": "Studio", "codiceFiscale": "%s", "tipo": "RIVENDITORE", "piano": "BASE"}
                """.formatted(partita));
        Risposta logoFinto = caricaFileCon(HttpMethod.PUT, "/api/v1/organizzazioni/" + org + "/logo", admin,
                "logo.png", "<svg/>".getBytes());
        Risposta logo = caricaFileCon(HttpMethod.PUT, "/api/v1/organizzazioni/" + org + "/logo", admin, "logo.png", png);

        assertThat(riduzione.stato()).isEqualTo(409);
        assertThat(logoFinto.stato()).isEqualTo(400);
        assertThat(logo.<Boolean>json("$.conLogo")).isTrue();
        assertThat(scaricaByte("/api/v1/organizzazioni/" + org + "/logo", admin)).isEqualTo(png);
    }
}

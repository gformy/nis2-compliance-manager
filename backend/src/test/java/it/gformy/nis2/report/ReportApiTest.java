package it.gformy.nis2.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import it.gformy.nis2.ApiTestBase;
import it.gformy.nis2.TokenDiTest;

/** Report PDF: contenuto coerente con i dati, tracciamento e isolamento. */
class ReportApiTest extends ApiTestBase {

    private static final String GIORGIO = TokenDiTest.token("giorgio.referente");
    private static final String LIA_REVISORE = TokenDiTest.token("lia.revisore");

    @Test
    void ilReportContieneISoggettoGliIncidentiEIlRegistroLoTraccia() throws IOException {
        long soggetto = nuovoSoggetto();
        assegna("giorgio.referente", soggetto, "REFERENTE_NIS");
        assegna("lia.revisore", soggetto, "REVISORE");
        nuovoAsset(soggetto, "Server di controllo cabine");
        chiama(HttpMethod.POST, "/api/v1/soggetti/" + soggetto + "/incidenti", GIORGIO, """
                {"titolo": "Attacco DDoS al portale clienti", "tipoIncidente": "IS-3", "gravita": "ALTA",
                 "dataEvidenza": "%s"}
                """.formatted(Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)));

        byte[] pdf = scaricaByte("/api/v1/soggetti/" + soggetto + "/report.pdf", LIA_REVISORE);
        Integer tracce = jdbc.queryForObject("""
                SELECT COUNT(*) FROM LogEstrazione l JOIN RegistroTabelle r ON r.id = l.registro_tabella_id
                WHERE r.nome_tabella = 'SoggettoNIS' AND l.record_id = ? AND l.utente = 'lia.revisore'
                  AND DBMS_LOB.SUBSTR(l.snapshot_dati, 18, 1) = 'report_pdf;sha256='
                """, Integer.class, soggetto);

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String testo = new PDFTextStripper().getText(documento);
            assertThat(testo).contains("Stato di conformità NIS2", "Soggetto di test", "Server di controllo cabine",
                    "Attacco DDoS al portale clienti", "Pre-notifica entro il", "Politiche e piani");
            assertThat(testo).contains("lia.revisore");
        }
        assertThat(tracce).isEqualTo(1);
    }

    @Test
    void ilReportDiUnSoggettoNonAccessibileRisponde404() {
        long soggetto = nuovoSoggetto();

        Risposta risposta = get("/api/v1/soggetti/" + soggetto + "/report.pdf", GIORGIO);

        assertThat(risposta.stato()).isEqualTo(404);
    }
}

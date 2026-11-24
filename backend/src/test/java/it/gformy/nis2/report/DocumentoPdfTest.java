package it.gformy.nis2.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Impaginazione del PDF, senza database. */
class DocumentoPdfTest {

    private static final PDType1Font HELVETICA = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    @Test
    void iCaratteriNonRappresentabiliDiventanoPuntiInterrogativo() {
        assertThat(DocumentoPdf.testo("Attività · 59 € — ok", HELVETICA)).isEqualTo("Attività · 59 € — ok");
        assertThat(DocumentoPdf.testo("Mappa 🗺 ≥ 漢", HELVETICA)).isEqualTo("Mappa ? ? ?");
        assertThat(DocumentoPdf.testo(null, HELVETICA)).isEmpty();
    }

    @Test
    void ilTestoVaACapoESpezzaLeParoleTroppoLunghe() {
        List<String> righe = DocumentoPdf.aCapo("uno due tre quattro cinque sei sette", HELVETICA, 10, 60);
        List<String> hash = DocumentoPdf.aCapo("a".repeat(64), HELVETICA, 10, 60);

        assertThat(righe).hasSizeGreaterThan(1).allSatisfy(r -> assertThat(r).isNotBlank());
        assertThat(String.join(" ", righe)).isEqualTo("uno due tre quattro cinque sei sette");
        assertThat(hash).hasSizeGreaterThan(1);
        assertThat(String.join("", hash)).isEqualTo("a".repeat(64));
    }

    @Test
    void unaTabellaLungaContinuaSuPiuPagineConLaNumerazione() throws IOException {
        List<List<String>> righe = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            righe.add(List.of("Elemento " + i, "Descrizione piuttosto lunga che va a capo nella sua colonna " + i));
        }
        byte[] pdf;
        try (DocumentoPdf documento = new DocumentoPdf("Prova", "Test", "Piede di prova")) {
            documento.titolo("Titolo");
            documento.tabella(List.of("Nome", "Descrizione"), new float[] {1, 2}, righe);
            pdf = documento.chiudiEOttieni();
        }

        try (PDDocument letto = Loader.loadPDF(pdf)) {
            String testo = new PDFTextStripper().getText(letto);
            assertThat(letto.getNumberOfPages()).isGreaterThan(2);
            assertThat(testo).contains("Elemento 119", "Pagina 1 di " + letto.getNumberOfPages());
            assertThat(letto.getDocumentInformation().getTitle()).isEqualTo("Prova");
        }
    }
}

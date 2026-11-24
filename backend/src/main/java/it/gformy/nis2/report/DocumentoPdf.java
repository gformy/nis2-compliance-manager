package it.gformy.nis2.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

/**
 * Piccolo motore di impaginazione sopra PDFBox: titoli, paragrafi a capo automatico,
 * tabelle che si spezzano tra le pagine e piede di pagina con la numerazione.
 *
 * Usa i font standard Helvetica (codifica WinAnsi): copre l'italiano, accenti ed euro
 * compresi; i caratteri non rappresentabili diventano "?" invece di far fallire il report.
 */
final class DocumentoPdf implements AutoCloseable {

    static final float MARGINE = 50;
    private static final PDRectangle FORMATO = PDRectangle.A4;
    private static final float LARGHEZZA = FORMATO.getWidth() - 2 * MARGINE;
    private static final Color GRIGIO = new Color(0x55, 0x5f, 0x6b);
    private static final Color BLU = new Color(0x1a, 0x3a, 0x6b);
    private static final Color SFONDO_INTESTAZIONE = new Color(0xe8, 0xee, 0xf6);

    private final PDDocument documento = new PDDocument();
    private final PDType1Font normale = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font grassetto = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final String piede;
    private PDPageContentStream pagina;
    private float y;

    DocumentoPdf(String titolo, String autore, String piede) {
        this.piede = testo(piede, normale);
        PDDocumentInformation info = documento.getDocumentInformation();
        info.setTitle(titolo);
        info.setAuthor(autore);
        info.setCreator("NIS2 Compliance Manager");
        info.setCreationDate(Calendar.getInstance());
        nuovaPagina();
    }

    // ---------------------------------------------------------------- blocchi

    void logo(byte[] immagine, float altezza) {
        try {
            PDImageXObject logo = PDImageXObject.createFromByteArray(documento, immagine, "logo");
            float larghezza = Math.min(LARGHEZZA / 2, logo.getWidth() * altezza / logo.getHeight());
            float h = logo.getHeight() * larghezza / logo.getWidth();
            pagina.drawImage(logo, MARGINE, y - h, larghezza, h);
            y -= h + 12;
        } catch (IOException | IllegalArgumentException e) {
            // Un logo illeggibile non deve impedire il report.
        }
    }

    void titolo(String testo) {
        scrivi(testo, grassetto, 20, BLU, 26);
    }

    void sezione(String testo) {
        spazio(10);
        spazioPer(60);
        scrivi(testo, grassetto, 13, BLU, 18);
        disegna(() -> {
            pagina.setStrokingColor(BLU);
            pagina.setLineWidth(0.8f);
            pagina.moveTo(MARGINE, y + 4);
            pagina.lineTo(MARGINE + LARGHEZZA, y + 4);
            pagina.stroke();
        });
        spazio(6);
    }

    void paragrafo(String testo) {
        scrivi(testo, normale, 10, Color.BLACK, 14);
    }

    void nota(String testo) {
        scrivi(testo, normale, 8.5f, GRIGIO, 12);
    }

    /** Coppie etichetta/valore in due colonne (indicatori principali). */
    void indicatori(List<String[]> coppie) {
        float colonna = LARGHEZZA / 2;
        for (int i = 0; i < coppie.size(); i += 2) {
            spazioPer(34);
            for (int j = 0; j < 2 && i + j < coppie.size(); j++) {
                float x = MARGINE + j * colonna;
                String[] c = coppie.get(i + j);
                testoIn(x, y - 9, testo(c[0], normale), normale, 8.5f, GRIGIO);
                testoIn(x, y - 25, testo(c[1], grassetto), grassetto, 15, BLU);
            }
            y -= 36;
        }
    }

    /** Tabella con larghezze relative; le righe vanno a capo e la tabella continua sulle pagine successive. */
    void tabella(List<String> intestazioni, float[] pesi, List<List<String>> righe) {
        float somma = 0;
        for (float p : pesi) {
            somma += p;
        }
        float[] larghezze = new float[pesi.length];
        for (int i = 0; i < pesi.length; i++) {
            larghezze[i] = LARGHEZZA * pesi[i] / somma;
        }
        if (righe.isEmpty()) {
            nota("Nessun dato.");
            return;
        }
        rigaTabella(intestazioni, larghezze, true);
        for (List<String> riga : righe) {
            if (spazioPer(altezzaRiga(riga, larghezze, normale))) {
                rigaTabella(intestazioni, larghezze, true);
            }
            rigaTabella(riga, larghezze, false);
        }
        spazio(4);
    }

    void spazio(float punti) {
        y -= punti;
    }

    byte[] chiudiEOttieni() {
        try {
            pagina.close();
            numeraPagine();
            ByteArrayOutputStream uscita = new ByteArrayOutputStream();
            documento.save(uscita);
            return uscita.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            documento.close();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- testo

    /** Sostituisce i caratteri che la codifica WinAnsi di Helvetica non rappresenta. */
    static String testo(String valore, PDType1Font font) {
        if (valore == null) {
            return "";
        }
        StringBuilder pulito = new StringBuilder(valore.length());
        valore.replace('\t', ' ').replace("\r", "").codePoints().forEach(c -> {
            String carattere = new String(Character.toChars(c));
            if (c == '\n') {
                pulito.append(carattere);
                return;
            }
            try {
                font.encode(carattere);
                pulito.append(carattere);
            } catch (IOException | IllegalArgumentException e) {
                pulito.append('?');
            }
        });
        return pulito.toString();
    }

    /** Divide il testo in righe che stanno nella larghezza data. */
    static List<String> aCapo(String testo, PDType1Font font, float corpo, float larghezza) {
        List<String> righe = new ArrayList<>();
        for (String paragrafo : testo.split("\n", -1)) {
            StringBuilder riga = new StringBuilder();
            for (String parola : paragrafo.split(" ")) {
                String prova = riga.isEmpty() ? parola : riga + " " + parola;
                if (larghezza(prova, font, corpo) <= larghezza) {
                    riga.setLength(0);
                    riga.append(prova);
                    continue;
                }
                if (!riga.isEmpty()) {
                    righe.add(riga.toString());
                    riga.setLength(0);
                }
                // Parola piu' lunga della colonna (codici, hash): si spezza a caratteri.
                String resto = parola;
                while (larghezza(resto, font, corpo) > larghezza && resto.length() > 1) {
                    int n = resto.length() - 1;
                    while (n > 1 && larghezza(resto.substring(0, n), font, corpo) > larghezza) {
                        n--;
                    }
                    righe.add(resto.substring(0, n));
                    resto = resto.substring(n);
                }
                riga.append(resto);
            }
            righe.add(riga.toString());
        }
        return righe;
    }

    private static float larghezza(String testo, PDType1Font font, float corpo) {
        try {
            return font.getStringWidth(testo) / 1000 * corpo;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---------------------------------------------------------------- impaginazione

    private static final float CORPO_TABELLA = 8.5f;
    private static final float INTERLINEA_TABELLA = 11;
    private static final float PADDING = 4;

    private float altezzaRiga(List<String> celle, float[] larghezze, PDType1Font font) {
        int massimo = 1;
        for (int i = 0; i < celle.size(); i++) {
            massimo = Math.max(massimo,
                    aCapo(testo(celle.get(i), font), font, CORPO_TABELLA, larghezze[i] - 2 * PADDING).size());
        }
        return massimo * INTERLINEA_TABELLA + 2 * PADDING;
    }

    private void rigaTabella(List<String> celle, float[] larghezze, boolean intestazione) {
        PDType1Font font = intestazione ? grassetto : normale;
        float altezza = altezzaRiga(celle, larghezze, font);
        float alto = y;
        disegna(() -> {
            if (intestazione) {
                pagina.setNonStrokingColor(SFONDO_INTESTAZIONE);
                pagina.addRect(MARGINE, alto - altezza, LARGHEZZA, altezza);
                pagina.fill();
            }
            pagina.setStrokingColor(new Color(0xd0, 0xd5, 0xdd));
            pagina.setLineWidth(0.5f);
            pagina.moveTo(MARGINE, alto - altezza);
            pagina.lineTo(MARGINE + LARGHEZZA, alto - altezza);
            pagina.stroke();
        });
        float x = MARGINE;
        for (int i = 0; i < celle.size(); i++) {
            List<String> righe = aCapo(testo(celle.get(i), font), font, CORPO_TABELLA, larghezze[i] - 2 * PADDING);
            float riga = alto - PADDING - CORPO_TABELLA;
            for (String r : righe) {
                testoIn(x + PADDING, riga, r, font, CORPO_TABELLA, Color.BLACK);
                riga -= INTERLINEA_TABELLA;
            }
            x += larghezze[i];
        }
        y -= altezza;
    }

    private void scrivi(String valore, PDType1Font font, float corpo, Color colore, float interlinea) {
        for (String riga : aCapo(testo(valore, font), font, corpo, LARGHEZZA)) {
            spazioPer(interlinea);
            testoIn(MARGINE, y - corpo, riga, font, corpo, colore);
            y -= interlinea;
        }
    }

    private void testoIn(float x, float yBase, String riga, PDType1Font font, float corpo, Color colore) {
        disegna(() -> {
            pagina.beginText();
            pagina.setFont(font, corpo);
            pagina.setNonStrokingColor(colore);
            pagina.newLineAtOffset(x, yBase);
            pagina.showText(riga);
            pagina.endText();
        });
    }

    /** Va a pagina nuova se lo spazio non basta; restituisce true se ha cambiato pagina. */
    private boolean spazioPer(float altezza) {
        if (y - altezza < MARGINE + 20) {
            disegna(pagina::close);
            nuovaPagina();
            return true;
        }
        return false;
    }

    private void nuovaPagina() {
        PDPage nuova = new PDPage(FORMATO);
        documento.addPage(nuova);
        try {
            pagina = new PDPageContentStream(documento, nuova);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        y = FORMATO.getHeight() - MARGINE;
    }

    private void numeraPagine() throws IOException {
        int totale = documento.getNumberOfPages();
        for (int i = 0; i < totale; i++) {
            try (PDPageContentStream piedeDiPagina = new PDPageContentStream(documento, documento.getPage(i),
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                piedeDiPagina.beginText();
                piedeDiPagina.setFont(normale, 7.5f);
                piedeDiPagina.setNonStrokingColor(GRIGIO);
                piedeDiPagina.newLineAtOffset(MARGINE, MARGINE / 2);
                piedeDiPagina.showText(piede + "  ·  Pagina " + (i + 1) + " di " + totale);
                piedeDiPagina.endText();
            }
        }
    }

    @FunctionalInterface
    private interface Disegno {
        void esegui() throws IOException;
    }

    private void disegna(Disegno disegno) {
        try {
            disegno.esegui();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

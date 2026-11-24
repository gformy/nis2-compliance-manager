package it.gformy.nis2.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import it.gformy.nis2.comune.Errori;

/**
 * Controlli sui file caricati. Il tipo si decide dall'estensione e si verifica sui
 * primi byte del contenuto: il Content-Type dichiarato dal browser non conta.
 * Sono ammessi solo PDF e documenti Office/OpenDocument (archivi ZIP).
 */
public final class FileCaricati {

    public static final int DIMENSIONE_MASSIMA = 10 * 1024 * 1024;

    private static final byte[] PDF = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP = {'P', 'K', 3, 4};

    private static final Map<String, String> TIPI = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "odt", "application/vnd.oasis.opendocument.text",
            "ods", "application/vnd.oasis.opendocument.spreadsheet");

    public record FileVerificato(String nome, String contentType, byte[] contenuto, String sha256) {
    }

    private FileCaricati() {
    }

    public static FileVerificato verifica(String nomeOriginale, byte[] contenuto) {
        if (contenuto == null || contenuto.length == 0) {
            throw Errori.nonValido("file", "il file e' vuoto");
        }
        if (contenuto.length > DIMENSIONE_MASSIMA) {
            throw Errori.nonValido("file", "il file supera i 10 MB");
        }
        String nome = nomeSicuro(nomeOriginale);
        int punto = nome.lastIndexOf('.');
        String estensione = punto < 0 ? "" : nome.substring(punto + 1).toLowerCase(Locale.ROOT);
        String tipo = TIPI.get(estensione);
        if (tipo == null) {
            throw Errori.nonValido("file", "formato non ammesso: si accettano PDF, DOCX, XLSX, ODT e ODS");
        }
        byte[] attesi = "pdf".equals(estensione) ? PDF : ZIP;
        if (!iniziaCon(contenuto, attesi)) {
            throw Errori.nonValido("file", "il contenuto non corrisponde all'estensione ." + estensione);
        }
        return new FileVerificato(nome, tipo, contenuto, sha256(contenuto));
    }

    /** Solo il nome finale, senza percorsi ne' caratteri di controllo, al massimo 255 caratteri. */
    static String nomeSicuro(String nome) {
        if (nome == null) {
            return "documento";
        }
        String base = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1)
                .replaceAll("\\p{Cntrl}", "")
                .strip();
        if (base.isEmpty() || base.equals(".") || base.equals("..")) {
            return "documento";
        }
        return base.length() <= 255 ? base : base.substring(base.length() - 255);
    }

    private static boolean iniziaCon(byte[] contenuto, byte[] prefisso) {
        if (contenuto.length < prefisso.length) {
            return false;
        }
        for (int i = 0; i < prefisso.length; i++) {
            if (contenuto[i] != prefisso[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] contenuto) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenuto));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

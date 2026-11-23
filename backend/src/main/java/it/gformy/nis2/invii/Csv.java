package it.gformy.nis2.invii;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CSV per Excel in italiano: separatore punto e virgola, UTF-8 con BOM, righe CRLF.
 *
 * Ogni campo testuale passa da {@link #campo(Object)}, che lo protegge dalla
 * CSV/formula injection: un valore che inizia con = + - @ (o tab, ritorno a capo)
 * verrebbe eseguito da Excel come formula, quindi gli si antepone un apice.
 * Un nome di asset come {@code =HYPERLINK("http://...")} resta cosi' testo.
 */
public final class Csv {

    private static final String BOM = "﻿";
    private static final String SEPARATORE = ";";

    private Csv() {
    }

    public static byte[] documento(List<String> intestazioni, List<List<Object>> righe) {
        StringBuilder csv = new StringBuilder(BOM).append(riga(List.copyOf(intestazioni)));
        for (List<Object> r : righe) {
            csv.append(riga(r));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    static String riga(List<?> valori) {
        return valori.stream().map(Csv::campo).collect(Collectors.joining(SEPARATORE)) + "\r\n";
    }

    static String campo(Object valore) {
        if (valore == null) {
            return "";
        }
        String testo = valore.toString();
        if (valore instanceof String && !testo.isEmpty() && "=+-@\t\r".indexOf(testo.charAt(0)) >= 0) {
            testo = "'" + testo;
        }
        boolean daQuotare = testo.contains(SEPARATORE) || testo.contains("\"") || testo.contains("\n")
                || testo.contains("\r") || testo.startsWith("'");
        return daQuotare ? "\"" + testo.replace("\"", "\"\"") + "\"" : testo;
    }
}

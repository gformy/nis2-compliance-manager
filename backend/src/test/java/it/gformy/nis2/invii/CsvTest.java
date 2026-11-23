package it.gformy.nis2.invii;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Formato CSV e protezione dalla formula injection. */
class CsvTest {

    @Test
    void iValoriCheExcelEseguirebbeComeFormulaDiventanoTesto() {
        assertThat(Csv.campo("=HYPERLINK(\"http://x\")")).isEqualTo("\"'=HYPERLINK(\"\"http://x\"\")\"");
        assertThat(Csv.campo("+39 06 1234")).isEqualTo("\"'+39 06 1234\"");
        assertThat(Csv.campo("-1+1")).isEqualTo("\"'-1+1\"");
        assertThat(Csv.campo("@SUM(A1)")).isEqualTo("\"'@SUM(A1)\"");
    }

    @Test
    void numeriEDateNonVengonoAlterati() {
        assertThat(Csv.campo(-5)).isEqualTo("-5");
        assertThat(Csv.campo(java.time.LocalDate.of(2026, 3, 1))).isEqualTo("2026-03-01");
        assertThat(Csv.campo(null)).isEmpty();
    }

    @Test
    void separatoriVirgoletteEACapoSonoQuotati() {
        assertThat(Csv.campo("Rossi; Bianchi")).isEqualTo("\"Rossi; Bianchi\"");
        assertThat(Csv.campo("riga1\nriga2")).isEqualTo("\"riga1\nriga2\"");
        assertThat(Csv.campo("normale")).isEqualTo("normale");
    }

    @Test
    void ilDocumentoHaBomIntestazioniERigheCrlf() {
        byte[] documento = Csv.documento(List.of("a", "b"), List.of(Arrays.asList("x", null)));

        assertThat(new String(documento, StandardCharsets.UTF_8)).isEqualTo("﻿a;b\r\nx;\r\n");
    }
}

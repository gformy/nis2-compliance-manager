package it.gformy.nis2.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import it.gformy.nis2.governance.ModelloGovernance.StatoDocumento;

/** Regole di riesame (GV.PO-02) e controlli sui file, senza database. */
class StatoDocumentiTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 11, 24);

    @Test
    void ilProssimoRiesameDecorreDallUltimoRiesameODallApprovazione() {
        LocalDate approvato = LocalDate.of(2026, 1, 15);
        assertThat(StatoDocumenti.prossimoRiesame(approvato, null, 12)).isEqualTo("2027-01-15");
        assertThat(StatoDocumenti.prossimoRiesame(approvato, LocalDate.of(2026, 6, 1), 6)).isEqualTo("2026-12-01");
        assertThat(StatoDocumenti.prossimoRiesame(null, null, 12)).isNull();
    }

    @Test
    void lStatoSegueApprovazioneEScadenzaDelRiesame() {
        assertThat(StatoDocumenti.stato(false, null, OGGI)).isEqualTo(StatoDocumento.MANCANTE);
        assertThat(StatoDocumenti.stato(true, null, OGGI)).isEqualTo(StatoDocumento.DA_APPROVARE);
        assertThat(StatoDocumenti.stato(true, OGGI.plusDays(31), OGGI)).isEqualTo(StatoDocumento.VALIDO);
        assertThat(StatoDocumenti.stato(true, OGGI.plusDays(30), OGGI)).isEqualTo(StatoDocumento.RIESAME_IN_SCADENZA);
        assertThat(StatoDocumenti.stato(true, OGGI, OGGI)).isEqualTo(StatoDocumento.RIESAME_IN_SCADENZA);
        assertThat(StatoDocumenti.stato(true, OGGI.minusDays(1), OGGI)).isEqualTo(StatoDocumento.RIESAME_SCADUTO);
    }

    @Test
    void siAccettanoSoloPdfEDocumentiOfficeConContenutoCoerente() {
        byte[] pdf = "%PDF-1.7 prova".getBytes(StandardCharsets.US_ASCII);
        byte[] zip = {'P', 'K', 3, 4, 0, 0};
        byte[] eseguibile = {'M', 'Z', 0, 0};

        assertThat(FileCaricati.verifica("politica.pdf", pdf).contentType()).isEqualTo("application/pdf");
        assertThat(FileCaricati.verifica("piano.DOCX", zip).nome()).isEqualTo("piano.DOCX");
        assertThatThrownBy(() -> FileCaricati.verifica("politica.pdf", eseguibile)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(() -> FileCaricati.verifica("script.exe", eseguibile)).isInstanceOf(org.springframework.web.ErrorResponseException.class);
        assertThatThrownBy(() -> FileCaricati.verifica("vuoto.pdf", new byte[0])).isInstanceOf(org.springframework.web.ErrorResponseException.class);
    }

    @Test
    void ilNomeDelFilePerdeIPercorsiEICaratteriDiControllo() {
        assertThat(FileCaricati.nomeSicuro("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(FileCaricati.nomeSicuro("C:\\\\Users\\\\x\\\\piano.pdf")).isEqualTo("piano.pdf");
        assertThat(FileCaricati.nomeSicuro("a\r\nb.pdf")).isEqualTo("ab.pdf");
        assertThat(FileCaricati.nomeSicuro("..")).isEqualTo("documento");
    }
}

package it.gformy.nis2.formazione;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.gformy.nis2.formazione.ModelloFormazione.Categoria;
import it.gformy.nis2.formazione.ModelloFormazione.Destinatari;
import it.gformy.nis2.formazione.ModelloFormazione.Esito;
import it.gformy.nis2.formazione.ModelloFormazione.Partecipazione;
import it.gformy.nis2.formazione.ModelloFormazione.StatoAttivita;
import it.gformy.nis2.formazione.RegoleFormazione.PersonaValutata;

class RegoleFormazioneTest {

    private static final LocalDate OGGI = LocalDate.of(2026, 11, 24);

    private static Partecipazione p(Destinatari destinatari, LocalDate data, Esito esito) {
        return new Partecipazione(1, 1, "corso", destinatari, 1, "persona", data, esito, null, null, "test");
    }

    @Test
    void contaSoloLaFormazioneValidaDegliUltimiDodiciMesi() {
        List<Partecipazione> registro = List.of(
                p(Destinatari.TUTTI, OGGI.minusMonths(13), Esito.SUPERATA),
                p(Destinatari.TUTTI, OGGI.minusMonths(2), Esito.NON_SUPERATA),
                p(Destinatari.TUTTI, OGGI.minusMonths(5), Esito.NON_PREVISTA));

        assertThat(RegoleFormazione.ultimaFormazioneValida(Categoria.PERSONALE, registro, OGGI))
                .contains(OGGI.minusMonths(5));
        assertThat(RegoleFormazione.ultimaFormazioneValida(Categoria.PERSONALE, registro.subList(0, 2), OGGI))
                .isEmpty();
    }

    @Test
    void iRuoliSpecializzatiSonoInRegolaSoloConLaFormazioneDedicata() {
        List<Partecipazione> generale = List.of(p(Destinatari.TUTTI, OGGI.minusMonths(1), Esito.SUPERATA));
        List<Partecipazione> dedicata = List.of(p(Destinatari.RUOLO_SPECIALIZZATO, OGGI.minusMonths(1), Esito.SUPERATA));

        assertThat(RegoleFormazione.ultimaFormazioneValida(Categoria.RUOLO_SPECIALIZZATO, generale, OGGI)).isEmpty();
        assertThat(RegoleFormazione.ultimaFormazioneValida(Categoria.RUOLO_SPECIALIZZATO, dedicata, OGGI)).isPresent();
        assertThat(RegoleFormazione.ultimaFormazioneValida(Categoria.ORGANO, generale, OGGI)).isPresent();
        assertThat(RegoleFormazione.destinatariCompatibili(Destinatari.ORGANO, Categoria.PERSONALE)).isFalse();
        assertThat(RegoleFormazione.destinatariCompatibili(Destinatari.TUTTI, Categoria.RUOLO_SPECIALIZZATO)).isTrue();
    }

    @Test
    void lEsitoDeveEssereCoerenteConLaPresenzaDiUnaVerifica() {
        assertThat(RegoleFormazione.esitoNonCoerente("Test a risposta multipla", Esito.NON_PREVISTA)).isPresent();
        assertThat(RegoleFormazione.esitoNonCoerente(null, Esito.SUPERATA)).isPresent();
        assertThat(RegoleFormazione.esitoNonCoerente("Test", Esito.NON_SUPERATA)).isEmpty();
        assertThat(RegoleFormazione.esitoNonCoerente(" ", Esito.NON_PREVISTA)).isEmpty();
    }

    @Test
    void statoDelleAttivitaECoperturaPerCategoria() {
        assertThat(RegoleFormazione.statoAttivita(OGGI.minusDays(1), 0, OGGI)).isEqualTo(StatoAttivita.DA_REGISTRARE);
        assertThat(RegoleFormazione.statoAttivita(OGGI, 0, OGGI)).isEqualTo(StatoAttivita.PIANIFICATA);
        assertThat(RegoleFormazione.statoAttivita(OGGI.minusDays(1), 3, OGGI)).isEqualTo(StatoAttivita.SVOLTA);

        var copertura = RegoleFormazione.copertura(List.of(
                new PersonaValutata(Categoria.PERSONALE, true, true),
                new PersonaValutata(Categoria.PERSONALE, true, false),
                new PersonaValutata(Categoria.PERSONALE, true, false),
                new PersonaValutata(Categoria.PERSONALE, false, false)));

        assertThat(copertura.get(2).categoria()).isEqualTo(Categoria.PERSONALE);
        assertThat(copertura.get(2).persone()).isEqualTo(3);
        assertThat(copertura.get(2).percentuale()).isEqualTo(33.3);
        assertThat(copertura.get(0).percentuale()).isZero();
    }
}

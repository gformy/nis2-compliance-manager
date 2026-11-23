package it.gformy.nis2.incidenti;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import it.gformy.nis2.incidenti.ModelloIncidenti.Notifica;
import it.gformy.nis2.incidenti.ModelloIncidenti.Scadenza;
import it.gformy.nis2.incidenti.ModelloIncidenti.StatoScadenza;
import it.gformy.nis2.incidenti.ModelloIncidenti.TipoNotifica;

/** Termini dell'art. 25 del D.Lgs. 138/2024, senza database. */
class ScadenzeTest {

    private static final Instant EVIDENZA = Instant.parse("2026-03-10T08:00:00Z");

    private static Notifica notifica(TipoNotifica tipo, Instant quando) {
        return new Notifica(1, tipo, quando, null, "test");
    }

    private static Scadenza di(List<Scadenza> scadenze, TipoNotifica tipo) {
        return scadenze.stream().filter(s -> s.tipo() == tipo).findFirst().orElseThrow();
    }

    @Test
    void appenaRilevatoPreNotificaENotificaSonoInTempoELaRelazioneFinaleAttendeLaNotifica() {
        List<Scadenza> scadenze = Scadenze.calcola(EVIDENZA, List.of(), EVIDENZA.plus(Duration.ofHours(1)));

        assertThat(di(scadenze, TipoNotifica.PRE_NOTIFICA).scadenza()).isEqualTo("2026-03-11T08:00:00Z");
        assertThat(di(scadenze, TipoNotifica.PRE_NOTIFICA).stato()).isEqualTo(StatoScadenza.IN_TEMPO);
        assertThat(di(scadenze, TipoNotifica.NOTIFICA).scadenza()).isEqualTo("2026-03-13T08:00:00Z");
        assertThat(di(scadenze, TipoNotifica.RELAZIONE_FINALE).stato()).isEqualTo(StatoScadenza.IN_ATTESA);
        assertThat(Scadenze.prossima(scadenze)).map(Scadenza::tipo).contains(TipoNotifica.PRE_NOTIFICA);
    }

    @Test
    void nellUltimoQuartoDelTempoLaScadenzaEInScadenzaEPoiScaduta() {
        Instant dopo19Ore = EVIDENZA.plus(Duration.ofHours(19));
        Instant dopo25Ore = EVIDENZA.plus(Duration.ofHours(25));

        assertThat(di(Scadenze.calcola(EVIDENZA, List.of(), dopo19Ore), TipoNotifica.PRE_NOTIFICA).stato())
                .isEqualTo(StatoScadenza.IN_SCADENZA);
        assertThat(di(Scadenze.calcola(EVIDENZA, List.of(), dopo25Ore), TipoNotifica.PRE_NOTIFICA).stato())
                .isEqualTo(StatoScadenza.SCADUTA);
    }

    @Test
    void unaTrasmissioneOltreIlTermineRestaSegnalataComeInRitardo() {
        List<Notifica> notifiche = List.of(
                notifica(TipoNotifica.PRE_NOTIFICA, EVIDENZA.plus(Duration.ofHours(30))),
                notifica(TipoNotifica.NOTIFICA, EVIDENZA.plus(Duration.ofHours(48))));

        List<Scadenza> scadenze = Scadenze.calcola(EVIDENZA, notifiche, EVIDENZA.plus(Duration.ofDays(3)));

        assertThat(di(scadenze, TipoNotifica.PRE_NOTIFICA).stato()).isEqualTo(StatoScadenza.TRASMESSA_IN_RITARDO);
        assertThat(di(scadenze, TipoNotifica.NOTIFICA).stato()).isEqualTo(StatoScadenza.TRASMESSA);
    }

    @Test
    void laRelazioneFinaleScadeUnMeseDopoLaNotificaSulCalendarioItaliano() {
        // Notifica il 12 marzo alle 10 ora italiana (09 UTC): nel frattempo scatta l'ora legale,
        // quindi il 12 aprile alle 10 italiane sono le 08 UTC.
        Instant notificaIl = Instant.parse("2026-03-12T09:00:00Z");
        List<Scadenza> scadenze = Scadenze.calcola(EVIDENZA,
                List.of(notifica(TipoNotifica.PRE_NOTIFICA, EVIDENZA.plus(Duration.ofHours(2))),
                        notifica(TipoNotifica.NOTIFICA, notificaIl)),
                notificaIl.plus(Duration.ofDays(1)));

        Scadenza finale = di(scadenze, TipoNotifica.RELAZIONE_FINALE);
        assertThat(finale.scadenza()).isEqualTo("2026-04-12T08:00:00Z");
        assertThat(finale.stato()).isEqualTo(StatoScadenza.IN_TEMPO);
        assertThat(Scadenze.prossima(scadenze)).map(Scadenza::tipo).contains(TipoNotifica.RELAZIONE_FINALE);
    }
}

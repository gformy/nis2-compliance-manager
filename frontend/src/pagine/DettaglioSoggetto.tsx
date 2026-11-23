import { Box, Breadcrumbs, Link, Stack, Tab, Tabs, Typography } from '@mui/material'
import { Link as RouterLink, Outlet, useLocation, useParams } from 'react-router'
import { ErroreApi } from '../api/client'
import { puoModificare, useProfilo } from '../api/profilo'
import { useSoggetto } from '../api/soggetti'
import { StatoCaricamento } from '../componenti/StatoCaricamento'
import { useContestoSoggetto, type ContestoSoggetto } from './contestoSoggetto'
import { FormAnagrafica } from './FormAnagrafica'
import { GestioneAccessi } from './GestioneAccessi'
import { StoricoVersioni } from './StoricoVersioni'

const schede = [
  { percorso: '', etichetta: 'Anagrafica' },
  { percorso: 'asset', etichetta: 'Asset' },
  { percorso: 'servizi', etichetta: 'Servizi' },
  { percorso: 'fornitori', etichetta: 'Fornitori' },
]

/** Intestazione del soggetto con le schede; il contenuto della scheda arriva dalle rotte figlie. */
export function DettaglioSoggetto() {
  const id = Number(useParams().id)
  const { pathname } = useLocation()
  const { data: soggetto, error } = useSoggetto(id)
  const { data: profilo } = useProfilo()
  // Per un soggetto non assegnato il backend risponde 404, come se non esistesse
  const errore =
    error instanceof ErroreApi && error.stato === 404
      ? new Error('Soggetto non trovato oppure non assegnato al tuo utente.')
      : error

  const schedaAttiva = pathname.split('/')[3] ?? ''

  return (
    <Stack spacing={3}>
      <Breadcrumbs>
        <Link component={RouterLink} to="/">
          Soggetti NIS
        </Link>
        <Typography color="text.primary">{soggetto?.denominazione ?? '…'}</Typography>
      </Breadcrumbs>
      {!soggetto ? (
        <StatoCaricamento errore={errore} />
      ) : (
        <>
          <div>
            <Typography variant="h4" component="h1">
              {soggetto.denominazione}
            </Typography>
            <Typography color="text.secondary">Codice fiscale {soggetto.codiceFiscale}</Typography>
          </div>
          <Box sx={{ borderBottom: 1, borderColor: 'divider' }}>
            <Tabs value={schedaAttiva}>
              {schede.map(({ percorso, etichetta }) => (
                <Tab
                  key={etichetta}
                  value={percorso}
                  label={etichetta}
                  component={RouterLink}
                  to={`/soggetti/${id}${percorso ? `/${percorso}` : ''}`}
                />
              ))}
            </Tabs>
          </Box>
          <Outlet
            context={
              {
                soggetto,
                modificabile: puoModificare(profilo, soggetto.id),
                amministratore: profilo?.amministratore ?? false,
              } satisfies ContestoSoggetto
            }
          />
        </>
      )}
    </Stack>
  )
}

export function SchedaAnagrafica() {
  const { soggetto, modificabile, amministratore } = useContestoSoggetto()
  return (
    <Stack spacing={3}>
      <FormAnagrafica soggetto={soggetto} solaLettura={!modificabile} />
      <StoricoVersioni soggettoId={soggetto.id} />
      {amministratore && <GestioneAccessi soggettoId={soggetto.id} />}
    </Stack>
  )
}

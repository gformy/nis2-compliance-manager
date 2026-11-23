import { Breadcrumbs, Link, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { ErroreApi } from '../api/client'
import { puoModificare, useProfilo } from '../api/profilo'
import { useSoggetto } from '../api/soggetti'
import { StatoCaricamento } from '../componenti/StatoCaricamento'
import { FormAnagrafica } from './FormAnagrafica'
import { GestioneAccessi } from './GestioneAccessi'
import { StoricoVersioni } from './StoricoVersioni'

export function DettaglioSoggetto() {
  const id = Number(useParams().id)
  const { data: soggetto, error } = useSoggetto(id)
  const { data: profilo } = useProfilo()
  // Per un soggetto non assegnato il backend risponde 404, come se non esistesse
  const errore =
    error instanceof ErroreApi && error.stato === 404
      ? new Error('Soggetto non trovato oppure non assegnato al tuo utente.')
      : error

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
          <FormAnagrafica soggetto={soggetto} solaLettura={!puoModificare(profilo, soggetto.id)} />
          <StoricoVersioni soggettoId={soggetto.id} />
          {profilo?.amministratore && <GestioneAccessi soggettoId={soggetto.id} />}
        </>
      )}
    </Stack>
  )
}

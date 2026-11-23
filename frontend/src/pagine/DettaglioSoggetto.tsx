import { Breadcrumbs, Link, Stack, Typography } from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { useSoggetto } from '../api/soggetti'
import { StatoCaricamento } from '../componenti/StatoCaricamento'
import { FormAnagrafica } from './FormAnagrafica'
import { StoricoVersioni } from './StoricoVersioni'

export function DettaglioSoggetto() {
  const id = Number(useParams().id)
  const { data: soggetto, error } = useSoggetto(id)

  return (
    <Stack spacing={3}>
      <Breadcrumbs>
        <Link component={RouterLink} to="/">
          Soggetti NIS
        </Link>
        <Typography color="text.primary">{soggetto?.denominazione ?? '…'}</Typography>
      </Breadcrumbs>
      {!soggetto ? (
        <StatoCaricamento errore={error} />
      ) : (
        <>
          <div>
            <Typography variant="h4" component="h1">
              {soggetto.denominazione}
            </Typography>
            <Typography color="text.secondary">Codice fiscale {soggetto.codiceFiscale}</Typography>
          </div>
          <FormAnagrafica soggetto={soggetto} />
          <StoricoVersioni soggettoId={soggetto.id} />
        </>
      )}
    </Stack>
  )
}

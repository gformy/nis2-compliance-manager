import {
  Alert,
  Breadcrumbs,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { Link as RouterLink, useParams } from 'react-router'
import { useFornitore, useSalvaFornitore, useVersioniFornitore } from '../../api/fornitori'
import { nomePaese } from '../../api/inventario'
import { useProfilo } from '../../api/profilo'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { data } from '../inventario/date'
import { FormFornitore } from './FormFornitore'

export function DettaglioFornitore() {
  const id = Number(useParams().fornitoreId)
  const { data: fornitore, error } = useFornitore(id)
  const versioni = useVersioniFornitore(id)
  const { data: profilo } = useProfilo()
  const salva = useSalvaFornitore(id)
  const amministratore = profilo?.amministratore ?? false

  return (
    <Stack spacing={3}>
      <Breadcrumbs>
        <Link component={RouterLink} to="/fornitori">
          Fornitori
        </Link>
        <Typography color="text.primary">{fornitore?.denominazione ?? '…'}</Typography>
      </Breadcrumbs>
      {!fornitore ? (
        <StatoCaricamento errore={error} />
      ) : (
        <>
          <Typography variant="h4" component="h1">
            {fornitore.denominazione}
          </Typography>
          <Paper variant="outlined" sx={{ p: 3 }}>
            {!amministratore && (
              <Alert severity="info" sx={{ mb: 2 }}>
                L'anagrafica è condivisa tra tutti i soggetti: la modifica solo l'amministratore.
              </Alert>
            )}
            <FormFornitore
              key={`${fornitore.id}-${fornitore.denominazione}-${fornitore.cpv.length}`}
              nuovo={false}
              solaLettura={!amministratore}
              iniziali={{
                codiceFiscale: fornitore.codiceFiscale,
                denominazione: fornitore.denominazione,
                paeseSedeLegale: fornitore.paeseSedeLegale,
                cpv: fornitore.cpv.map((c) => c.codice),
              }}
              inCorso={salva.isPending}
              errore={salva.error}
              onSalva={(valori) =>
                salva.mutate({
                  dati: { denominazione: valori.denominazione, paeseSedeLegale: valori.paeseSedeLegale, cpv: valori.cpv },
                })
              }
            />
            {salva.isSuccess && (
              <Alert severity="success" sx={{ mt: 2 }}>
                Salvato: la versione precedente è nello storico.
              </Alert>
            )}
          </Paper>
          <Paper variant="outlined" sx={{ p: 3 }}>
            <Typography variant="h6" component="h2" gutterBottom>
              Storico
            </Typography>
            {!versioni.data ? (
              <StatoCaricamento errore={versioni.error} />
            ) : (
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Versione</TableCell>
                    <TableCell>Denominazione</TableCell>
                    <TableCell>Paese</TableCell>
                    <TableCell>Valida dal</TableCell>
                    <TableCell>Modificata da</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {versioni.data.map((v) => (
                    <TableRow key={v.versione}>
                      <TableCell>{v.versione}</TableCell>
                      <TableCell>{v.denominazione}</TableCell>
                      <TableCell>{nomePaese(v.paeseSedeLegale)}</TableCell>
                      <TableCell>{data(v.validoDa)}</TableCell>
                      <TableCell>{v.modificataDa ?? 'creazione'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </Paper>
        </>
      )}
    </Stack>
  )
}

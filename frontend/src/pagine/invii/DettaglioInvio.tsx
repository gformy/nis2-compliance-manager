import {
  Alert,
  Box,
  Button,
  Grid,
  Link,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  Typography,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import {
  etichetteTipoInvio,
  useEstrazioni,
  useProfiloInvio,
  useScaricaCsv,
  type Profilo,
  type Sezione,
} from '../../api/invii'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { data } from '../inventario/date'
import { etichetteCampi } from './confronto'

const euro = new Intl.NumberFormat('it-IT', { style: 'currency', currency: 'EUR' })

export function DettaglioInvio() {
  const { soggetto } = useContestoSoggetto()
  const id = Number(useParams().invioId)
  const { data: profilo, error } = useProfiloInvio(soggetto.id, id)
  const estrazioni = useEstrazioni(soggetto.id, id)
  const scarica = useScaricaCsv(soggetto.id, id)
  const [sezione, setSezione] = useState<Sezione>('elementi')
  const [erroreCsv, setErroreCsv] = useState<string | null>(null)

  if (!profilo) return <StatoCaricamento errore={error} />
  const { invio, anagrafica } = profilo

  return (
    <Stack spacing={3}>
      <Box>
        <Link component={RouterLink} to=".." relative="path">
          ← Tutti gli invii
        </Link>
      </Box>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h5" component="h2">
          {etichetteTipoInvio[invio.tipo]} {invio.periodoRiferimento}
        </Typography>
        <Typography color="text.secondary">
          Inviato il {data(invio.dataInvio)}, registrato da {invio.registratoDa}
        </Typography>
        {invio.note && <Typography sx={{ mt: 1 }}>{invio.note}</Typography>}
        <Grid container spacing={2} sx={{ mt: 1 }}>
          {(Object.keys(etichetteCampi) as (keyof typeof anagrafica)[]).map((campo) => (
            <Grid key={campo} size={{ xs: 12, sm: 6, md: 4 }}>
              <Typography variant="caption" color="text.secondary">
                {etichetteCampi[campo]}
              </Typography>
              <Typography>
                {anagrafica[campo] === null
                  ? '—'
                  : campo === 'fatturato'
                    ? euro.format(Number(anagrafica[campo]))
                    : String(anagrafica[campo])}
              </Typography>
            </Grid>
          ))}
        </Grid>
      </Paper>

      <Paper variant="outlined">
        <Box sx={{ display: 'flex', alignItems: 'center', borderBottom: 1, borderColor: 'divider', pr: 2 }}>
          <Tabs value={sezione} onChange={(_, v: Sezione) => setSezione(v)} sx={{ flexGrow: 1 }}>
            <Tab value="elementi" label={`Asset e servizi (${profilo.elementi.length})`} />
            <Tab value="fornitori" label={`Fornitori rilevanti (${profilo.fornitori.length})`} />
            <Tab value="adempimenti" label={`Adempimenti (${profilo.adempimenti.length})`} />
          </Tabs>
          <Button
            onClick={() => {
              setErroreCsv(null)
              scarica(sezione).catch((e: Error) => setErroreCsv(e.message))
            }}
          >
            Scarica CSV
          </Button>
        </Box>
        <Box sx={{ p: 2 }}>
          {erroreCsv && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {erroreCsv}
            </Alert>
          )}
          <TabellaSezione profilo={profilo} sezione={sezione} />
        </Box>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h3" gutterBottom>
          Estrazioni di questo invio
        </Typography>
        {!estrazioni.data ? (
          <StatoCaricamento errore={estrazioni.error} />
        ) : estrazioni.data.length === 0 ? (
          <Typography color="text.secondary">Nessuna estrazione finora.</Typography>
        ) : (
          <Table size="small">
            <TableBody>
              {estrazioni.data.map((e, i) => (
                <TableRow key={i}>
                  <TableCell>{data(e.dataOra)}</TableCell>
                  <TableCell>{e.utente}</TableCell>
                  <TableCell>{e.dettaglio}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>
    </Stack>
  )
}

function TabellaSezione({ profilo, sezione }: { profilo: Profilo; sezione: Sezione }) {
  const righe: { chiave: string | number; celle: ReactNode[] }[] =
    sezione === 'elementi'
      ? profilo.elementi.map((e) => ({ chiave: e.id, celle: [e.tipo === 'ASSET' ? 'Asset' : 'Servizio', e.nome] }))
      : sezione === 'fornitori'
        ? profilo.fornitori.map((f) => ({
            chiave: f.dipendenzaId,
            celle: [f.fornitore, f.codiceFiscale, f.elemento, f.criterioRilevanza, f.tipologiaFornitura ?? '—'],
          }))
        : profilo.adempimenti.map((r) => ({
            chiave: r.requisito,
            celle: [r.requisito, r.stato.replaceAll('_', ' ').toLowerCase(), data(r.dataValutazione)],
          }))
  const intestazioni =
    sezione === 'elementi'
      ? ['Tipo', 'Nome']
      : sezione === 'fornitori'
        ? ['Fornitore', 'Codice fiscale', 'Elemento', 'Criterio', 'Fornitura']
        : ['Requisito', 'Stato', 'Valutato il']

  if (righe.length === 0) return <Typography color="text.secondary">Nessuna voce dichiarata.</Typography>
  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          {intestazioni.map((h) => (
            <TableCell key={h}>{h}</TableCell>
          ))}
        </TableRow>
      </TableHead>
      <TableBody>
        {righe.map((r) => (
          <TableRow key={r.chiave}>
            {r.celle.map((c, i) => (
              <TableCell key={i}>{c}</TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}

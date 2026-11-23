import {
  Alert,
  Box,
  Chip,
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
import type { ReactNode } from 'react'
import { Link as RouterLink, useSearchParams } from 'react-router'
import { etichetteTipoInvio, useConfronto, type Differenze, type Invio } from '../../api/invii'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { data } from '../inventario/date'
import { etichetteCampi, nessunaDifferenza, totaleDifferenze } from './confronto'

const descriviInvio = (i: Invio) => `${etichetteTipoInvio[i.tipo]} ${i.periodoRiferimento} del ${data(i.dataInvio)}`

export function ConfrontoInvii() {
  const { soggetto } = useContestoSoggetto()
  const [parametri] = useSearchParams()
  const da = Number(parametri.get('da'))
  const a = Number(parametri.get('a'))
  const { data: confronto, error } = useConfronto(soggetto.id, da, a)

  if (!confronto) return <StatoCaricamento errore={error} />

  return (
    <Stack spacing={3}>
      <Box>
        <Link component={RouterLink} to=".." relative="path">
          ← Tutti gli invii
        </Link>
      </Box>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h5" component="h2">
          Cosa è cambiato
        </Typography>
        <Typography color="text.secondary">
          Da {descriviInvio(confronto.da)} a {descriviInvio(confronto.a)}
        </Typography>
        {nessunaDifferenza(confronto) && (
          <Alert severity="success" sx={{ mt: 2 }}>
            I due profili dichiarati sono identici.
          </Alert>
        )}
      </Paper>

      {confronto.anagrafica.length > 0 && (
        <Paper variant="outlined" sx={{ p: 3 }}>
          <Typography variant="h6" component="h3" gutterBottom>
            Anagrafica
          </Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Campo</TableCell>
                <TableCell>Prima</TableCell>
                <TableCell>Dopo</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {confronto.anagrafica.map((c) => (
                <TableRow key={c.campo}>
                  <TableCell>{etichetteCampi[c.campo]}</TableCell>
                  <TableCell>{String(c.prima ?? '—')}</TableCell>
                  <TableCell>{String(c.dopo ?? '—')}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      )}

      <SezioneDifferenze
        titolo="Asset e servizi"
        differenze={confronto.elementi}
        descrivi={(e) => `${e.tipo === 'ASSET' ? 'Asset' : 'Servizio'}: ${e.nome}`}
      />
      <SezioneDifferenze
        titolo="Fornitori rilevanti"
        differenze={confronto.fornitori}
        descrivi={(f) => `${f.fornitore} per ${f.elemento} (${f.criterioRilevanza})`}
      />
      <SezioneDifferenze
        titolo="Adempimenti"
        differenze={confronto.adempimenti}
        descrivi={(r) => `${r.requisito}: ${r.stato.replaceAll('_', ' ').toLowerCase()}`}
      />
    </Stack>
  )
}

function SezioneDifferenze<T>({
  titolo,
  differenze,
  descrivi,
}: {
  titolo: string
  differenze: Differenze<T>
  descrivi: (voce: T) => ReactNode
}) {
  if (totaleDifferenze(differenze) === 0) return null
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h3" gutterBottom>
        {titolo}
      </Typography>
      <Stack spacing={1}>
        {differenze.aggiunti.map((v, i) => (
          <Riga key={`+${i}`} colore="success" etichetta="Aggiunto">
            {descrivi(v)}
          </Riga>
        ))}
        {differenze.rimossi.map((v, i) => (
          <Riga key={`-${i}`} colore="error" etichetta="Rimosso">
            {descrivi(v)}
          </Riga>
        ))}
        {differenze.modificati.map((m, i) => (
          <Riga key={`~${i}`} colore="warning" etichetta="Modificato">
            {descrivi(m.prima)} → {descrivi(m.dopo)}
          </Riga>
        ))}
      </Stack>
    </Paper>
  )
}

function Riga({
  colore,
  etichetta,
  children,
}: {
  colore: 'success' | 'error' | 'warning'
  etichetta: string
  children: ReactNode
}) {
  return (
    <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
      <Chip size="small" color={colore} label={etichetta} sx={{ minWidth: 96 }} />
      <Typography>{children}</Typography>
    </Box>
  )
}

import {
  Alert,
  Box,
  Button,
  Checkbox,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { etichetteTipoInvio, useInvii, useRegistraInvio, type TipoInvio } from '../../api/invii'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { data } from '../inventario/date'
import { ordinePerConfronto } from './confronto'

export function ElencoInvii() {
  const { soggetto, inviabile } = useContestoSoggetto()
  const { data: invii, error } = useInvii(soggetto.id)
  const naviga = useNavigate()
  const [scelti, setScelti] = useState<number[]>([])
  const [nuovo, setNuovo] = useState(false)

  const coppia = ordinePerConfronto(scelti, new Map((invii ?? []).map((i) => [i.id, i.dataInvio])))
  const alterna = (id: number) => setScelti((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id].slice(-2)))

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6" component="h2">
            Invii alla piattaforma ACN
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Ogni invio conserva il profilo dichiarato in quel momento, ricostruito dagli storici. Selezionane due per
            vedere cosa è cambiato.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          disabled={!coppia}
          onClick={() => coppia && naviga(`confronto?da=${coppia[0]}&a=${coppia[1]}`)}
        >
          Confronta
        </Button>
        {inviabile && (
          <Button variant="contained" onClick={() => setNuovo(true)}>
            Registra invio
          </Button>
        )}
      </Box>

      {!invii ? (
        <StatoCaricamento errore={error} />
      ) : invii.length === 0 ? (
        <Typography color="text.secondary">Nessun invio registrato.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell padding="checkbox" />
                <TableCell>Data</TableCell>
                <TableCell>Tipo</TableCell>
                <TableCell>Anno</TableCell>
                <TableCell>Registrato da</TableCell>
                <TableCell>Note</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {invii.map((i) => (
                <TableRow key={i.id} hover selected={scelti.includes(i.id)}>
                  <TableCell padding="checkbox">
                    <Checkbox
                      checked={scelti.includes(i.id)}
                      onChange={() => alterna(i.id)}
                      slotProps={{ input: { 'aria-label': `Seleziona l'invio del ${data(i.dataInvio)}` } }}
                    />
                  </TableCell>
                  <TableCell>
                    <Link component={RouterLink} to={String(i.id)}>
                      {data(i.dataInvio)}
                    </Link>
                  </TableCell>
                  <TableCell>{etichetteTipoInvio[i.tipo]}</TableCell>
                  <TableCell>{i.periodoRiferimento}</TableCell>
                  <TableCell>{i.registratoDa}</TableCell>
                  <TableCell>{i.note ?? '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {nuovo && <DialogoInvio soggettoId={soggetto.id} onChiudi={() => setNuovo(false)} />}
    </Paper>
  )
}

function DialogoInvio({ soggettoId, onChiudi }: { soggettoId: number; onChiudi: () => void }) {
  const registra = useRegistraInvio(soggettoId)
  const naviga = useNavigate()
  const [tipo, setTipo] = useState<TipoInvio>('AGGIORNAMENTO_ANNUALE')
  const [anno, setAnno] = useState(() => new Date().getFullYear())
  const [note, setNote] = useState('')

  return (
    <Dialog open onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>Registra un invio ad ACN</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Alert severity="info">
            Registra l’invio subito dopo averlo fatto sul portale ACN: il profilo viene fotografato adesso e non si
            potrà più modificare né cancellare.
          </Alert>
          <TextField select label="Tipo di invio" value={tipo} onChange={(e) => setTipo(e.target.value as TipoInvio)}>
            {(Object.keys(etichetteTipoInvio) as TipoInvio[]).map((t) => (
              <MenuItem key={t} value={t}>
                {etichetteTipoInvio[t]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            type="number"
            label="Anno di riferimento"
            value={anno}
            onChange={(e) => setAnno(Number(e.target.value))}
            slotProps={{ htmlInput: { min: 2024, max: 2100 } }}
          />
          <TextField
            label="Note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            multiline
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          {registra.error && <Alert severity="error">{registra.error.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button
          variant="contained"
          disabled={registra.isPending || anno < 2024 || anno > 2100}
          onClick={() =>
            registra.mutate(
              { tipo, periodoRiferimento: anno, note: note.trim() || null },
              { onSuccess: (invio) => naviga(String(invio.id)) },
            )
          }
        >
          Registra
        </Button>
      </DialogActions>
    </Dialog>
  )
}

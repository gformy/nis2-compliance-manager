import {
  Alert,
  Box,
  Button,
  MenuItem,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type FormEvent } from 'react'
import { etichetteRuolo, useAssegna, useAssegnazioni, useRevoca, type RuoloSoggetto } from '../api/profilo'
import { StatoCaricamento } from '../componenti/StatoCaricamento'

const formatoData = new Intl.DateTimeFormat('it-IT', { dateStyle: 'short', timeStyle: 'short' })
const FORMATO_UTENTE = /^[a-zA-Z0-9._@-]{1,64}$/

/** Pannello dell'amministratore: chi lavora su questo soggetto e con quale ruolo. */
export function GestioneAccessi({ soggettoId }: { soggettoId: number }) {
  const { data: assegnazioni, error } = useAssegnazioni(soggettoId, true)
  const assegna = useAssegna(soggettoId)
  const revoca = useRevoca(soggettoId)
  const [utente, setUtente] = useState('')
  const [ruolo, setRuolo] = useState<RuoloSoggetto>('OPERATORE')

  const utenteValido = FORMATO_UTENTE.test(utente)

  const aggiungi = (evento: FormEvent) => {
    evento.preventDefault()
    if (!utenteValido) {
      return
    }
    assegna.mutate({ utente, ruolo }, { onSuccess: () => setUtente('') })
  }

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        Accessi al soggetto
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Solo gli utenti elencati vedono questo soggetto. Lo username è quello di Keycloak.
      </Typography>

      {!assegnazioni ? (
        <StatoCaricamento errore={error} />
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Utente</TableCell>
                <TableCell>Ruolo</TableCell>
                <TableCell>Assegnato da</TableCell>
                <TableCell>Data</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {assegnazioni.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5}>Nessun utente assegnato.</TableCell>
                </TableRow>
              )}
              {assegnazioni.map((a) => (
                <TableRow key={a.utente}>
                  <TableCell>{a.utente}</TableCell>
                  <TableCell>{etichetteRuolo[a.ruolo]}</TableCell>
                  <TableCell>{a.assegnatoDa}</TableCell>
                  <TableCell>{formatoData.format(new Date(a.dataAssegnazione))}</TableCell>
                  <TableCell align="right">
                    <Button
                      size="small"
                      color="error"
                      disabled={revoca.isPending}
                      onClick={() => revoca.mutate(a.utente)}
                    >
                      Revoca
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Box component="form" onSubmit={aggiungi} sx={{ display: 'flex', gap: 2, mt: 3, flexWrap: 'wrap' }}>
        <TextField
          label="Username"
          size="small"
          value={utente}
          onChange={(e) => setUtente(e.target.value.trim())}
          error={utente !== '' && !utenteValido}
          helperText={utente !== '' && !utenteValido ? 'Solo lettere, cifre e . _ @ -' : ' '}
        />
        <TextField
          select
          label="Ruolo"
          size="small"
          value={ruolo}
          onChange={(e) => setRuolo(e.target.value as RuoloSoggetto)}
          sx={{ minWidth: 220 }}
        >
          {(Object.keys(etichetteRuolo) as RuoloSoggetto[]).map((r) => (
            <MenuItem key={r} value={r}>
              {etichetteRuolo[r]}
            </MenuItem>
          ))}
        </TextField>
        <Box>
          <Button type="submit" variant="outlined" disabled={!utenteValido || assegna.isPending}>
            Assegna
          </Button>
        </Box>
      </Box>
      {(assegna.isError || revoca.isError) && (
        <Alert severity="error" sx={{ mt: 2 }}>
          {(assegna.error ?? revoca.error)?.message}
        </Alert>
      )}
    </Paper>
  )
}

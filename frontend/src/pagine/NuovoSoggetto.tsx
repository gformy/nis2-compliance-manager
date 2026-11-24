import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from '@mui/material'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { ErroreApi } from '../api/client'
import { useOrganizzazioni } from '../api/organizzazioni'
import { useCreaSoggetto, type TipoSoggetto } from '../api/soggetti'

/**
 * Nuovo soggetto NIS. Il gestore sceglie l'organizzazione; un amministratore di
 * organizzazione crea sempre nella propria, entro la quota del piano.
 */
export function NuovoSoggetto({ gestore, onChiudi }: { gestore: boolean; onChiudi: () => void }) {
  const crea = useCreaSoggetto()
  const organizzazioni = useOrganizzazioni()
  const naviga = useNavigate()
  const [codiceFiscale, setCodiceFiscale] = useState('')
  const [denominazione, setDenominazione] = useState('')
  const [indirizzo, setIndirizzo] = useState('')
  const [pec, setPec] = useState('')
  const [tipo, setTipo] = useState<TipoSoggetto>('IMPORTANTE')
  const [organizzazioneId, setOrganizzazioneId] = useState<number | ''>('')
  const cfValido = /^([0-9]{11}|[A-Z0-9]{16})$/.test(codiceFiscale)
  const errori = crea.error instanceof ErroreApi ? crea.error.problema?.errori : undefined

  return (
    <Dialog open onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>Nuovo soggetto NIS</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {gestore && (
            <TextField
              select
              label="Organizzazione"
              value={organizzazioneId}
              onChange={(e) => setOrganizzazioneId(Number(e.target.value))}
            >
              {(organizzazioni.data ?? []).map((o) => (
                <MenuItem key={o.id} value={o.id}>
                  {o.nome}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label="Partita IVA o codice fiscale"
            value={codiceFiscale}
            onChange={(e) => setCodiceFiscale(e.target.value.toUpperCase().trim())}
            error={codiceFiscale !== '' && !cfValido}
          />
          <TextField label="Denominazione" value={denominazione} onChange={(e) => setDenominazione(e.target.value)} />
          <TextField label="Sede legale" value={indirizzo} onChange={(e) => setIndirizzo(e.target.value)} />
          <TextField
            label="PEC"
            value={pec}
            onChange={(e) => setPec(e.target.value.trim())}
            error={Boolean(errori?.['dati.pec'])}
            helperText={errori?.['dati.pec']}
          />
          <TextField
            select
            label="Tipo di soggetto"
            value={tipo}
            onChange={(e) => setTipo(e.target.value as TipoSoggetto)}
          >
            <MenuItem value="ESSENZIALE">Essenziale</MenuItem>
            <MenuItem value="IMPORTANTE">Importante</MenuItem>
          </TextField>
          {crea.error && <Alert severity="error">{crea.error.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button
          variant="contained"
          disabled={
            !cfValido ||
            !denominazione.trim() ||
            !indirizzo.trim() ||
            !pec ||
            (gestore && organizzazioneId === '') ||
            crea.isPending
          }
          onClick={() =>
            crea.mutate(
              {
                codiceFiscale,
                organizzazioneId: organizzazioneId === '' ? null : organizzazioneId,
                dati: {
                  denominazione: denominazione.trim(),
                  indirizzoSedeLegale: indirizzo.trim(),
                  pec,
                  emailFunzionale: null,
                  telefono: null,
                  fatturato: null,
                  bilancio: null,
                  numeroDipendenti: null,
                  tipoSoggetto: tipo,
                },
              },
              { onSuccess: (s) => naviga(`/soggetti/${s.id}`) },
            )
          }
        >
          Crea
        </Button>
      </DialogActions>
    </Dialog>
  )
}

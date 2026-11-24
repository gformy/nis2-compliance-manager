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
import {
  etichetteTipoOrganizzazione,
  euro,
  useAzioniOrganizzazione,
  usePiani,
  type Organizzazione,
  type StatoOrganizzazione,
  type TipoOrganizzazione,
} from '../../api/organizzazioni'

/** Creazione o modifica di un'organizzazione: solo il gestore della piattaforma. */
export function FormOrganizzazione({
  organizzazione,
  onChiudi,
  onSalvata,
}: {
  organizzazione?: Organizzazione
  onChiudi: () => void
  onSalvata?: (o: Organizzazione) => void
}) {
  const piani = usePiani()
  const { salva } = useAzioniOrganizzazione()
  const [nome, setNome] = useState(organizzazione?.nome ?? '')
  const [codiceFiscale, setCodiceFiscale] = useState(organizzazione?.codiceFiscale ?? '')
  const [tipo, setTipo] = useState<TipoOrganizzazione>(organizzazione?.tipo ?? 'RIVENDITORE')
  const [piano, setPiano] = useState(organizzazione?.piano.codice ?? 'PROFESSIONAL')
  const [stato, setStato] = useState<StatoOrganizzazione>(organizzazione?.stato ?? 'ATTIVA')
  const [contratto, setContratto] = useState(organizzazione?.riferimentoContratto ?? '')
  const [dataContratto, setDataContratto] = useState(organizzazione?.dataContratto ?? '')
  const cfValido = /^([0-9]{11}|[A-Z0-9]{16})$/.test(codiceFiscale)

  return (
    <Dialog open onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>{organizzazione ? 'Modifica organizzazione' : 'Nuova organizzazione'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField label="Ragione sociale" value={nome} onChange={(e) => setNome(e.target.value)} />
          <TextField
            label="Partita IVA o codice fiscale"
            value={codiceFiscale}
            onChange={(e) => setCodiceFiscale(e.target.value.toUpperCase().trim())}
            error={codiceFiscale !== '' && !cfValido}
          />
          <TextField select label="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoOrganizzazione)}>
            {(Object.keys(etichetteTipoOrganizzazione) as TipoOrganizzazione[]).map((t) => (
              <MenuItem key={t} value={t}>
                {etichetteTipoOrganizzazione[t]}
              </MenuItem>
            ))}
          </TextField>
          <TextField select label="Piano" value={piano} onChange={(e) => setPiano(e.target.value)}>
            {(piani.data ?? []).map((p) => (
              <MenuItem key={p.codice} value={p.codice}>
                {p.nome} · {euro.format(p.prezzoMensileSoggetto)} per soggetto/mese
              </MenuItem>
            ))}
          </TextField>
          {organizzazione && (
            <TextField
              select
              label="Stato"
              value={stato}
              onChange={(e) => setStato(e.target.value as StatoOrganizzazione)}
              helperText="Un'organizzazione sospesa può consultare i dati ma non modificarli"
            >
              <MenuItem value="ATTIVA">Attiva</MenuItem>
              <MenuItem value="SOSPESA">Sospesa</MenuItem>
            </TextField>
          )}
          <Stack direction="row" spacing={2}>
            <TextField
              label="Riferimento contratto"
              value={contratto}
              onChange={(e) => setContratto(e.target.value)}
              fullWidth
            />
            <TextField
              type="date"
              label="Data contratto"
              value={dataContratto}
              onChange={(e) => setDataContratto(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
              fullWidth
            />
          </Stack>
          {salva.error && <Alert severity="error">{salva.error.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button
          variant="contained"
          disabled={!nome.trim() || !cfValido || salva.isPending}
          onClick={() =>
            salva.mutate(
              {
                id: organizzazione?.id,
                dati: {
                  nome: nome.trim(),
                  codiceFiscale,
                  tipo,
                  piano,
                  stato,
                  riferimentoContratto: contratto.trim() || null,
                  dataContratto: dataContratto || null,
                },
              },
              {
                onSuccess: (o) => {
                  onSalvata?.(o)
                  onChiudi()
                },
              },
            )
          }
        >
          Salva
        </Button>
      </DialogActions>
    </Dialog>
  )
}

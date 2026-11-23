import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import {
  etichetteCriterio,
  useAggiornaDipendenza,
  useCercaFornitori,
  useCreaDipendenza,
  type CriterioRilevanza,
  type Dipendenza,
  type Fornitore,
  type StatoDipendenza,
} from '../../api/fornitori'
import { useElenco, type Asset, type Servizio } from '../../api/inventario'

const testoONull = (valore: string) => (valore.trim() === '' ? null : valore.trim())

/**
 * Creazione di una dipendenza (dipendenza assente) o modifica di una esistente.
 * In modifica fornitore ed elemento non cambiano: si cambia criterio, dettagli e stato.
 */
export function DialogoDipendenza({
  soggettoId,
  dipendenza,
  aperto,
  onChiudi,
}: {
  soggettoId: number
  dipendenza?: Dipendenza
  aperto: boolean
  onChiudi: () => void
}) {
  const modifica = dipendenza !== undefined
  const [ricerca, setRicerca] = useState('')
  const [fornitore, setFornitore] = useState<Fornitore | null>(null)
  const [elemento, setElemento] = useState('')
  const [criterio, setCriterio] = useState<CriterioRilevanza>(dipendenza?.criterioRilevanza ?? 'ICT')
  const [tipologia, setTipologia] = useState(dipendenza?.tipologiaFornitura ?? '')
  const [referente, setReferente] = useState(dipendenza?.referenteContatto ?? '')
  const [stato, setStato] = useState<StatoDipendenza>(dipendenza?.stato ?? 'ATTIVA')

  const fornitori = useCercaFornitori(ricerca)
  const asset = useElenco<Asset>(soggettoId, 'asset')
  const servizi = useElenco<Servizio>(soggettoId, 'servizi')
  const crea = useCreaDipendenza(soggettoId)
  const aggiorna = useAggiornaDipendenza(soggettoId)
  const errore = crea.error ?? aggiorna.error

  const elementi = [
    ...(asset.data ?? []).filter((a) => a.stato === 'ATTIVO').map((a) => ({ id: a.id, nome: a.nome, tipo: 'Asset' })),
    ...(servizi.data ?? []).filter((s) => s.stato === 'ATTIVO').map((s) => ({ id: s.id, nome: s.nome, tipo: 'Servizio' })),
  ]

  const salva = () => {
    const dettagli = { tipologiaFornitura: testoONull(tipologia), referenteContatto: testoONull(referente) }
    if (modifica) {
      aggiorna.mutate(
        { id: dipendenza.id, dati: { criterioRilevanza: criterio, stato, ...dettagli } },
        { onSuccess: onChiudi },
      )
    } else if (fornitore && elemento) {
      crea.mutate(
        { fornitoreId: fornitore.id, elementoCriticoId: Number(elemento), criterioRilevanza: criterio, ...dettagli },
        { onSuccess: onChiudi },
      )
    }
  }

  return (
    <Dialog open={aperto} onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>{modifica ? 'Modifica dipendenza' : 'Nuova dipendenza'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {modifica ? (
            <Typography>
              <strong>{dipendenza.fornitoreDenominazione}</strong> per <strong>{dipendenza.elementoNome}</strong>
            </Typography>
          ) : (
            <>
              <Autocomplete
                options={fornitori.data ?? []}
                value={fornitore}
                onChange={(_, valore) => setFornitore(valore)}
                onInputChange={(_, valore) => setRicerca(valore)}
                getOptionLabel={(f) => `${f.denominazione} (${f.codiceFiscale})`}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                filterOptions={(opzioni) => opzioni}
                loading={fornitori.isFetching}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Fornitore"
                    helperText="Se il fornitore non c'è, chiedi all'amministratore di aggiungerlo al catalogo"
                  />
                )}
              />
              <TextField select label="Asset o servizio" value={elemento} onChange={(e) => setElemento(e.target.value)}>
                {elementi.map((e) => (
                  <MenuItem key={e.id} value={String(e.id)}>
                    {e.tipo}: {e.nome}
                  </MenuItem>
                ))}
              </TextField>
            </>
          )}
          <RadioGroup value={criterio} onChange={(_, valore) => setCriterio(valore as CriterioRilevanza)}>
            {(Object.keys(etichetteCriterio) as CriterioRilevanza[]).map((c) => (
              <FormControlLabel key={c} value={c} control={<Radio />} label={etichetteCriterio[c]} />
            ))}
          </RadioGroup>
          <TextField
            label="Tipologia di fornitura"
            value={tipologia}
            onChange={(e) => setTipologia(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 255 } }}
          />
          <TextField
            label="Referente presso il fornitore"
            value={referente}
            onChange={(e) => setReferente(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 255 } }}
          />
          {modifica && (
            <TextField select label="Stato" value={stato} onChange={(e) => setStato(e.target.value as StatoDipendenza)}>
              <MenuItem value="ATTIVA">Attiva</MenuItem>
              <MenuItem value="CESSATA">Cessata</MenuItem>
            </TextField>
          )}
          {errore && <Alert severity="error">{errore.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button
          variant="contained"
          onClick={salva}
          disabled={crea.isPending || aggiorna.isPending || (!modifica && (!fornitore || !elemento))}
        >
          Salva
        </Button>
      </DialogActions>
    </Dialog>
  )
}

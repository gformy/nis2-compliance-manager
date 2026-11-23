import {
  Alert,
  Autocomplete,
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
  etichetteGravita,
  useCatalogoIncidenti,
  type DatiIncidente,
  type Gravita,
  type Incidente,
} from '../../api/incidenti'
import { useElenco, type Asset, type Servizio } from '../../api/inventario'
import { daInputLocale, perInputLocale } from './tempo'
import { useAdesso } from './useAdesso'

const testoONull = (valore: string) => (valore.trim() === '' ? null : valore.trim())

interface Elemento {
  id: number
  nome: string
  tipo: string
}

/** Registrazione di un nuovo incidente o modifica dei suoi dati di base. */
export function DialogoIncidente({
  soggettoId,
  incidente,
  aperto,
  inCorso,
  errore,
  onSalva,
  onChiudi,
}: {
  soggettoId: number
  incidente?: Incidente
  aperto: boolean
  inCorso: boolean
  errore: Error | null
  onSalva: (dati: DatiIncidente) => void
  onChiudi: () => void
}) {
  const catalogo = useCatalogoIncidenti(soggettoId)
  const adesso = useAdesso(10_000)
  const asset = useElenco<Asset>(soggettoId, 'asset')
  const servizi = useElenco<Servizio>(soggettoId, 'servizi')

  const [titolo, setTitolo] = useState(incidente?.titolo ?? '')
  const [descrizione, setDescrizione] = useState(incidente?.descrizione ?? '')
  const [tipo, setTipo] = useState(incidente?.tipo.codice ?? '')
  const [categoria, setCategoria] = useState(incidente?.categoria ?? '')
  const [gravita, setGravita] = useState<Gravita | ''>(incidente?.gravita ?? '')
  const [evidenza, setEvidenza] = useState(() => perInputLocale(incidente?.dataEvidenza ?? new Date()))
  const [rootCause, setRootCause] = useState(incidente?.rootCause ?? '')
  const [elementi, setElementi] = useState<Elemento[]>(
    incidente?.elementi.map((e) => ({
      id: e.id,
      nome: e.nome,
      tipo: e.tipo === 'ASSET' ? 'Asset' : 'Servizio',
    })) ?? [],
  )

  const opzioni: Elemento[] = [
    ...(asset.data ?? []).map((a) => ({
      id: a.id,
      nome: a.nome,
      tipo: 'Asset',
    })),
    ...(servizi.data ?? []).map((s) => ({
      id: s.id,
      nome: s.nome,
      tipo: 'Servizio',
    })),
  ]
  const futura = evidenza !== '' && new Date(evidenza).getTime() > adesso
  const valido = titolo.trim() !== '' && tipo !== '' && evidenza !== '' && !futura
  const conNotifiche = (incidente?.notifiche.length ?? 0) > 0

  const salva = () =>
    onSalva({
      titolo: titolo.trim(),
      descrizione: testoONull(descrizione),
      tipoIncidente: tipo,
      categoria: categoria || null,
      gravita: gravita || null,
      dataEvidenza: daInputLocale(evidenza),
      rootCause: testoONull(rootCause),
      elementiCritici: elementi.map((e) => e.id),
    })

  return (
    <Dialog open={aperto} onClose={onChiudi} fullWidth maxWidth="md">
      <DialogTitle>{incidente ? 'Modifica incidente' : 'Nuovo incidente'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <TextField
            label="Titolo"
            value={titolo}
            onChange={(e) => setTitolo(e.target.value)}
            required
            slotProps={{ htmlInput: { maxLength: 200 } }}
          />
          <TextField
            label="Descrizione"
            value={descrizione}
            onChange={(e) => setDescrizione(e.target.value)}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          <TextField
            select
            label="Tipo di incidente (ACN)"
            value={tipo}
            onChange={(e) => setTipo(e.target.value)}
            required
          >
            {(catalogo.data?.tipi ?? []).map((t) => (
              <MenuItem key={t.codice} value={t.codice} sx={{ whiteSpace: 'normal' }}>
                {t.codice} — {t.descrizione}
              </MenuItem>
            ))}
          </TextField>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
            <TextField
              select
              label="Categoria"
              value={categoria}
              onChange={(e) => setCategoria(e.target.value)}
              fullWidth
            >
              <MenuItem value="">Non ancora nota</MenuItem>
              {(catalogo.data?.categorie ?? []).map((c) => (
                <MenuItem key={c} value={c}>
                  {c.replaceAll('_', ' ').toLowerCase()}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              label="Gravità"
              value={gravita}
              onChange={(e) => setGravita(e.target.value as Gravita | '')}
              fullWidth
            >
              <MenuItem value="">Da stabilire</MenuItem>
              {(Object.keys(etichetteGravita) as Gravita[]).map((g) => (
                <MenuItem key={g} value={g}>
                  {etichetteGravita[g]}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              type="datetime-local"
              label="Data di evidenza"
              value={evidenza}
              onChange={(e) => setEvidenza(e.target.value)}
              disabled={conNotifiche}
              error={futura}
              helperText={
                futura
                  ? 'Non può essere nel futuro'
                  : conNotifiche
                    ? 'Bloccata: c’è già una trasmissione al CSIRT'
                    : 'Da qui decorrono 24 h e 72 h'
              }
              fullWidth
              slotProps={{ inputLabel: { shrink: true } }}
            />
          </Stack>
          <Autocomplete
            multiple
            options={opzioni}
            value={elementi}
            onChange={(_, valore) => setElementi(valore)}
            getOptionLabel={(e) => `${e.tipo}: ${e.nome}`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => <TextField {...params} label="Asset e servizi coinvolti" />}
          />
          <TextField
            label="Causa originaria"
            value={rootCause}
            onChange={(e) => setRootCause(e.target.value)}
            helperText="Spesso nota solo a posteriori"
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          {errore && <Alert severity="error">{errore.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button variant="contained" onClick={salva} disabled={!valido || inCorso}>
          Salva
        </Button>
      </DialogActions>
    </Dialog>
  )
}

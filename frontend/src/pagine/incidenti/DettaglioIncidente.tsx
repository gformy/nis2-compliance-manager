import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  Link,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useState, type ReactNode } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import {
  etichetteFase,
  etichetteGravita,
  etichetteNotifica,
  tipiIoc,
  useAzioneIncidente,
  useIncidente,
  type FaseRisposta,
  type FaseTimeline,
  type Incidente,
  type TipoIoc,
  type TipoNotifica,
} from '../../api/incidenti'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { data } from '../inventario/date'
import { ChipScadenza } from './ChipScadenza'
import { DialogoIncidente } from './DialogoIncidente'
import { daInputLocale, perInputLocale } from './tempo'
import { useAdesso } from './useAdesso'

type Azione = ReturnType<typeof useAzioneIncidente>

export function DettaglioIncidente() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const id = Number(useParams().incidenteId)
  const { data: incidente, error } = useIncidente(soggetto.id, id)
  const azione = useAzioneIncidente(soggetto.id, id)
  const adesso = useAdesso()
  const [modifica, setModifica] = useState(false)
  const [chiusura, setChiusura] = useState(false)
  const [trasmissione, setTrasmissione] = useState(false)

  if (!incidente) return <StatoCaricamento errore={error} />
  const aperto = incidente.stato === 'APERTO'
  const puoGestire = modificabile && aperto

  return (
    <Stack spacing={3}>
      <Box>
        <Link component={RouterLink} to=".." relative="path">
          ← Tutti gli incidenti
        </Link>
      </Box>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box
          sx={{
            display: 'flex',
            gap: 2,
            alignItems: 'flex-start',
            flexWrap: 'wrap',
          }}
        >
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="h5" component="h2">
              {incidente.titolo}
            </Typography>
            <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1, flexWrap: 'wrap' }}>
              <Chip size="small" label={incidente.tipo.codice} title={incidente.tipo.descrizione} />
              {incidente.categoria && <Chip size="small" variant="outlined" label={incidente.categoria} />}
              {incidente.gravita && (
                <Chip
                  size="small"
                  color={incidente.gravita === 'CRITICA' || incidente.gravita === 'ALTA' ? 'error' : 'default'}
                  label={`Gravità ${etichetteGravita[incidente.gravita].toLowerCase()}`}
                />
              )}
              <Chip
                size="small"
                color={aperto ? 'warning' : 'success'}
                label={aperto ? 'Aperto' : `Chiuso il ${data(incidente.dataChiusura)}`}
              />
            </Stack>
          </Box>
          {puoGestire && (
            <Stack direction="row" spacing={1}>
              <Button onClick={() => setModifica(true)}>Modifica</Button>
              <Button color="warning" onClick={() => setChiusura(true)}>
                Chiudi incidente
              </Button>
            </Stack>
          )}
        </Box>
        {incidente.descrizione && <Typography sx={{ mt: 2 }}>{incidente.descrizione}</Typography>}
        <Grid container spacing={2} sx={{ mt: 1 }}>
          <Campo etichetta="Data di evidenza">{data(incidente.dataEvidenza)}</Campo>
          <Campo etichetta="Registrato">
            {data(incidente.dataRegistrazione)} da {incidente.registratoDa}
          </Campo>
          <Campo etichetta="Causa originaria">{incidente.rootCause ?? 'Non ancora nota'}</Campo>
          <Campo etichetta="Asset e servizi coinvolti">
            {incidente.elementi.length === 0
              ? 'Nessuno indicato'
              : incidente.elementi.map((e) => (
                  <Link
                    key={e.id}
                    component={RouterLink}
                    to={`../../${e.tipo === 'ASSET' ? 'asset' : 'servizi'}/${e.id}`}
                    relative="path"
                    sx={{ display: 'block' }}
                  >
                    {e.nome}
                  </Link>
                ))}
          </Campo>
        </Grid>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h3" sx={{ flexGrow: 1 }}>
            Trasmissioni al CSIRT Italia
          </Typography>
          {modificabile && (
            <Button variant="contained" onClick={() => setTrasmissione(true)}>
              Registra trasmissione
            </Button>
          )}
        </Box>
        <Grid container spacing={2} sx={{ mb: 2 }}>
          {incidente.scadenze.map((s) => (
            <Grid key={s.tipo} size={{ xs: 12, md: 4 }}>
              <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
                <Typography sx={{ fontWeight: 600 }}>{etichetteNotifica[s.tipo]}</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  {s.scadenza ? `Entro il ${data(s.scadenza)}` : 'Decorre dalla notifica'}
                </Typography>
                <ChipScadenza scadenza={s} adesso={adesso} />
                {s.trasmessaIl && (
                  <Typography variant="body2" sx={{ mt: 1 }}>
                    Trasmessa il {data(s.trasmessaIl)}
                  </Typography>
                )}
              </Paper>
            </Grid>
          ))}
        </Grid>
        {incidente.notifiche.length > 0 && (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Tipo</TableCell>
                <TableCell>Trasmessa il</TableCell>
                <TableCell>Contenuto</TableCell>
                <TableCell>Registrata da</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {incidente.notifiche.map((n) => (
                <TableRow key={n.id}>
                  <TableCell>{etichetteNotifica[n.tipo]}</TableCell>
                  <TableCell>{data(n.dataTrasmissione)}</TableCell>
                  <TableCell sx={{ whiteSpace: 'pre-wrap' }}>{n.contenuto ?? '—'}</TableCell>
                  <TableCell>{n.registrataDa}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <SezioneIoc incidente={incidente} azione={azione} modificabile={puoGestire} />
        </Grid>
        <Grid size={{ xs: 12, md: 6 }}>
          <SezioneCronologia incidente={incidente} azione={azione} modificabile={puoGestire} />
        </Grid>
      </Grid>
      <SezioneAttivita incidente={incidente} azione={azione} modificabile={puoGestire} />

      {modifica && (
        <DialogoIncidente
          soggettoId={soggetto.id}
          incidente={incidente}
          aperto
          inCorso={azione.isPending}
          errore={azione.error}
          onChiudi={() => {
            azione.reset()
            setModifica(false)
          }}
          onSalva={(dati) => azione.mutate({ metodo: 'PUT', corpo: dati }, { onSuccess: () => setModifica(false) })}
        />
      )}
      {trasmissione && (
        <DialogoTrasmissione
          incidente={incidente}
          azione={azione}
          onChiudi={() => {
            azione.reset()
            setTrasmissione(false)
          }}
        />
      )}
      <Dialog open={chiusura} onClose={() => setChiusura(false)}>
        <DialogTitle>Chiudere l’incidente?</DialogTitle>
        <DialogContent>
          <Typography>
            Dopo la chiusura si potranno ancora registrare notifiche e relazioni al CSIRT, ma non modificare dati,
            indicatori, cronologia e attività.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setChiusura(false)}>Annulla</Button>
          <Button
            color="warning"
            variant="contained"
            onClick={() => azione.mutate({ percorso: '/chiusura' }, { onSuccess: () => setChiusura(false) })}
          >
            Chiudi incidente
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  )
}

function Campo({ etichetta, children }: { etichetta: string; children: ReactNode }) {
  return (
    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
      <Typography variant="caption" color="text.secondary">
        {etichetta}
      </Typography>
      <Box>{children}</Box>
    </Grid>
  )
}

function DialogoTrasmissione({
  incidente,
  azione,
  onChiudi,
}: {
  incidente: Incidente
  azione: Azione
  onChiudi: () => void
}) {
  const giaInviate = new Set(incidente.notifiche.map((n) => n.tipo))
  const haNotifica = giaInviate.has('NOTIFICA')
  const disponibili = (Object.keys(etichetteNotifica) as TipoNotifica[]).filter((t) => {
    if ((t === 'PRE_NOTIFICA' || t === 'NOTIFICA' || t === 'RELAZIONE_FINALE') && giaInviate.has(t)) return false
    return t === 'PRE_NOTIFICA' || t === 'NOTIFICA' || haNotifica
  })
  const [tipo, setTipo] = useState<TipoNotifica | ''>(disponibili[0] ?? '')
  const [quando, setQuando] = useState(() => perInputLocale(new Date()))
  const [contenuto, setContenuto] = useState('')

  return (
    <Dialog open onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>Registra una trasmissione al CSIRT</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          <Typography variant="body2" color="text.secondary">
            Si registra un invio già fatto sul portale ACN, con la data effettiva di trasmissione.
          </Typography>
          <TextField select label="Tipo" value={tipo} onChange={(e) => setTipo(e.target.value as TipoNotifica)}>
            {disponibili.map((t) => (
              <MenuItem key={t} value={t}>
                {etichetteNotifica[t]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            type="datetime-local"
            label="Trasmessa il"
            value={quando}
            onChange={(e) => setQuando(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            label="Sintesi del contenuto"
            value={contenuto}
            onChange={(e) => setContenuto(e.target.value)}
            multiline
            minRows={3}
          />
          {azione.error && <Alert severity="error">{azione.error.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button
          variant="contained"
          disabled={!tipo || !quando || azione.isPending}
          onClick={() =>
            azione.mutate(
              {
                percorso: '/notifiche',
                corpo: {
                  tipo,
                  dataTrasmissione: daInputLocale(quando),
                  contenuto: contenuto.trim() || null,
                },
              },
              { onSuccess: onChiudi },
            )
          }
        >
          Registra
        </Button>
      </DialogActions>
    </Dialog>
  )
}

interface PropsSezione {
  incidente: Incidente
  azione: Azione
  modificabile: boolean
}

function SezioneIoc({ incidente, azione, modificabile }: PropsSezione) {
  const [tipo, setTipo] = useState<TipoIoc>('IP')
  const [valore, setValore] = useState('')
  const [errore, setErrore] = useState<string | null>(null)

  const aggiungi = () =>
    azione.mutate(
      { percorso: '/ioc', corpo: { tipo, valore: valore.trim() } },
      {
        onSuccess: () => {
          setValore('')
          setErrore(null)
        },
        onError: (e) => setErrore(e.message),
      },
    )

  return (
    <Paper variant="outlined" sx={{ p: 3, height: '100%' }}>
      <Typography variant="h6" component="h3" gutterBottom>
        Indicatori di compromissione
      </Typography>
      {incidente.ioc.length === 0 ? (
        <Typography color="text.secondary">Nessun indicatore.</Typography>
      ) : (
        <Table size="small">
          <TableBody>
            {incidente.ioc.map((i) => (
              <TableRow key={i.id}>
                <TableCell>{i.tipo}</TableCell>
                <TableCell sx={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{i.valore}</TableCell>
                {modificabile && (
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      aria-label={`Rimuovi ${i.valore}`}
                      onClick={() =>
                        azione.mutate({
                          percorso: `/ioc/${i.id}`,
                          metodo: 'DELETE',
                        })
                      }
                    >
                      ✕
                    </IconButton>
                  </TableCell>
                )}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {modificabile && (
        <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
          <TextField select size="small" value={tipo} onChange={(e) => setTipo(e.target.value as TipoIoc)}>
            {tipiIoc.map((t) => (
              <MenuItem key={t} value={t}>
                {t}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            label="Valore"
            value={valore}
            onChange={(e) => setValore(e.target.value)}
            error={errore !== null}
            helperText={errore}
            fullWidth
          />
          <Button onClick={aggiungi} disabled={!valore.trim() || azione.isPending}>
            Aggiungi
          </Button>
        </Stack>
      )}
    </Paper>
  )
}

function SezioneCronologia({ incidente, azione, modificabile }: PropsSezione) {
  const [quando, setQuando] = useState(() => perInputLocale(new Date()))
  const [fase, setFase] = useState<FaseTimeline>('INVESTIGAZIONE')
  const [descrizione, setDescrizione] = useState('')

  return (
    <Paper variant="outlined" sx={{ p: 3, height: '100%' }}>
      <Typography variant="h6" component="h3" gutterBottom>
        Cronologia
      </Typography>
      {incidente.timeline.length === 0 ? (
        <Typography color="text.secondary">Nessun evento.</Typography>
      ) : (
        <Stack spacing={1}>
          {incidente.timeline.map((e) => (
            <Box key={e.id} sx={{ borderLeft: 3, borderColor: 'primary.main', pl: 2 }}>
              <Typography variant="body2" color="text.secondary">
                {data(e.dataOra)} · {etichetteFase[e.fase]}
              </Typography>
              <Typography>{e.descrizione}</Typography>
            </Box>
          ))}
        </Stack>
      )}
      {modificabile && (
        <Stack spacing={1} sx={{ mt: 2 }}>
          <Stack direction="row" spacing={1}>
            <TextField
              type="datetime-local"
              size="small"
              value={quando}
              onChange={(e) => setQuando(e.target.value)}
              slotProps={{ htmlInput: { 'aria-label': 'Data e ora' } }}
            />
            <TextField select size="small" value={fase} onChange={(e) => setFase(e.target.value as FaseTimeline)}>
              {(Object.keys(etichetteFase) as FaseTimeline[]).map((f) => (
                <MenuItem key={f} value={f}>
                  {etichetteFase[f]}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label="Cosa è successo"
              value={descrizione}
              onChange={(e) => setDescrizione(e.target.value)}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
            <Button
              disabled={!descrizione.trim() || !quando || azione.isPending}
              onClick={() =>
                azione.mutate(
                  {
                    percorso: '/timeline',
                    corpo: {
                      dataOra: daInputLocale(quando),
                      fase,
                      descrizione: descrizione.trim(),
                    },
                  },
                  { onSuccess: () => setDescrizione('') },
                )
              }
            >
              Aggiungi
            </Button>
          </Stack>
        </Stack>
      )}
    </Paper>
  )
}

function SezioneAttivita({ incidente, azione, modificabile }: PropsSezione) {
  const [fase, setFase] = useState<FaseRisposta>('CONTENIMENTO')
  const [descrizione, setDescrizione] = useState('')
  const [motivazione, setMotivazione] = useState('')
  const [esito, setEsito] = useState('')

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h3" gutterBottom>
        Attività di risposta
      </Typography>
      {incidente.attivita.length === 0 ? (
        <Typography color="text.secondary">Nessuna attività registrata.</Typography>
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Quando</TableCell>
              <TableCell>Fase</TableCell>
              <TableCell>Attività</TableCell>
              <TableCell>Motivazione</TableCell>
              <TableCell>Esito</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {incidente.attivita.map((a) => (
              <TableRow key={a.id}>
                <TableCell>{data(a.dataEsecuzione)}</TableCell>
                <TableCell>{etichetteFase[a.fase]}</TableCell>
                <TableCell>{a.descrizione}</TableCell>
                <TableCell>{a.motivazione ?? '—'}</TableCell>
                <TableCell>{a.esito ?? '—'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {modificabile && (
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mt: 2 }}>
          <TextField select size="small" value={fase} onChange={(e) => setFase(e.target.value as FaseRisposta)}>
            <MenuItem value="CONTENIMENTO">Contenimento</MenuItem>
            <MenuItem value="ERADICAZIONE">Eradicazione</MenuItem>
          </TextField>
          <TextField
            size="small"
            label="Attività"
            value={descrizione}
            onChange={(e) => setDescrizione(e.target.value)}
            fullWidth
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          <TextField
            size="small"
            label="Motivazione"
            value={motivazione}
            onChange={(e) => setMotivazione(e.target.value)}
            fullWidth
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
          <TextField
            size="small"
            label="Esito"
            value={esito}
            onChange={(e) => setEsito(e.target.value)}
            slotProps={{ htmlInput: { maxLength: 255 } }}
          />
          <Button
            disabled={!descrizione.trim() || azione.isPending}
            onClick={() =>
              azione.mutate(
                {
                  percorso: '/attivita',
                  corpo: {
                    fase,
                    descrizione: descrizione.trim(),
                    motivazione: motivazione.trim() || null,
                    esito: esito.trim() || null,
                  },
                },
                {
                  onSuccess: () => {
                    setDescrizione('')
                    setMotivazione('')
                    setEsito('')
                  },
                },
              )
            }
          >
            Aggiungi
          </Button>
        </Stack>
      )}
    </Paper>
  )
}

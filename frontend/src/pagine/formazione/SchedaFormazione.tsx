import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Grid,
  LinearProgress,
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
import { useState } from 'react'
import {
  etichetteCategoria,
  etichetteDestinatari,
  etichetteEsito,
  percorsoRegistroCsv,
  useAzioniFormazione,
  useFormazione,
  type Attivita,
  type Categoria,
  type Destinatari,
  type Esito,
  type Persona,
} from '../../api/formazione'
import { useScarica } from '../../api/scarica'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { oggiIso } from '../governance/governance'
import { data } from '../inventario/date'
import { colorePercentuale, esitiAmmessi, personeAmmesse } from './formazione'

const etichetteStatoAttivita = { PIANIFICATA: 'Pianificata', SVOLTA: 'Svolta', DA_REGISTRARE: 'Da registrare' }
const coloriStatoAttivita = { PIANIFICATA: 'info', SVOLTA: 'success', DA_REGISTRARE: 'warning' } as const

export function SchedaFormazione() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const { data: formazione, error } = useFormazione(soggetto.id)
  const scarica = useScarica()
  const [nuovaPersona, setNuovaPersona] = useState(false)
  const [modificaAttivita, setModificaAttivita] = useState<Attivita | 'nuova' | null>(null)
  const [registra, setRegistra] = useState<Attivita | null>(null)
  const [cessa, setCessa] = useState<Persona | null>(null)
  const [erroreCsv, setErroreCsv] = useState<string | null>(null)

  if (!formazione) return <StatoCaricamento errore={error} />
  const { riepilogo } = formazione

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Copertura della formazione negli ultimi 12 mesi
        </Typography>
        <Grid container spacing={3}>
          {riepilogo.copertura.map((c) => (
            <Grid key={c.categoria} size={{ xs: 12, md: 4 }}>
              <Typography sx={{ fontWeight: 600 }}>{etichetteCategoria[c.categoria]}</Typography>
              <LinearProgress
                variant="determinate"
                value={c.percentuale}
                color={colorePercentuale(c.percentuale, c.persone)}
                sx={{ height: 8, borderRadius: 4, my: 1 }}
                aria-label={`Copertura ${etichetteCategoria[c.categoria]}`}
              />
              <Typography variant="body2" color="text.secondary">
                {c.persone === 0
                  ? 'Nessuna persona'
                  : `${c.formate} su ${c.persone} · ${c.percentuale.toLocaleString('it-IT')}%`}
              </Typography>
            </Grid>
          ))}
        </Grid>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          Il piano include gli organi di amministrazione (PR.AT-01). Per i ruoli specializzati, amministratori di
          sistema compresi, conta solo la formazione dedicata (PR.AT-02). Il piano come documento si approva nella
          scheda Governance.
        </Typography>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h3" sx={{ flexGrow: 1 }}>
            Attività del piano
          </Typography>
          {modificabile && (
            <Button variant="contained" onClick={() => setModificaAttivita('nuova')}>
              Nuova attività
            </Button>
          )}
        </Box>
        {formazione.attivita.length === 0 ? (
          <Typography color="text.secondary">Nessuna attività pianificata.</Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Attività</TableCell>
                <TableCell>Destinatari</TableCell>
                <TableCell>Data</TableCell>
                <TableCell>Verifica</TableCell>
                <TableCell>Stato</TableCell>
                <TableCell align="right" />
              </TableRow>
            </TableHead>
            <TableBody>
              {formazione.attivita.map((a) => (
                <TableRow key={a.id}>
                  <TableCell>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {a.titolo}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {a.contenuti}
                    </Typography>
                  </TableCell>
                  <TableCell>{etichetteDestinatari[a.destinatari]}</TableCell>
                  <TableCell>
                    {data(a.dataPrevista)}
                    {a.durataOre && (
                      <Typography variant="body2" color="text.secondary">
                        {a.durataOre.toLocaleString('it-IT')} ore
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{a.modalitaVerifica ?? '—'}</TableCell>
                  <TableCell>
                    <Chip size="small" color={coloriStatoAttivita[a.stato]} label={etichetteStatoAttivita[a.stato]} />
                    {a.partecipanti > 0 && (
                      <Typography variant="body2" color="text.secondary">
                        {a.partecipanti} registrazioni
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {modificabile && (
                      <>
                        <Button size="small" onClick={() => setRegistra(a)}>
                          Registra
                        </Button>
                        <Button size="small" onClick={() => setModificaAttivita(a)}>
                          Modifica
                        </Button>
                      </>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h3" sx={{ flexGrow: 1 }}>
            Persone da formare
          </Typography>
          {modificabile && <Button onClick={() => setNuovaPersona(true)}>Aggiungi persona</Button>}
        </Box>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Nome</TableCell>
              <TableCell>Categoria</TableCell>
              <TableCell>Ultima formazione valida</TableCell>
              <TableCell>Stato</TableCell>
              <TableCell align="right" />
            </TableRow>
          </TableHead>
          <TableBody>
            {formazione.persone.map((p) => (
              <TableRow key={p.id} sx={p.dataCessazione ? { opacity: 0.6 } : undefined}>
                <TableCell>
                  {p.nome}
                  {p.ruolo && (
                    <Typography variant="body2" color="text.secondary">
                      {p.ruolo}
                    </Typography>
                  )}
                </TableCell>
                <TableCell>{etichetteCategoria[p.categoria]}</TableCell>
                <TableCell>{p.ultimaFormazione ? data(p.ultimaFormazione) : '—'}</TableCell>
                <TableCell>
                  {p.dataCessazione ? (
                    <Chip size="small" label={`Cessata il ${data(p.dataCessazione)}`} />
                  ) : (
                    <Chip
                      size="small"
                      color={p.inRegola ? 'success' : 'error'}
                      label={p.inRegola ? 'Formata' : 'Da formare'}
                    />
                  )}
                </TableCell>
                <TableCell align="right">
                  {modificabile && !p.dataCessazione && (
                    <Button size="small" onClick={() => setCessa(p)}>
                      Cessazione
                    </Button>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h3" sx={{ flexGrow: 1 }}>
            Registro della formazione
          </Typography>
          <Button
            onClick={() => {
              setErroreCsv(null)
              scarica(percorsoRegistroCsv(soggetto.id), `registro-formazione-${soggetto.id}.csv`).catch((e: Error) =>
                setErroreCsv(e.message),
              )
            }}
          >
            Scarica CSV
          </Button>
        </Box>
        {erroreCsv && <Alert severity="error">{erroreCsv}</Alert>}
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          Le righe non si modificano: una verifica non superata si ripete con una nuova registrazione.
        </Typography>
        {formazione.registro.length === 0 ? (
          <Typography color="text.secondary">Registro vuoto.</Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Data</TableCell>
                <TableCell>Persona</TableCell>
                <TableCell>Attività</TableCell>
                <TableCell>Esito</TableCell>
                <TableCell>Registrata da</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {formazione.registro.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{data(r.dataSvolgimento)}</TableCell>
                  <TableCell>{r.persona}</TableCell>
                  <TableCell>{r.attivita}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      color={r.esito === 'NON_SUPERATA' ? 'error' : 'default'}
                      label={etichetteEsito[r.esito]}
                    />
                  </TableCell>
                  <TableCell>{r.registrataDa}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </Paper>

      {nuovaPersona && <DialogoPersona soggettoId={soggetto.id} onChiudi={() => setNuovaPersona(false)} />}
      {modificaAttivita && (
        <DialogoAttivita
          soggettoId={soggetto.id}
          attivita={modificaAttivita === 'nuova' ? undefined : modificaAttivita}
          onChiudi={() => setModificaAttivita(null)}
        />
      )}
      {registra && (
        <DialogoRegistrazione
          soggettoId={soggetto.id}
          attivita={registra}
          persone={formazione.persone}
          onChiudi={() => setRegistra(null)}
        />
      )}
      {cessa && <DialogoCessazione soggettoId={soggetto.id} persona={cessa} onChiudi={() => setCessa(null)} />}
    </Stack>
  )
}

function Dialogo({
  titolo,
  onChiudi,
  onSalva,
  disabilitato,
  errore,
  children,
}: {
  titolo: string
  onChiudi: () => void
  onSalva: () => void
  disabilitato: boolean
  errore: Error | null
  children: React.ReactNode
}) {
  return (
    <Dialog open onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>{titolo}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {children}
          {errore && <Alert severity="error">{errore.message}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>Annulla</Button>
        <Button variant="contained" disabled={disabilitato} onClick={onSalva}>
          Salva
        </Button>
      </DialogActions>
    </Dialog>
  )
}

function DialogoPersona({ soggettoId, onChiudi }: { soggettoId: number; onChiudi: () => void }) {
  const { persona } = useAzioniFormazione(soggettoId)
  const [nome, setNome] = useState('')
  const [categoria, setCategoria] = useState<Categoria>('PERSONALE')
  const [ruolo, setRuolo] = useState('')
  return (
    <Dialogo
      titolo="Nuova persona da formare"
      onChiudi={onChiudi}
      errore={persona.error}
      disabilitato={!nome.trim() || persona.isPending}
      onSalva={() =>
        persona.mutate({ nome: nome.trim(), categoria, ruolo: ruolo.trim() || null }, { onSuccess: onChiudi })
      }
    >
      <TextField label="Nome e cognome" value={nome} onChange={(e) => setNome(e.target.value)} />
      <TextField select label="Categoria" value={categoria} onChange={(e) => setCategoria(e.target.value as Categoria)}>
        {(Object.keys(etichetteCategoria) as Categoria[]).map((c) => (
          <MenuItem key={c} value={c}>
            {etichetteCategoria[c]}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        label="Ruolo"
        value={ruolo}
        onChange={(e) => setRuolo(e.target.value)}
        helperText="Es. amministratore di sistema, consigliere"
      />
    </Dialogo>
  )
}

function DialogoAttivita({
  soggettoId,
  attivita,
  onChiudi,
}: {
  soggettoId: number
  attivita?: Attivita
  onChiudi: () => void
}) {
  const azioni = useAzioniFormazione(soggettoId)
  const [titolo, setTitolo] = useState(attivita?.titolo ?? '')
  const [contenuti, setContenuti] = useState(attivita?.contenuti ?? '')
  const [destinatari, setDestinatari] = useState<Destinatari>(attivita?.destinatari ?? 'TUTTI')
  const [dataPrevista, setDataPrevista] = useState(attivita?.dataPrevista ?? oggiIso())
  const [ore, setOre] = useState(attivita?.durataOre ? String(attivita.durataOre) : '')
  const [verifica, setVerifica] = useState(attivita?.modalitaVerifica ?? '')
  const bloccata = (attivita?.partecipanti ?? 0) > 0
  return (
    <Dialogo
      titolo={attivita ? 'Modifica attività' : 'Nuova attività del piano'}
      onChiudi={onChiudi}
      errore={azioni.attivita.error}
      disabilitato={!titolo.trim() || !contenuti.trim() || !dataPrevista || azioni.attivita.isPending}
      onSalva={() =>
        azioni.attivita.mutate(
          {
            id: attivita?.id,
            dati: {
              titolo: titolo.trim(),
              contenuti: contenuti.trim(),
              destinatari,
              dataPrevista,
              durataOre: ore ? Number(ore.replace(',', '.')) : null,
              modalitaVerifica: verifica.trim() || null,
            },
          },
          { onSuccess: onChiudi },
        )
      }
    >
      <TextField label="Titolo" value={titolo} onChange={(e) => setTitolo(e.target.value)} />
      <TextField
        label="Contenuti"
        value={contenuti}
        onChange={(e) => setContenuti(e.target.value)}
        multiline
        minRows={3}
        helperText="Per i ruoli specializzati: configurazione sicura, minacce note, comportamento in caso di eventi"
      />
      <TextField
        select
        label="Destinatari"
        value={destinatari}
        disabled={bloccata}
        onChange={(e) => setDestinatari(e.target.value as Destinatari)}
      >
        {(Object.keys(etichetteDestinatari) as Destinatari[]).map((d) => (
          <MenuItem key={d} value={d}>
            {etichetteDestinatari[d]}
          </MenuItem>
        ))}
      </TextField>
      <Stack direction="row" spacing={2}>
        <TextField
          type="date"
          label="Data prevista"
          value={dataPrevista}
          onChange={(e) => setDataPrevista(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          fullWidth
        />
        <TextField label="Durata (ore)" value={ore} onChange={(e) => setOre(e.target.value)} fullWidth />
      </Stack>
      <TextField
        label="Modalità di verifica"
        value={verifica}
        onChange={(e) => setVerifica(e.target.value)}
        disabled={bloccata && !attivita?.modalitaVerifica}
        helperText={
          bloccata
            ? 'Ci sono già registrazioni: la verifica non si può aggiungere o togliere'
            : 'Facoltativa (PR.AT-01 b)'
        }
      />
    </Dialogo>
  )
}

function DialogoRegistrazione({
  soggettoId,
  attivita,
  persone,
  onChiudi,
}: {
  soggettoId: number
  attivita: Attivita
  persone: Persona[]
  onChiudi: () => void
}) {
  const { partecipazioni } = useAzioniFormazione(soggettoId)
  const [quando, setQuando] = useState(attivita.dataPrevista <= oggiIso() ? attivita.dataPrevista : oggiIso())
  const esiti = esitiAmmessi(attivita)
  const [esito, setEsito] = useState<Esito>(esiti[0])
  const [scelte, setScelte] = useState<number[]>([])
  const ammesse = personeAmmesse(persone, attivita, quando)
  return (
    <Dialogo
      titolo={`Registra: ${attivita.titolo}`}
      onChiudi={onChiudi}
      errore={partecipazioni.error}
      disabilitato={scelte.length === 0 || !quando || partecipazioni.isPending}
      onSalva={() =>
        partecipazioni.mutate(
          { id: attivita.id, persone: scelte, data: quando, esito, note: null },
          { onSuccess: onChiudi },
        )
      }
    >
      <Stack direction="row" spacing={2}>
        <TextField
          type="date"
          label="Svolta il"
          value={quando}
          onChange={(e) => setQuando(e.target.value)}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: oggiIso() } }}
          fullWidth
        />
        <TextField select label="Esito" value={esito} onChange={(e) => setEsito(e.target.value as Esito)} fullWidth>
          {esiti.map((e) => (
            <MenuItem key={e} value={e}>
              {etichetteEsito[e]}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      <Typography variant="body2" color="text.secondary">
        Partecipanti ({etichetteDestinatari[attivita.destinatari].toLowerCase()})
      </Typography>
      {ammesse.length === 0 ? (
        <Typography color="text.secondary">Nessuna persona tra i destinatari.</Typography>
      ) : (
        <Box>
          {ammesse.map((p) => (
            <FormControlLabel
              key={p.id}
              sx={{ display: 'flex' }}
              control={
                <Checkbox
                  checked={scelte.includes(p.id)}
                  onChange={(e) => setScelte((s) => (e.target.checked ? [...s, p.id] : s.filter((x) => x !== p.id)))}
                />
              }
              label={`${p.nome}${p.ruolo ? ` · ${p.ruolo}` : ''}`}
            />
          ))}
        </Box>
      )}
    </Dialogo>
  )
}

function DialogoCessazione({
  soggettoId,
  persona,
  onChiudi,
}: {
  soggettoId: number
  persona: Persona
  onChiudi: () => void
}) {
  const { cessazione } = useAzioniFormazione(soggettoId)
  const [quando, setQuando] = useState(oggiIso)
  return (
    <Dialogo
      titolo={`Cessazione di ${persona.nome}`}
      onChiudi={onChiudi}
      errore={cessazione.error}
      disabilitato={!quando || cessazione.isPending}
      onSalva={() => cessazione.mutate({ id: persona.id, data: quando }, { onSuccess: onChiudi })}
    >
      <Typography variant="body2">
        La persona esce dalla copertura ma resta nel registro con la formazione ricevuta.
      </Typography>
      <TextField
        type="date"
        label="Data di cessazione"
        value={quando}
        onChange={(e) => setQuando(e.target.value)}
        slotProps={{ inputLabel: { shrink: true } }}
      />
    </Dialogo>
  )
}

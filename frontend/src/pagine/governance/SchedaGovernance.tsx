import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
  percorsoFile,
  useAzioniDocumento,
  useDocumenti,
  useFileDocumento,
  useGestioneOrgani,
  useOrgani,
  useVersioniDocumento,
  type Voce,
} from '../../api/governance'
import { useScarica } from '../../api/scarica'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { data } from '../inventario/date'
import {
  coloreStatoDocumento,
  dimensioneLeggibile,
  dividiPerCategoria,
  etichetteStatoDocumento,
  oggiIso,
} from './governance'

type Azione = 'crea' | 'carica' | 'approva' | 'riesame' | 'file' | 'storico'

export function SchedaGovernance() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const { data: governance, error } = useDocumenti(soggetto.id)
  const [aperta, setAperta] = useState<{ azione: Azione; voce: Voce } | null>(null)

  if (!governance) return <StatoCaricamento errore={error} />
  const { riepilogo } = governance
  const { politiche, documenti } = dividiPerCategoria(governance.voci)
  const apri = (azione: Azione) => (voce: Voce) => setAperta({ azione, voce })

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Politiche e piani approvati
        </Typography>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <LinearProgress
            variant="determinate"
            value={riepilogo.percentualeInRegola}
            sx={{ flexGrow: 1, height: 10, borderRadius: 5 }}
            aria-label="Documenti in regola"
          />
          <Typography variant="h5" component="p">
            {riepilogo.percentualeInRegola.toLocaleString('it-IT')}%
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, flexWrap: 'wrap' }}>
          <Chip color="success" label={`Validi ${riepilogo.validi}`} />
          <Chip color="warning" label={`Riesame in scadenza ${riepilogo.inScadenza}`} />
          <Chip color="error" label={`Riesame scaduto ${riepilogo.scaduti}`} />
          <Chip color="info" label={`Da approvare ${riepilogo.daApprovare}`} />
          <Chip variant="outlined" label={`Mancanti ${riepilogo.mancanti}`} />
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Le politiche (GV.PO-01) e i piani sono approvati dagli organi di amministrazione e riesaminati almeno ogni
          anno (GV.PO-02). Un nuovo file torna da approvare.
        </Typography>
      </Paper>

      <Organi />

      <TabellaVoci
        titolo="Politiche di sicurezza informatica (GV.PO-01)"
        voci={politiche}
        modificabile={modificabile}
        apri={apri}
      />
      <TabellaVoci
        titolo="Organizzazione, valutazioni e piani"
        voci={documenti}
        modificabile={modificabile}
        apri={apri}
      />

      {aperta && (
        <DialogoAzione
          soggettoId={soggetto.id}
          azione={aperta.azione}
          voce={aperta.voce}
          onChiudi={() => setAperta(null)}
        />
      )}
    </Stack>
  )
}

function TabellaVoci({
  titolo,
  voci,
  modificabile,
  apri,
}: {
  titolo: string
  voci: Voce[]
  modificabile: boolean
  apri: (azione: Azione) => (voce: Voce) => void
}) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h3" gutterBottom>
        {titolo}
      </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>Documento</TableCell>
            <TableCell>Stato</TableCell>
            <TableCell>Versione</TableCell>
            <TableCell>Approvato</TableCell>
            <TableCell>Prossimo riesame</TableCell>
            <TableCell align="right">Azioni</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {voci.map((v) => {
            const d = v.documento
            return (
              <TableRow key={v.codice}>
                <TableCell>
                  <Typography variant="body2" sx={{ fontWeight: 600 }}>
                    {v.ambito ? `${v.ambito})` : v.codice}
                  </Typography>
                  <Typography variant="body2">{v.nome.replace(/^Politica: /, '')}</Typography>
                </TableCell>
                <TableCell>
                  <Chip size="small" color={coloreStatoDocumento(v.stato)} label={etichetteStatoDocumento[v.stato]} />
                </TableCell>
                <TableCell>{d?.versione ?? '—'}</TableCell>
                <TableCell>
                  {d?.dataApprovazione ? (
                    <>
                      {data(d.dataApprovazione)}
                      <Typography variant="body2" color="text.secondary">
                        {d.approvatoDa?.nome}
                      </Typography>
                    </>
                  ) : (
                    '—'
                  )}
                </TableCell>
                <TableCell>{d?.prossimoRiesame ? data(d.prossimoRiesame) : '—'}</TableCell>
                <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                  {!d ? (
                    modificabile && (
                      <Button size="small" onClick={() => apri('crea')(v)}>
                        Crea
                      </Button>
                    )
                  ) : (
                    <>
                      {modificabile && (
                        <Button size="small" onClick={() => apri('carica')(v)}>
                          Carica
                        </Button>
                      )}
                      {modificabile && d.ultimoFile && !d.dataApprovazione && (
                        <Button size="small" onClick={() => apri('approva')(v)}>
                          Approva
                        </Button>
                      )}
                      {modificabile && d.dataApprovazione && (
                        <Button size="small" onClick={() => apri('riesame')(v)}>
                          Riesame
                        </Button>
                      )}
                      {d.ultimoFile && (
                        <Button size="small" onClick={() => apri('file')(v)}>
                          File
                        </Button>
                      )}
                      <Button size="small" onClick={() => apri('storico')(v)}>
                        Storico
                      </Button>
                    </>
                  )}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </Paper>
  )
}

function Organi() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const { data: organi, error } = useOrgani(soggetto.id)
  const { aggiungi, elimina } = useGestioneOrgani(soggetto.id)
  const [nome, setNome] = useState('')
  const [ruolo, setRuolo] = useState('')

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h3" gutterBottom>
        Organi di amministrazione e direttivi
      </Typography>
      {!organi ? (
        <StatoCaricamento errore={error} />
      ) : organi.length === 0 ? (
        <Typography color="text.secondary">Nessun organo registrato: servono per approvare i documenti.</Typography>
      ) : (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
          {organi.map((o) => (
            <Chip
              key={o.id}
              label={`${o.nome} · ${o.ruolo}`}
              onDelete={modificabile ? () => elimina.mutate(o.id) : undefined}
            />
          ))}
        </Stack>
      )}
      {elimina.isError && (
        <Alert severity="error" sx={{ mt: 2 }} onClose={() => elimina.reset()}>
          {elimina.error.message}
        </Alert>
      )}
      {modificabile && (
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mt: 2 }}>
          <TextField size="small" label="Nome" value={nome} onChange={(e) => setNome(e.target.value)} fullWidth />
          <TextField size="small" label="Ruolo" value={ruolo} onChange={(e) => setRuolo(e.target.value)} fullWidth />
          <Button
            disabled={!nome.trim() || !ruolo.trim() || aggiungi.isPending}
            onClick={() =>
              aggiungi.mutate(
                { nome: nome.trim(), ruolo: ruolo.trim() },
                {
                  onSuccess: () => {
                    setNome('')
                    setRuolo('')
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

function DialogoAzione({
  soggettoId,
  azione,
  voce,
  onChiudi,
}: {
  soggettoId: number
  azione: Azione
  voce: Voce
  onChiudi: () => void
}) {
  const azioni = useAzioniDocumento(soggettoId)
  const organi = useOrgani(soggettoId)
  const id = voce.documento?.id ?? null
  const files = useFileDocumento(soggettoId, azione === 'file' ? id : null)
  const versioni = useVersioniDocumento(soggettoId, azione === 'storico' ? id : null)
  const scarica = useScarica()
  const [versione, setVersione] = useState(voce.documento?.versione ?? '1.0')
  const [periodicita, setPeriodicita] = useState(12)
  const [file, setFile] = useState<File | null>(null)
  const [quando, setQuando] = useState(oggiIso)
  const [organoId, setOrganoId] = useState<number | ''>('')
  const [errore, setErrore] = useState<string | null>(null)

  const esegui = (promessa: Promise<unknown>) => promessa.then(onChiudi).catch((e: Error) => setErrore(e.message))
  const inCorso = Object.values(azioni).some((m) => m.isPending)

  const titoli: Record<Azione, string> = {
    crea: 'Nuovo documento',
    carica: 'Carica una nuova versione',
    approva: 'Registra l’approvazione',
    riesame: 'Registra il riesame',
    file: 'File caricati',
    storico: 'Storico di approvazioni e riesami',
  }

  let contenuto
  let conferma: (() => void) | null = null
  switch (azione) {
    case 'crea':
      contenuto = (
        <>
          <TextField label="Versione" value={versione} onChange={(e) => setVersione(e.target.value)} />
          <TextField
            select
            label="Riesame ogni"
            value={periodicita}
            onChange={(e) => setPeriodicita(Number(e.target.value))}
            helperText="Almeno una volta l’anno (GV.PO-02)"
          >
            {[3, 6, 12].map((m) => (
              <MenuItem key={m} value={m}>
                {m} mesi
              </MenuItem>
            ))}
          </TextField>
        </>
      )
      conferma = () =>
        void esegui(
          azioni.crea.mutateAsync({
            tipo: voce.codice,
            versione: versione || null,
            periodicitaRiesameMesi: periodicita,
          }),
        )
      break
    case 'carica':
      contenuto = (
        <>
          {voce.documento?.dataApprovazione && (
            <Alert severity="warning">Il documento è approvato: una nuova versione andrà approvata di nuovo.</Alert>
          )}
          <Button variant="outlined" component="label">
            {file ? file.name : 'Scegli il file (PDF, DOCX, XLSX, ODT, ODS · max 10 MB)'}
            <input
              hidden
              type="file"
              accept=".pdf,.docx,.xlsx,.odt,.ods"
              onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            />
          </Button>
          <TextField label="Versione" value={versione} onChange={(e) => setVersione(e.target.value)} />
        </>
      )
      if (file && id) conferma = () => void esegui(azioni.carica.mutateAsync({ id, file, versione }))
      break
    case 'approva':
      contenuto = (
        <>
          <TextField
            select
            label="Approvato da"
            value={organoId}
            onChange={(e) => setOrganoId(Number(e.target.value))}
            helperText={organi.data?.length === 0 ? 'Aggiungi prima un organo di amministrazione' : undefined}
          >
            {(organi.data ?? []).map((o) => (
              <MenuItem key={o.id} value={o.id}>
                {o.nome} · {o.ruolo}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            type="date"
            label="Data della delibera"
            value={quando}
            onChange={(e) => setQuando(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </>
      )
      if (id && organoId !== '' && quando)
        conferma = () => void esegui(azioni.approva.mutateAsync({ id, data: quando, organoId }))
      break
    case 'riesame':
      contenuto = (
        <TextField
          type="date"
          label="Data del riesame"
          value={quando}
          onChange={(e) => setQuando(e.target.value)}
          helperText="Il documento è stato riesaminato e resta valido senza modifiche"
          slotProps={{ inputLabel: { shrink: true } }}
        />
      )
      if (id && quando) conferma = () => void esegui(azioni.riesame.mutateAsync({ id, data: quando }))
      break
    case 'file':
      contenuto = !files.data ? (
        <StatoCaricamento errore={files.error} />
      ) : (
        <Table size="small">
          <TableBody>
            {files.data.map((f) => (
              <TableRow key={f.id}>
                <TableCell>
                  {f.nomeFile}
                  <Typography variant="body2" color="text.secondary">
                    v. {f.versioneDocumento ?? '—'} · {dimensioneLeggibile(f.dimensione)} · {data(f.caricatoIl)} ·{' '}
                    {f.caricatoDa}
                  </Typography>
                </TableCell>
                <TableCell align="right">
                  <Button
                    size="small"
                    onClick={() =>
                      id &&
                      scarica(percorsoFile(soggettoId, id, f.id), f.nomeFile).catch((e: Error) => setErrore(e.message))
                    }
                  >
                    Scarica
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )
      break
    case 'storico':
      contenuto = !versioni.data ? (
        <StatoCaricamento errore={versioni.error} />
      ) : (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>#</TableCell>
              <TableCell>Approvato</TableCell>
              <TableCell>Ultimo riesame</TableCell>
              <TableCell>Dal</TableCell>
              <TableCell>Registrato da</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {versioni.data.map((v) => (
              <TableRow key={v.versione}>
                <TableCell>{v.versione}</TableCell>
                <TableCell>
                  {v.dataApprovazione ? `${data(v.dataApprovazione)} · ${v.approvatoDa ?? ''}` : 'No'}
                </TableCell>
                <TableCell>{v.dataUltimoRiesame ? data(v.dataUltimoRiesame) : '—'}</TableCell>
                <TableCell>{data(v.validoDa)}</TableCell>
                <TableCell>{v.modificataDa ?? 'creazione'}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )
      break
  }

  return (
    <Dialog open onClose={onChiudi} fullWidth maxWidth="sm">
      <DialogTitle>
        {titoli[azione]}
        <Typography variant="body2" color="text.secondary">
          {voce.nome}
        </Typography>
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {contenuto}
          {errore && <Alert severity="error">{errore}</Alert>}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onChiudi}>
          {conferma || azione === 'carica' || azione === 'approva' ? 'Annulla' : 'Chiudi'}
        </Button>
        {(azione === 'crea' || azione === 'carica' || azione === 'approva' || azione === 'riesame') && (
          <Button variant="contained" disabled={!conferma || inCorso} onClick={() => conferma?.()}>
            Salva
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}

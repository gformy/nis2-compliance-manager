import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import {
  etichetteCriterio,
  useDipendenze,
  useFornitoriRilevanti,
  useVersioniDipendenza,
  type Dipendenza,
} from '../../api/fornitori'
import { nomePaese } from '../../api/inventario'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { ChipStato } from '../inventario/componenti'
import { data } from '../inventario/date'
import { DialogoDipendenza } from './DialogoDipendenza'

export function SchedaFornitori() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const dipendenze = useDipendenze(soggetto.id)
  const rilevanti = useFornitoriRilevanti(soggetto.id)
  const [dialogo, setDialogo] = useState<{ dipendenza?: Dipendenza } | null>(null)
  const [storico, setStorico] = useState<Dipendenza | null>(null)

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
            Dipendenze da fornitori
          </Typography>
          {modificabile && (
            <Button variant="contained" onClick={() => setDialogo({})}>
              Nuova dipendenza
            </Button>
          )}
        </Box>
        {!dipendenze.data ? (
          <StatoCaricamento errore={dipendenze.error} />
        ) : dipendenze.data.length === 0 ? (
          <Typography color="text.secondary">Nessuna dipendenza registrata.</Typography>
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Fornitore</TableCell>
                  <TableCell>Asset o servizio</TableCell>
                  <TableCell>Criterio</TableCell>
                  <TableCell>Tipologia</TableCell>
                  <TableCell>Stato</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {dipendenze.data.map((d) => (
                  <TableRow key={d.id}>
                    <TableCell>{d.fornitoreDenominazione}</TableCell>
                    <TableCell>
                      {d.elementoTipo === 'ASSET' ? 'Asset' : 'Servizio'}: {d.elementoNome}
                    </TableCell>
                    <TableCell>{etichetteCriterio[d.criterioRilevanza]}</TableCell>
                    <TableCell>{d.tipologiaFornitura ?? '—'}</TableCell>
                    <TableCell>
                      <ChipStato stato={d.stato} />
                    </TableCell>
                    <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                      <Button size="small" onClick={() => setStorico(d)}>
                        Storico
                      </Button>
                      {modificabile && (
                        <Button size="small" onClick={() => setDialogo({ dipendenza: d })}>
                          Modifica
                        </Button>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2">
          Fornitori rilevanti per il profilo ACN
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          I cinque campi richiesti dal portale per ogni fornitore rilevante, calcolati dalle dipendenze attive.
        </Typography>
        {!rilevanti.data ? (
          <StatoCaricamento errore={rilevanti.error} />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Denominazione</TableCell>
                  <TableCell>Codice fiscale</TableCell>
                  <TableCell>Paese sede legale</TableCell>
                  <TableCell>Codici CPV</TableCell>
                  <TableCell>Criterio</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rilevanti.data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>Nessun fornitore rilevante.</TableCell>
                  </TableRow>
                )}
                {rilevanti.data.map((f) => (
                  <TableRow key={f.codiceFiscale}>
                    <TableCell>{f.denominazione}</TableCell>
                    <TableCell>{f.codiceFiscale}</TableCell>
                    <TableCell>{nomePaese(f.paeseSedeLegale)}</TableCell>
                    <TableCell>{f.codiciCpv.join(', ') || '—'}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5}>
                        {f.criteriRilevanza.map((c) => (
                          <Chip key={c} size="small" label={c} title={c === 1 ? 'ICT' : 'Non fungibile'} />
                        ))}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {dialogo && (
        <DialogoDipendenza
          aperto
          soggettoId={soggetto.id}
          dipendenza={dialogo.dipendenza}
          onChiudi={() => setDialogo(null)}
        />
      )}
      <StoricoDipendenza soggettoId={soggetto.id} dipendenza={storico} onChiudi={() => setStorico(null)} />
    </Stack>
  )
}

function StoricoDipendenza({
  soggettoId,
  dipendenza,
  onChiudi,
}: {
  soggettoId: number
  dipendenza: Dipendenza | null
  onChiudi: () => void
}) {
  const versioni = useVersioniDipendenza(soggettoId, dipendenza?.id ?? null)
  return (
    <Dialog open={dipendenza !== null} onClose={onChiudi} fullWidth maxWidth="md">
      <DialogTitle>
        Storico: {dipendenza?.fornitoreDenominazione} per {dipendenza?.elementoNome}
      </DialogTitle>
      <DialogContent>
        {!versioni.data ? (
          <StatoCaricamento errore={versioni.error} />
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Versione</TableCell>
                <TableCell>Criterio</TableCell>
                <TableCell>Tipologia</TableCell>
                <TableCell>Stato</TableCell>
                <TableCell>Valida dal</TableCell>
                <TableCell>Modificata da</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {versioni.data.map((v) => (
                <TableRow key={v.versione}>
                  <TableCell>{v.versione}</TableCell>
                  <TableCell>{etichetteCriterio[v.criterioRilevanza]}</TableCell>
                  <TableCell>{v.tipologiaFornitura ?? '—'}</TableCell>
                  <TableCell>
                    <ChipStato stato={v.stato} />
                  </TableCell>
                  <TableCell>{data(v.validoDa)}</TableCell>
                  <TableCell>{v.modificataDa ?? 'creazione'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  )
}

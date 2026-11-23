import {
  Alert,
  Box,
  Button,
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
import { Link as RouterLink, useParams } from 'react-router'
import {
  etichetteTipoAsset,
  useAggiorna,
  useCollegamenti,
  useCollegamento,
  useDismetti,
  useElemento,
  useElenco,
  useVersioniElemento,
  type Asset,
  type DatiServizio,
  type Servizio,
} from '../../api/inventario'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { CampiServizio } from './CampiServizio'
import { ChipStato, DialogoDismissione, TabellaVersioni } from './componenti'
import { data } from './date'
import { servizioAForm } from './formInventario'

export function DettaglioServizio() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const id = Number(useParams().servizioId)
  const { data: servizio, error } = useElemento<Servizio>(soggetto.id, 'servizi', id)
  const versioni = useVersioniElemento(soggetto.id, 'servizi', id)
  const collegamenti = useCollegamenti(soggetto.id, 'servizi', id)
  const tuttiGliAsset = useElenco<Asset>(soggetto.id, 'asset')
  const aggiorna = useAggiorna<DatiServizio, Servizio>(soggetto.id, 'servizi', id)
  const dismetti = useDismetti(soggetto.id, 'servizi', id)
  const collegamento = useCollegamento(soggetto.id, id)
  const [conferma, setConferma] = useState(false)
  const [assetScelto, setAssetScelto] = useState('')

  if (!servizio) {
    return <StatoCaricamento errore={error} />
  }
  const attivo = servizio.stato === 'ATTIVO'
  const modificabileOra = modificabile && attivo

  const giaCollegati = new Set(collegamenti.data?.filter((c) => c.stato === 'ATTIVA').map((c) => c.assetId))
  const collegabili = tuttiGliAsset.data?.filter((a) => a.stato === 'ATTIVO' && !giaCollegati.has(a.id)) ?? []
  const tipoDi = new Map(tuttiGliAsset.data?.map((a) => [a.id, a.tipoAsset]))

  return (
    <Stack spacing={3}>
      <Link component={RouterLink} to={`/soggetti/${soggetto.id}/servizi`}>
        ← Tutti i servizi
      </Link>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
            {servizio.nome}
          </Typography>
          <ChipStato stato={servizio.stato} />
          {modificabileOra && (
            <Button color="error" onClick={() => setConferma(true)}>
              Dismetti
            </Button>
          )}
        </Box>
        {!attivo && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Dismesso il {data(servizio.dataFineValidita)}: resta nello storico ma non si modifica più.
          </Alert>
        )}
        <CampiServizio
          valori={servizioAForm(servizio)}
          solaLettura={!modificabileOra}
          etichettaSalva="Salva"
          errore={aggiorna.error}
          onSalva={(dati) => aggiorna.mutateAsync(dati)}
        />
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Asset da cui dipende
        </Typography>
        {!collegamenti.data ? (
          <StatoCaricamento errore={collegamenti.error} />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Asset</TableCell>
                  <TableCell>Tipo</TableCell>
                  <TableCell>Collegamento</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {collegamenti.data.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4}>Nessun asset collegato.</TableCell>
                  </TableRow>
                )}
                {collegamenti.data.map((c) => {
                  const tipo = tipoDi.get(c.assetId)
                  return (
                    <TableRow key={c.assetId}>
                      <TableCell>
                        <Link component={RouterLink} to={`/soggetti/${soggetto.id}/asset/${c.assetId}`}>
                          {c.assetNome}
                        </Link>
                      </TableCell>
                      <TableCell>{tipo ? etichetteTipoAsset[tipo] : ''}</TableCell>
                      <TableCell>
                        <ChipStato stato={c.stato} />
                      </TableCell>
                      <TableCell align="right">
                        {modificabileOra && c.stato === 'ATTIVA' && (
                          <Button
                            size="small"
                            disabled={collegamento.isPending}
                            onClick={() => collegamento.mutate({ assetId: c.assetId, attivo: false })}
                          >
                            Cessa
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        {modificabileOra && (
          <Box sx={{ display: 'flex', gap: 2, mt: 2 }}>
            <TextField
              select
              size="small"
              label="Collega un asset"
              value={assetScelto}
              onChange={(e) => setAssetScelto(e.target.value)}
              sx={{ minWidth: 320 }}
              disabled={collegabili.length === 0}
              helperText={collegabili.length === 0 ? 'Nessun altro asset attivo da collegare' : ' '}
            >
              {collegabili.map((a) => (
                <MenuItem key={a.id} value={String(a.id)}>
                  {a.nome}
                </MenuItem>
              ))}
            </TextField>
            <Box>
              <Button
                variant="outlined"
                disabled={!assetScelto || collegamento.isPending}
                onClick={() =>
                  collegamento.mutate({ assetId: Number(assetScelto), attivo: true }, { onSuccess: () => setAssetScelto('') })
                }
              >
                Collega
              </Button>
            </Box>
          </Box>
        )}
        {collegamento.isError && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {collegamento.error.message}
          </Alert>
        )}
      </Paper>

      <TabellaVersioni versioni={versioni.data} errore={versioni.error} />

      <DialogoDismissione
        aperto={conferma}
        nome={servizio.nome}
        inCorso={dismetti.isPending}
        onAnnulla={() => setConferma(false)}
        onConferma={() => dismetti.mutate(undefined, { onSettled: () => setConferma(false) })}
      />
      {dismetti.isError && <Alert severity="error">{dismetti.error.message}</Alert>}
    </Stack>
  )
}

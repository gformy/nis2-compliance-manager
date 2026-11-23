import { Alert, Box, Button, Link, List, ListItem, ListItemText, Paper, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router'
import {
  useAggiorna,
  useCollegamenti,
  useDismetti,
  useElemento,
  useVersioniElemento,
  type Asset,
  type DatiAsset,
} from '../../api/inventario'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { CampiAsset } from './CampiAsset'
import { ChipStato, DialogoDismissione, TabellaVersioni } from './componenti'
import { data } from './date'

export function DettaglioAsset() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const id = Number(useParams().assetId)
  const { data: asset, error } = useElemento<Asset>(soggetto.id, 'asset', id)
  const versioni = useVersioniElemento(soggetto.id, 'asset', id)
  const servizi = useCollegamenti(soggetto.id, 'asset', id)
  const aggiorna = useAggiorna<DatiAsset, Asset>(soggetto.id, 'asset', id)
  const dismetti = useDismetti(soggetto.id, 'asset', id)
  const [conferma, setConferma] = useState(false)

  if (!asset) {
    return <StatoCaricamento errore={error} />
  }
  const attivo = asset.stato === 'ATTIVO'

  return (
    <Stack spacing={3}>
      <Link component={RouterLink} to={`/soggetti/${soggetto.id}/asset`}>
        ← Tutti gli asset
      </Link>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
          <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
            {asset.nome}
          </Typography>
          <ChipStato stato={asset.stato} />
          {modificabile && attivo && (
            <Button color="error" onClick={() => setConferma(true)}>
              Dismetti
            </Button>
          )}
        </Box>
        {!attivo && (
          <Alert severity="info" sx={{ mb: 2 }}>
            Dismesso il {data(asset.dataFineValidita)}: resta nello storico ma non si modifica più.
          </Alert>
        )}
        <CampiAsset
          valori={{ nome: asset.nome, tipoAsset: asset.tipoAsset, rilevante: asset.rilevante }}
          solaLettura={!modificabile || !attivo}
          etichettaSalva="Salva"
          errore={aggiorna.error}
          onSalva={(dati) => aggiorna.mutateAsync(dati)}
        />
      </Paper>

      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2">
          Servizi che dipendono da questo asset
        </Typography>
        {!servizi.data ? (
          <StatoCaricamento errore={servizi.error} />
        ) : servizi.data.length === 0 ? (
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Nessun servizio collegato. I collegamenti si gestiscono dalla pagina del servizio.
          </Typography>
        ) : (
          <List dense>
            {servizi.data.map((c) => (
              <ListItem key={c.servizioId} secondaryAction={<ChipStato stato={c.stato} />}>
                <ListItemText
                  primary={
                    <Link component={RouterLink} to={`/soggetti/${soggetto.id}/servizi/${c.servizioId}`}>
                      {c.servizioNome}
                    </Link>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}
      </Paper>

      <TabellaVersioni versioni={versioni.data} errore={versioni.error} />

      <DialogoDismissione
        aperto={conferma}
        nome={asset.nome}
        inCorso={dismetti.isPending}
        onAnnulla={() => setConferma(false)}
        onConferma={() => dismetti.mutate(undefined, { onSettled: () => setConferma(false) })}
      />
      {dismetti.isError && <Alert severity="error">{dismetti.error.message}</Alert>}
    </Stack>
  )
}

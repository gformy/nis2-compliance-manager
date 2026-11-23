import {
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Link,
  Paper,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router'
import { etichetteTipoAsset, useCrea, useElenco, type Asset, type DatiAsset } from '../../api/inventario'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { CampiAsset } from './CampiAsset'
import { ChipStato } from './componenti'
import { assetVuoto } from './formInventario'

export function ElencoAsset() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const { data: asset, error } = useElenco<Asset>(soggetto.id, 'asset')
  const crea = useCrea<DatiAsset, Asset>(soggetto.id, 'asset')
  const naviga = useNavigate()
  const [mostraDismessi, setMostraDismessi] = useState(false)
  const [nuovo, setNuovo] = useState(false)

  const visibili = asset?.filter((a) => mostraDismessi || a.stato === 'ATTIVO')

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
          Asset
        </Typography>
        <FormControlLabel
          control={<Switch checked={mostraDismessi} onChange={(_, v) => setMostraDismessi(v)} />}
          label="Mostra dismessi"
        />
        {modificabile && (
          <Button variant="contained" onClick={() => setNuovo(true)}>
            Nuovo asset
          </Button>
        )}
      </Box>

      {!visibili ? (
        <StatoCaricamento errore={error} />
      ) : visibili.length === 0 ? (
        <Typography color="text.secondary">Nessun asset.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Nome</TableCell>
                <TableCell>Tipo</TableCell>
                <TableCell>Rilevante</TableCell>
                <TableCell>Stato</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibili.map((a) => (
                <TableRow key={a.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`/soggetti/${soggetto.id}/asset/${a.id}`}>
                      {a.nome}
                    </Link>
                  </TableCell>
                  <TableCell>{etichetteTipoAsset[a.tipoAsset]}</TableCell>
                  <TableCell>{a.rilevante ? 'Sì' : 'No'}</TableCell>
                  <TableCell>
                    <ChipStato stato={a.stato} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={nuovo} onClose={() => setNuovo(false)} fullWidth maxWidth="sm">
        <DialogTitle>Nuovo asset</DialogTitle>
        <DialogContent sx={{ pt: 1 }}>
          <Box sx={{ pt: 1 }}>
            <CampiAsset
              valori={assetVuoto}
              solaLettura={false}
              etichettaSalva="Crea"
              errore={crea.error}
              onAnnulla={() => setNuovo(false)}
              onSalva={async (dati) => {
                const creato = await crea.mutateAsync(dati)
                setNuovo(false)
                void naviga(`/soggetti/${soggetto.id}/asset/${creato.id}`)
              }}
            />
          </Box>
        </DialogContent>
      </Dialog>
    </Paper>
  )
}

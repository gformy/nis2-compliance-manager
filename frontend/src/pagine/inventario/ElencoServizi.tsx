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
import { nomePaese, useCrea, useElenco, type DatiServizio, type Servizio } from '../../api/inventario'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { CampiServizio } from './CampiServizio'
import { ChipStato } from './componenti'
import { servizioVuoto } from './formInventario'

export function ElencoServizi() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const { data: servizi, error } = useElenco<Servizio>(soggetto.id, 'servizi')
  const crea = useCrea<DatiServizio, Servizio>(soggetto.id, 'servizi')
  const naviga = useNavigate()
  const [mostraDismessi, setMostraDismessi] = useState(false)
  const [nuovo, setNuovo] = useState(false)

  const visibili = servizi?.filter((s) => mostraDismessi || s.stato === 'ATTIVO')

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h6" component="h2" sx={{ flexGrow: 1 }}>
          Servizi erogati
        </Typography>
        <FormControlLabel
          control={<Switch checked={mostraDismessi} onChange={(_, v) => setMostraDismessi(v)} />}
          label="Mostra dismessi"
        />
        {modificabile && (
          <Button variant="contained" onClick={() => setNuovo(true)}>
            Nuovo servizio
          </Button>
        )}
      </Box>

      {!visibili ? (
        <StatoCaricamento errore={error} />
      ) : visibili.length === 0 ? (
        <Typography color="text.secondary">Nessun servizio.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Nome</TableCell>
                <TableCell>Stati membri</TableCell>
                <TableCell>Categoria</TableCell>
                <TableCell>Stato</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {visibili.map((s) => (
                <TableRow key={s.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`/soggetti/${soggetto.id}/servizi/${s.id}`}>
                      {s.nome}
                    </Link>
                  </TableCell>
                  <TableCell>{s.statiMembro.map(nomePaese).join(', ') || '—'}</TableCell>
                  <TableCell>{s.categoriaRilevanza ?? 'da definire'}</TableCell>
                  <TableCell>
                    <ChipStato stato={s.stato} />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={nuovo} onClose={() => setNuovo(false)} fullWidth maxWidth="sm">
        <DialogTitle>Nuovo servizio</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1 }}>
            <CampiServizio
              valori={servizioVuoto}
              solaLettura={false}
              etichettaSalva="Crea"
              errore={crea.error}
              onAnnulla={() => setNuovo(false)}
              onSalva={async (dati) => {
                const creato = await crea.mutateAsync(dati)
                setNuovo(false)
                void naviga(`/soggetti/${soggetto.id}/servizi/${creato.id}`)
              }}
            />
          </Box>
        </DialogContent>
      </Dialog>
    </Paper>
  )
}

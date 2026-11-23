import {
  Box,
  Button,
  Chip,
  Link,
  Paper,
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
import { etichetteGravita, useCreaIncidente, useIncidenti } from '../../api/incidenti'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { useContestoSoggetto } from '../contestoSoggetto'
import { data } from '../inventario/date'
import { ChipScadenza } from './ChipScadenza'
import { DialogoIncidente } from './DialogoIncidente'
import { useAdesso } from './useAdesso'

export function ElencoIncidenti() {
  const { soggetto, modificabile } = useContestoSoggetto()
  const { data: incidenti, error } = useIncidenti(soggetto.id)
  const crea = useCreaIncidente(soggetto.id)
  const naviga = useNavigate()
  const adesso = useAdesso()
  const [nuovo, setNuovo] = useState(false)

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6" component="h2">
            Incidenti significativi
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Pre-notifica entro 24 h e notifica entro 72 h dalla data di evidenza; relazione finale entro un mese dalla
            notifica (D.Lgs. 138/2024, art. 25).
          </Typography>
        </Box>
        {modificabile && (
          <Button variant="contained" onClick={() => setNuovo(true)}>
            Nuovo incidente
          </Button>
        )}
      </Box>

      {!incidenti ? (
        <StatoCaricamento errore={error} />
      ) : incidenti.length === 0 ? (
        <Typography color="text.secondary">Nessun incidente registrato.</Typography>
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Incidente</TableCell>
                <TableCell>Tipo</TableCell>
                <TableCell>Gravità</TableCell>
                <TableCell>Evidenza</TableCell>
                <TableCell>Stato</TableCell>
                <TableCell>Prossima scadenza</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {incidenti.map((i) => (
                <TableRow key={i.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={String(i.id)}>
                      {i.titolo}
                    </Link>
                  </TableCell>
                  <TableCell>{i.tipoIncidente}</TableCell>
                  <TableCell>{i.gravita ? etichetteGravita[i.gravita] : '—'}</TableCell>
                  <TableCell>{data(i.dataEvidenza)}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" label={i.stato === 'APERTO' ? 'Aperto' : 'Chiuso'} />
                  </TableCell>
                  <TableCell>
                    {i.prossimaScadenza ? (
                      <ChipScadenza scadenza={i.prossimaScadenza} adesso={adesso} conTipo />
                    ) : (
                      <Typography variant="body2" color="text.secondary">
                        Nessuna
                      </Typography>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {nuovo && (
        <DialogoIncidente
          soggettoId={soggetto.id}
          aperto
          inCorso={crea.isPending}
          errore={crea.error}
          onChiudi={() => setNuovo(false)}
          onSalva={(dati) =>
            crea.mutate(dati, {
              onSuccess: (creato) => naviga(String(creato.id)),
            })
          }
        />
      )}
    </Paper>
  )
}

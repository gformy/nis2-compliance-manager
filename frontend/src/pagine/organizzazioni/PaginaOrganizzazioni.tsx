import {
  Box,
  Button,
  Chip,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { useState } from 'react'
import { Navigate, Link as RouterLink, useNavigate } from 'react-router'
import { etichetteTipoOrganizzazione, euro, useOrganizzazioni } from '../../api/organizzazioni'
import { useProfilo } from '../../api/profilo'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { FormOrganizzazione } from './FormOrganizzazione'
import { descriviQuota } from './quota'

/** Il gestore vede tutte le organizzazioni; un amministratore di organizzazione va diretto alla sua. */
export function PaginaOrganizzazioni() {
  const { data: profilo } = useProfilo()
  const { data: organizzazioni, error } = useOrganizzazioni()
  const naviga = useNavigate()
  const [nuova, setNuova] = useState(false)

  if (profilo && !profilo.amministratore) {
    return profilo.organizzazione ? (
      <Navigate to={`/organizzazioni/${profilo.organizzazione.id}`} replace />
    ) : (
      <Typography>Non amministri nessuna organizzazione.</Typography>
    )
  }
  if (!organizzazioni) return <StatoCaricamento errore={error} />
  const ricavoMensile = organizzazioni.reduce((somma, o) => somma + o.costoMensile, 0)

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h4" component="h1">
            Organizzazioni clienti
          </Typography>
          <Typography color="text.secondary">
            Canone mensile complessivo: {euro.format(ricavoMensile)} (prezzo per soggetto × soggetti gestiti)
          </Typography>
        </Box>
        <Button variant="contained" onClick={() => setNuova(true)}>
          Nuova organizzazione
        </Button>
      </Box>
      <Paper variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Organizzazione</TableCell>
              <TableCell>Tipo</TableCell>
              <TableCell>Piano</TableCell>
              <TableCell>Soggetti</TableCell>
              <TableCell align="right">Canone mensile</TableCell>
              <TableCell>Stato</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {organizzazioni.map((o) => (
              <TableRow key={o.id} hover>
                <TableCell>
                  <Link component={RouterLink} to={`/organizzazioni/${o.id}`}>
                    {o.nome}
                  </Link>
                </TableCell>
                <TableCell>{etichetteTipoOrganizzazione[o.tipo]}</TableCell>
                <TableCell>{o.piano.nome}</TableCell>
                <TableCell>{descriviQuota(o)}</TableCell>
                <TableCell align="right">{euro.format(o.costoMensile)}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={o.stato === 'ATTIVA' ? 'success' : 'warning'}
                    label={o.stato === 'ATTIVA' ? 'Attiva' : 'Sospesa'}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Paper>
      {nuova && (
        <FormOrganizzazione onChiudi={() => setNuova(false)} onSalvata={(o) => naviga(`/organizzazioni/${o.id}`)} />
      )}
    </>
  )
}

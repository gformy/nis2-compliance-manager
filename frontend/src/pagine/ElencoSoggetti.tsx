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
import { Link as RouterLink } from 'react-router'
import { useProfilo } from '../api/profilo'
import { useSoggetti } from '../api/soggetti'
import { StatoCaricamento } from '../componenti/StatoCaricamento'
import { NuovoSoggetto } from './NuovoSoggetto'

export function ElencoSoggetti() {
  const { data: soggetti, error } = useSoggetti()
  const { data: profilo } = useProfilo()
  const [nuovo, setNuovo] = useState(false)
  const gestore = profilo?.amministratore ?? false
  const puoCreare = gestore || (profilo?.organizzazione != null && !profilo.organizzazione.sospesa)
  // La colonna serve solo a chi vede soggetti di piu' organizzazioni
  const conOrganizzazione = new Set(soggetti?.map((s) => s.organizzazioneId)).size > 1

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>
          Soggetti NIS
        </Typography>
        {puoCreare && (
          <Button variant="contained" onClick={() => setNuovo(true)}>
            Nuovo soggetto
          </Button>
        )}
      </Box>
      {!soggetti ? (
        <StatoCaricamento errore={error} />
      ) : soggetti.length === 0 ? (
        <Typography color="text.secondary">Nessun soggetto registrato.</Typography>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Denominazione</TableCell>
                <TableCell>Codice fiscale</TableCell>
                <TableCell>Tipo</TableCell>
                {conOrganizzazione && <TableCell>Organizzazione</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {soggetti.map((soggetto) => (
                <TableRow key={soggetto.id} hover>
                  <TableCell>
                    <Link component={RouterLink} to={`/soggetti/${soggetto.id}`}>
                      {soggetto.denominazione}
                    </Link>
                  </TableCell>
                  <TableCell>{soggetto.codiceFiscale}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      label={soggetto.tipoSoggetto === 'ESSENZIALE' ? 'Essenziale' : 'Importante'}
                      color={soggetto.tipoSoggetto === 'ESSENZIALE' ? 'primary' : 'default'}
                    />
                  </TableCell>
                  {conOrganizzazione && <TableCell>{soggetto.organizzazione}</TableCell>}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {nuovo && <NuovoSoggetto gestore={gestore} onChiudi={() => setNuovo(false)} />}
    </>
  )
}

import {
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
import { Link as RouterLink } from 'react-router'
import { useSoggetti } from '../api/soggetti'
import { StatoCaricamento } from '../componenti/StatoCaricamento'

export function ElencoSoggetti() {
  const { data: soggetti, error } = useSoggetti()

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>
        Soggetti NIS
      </Typography>
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
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </>
  )
}

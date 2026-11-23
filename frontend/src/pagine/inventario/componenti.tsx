import {
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import type { StatoElemento, VersioneElemento } from '../../api/inventario'
import { StatoCaricamento } from '../../componenti/StatoCaricamento'
import { data } from './date'


export function ChipStato({ stato }: { stato: StatoElemento | 'ATTIVA' | 'CESSATA' }) {
  const attivo = stato === 'ATTIVO' || stato === 'ATTIVA'
  const etichetta = { ATTIVO: 'Attivo', DISMESSO: 'Dismesso', ATTIVA: 'Attivo', CESSATA: 'Cessato' }[stato]
  return <Chip size="small" label={etichetta} color={attivo ? 'success' : 'default'} variant="outlined" />
}

/** Storico di un elemento critico: dalla vista ElementoCritico_Versioni del PW. */
export function TabellaVersioni({ versioni, errore }: { versioni?: VersioneElemento[]; errore?: Error | null }) {
  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        Storico
      </Typography>
      {!versioni ? (
        <StatoCaricamento errore={errore} />
      ) : (
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Versione</TableCell>
                <TableCell>Nome</TableCell>
                <TableCell>Stato</TableCell>
                <TableCell>Valida dal</TableCell>
                <TableCell>Valida fino al</TableCell>
                <TableCell>Modificata da</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {versioni.map((v) => (
                <TableRow key={v.versione}>
                  <TableCell>{v.versione}</TableCell>
                  <TableCell>{v.nome}</TableCell>
                  <TableCell>
                    <ChipStato stato={v.stato} />
                  </TableCell>
                  <TableCell>{data(v.validoDa)}</TableCell>
                  <TableCell>{data(v.validoA, 'in vigore')}</TableCell>
                  <TableCell>{v.modificataDa ?? 'creazione'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Paper>
  )
}

/** Conferma della dismissione: non si annulla dall'interfaccia. */
export function DialogoDismissione({
  aperto,
  nome,
  onConferma,
  onAnnulla,
  inCorso,
}: {
  aperto: boolean
  nome: string
  onConferma: () => void
  onAnnulla: () => void
  inCorso: boolean
}) {
  return (
    <Dialog open={aperto} onClose={onAnnulla}>
      <DialogTitle>Dismettere «{nome}»?</DialogTitle>
      <DialogContent>
        <DialogContentText>
          L'elemento non verrà cancellato: resta nello storico e nei profili ACN già inviati, ma non sarà più
          modificabile e i suoi collegamenti attivi cesseranno.
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={onAnnulla}>Annulla</Button>
        <Button color="error" variant="contained" disabled={inCorso} onClick={onConferma}>
          Dismetti
        </Button>
      </DialogActions>
    </Dialog>
  )
}

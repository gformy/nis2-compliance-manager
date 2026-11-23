import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  LinearProgress,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useState } from "react";
import {
  etichetteStato,
  useRequisiti,
  useRiepilogo,
  useValuta,
  useVersioniAdempimento,
  type Requisito,
  type StatoAdempimento,
} from "../../api/conformita";
import { StatoCaricamento } from "../../componenti/StatoCaricamento";
import { useContestoSoggetto } from "../contestoSoggetto";
import { data } from "../inventario/date";
import { coloreStato, raggruppaPerMisura } from "./conformita";

export function SchedaConformita() {
  const { soggetto, modificabile } = useContestoSoggetto();
  const requisiti = useRequisiti(soggetto.id);
  const riepilogo = useRiepilogo(soggetto.id);
  const [storico, setStorico] = useState<Requisito | null>(null);

  return (
    <Stack spacing={3}>
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography variant="h6" component="h2" gutterBottom>
          Attuazione delle misure di sicurezza
        </Typography>
        {!riepilogo.data ? (
          <StatoCaricamento errore={riepilogo.error} />
        ) : (
          <>
            <Box sx={{ display: "flex", alignItems: "center", gap: 2 }}>
              <LinearProgress
                variant="determinate"
                value={riepilogo.data.percentualeAttuazione}
                sx={{ flexGrow: 1, height: 10, borderRadius: 5 }}
                aria-label="Percentuale di attuazione"
              />
              <Typography variant="h5" component="p">
                {riepilogo.data.percentualeAttuazione.toLocaleString("it-IT")}%
              </Typography>
            </Box>
            <Stack
              direction="row"
              spacing={1}
              useFlexGap
              sx={{ mt: 2, flexWrap: "wrap" }}
            >
              <Chip
                color="success"
                label={`Attuati ${riepilogo.data.complessivo.attuati}`}
              />
              <Chip
                color="warning"
                label={`Parziali ${riepilogo.data.complessivo.parziali}`}
              />
              <Chip
                color="error"
                label={`Non attuati ${riepilogo.data.complessivo.nonAttuati}`}
              />
              <Chip
                label={`Non applicabili ${riepilogo.data.complessivo.nonApplicabili}`}
              />
              <Chip
                variant="outlined"
                label={`Da valutare ${riepilogo.data.complessivo.nonValutati}`}
              />
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              Un requisito parzialmente attuato vale metà; i non applicabili non
              entrano nel calcolo.
            </Typography>
          </>
        )}
      </Paper>

      {!requisiti.data ? (
        <StatoCaricamento errore={requisiti.error} />
      ) : (
        <Box>
          {raggruppaPerMisura(requisiti.data).map((misura) => {
            const percentuale = riepilogo.data?.misure.find(
              (m) => m.codice === misura.codice,
            )?.percentualeAttuazione;
            return (
              <Accordion key={misura.codice} disableGutters>
                <AccordionSummary>
                  <Box sx={{ flexGrow: 1 }}>
                    <Typography sx={{ fontWeight: 600 }}>
                      {misura.codice} · {misura.categoria}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {misura.descrizione}
                    </Typography>
                  </Box>
                  {percentuale !== undefined && (
                    <Typography sx={{ ml: 2, whiteSpace: "nowrap" }}>
                      {percentuale.toLocaleString("it-IT")}%
                    </Typography>
                  )}
                </AccordionSummary>
                <AccordionDetails>
                  <Stack spacing={2}>
                    {misura.requisiti.map((r) => (
                      <RigaRequisito
                        key={`${r.id}-${r.stato}-${r.note}`}
                        soggettoId={soggetto.id}
                        requisito={r}
                        modificabile={modificabile}
                        onStorico={() => setStorico(r)}
                      />
                    ))}
                  </Stack>
                </AccordionDetails>
              </Accordion>
            );
          })}
        </Box>
      )}

      <StoricoAdempimento
        soggettoId={soggetto.id}
        requisito={storico}
        onChiudi={() => setStorico(null)}
      />
    </Stack>
  );
}

function RigaRequisito({
  soggettoId,
  requisito,
  modificabile,
  onStorico,
}: {
  soggettoId: number;
  requisito: Requisito;
  modificabile: boolean;
  onStorico: () => void;
}) {
  const valuta = useValuta(soggettoId);
  const [stato, setStato] = useState<StatoAdempimento | "">(
    requisito.stato ?? "",
  );
  const [note, setNote] = useState(requisito.note ?? "");
  const modificato =
    stato !== (requisito.stato ?? "") || note !== (requisito.note ?? "");

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Box sx={{ display: "flex", gap: 1, alignItems: "center", mb: 1 }}>
        <Typography sx={{ fontWeight: 600 }}>{requisito.codice}</Typography>
        <Chip
          size="small"
          color={coloreStato(requisito.stato)}
          label={
            requisito.stato ? etichetteStato[requisito.stato] : "Da valutare"
          }
        />
        {requisito.dataValutazione && (
          <Typography variant="body2" color="text.secondary">
            valutato il {data(requisito.dataValutazione)}
          </Typography>
        )}
        <Box sx={{ flexGrow: 1 }} />
        {requisito.stato && (
          <Button size="small" onClick={onStorico}>
            Storico
          </Button>
        )}
      </Box>
      <Typography variant="body2" sx={{ mb: 2 }}>
        {requisito.testo}
      </Typography>
      {modificabile ? (
        <Stack direction={{ xs: "column", md: "row" }} spacing={2}>
          <TextField
            select
            size="small"
            label="Stato"
            value={stato}
            onChange={(e) => setStato(e.target.value as StatoAdempimento)}
            sx={{ minWidth: 220 }}
          >
            {(Object.keys(etichetteStato) as StatoAdempimento[]).map((s) => (
              <MenuItem key={s} value={s}>
                {etichetteStato[s]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            label="Note ed evidenze"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            fullWidth
            slotProps={{ htmlInput: { maxLength: 1000 } }}
          />
          <Box>
            <Button
              variant="contained"
              disabled={!stato || !modificato || valuta.isPending}
              onClick={() =>
                stato &&
                valuta.mutate({
                  requisitoId: requisito.id,
                  stato,
                  note: note.trim() === "" ? null : note.trim(),
                })
              }
            >
              Salva
            </Button>
          </Box>
        </Stack>
      ) : (
        requisito.note && (
          <Typography variant="body2" color="text.secondary">
            Note: {requisito.note}
          </Typography>
        )
      )}
      {valuta.isError && (
        <Alert severity="error" sx={{ mt: 1 }}>
          {valuta.error.message}
        </Alert>
      )}
    </Paper>
  );
}

function StoricoAdempimento({
  soggettoId,
  requisito,
  onChiudi,
}: {
  soggettoId: number;
  requisito: Requisito | null;
  onChiudi: () => void;
}) {
  const versioni = useVersioniAdempimento(soggettoId, requisito?.id ?? null);
  return (
    <Dialog
      open={requisito !== null}
      onClose={onChiudi}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>Storico di {requisito?.codice}</DialogTitle>
      <DialogContent>
        {!versioni.data ? (
          <StatoCaricamento errore={versioni.error} />
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Versione</TableCell>
                <TableCell>Stato</TableCell>
                <TableCell>Valida dal</TableCell>
                <TableCell>Modificata da</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {versioni.data.map((v) => (
                <TableRow key={v.versione}>
                  <TableCell>{v.versione}</TableCell>
                  <TableCell>{etichetteStato[v.stato]}</TableCell>
                  <TableCell>{data(v.validoDa)}</TableCell>
                  <TableCell>{v.modificataDa ?? "prima valutazione"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
    </Dialog>
  );
}

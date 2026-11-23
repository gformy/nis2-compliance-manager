import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, Grid, MenuItem, Paper, TextField, Typography } from '@mui/material'
import { Controller, useForm } from 'react-hook-form'
import { ErroreApi } from '../api/client'
import { useAggiornaSoggetto, type Soggetto } from '../api/soggetti'
import { aForm, daForm, schemaAnagrafica, type ValoriAnagrafica } from './anagrafica'

const campiTesto: { nome: keyof ValoriAnagrafica; etichetta: string; larghezza: number }[] = [
  { nome: 'denominazione', etichetta: 'Denominazione', larghezza: 12 },
  { nome: 'indirizzoSedeLegale', etichetta: 'Indirizzo sede legale', larghezza: 12 },
  { nome: 'pec', etichetta: 'PEC', larghezza: 6 },
  { nome: 'emailFunzionale', etichetta: 'Email funzionale', larghezza: 6 },
  { nome: 'telefono', etichetta: 'Telefono', larghezza: 6 },
  { nome: 'numeroDipendenti', etichetta: 'Numero dipendenti', larghezza: 6 },
  { nome: 'fatturato', etichetta: 'Fatturato (€)', larghezza: 6 },
  { nome: 'bilancio', etichetta: 'Bilancio (€)', larghezza: 6 },
]

export function FormAnagrafica({ soggetto, solaLettura }: { soggetto: Soggetto; solaLettura: boolean }) {
  const aggiorna = useAggiornaSoggetto(soggetto.id)
  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<ValoriAnagrafica>({
    resolver: zodResolver(schemaAnagrafica),
    values: aForm(soggetto),
  })

  const salva = handleSubmit(async (valori) => {
    try {
      const aggiornato = await aggiorna.mutateAsync(daForm(valori))
      reset(aForm(aggiornato))
    } catch (errore) {
      // Errori di validazione del backend: si mostrano sotto il campo giusto
      if (errore instanceof ErroreApi && errore.problema?.errori) {
        for (const [campo, messaggio] of Object.entries(errore.problema.errori)) {
          setError(campo as keyof ValoriAnagrafica, { message: messaggio })
        }
      }
    }
  })

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Typography variant="h6" component="h2" gutterBottom>
        Anagrafica
      </Typography>
      <Box component="form" noValidate onSubmit={salva}>
        <Grid container spacing={2}>
          {campiTesto.map(({ nome, etichetta, larghezza }) => (
            <Grid key={nome} size={{ xs: 12, md: larghezza }}>
              <TextField
                label={etichetta}
                fullWidth
                slotProps={{ input: { readOnly: solaLettura } }}
                {...register(nome)}
                error={Boolean(errors[nome])}
                helperText={errors[nome]?.message}
              />
            </Grid>
          ))}
          <Grid size={{ xs: 12, md: 6 }}>
            {/* Il menu a tendina di MUI e' un componente controllato: si collega con Controller */}
            <Controller
              name="tipoSoggetto"
              control={control}
              render={({ field }) => (
                <TextField
                  select
                  label="Tipo di soggetto"
                  fullWidth
                  slotProps={{ input: { readOnly: solaLettura } }}
                  {...field}
                >
                  <MenuItem value="ESSENZIALE">Essenziale</MenuItem>
                  <MenuItem value="IMPORTANTE">Importante</MenuItem>
                </TextField>
              )}
            />
          </Grid>
        </Grid>

        {aggiorna.isError && !(aggiorna.error instanceof ErroreApi && aggiorna.error.problema?.errori) && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {aggiorna.error.message}
          </Alert>
        )}
        {aggiorna.isSuccess && !isDirty && (
          <Alert severity="success" sx={{ mt: 2 }}>
            Modifiche salvate: la versione precedente è nello storico.
          </Alert>
        )}

        {solaLettura ? (
          <Alert severity="info" sx={{ mt: 2 }}>
            Hai accesso in sola lettura a questo soggetto.
          </Alert>
        ) : (
          <Box sx={{ display: 'flex', gap: 1, mt: 3 }}>
            <Button type="submit" variant="contained" disabled={!isDirty || isSubmitting}>
              Salva
            </Button>
            <Button disabled={!isDirty || isSubmitting} onClick={() => reset()}>
              Annulla
            </Button>
          </Box>
        )}
      </Box>
    </Paper>
  )
}

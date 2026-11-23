import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Autocomplete, Box, Button, Chip, Stack, TextField } from '@mui/material'
import { Controller, useForm } from 'react-hook-form'
import { nomePaese, statiMembroUE, type DatiServizio } from '../../api/inventario'
import { schemaServizio, servizioDaForm, type ValoriServizio } from './formInventario'

/** Form del servizio, usato sia per la creazione sia per la modifica. */
export function CampiServizio({
  valori,
  solaLettura,
  etichettaSalva,
  errore,
  onSalva,
  onAnnulla,
}: {
  valori: ValoriServizio
  solaLettura: boolean
  etichettaSalva: string
  errore?: Error | null
  onSalva: (dati: DatiServizio) => Promise<unknown>
  onAnnulla?: () => void
}) {
  const {
    register,
    control,
    handleSubmit,
    reset,
    formState: { errors, isDirty, isSubmitting },
  } = useForm<ValoriServizio>({ resolver: zodResolver(schemaServizio), values: valori })

  const salva = handleSubmit(async (dati) => {
    await onSalva(servizioDaForm(dati)).catch(() => undefined)
  })

  const solaLetturaInput = { input: { readOnly: solaLettura } }

  return (
    <Box component="form" noValidate onSubmit={salva}>
      <Stack spacing={2}>
        <TextField
          label="Nome"
          fullWidth
          slotProps={solaLetturaInput}
          {...register('nome')}
          error={Boolean(errors.nome)}
          helperText={errors.nome?.message}
        />
        <TextField
          label="Descrizione"
          fullWidth
          multiline
          minRows={2}
          slotProps={solaLetturaInput}
          {...register('descrizione')}
          error={Boolean(errors.descrizione)}
          helperText={errors.descrizione?.message}
        />
        <TextField
          label="Categoria di rilevanza"
          fullWidth
          slotProps={solaLetturaInput}
          {...register('categoriaRilevanza')}
          error={Boolean(errors.categoriaRilevanza)}
          helperText={
            errors.categoriaRilevanza?.message ?? 'Facoltativa finché ACN non pubblica il modello di categorizzazione'
          }
        />
        <Controller
          name="statiMembro"
          control={control}
          render={({ field }) => (
            <Autocomplete
              multiple
              readOnly={solaLettura}
              options={[...statiMembroUE]}
              value={field.value}
              onChange={(_, valore) => field.onChange(valore)}
              getOptionLabel={(codice) => `${nomePaese(codice)} (${codice})`}
              renderValue={(selezionati, getItemProps) =>
                selezionati.map((codice, indice) => {
                  const { key, ...props } = getItemProps({ index: indice })
                  return <Chip key={key} label={nomePaese(codice)} size="small" {...props} />
                })
              }
              renderInput={(params) => <TextField {...params} label="Stati membri UE in cui è offerto" />}
            />
          )}
        />
        {errore && <Alert severity="error">{errore.message}</Alert>}
        {!solaLettura && (
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button type="submit" variant="contained" disabled={!isDirty || isSubmitting}>
              {etichettaSalva}
            </Button>
            <Button
              disabled={isSubmitting}
              onClick={() => {
                reset()
                onAnnulla?.()
              }}
            >
              Annulla
            </Button>
          </Box>
        )}
      </Stack>
    </Box>
  )
}

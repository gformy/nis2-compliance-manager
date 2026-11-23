import { z } from 'zod'
import { statiMembroUE, type DatiAsset, type DatiServizio } from '../../api/inventario'

/** Regole dei form, uguali ai vincoli di DatiAsset e DatiServizio nel backend. */
export const schemaAsset = z.object({
  nome: z.string().trim().min(1, 'Obbligatorio').max(255),
  tipoAsset: z.enum(['HARDWARE', 'SOFTWARE', 'FLUSSO_RETE', 'SERVIZIO_FORNITORE']),
  rilevante: z.boolean(),
})

export const schemaServizio = z.object({
  nome: z.string().trim().min(1, 'Obbligatorio').max(255),
  descrizione: z.string().max(500, 'Al massimo 500 caratteri'),
  categoriaRilevanza: z.string().max(50, 'Al massimo 50 caratteri'),
  statiMembro: z.array(z.enum(statiMembroUE)),
})

export type ValoriServizio = z.infer<typeof schemaServizio>

const testoONull = (valore: string) => (valore.trim() === '' ? null : valore.trim())

export const assetVuoto: DatiAsset = { nome: '', tipoAsset: 'HARDWARE', rilevante: false }

export const servizioVuoto: ValoriServizio = { nome: '', descrizione: '', categoriaRilevanza: '', statiMembro: ['IT'] }

export function servizioDaForm(valori: ValoriServizio): DatiServizio {
  return {
    nome: valori.nome.trim(),
    descrizione: testoONull(valori.descrizione),
    categoriaRilevanza: testoONull(valori.categoriaRilevanza),
    statiMembro: valori.statiMembro,
  }
}

export function servizioAForm(servizio: DatiServizio): ValoriServizio {
  return {
    nome: servizio.nome,
    descrizione: servizio.descrizione ?? '',
    categoriaRilevanza: servizio.categoriaRilevanza ?? '',
    statiMembro: servizio.statiMembro,
  }
}

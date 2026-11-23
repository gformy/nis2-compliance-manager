import { z } from 'zod'
import type { DatiSoggetto, Soggetto, VersioneSoggetto } from '../api/soggetti'

const numeroFacoltativo = z
  .string()
  .regex(/^(\d{1,13}([.,]\d{1,2})?)?$/, 'Numero non valido (al massimo 2 decimali)')

/**
 * Regole del form, uguali ai vincoli di DatiSoggetto nel backend.
 * Tutti i campi sono testo: la conversione in numeri e null avviene in daForm.
 */
export const schemaAnagrafica = z.object({
  denominazione: z.string().min(1, 'Obbligatoria').max(255),
  indirizzoSedeLegale: z.string().min(1, 'Obbligatorio').max(255),
  pec: z.email('PEC non valida').max(255),
  emailFunzionale: z.union([z.literal(''), z.email('Email non valida').max(255)]),
  telefono: z.string().regex(/^([0-9 +]{6,30})?$/, 'Solo cifre, spazi e +, da 6 a 30 caratteri'),
  fatturato: numeroFacoltativo,
  bilancio: numeroFacoltativo,
  numeroDipendenti: z.string().regex(/^\d{0,10}$/, 'Solo cifre'),
  tipoSoggetto: z.enum(['ESSENZIALE', 'IMPORTANTE']),
})

export type ValoriAnagrafica = z.infer<typeof schemaAnagrafica>

const testoONull = (valore: string) => (valore.trim() === '' ? null : valore.trim())
const numeroONull = (valore: string) => (valore.trim() === '' ? null : Number(valore.trim().replace(',', '.')))

export function daForm(valori: ValoriAnagrafica): DatiSoggetto {
  return {
    denominazione: valori.denominazione.trim(),
    indirizzoSedeLegale: valori.indirizzoSedeLegale.trim(),
    pec: valori.pec.trim(),
    emailFunzionale: testoONull(valori.emailFunzionale),
    telefono: testoONull(valori.telefono),
    fatturato: numeroONull(valori.fatturato),
    bilancio: numeroONull(valori.bilancio),
    numeroDipendenti: numeroONull(valori.numeroDipendenti),
    tipoSoggetto: valori.tipoSoggetto,
  }
}

export function aForm(soggetto: Soggetto): ValoriAnagrafica {
  const testo = (valore: string | number | null) => (valore === null ? '' : String(valore))
  return {
    denominazione: soggetto.denominazione,
    indirizzoSedeLegale: soggetto.indirizzoSedeLegale,
    pec: soggetto.pec,
    emailFunzionale: testo(soggetto.emailFunzionale),
    telefono: testo(soggetto.telefono),
    fatturato: testo(soggetto.fatturato),
    bilancio: testo(soggetto.bilancio),
    numeroDipendenti: testo(soggetto.numeroDipendenti),
    tipoSoggetto: soggetto.tipoSoggetto,
  }
}

const etichette: Record<keyof Omit<VersioneSoggetto, 'versione' | 'validoDa' | 'validoA' | 'modificataDa'>, string> = {
  denominazione: 'Denominazione',
  indirizzoSedeLegale: 'Sede legale',
  pec: 'PEC',
  emailFunzionale: 'Email',
  telefono: 'Telefono',
  fatturato: 'Fatturato',
  bilancio: 'Bilancio',
  numeroDipendenti: 'Dipendenti',
}

/** Campi cambiati tra una versione e quella precedente, per lo storico. */
export function campiModificati(versione: VersioneSoggetto, precedente: VersioneSoggetto | undefined): string[] {
  if (!precedente) {
    return []
  }
  return (Object.keys(etichette) as (keyof typeof etichette)[])
    .filter((campo) => versione[campo] !== precedente[campo])
    .map((campo) => etichette[campo])
}

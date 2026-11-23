import { describe, expect, it } from 'vitest'
import type { VersioneSoggetto } from '../api/soggetti'
import { campiModificati, daForm, schemaAnagrafica, type ValoriAnagrafica } from './anagrafica'

const valori: ValoriAnagrafica = {
  denominazione: '  ASL Roma 2 ',
  indirizzoSedeLegale: 'Via Filippo Meda 35, Roma',
  pec: 'protocollo@pec.aslroma2.it',
  emailFunzionale: '',
  telefono: '',
  fatturato: '1250000,50',
  bilancio: '',
  numeroDipendenti: '7000',
  tipoSoggetto: 'ESSENZIALE',
}

describe('daForm', () => {
  it('converte vuoti in null, numeri con la virgola e toglie gli spazi', () => {
    expect(daForm(valori)).toEqual({
      denominazione: 'ASL Roma 2',
      indirizzoSedeLegale: 'Via Filippo Meda 35, Roma',
      pec: 'protocollo@pec.aslroma2.it',
      emailFunzionale: null,
      telefono: null,
      fatturato: 1250000.5,
      bilancio: null,
      numeroDipendenti: 7000,
      tipoSoggetto: 'ESSENZIALE',
    })
  })
})

describe('schemaAnagrafica', () => {
  it('accetta dati validi e rifiuta una PEC sbagliata', () => {
    expect(schemaAnagrafica.safeParse(valori).success).toBe(true)
    const errore = schemaAnagrafica.safeParse({ ...valori, pec: 'non-valida' })
    expect(errore.success).toBe(false)
    expect(errore.error?.issues[0]?.path).toEqual(['pec'])
  })
})

describe('campiModificati', () => {
  const base: VersioneSoggetto = {
    versione: 1,
    denominazione: 'Ente',
    indirizzoSedeLegale: 'Via Roma 1',
    pec: 'ente@pec.it',
    emailFunzionale: null,
    telefono: null,
    fatturato: null,
    bilancio: null,
    numeroDipendenti: 10,
    validoDa: '2026-01-01T10:00:00',
    validoA: '2026-02-01T10:00:00',
    modificataDa: null,
  }

  it('elenca solo i campi cambiati rispetto alla versione precedente', () => {
    const successiva = { ...base, versione: 2, pec: 'nuova@pec.it', numeroDipendenti: 12 }
    expect(campiModificati(successiva, base)).toEqual(['PEC', 'Dipendenti'])
  })

  it('per la prima versione non segnala modifiche', () => {
    expect(campiModificati(base, undefined)).toEqual([])
  })
})

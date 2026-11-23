import { describe, expect, it } from 'vitest'
import { coloreScadenza, daInputLocale, perInputLocale, tempoResiduo } from './tempo'

const adesso = Date.parse('2026-03-10T08:00:00Z')

describe('tempoResiduo', () => {
  it('mostra ore e minuti sotto il giorno', () => {
    expect(tempoResiduo('2026-03-10T13:20:00Z', adesso)).toBe('mancano 5 h 20 min')
  })
  it('mostra giorni e ore oltre il giorno', () => {
    expect(tempoResiduo('2026-03-12T11:00:00Z', adesso)).toBe('mancano 2 g 3 h')
  })
  it('dice da quanto e scaduta', () => {
    expect(tempoResiduo('2026-03-10T07:15:00Z', adesso)).toBe('scaduta da 45 min')
  })
})

describe('conversioni per datetime-local', () => {
  it('andata e ritorno conservano l istante al minuto', () => {
    const iso = '2026-03-10T08:30:00.000Z'
    expect(daInputLocale(perInputLocale(iso))).toBe(iso)
  })
})

describe('coloreScadenza', () => {
  it('segnala in rosso le scadute e in giallo i ritardi', () => {
    expect(coloreScadenza('SCADUTA')).toBe('error')
    expect(coloreScadenza('TRASMESSA_IN_RITARDO')).toBe('warning')
    expect(coloreScadenza('TRASMESSA')).toBe('success')
  })
})

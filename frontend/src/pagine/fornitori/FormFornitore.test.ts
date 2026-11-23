import { describe, expect, it } from 'vitest'
import { erroriFornitore } from './validazioneFornitore'

const base = { codiceFiscale: '01234567890', denominazione: 'Fornitore S.r.l.', paeseSedeLegale: 'IT', cpv: [] }

describe('erroriFornitore', () => {
  it('accetta una partita IVA italiana valida', () => {
    expect(erroriFornitore(base, true)).toEqual({})
  })

  it('per un fornitore italiano rifiuta un codice che non è partita IVA né codice fiscale', () => {
    expect(erroriFornitore({ ...base, codiceFiscale: 'ABC123' }, true).codiceFiscale).toBeDefined()
  })

  it('per un fornitore estero accetta la partita IVA del suo paese', () => {
    expect(erroriFornitore({ ...base, paeseSedeLegale: 'DE', codiceFiscale: 'DE123456789' }, true)).toEqual({})
  })

  it('in modifica non ricontrolla il codice fiscale, che non cambia', () => {
    expect(erroriFornitore({ ...base, codiceFiscale: 'x' }, false)).toEqual({})
  })
})

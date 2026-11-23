import { describe, expect, it } from 'vitest'
import { puoModificare, type Profilo } from './profilo'

const profilo = (soggetti: Profilo['soggetti'], amministratore = false): Profilo => ({
  utente: 'mario',
  amministratore,
  soggetti,
})

describe('puoModificare', () => {
  it('operatore e referente modificano solo i propri soggetti', () => {
    const mario = profilo([
      { soggettoId: 1, ruolo: 'OPERATORE' },
      { soggettoId: 2, ruolo: 'REFERENTE_NIS' },
    ])
    expect(puoModificare(mario, 1)).toBe(true)
    expect(puoModificare(mario, 2)).toBe(true)
    expect(puoModificare(mario, 3)).toBe(false)
  })

  it('il revisore non modifica, l amministratore modifica tutto', () => {
    expect(puoModificare(profilo([{ soggettoId: 1, ruolo: 'REVISORE' }]), 1)).toBe(false)
    expect(puoModificare(profilo([], true), 99)).toBe(true)
  })

  it('senza profilo caricato non si mostra nulla di modificabile', () => {
    expect(puoModificare(undefined, 1)).toBe(false)
  })
})

import { describe, expect, it } from 'vitest'
import type { Attivita, Persona } from '../../api/formazione'
import { colorePercentuale, esitiAmmessi, personeAmmesse } from './formazione'

const persona = (id: number, categoria: Persona['categoria'], dataCessazione: string | null = null): Persona => ({
  id,
  nome: `p${id}`,
  categoria,
  ruolo: null,
  dataCessazione,
  ultimaFormazione: null,
  inRegola: false,
})

const attivita = (destinatari: Attivita['destinatari'], modalitaVerifica: string | null = null): Attivita => ({
  id: 1,
  titolo: 't',
  contenuti: 'c',
  destinatari,
  dataPrevista: '2026-11-01',
  durataOre: 2,
  modalitaVerifica,
  partecipanti: 0,
  stato: 'PIANIFICATA',
})

describe('personeAmmesse', () => {
  const persone = [persona(1, 'ORGANO'), persona(2, 'PERSONALE'), persona(3, 'PERSONALE', '2026-01-01')]
  it('filtra per destinatari e per cessazione', () => {
    expect(personeAmmesse(persone, attivita('TUTTI'), '2026-11-24').map((p) => p.id)).toEqual([1, 2])
    expect(personeAmmesse(persone, attivita('ORGANO'), '2026-11-24').map((p) => p.id)).toEqual([1])
    expect(personeAmmesse(persone, attivita('TUTTI'), '2025-12-01').map((p) => p.id)).toEqual([1, 2, 3])
  })
})

describe('esitiAmmessi e colori', () => {
  it('chiede l esito solo se c e una verifica', () => {
    expect(esitiAmmessi(attivita('TUTTI', 'Test'))).toEqual(['SUPERATA', 'NON_SUPERATA'])
    expect(esitiAmmessi(attivita('TUTTI'))).toEqual(['NON_PREVISTA'])
    expect(colorePercentuale(100, 3)).toBe('success')
    expect(colorePercentuale(0, 2)).toBe('error')
    expect(colorePercentuale(0, 0)).toBe('inherit')
  })
})

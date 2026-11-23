import { describe, expect, it } from 'vitest'
import type { Confronto } from '../../api/invii'
import { nessunaDifferenza, ordinePerConfronto } from './confronto'

const vuote = { aggiunti: [], rimossi: [], modificati: [] }

describe('ordinePerConfronto', () => {
  const date = new Map([
    [1, '2026-01-10T10:00:00Z'],
    [2, '2026-06-01T10:00:00Z'],
  ])
  it('mette prima l invio piu vecchio qualunque sia l ordine di selezione', () => {
    expect(ordinePerConfronto([2, 1], date)).toEqual([1, 2])
    expect(ordinePerConfronto([1, 2], date)).toEqual([1, 2])
  })
  it('serve esattamente una coppia', () => {
    expect(ordinePerConfronto([1], date)).toBeNull()
  })
})

describe('nessunaDifferenza', () => {
  it('riconosce due profili identici', () => {
    const c = { anagrafica: [], elementi: vuote, fornitori: vuote, adempimenti: vuote } as unknown as Confronto
    expect(nessunaDifferenza(c)).toBe(true)
    expect(nessunaDifferenza({ ...c, anagrafica: [{ campo: 'pec', prima: 'a', dopo: 'b' }] })).toBe(false)
  })
})

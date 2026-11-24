import { describe, expect, it } from 'vitest'
import { descriviQuota, quotaPiena, quotaUsata } from './quota'

const piano = (maxSoggetti: number | null) => ({ codice: 'X', nome: 'X', maxSoggetti, prezzoMensileSoggetto: 10 })

describe('quota del piano', () => {
  it('calcola uso e saturazione', () => {
    expect(quotaUsata({ soggetti: 3, piano: piano(10) })).toBe(30)
    expect(quotaPiena({ soggetti: 1, piano: piano(1) })).toBe(true)
    expect(quotaPiena({ soggetti: 5, piano: piano(null) })).toBe(false)
    expect(quotaUsata({ soggetti: 5, piano: piano(null) })).toBeNull()
    expect(descriviQuota({ soggetti: 2, piano: piano(10) })).toBe('2 di 10 soggetti')
  })
})

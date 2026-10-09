import { formatDate, formatPhone, initials, splitPets, summarizePets } from './format'

describe('formatPhone', () => {
  it('formats exactly ten digits', () => {
    expect(formatPhone('6085551023')).toBe('(608) 555-1023')
  })

  it('leaves other lengths and shapes untouched', () => {
    expect(formatPhone('5551023')).toBe('5551023')
    expect(formatPhone('16085551023')).toBe('16085551023')
    expect(formatPhone('')).toBe('')
  })
})

describe('splitPets', () => {
  it('returns the first two names and how many are hidden', () => {
    const pets = [{ name: 'Leo' }, { name: 'Basil' }, { name: 'Rosy' }, { name: 'Jewel' }]
    expect(splitPets(pets)).toEqual({ names: ['Leo', 'Basil'], hiddenCount: 2 })
  })

  it('hides nothing when there are two or fewer', () => {
    expect(splitPets([{ name: 'Leo' }])).toEqual({ names: ['Leo'], hiddenCount: 0 })
    expect(splitPets([])).toEqual({ names: [], hiddenCount: 0 })
  })
})

describe('summarizePets', () => {
  it('lists up to two names', () => {
    expect(summarizePets([{ name: 'Leo' }, { name: 'Basil' }])).toBe('Leo, Basil')
  })

  it('counts the rest', () => {
    expect(summarizePets([{ name: 'Leo' }, { name: 'Basil' }, { name: 'Rosy' }, { name: 'Jewel' }])).toBe(
      'Leo, Basil +2',
    )
  })

  it('is empty when there are no pets', () => {
    expect(summarizePets([])).toBe('')
  })
})

describe('initials', () => {
  it('uses the first letter of each name, uppercased', () => {
    expect(initials('George', 'Franklin')).toBe('GF')
    expect(initials('maria', 'de la Cruz')).toBe('MD')
  })

  it('copes with empty names', () => {
    expect(initials('', 'Franklin')).toBe('F')
  })
})

describe('formatDate', () => {
  it('formats an ISO date without shifting the day', () => {
    expect(formatDate('2020-09-07')).toBe('Sep 7, 2020')
    expect(formatDate('2026-01-01')).toBe('Jan 1, 2026')
  })

  it('returns anything that is not an ISO date unchanged', () => {
    expect(formatDate('soon')).toBe('soon')
  })
})

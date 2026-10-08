import { formatPhone, summarizePets } from './format'

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

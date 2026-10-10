import { trimOwner, validateOwner, validateOwnerField } from './ownerValidation'

const valid = { firstName: 'George', lastName: 'Franklin', address: '110 W. Liberty St.', city: 'Madison', telephone: '6085551023' }

describe('validateOwner', () => {
  it('accepts a valid owner', () => {
    expect(validateOwner(valid)).toEqual({})
  })

  it('requires every field', () => {
    const errors = validateOwner({ firstName: '', lastName: ' ', address: '', city: '', telephone: '' })
    expect(Object.keys(errors)).toEqual(['firstName', 'lastName', 'address', 'city', 'telephone'])
    expect(errors.firstName).toBe('First name is required.')
  })

  it('accepts names the API accepts', () => {
    expect(validateOwnerField('firstName', 'Mary-Jane')).toBeUndefined()
    expect(validateOwnerField('lastName', "O'Neil")).toBeUndefined()
    expect(validateOwnerField('lastName', 'de la Cruz')).toBeUndefined()
    expect(validateOwnerField('firstName', 'José')).toBeUndefined()
    expect(validateOwnerField('lastName', 'Smith Jr.')).toBeUndefined()
  })

  it('rejects names with digits or a fourth word', () => {
    expect(validateOwnerField('firstName', 'G3orge')).toMatch(/only contain letters/)
    expect(validateOwnerField('firstName', 'a b c d')).toMatch(/only contain letters/)
    expect(validateOwnerField('firstName', "Mary-Jane O'Neil")).toMatch(/only contain letters/) // 4 words: the API rejects it too
    expect(validateOwnerField('firstName', 'George.')).toMatch(/only contain letters/)
  })

  it('enforces maximum lengths', () => {
    expect(validateOwnerField('firstName', 'a'.repeat(31))).toBe('First name must be 30 characters or fewer.')
    expect(validateOwnerField('address', 'a'.repeat(256))).toBe('Address must be 255 characters or fewer.')
    expect(validateOwnerField('city', 'a'.repeat(81))).toBe('City must be 80 characters or fewer.')
  })

  describe('telephone', () => {
    const message = 'Telephone must be exactly 10 digits, with no spaces or dashes.'

    it('accepts exactly 10 digits', () => {
      expect(validateOwnerField('telephone', '6085551023')).toBeUndefined()
      expect(validateOwnerField('telephone', ' 6085551023 ')).toBeUndefined()
    })

    it('rejects fewer than 10 digits', () => {
      expect(validateOwnerField('telephone', '5551234')).toBe(message)
      expect(validateOwnerField('telephone', '123456789')).toBe(message)
    })

    it('rejects more than 10 digits', () => {
      expect(validateOwnerField('telephone', '60855510231')).toBe(message)
      expect(validateOwnerField('telephone', '1'.repeat(21))).toBe(message)
    })

    it('rejects anything that is not all digits', () => {
      expect(validateOwnerField('telephone', '608-555-1023')).toBe(message)
      expect(validateOwnerField('telephone', '(608) 5551023')).toBe(message)
      expect(validateOwnerField('telephone', '608555102a')).toBe(message)
    })

    it('still reports an empty telephone as required', () => {
      expect(validateOwnerField('telephone', '')).toBe('Telephone is required.')
      expect(validateOwnerField('telephone', '   ')).toBe('Telephone is required.')
    })
  })
})

describe('trimOwner', () => {
  it('trims every field', () => {
    expect(trimOwner({ firstName: ' A ', lastName: ' B ', address: ' C ', city: ' D ', telephone: ' 6085551023 ' })).toEqual({
      firstName: 'A',
      lastName: 'B',
      address: 'C',
      city: 'D',
      telephone: '6085551023',
    })
  })
})

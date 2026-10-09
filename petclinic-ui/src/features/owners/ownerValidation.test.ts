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
    expect(validateOwnerField('telephone', '1'.repeat(21))).toBe('Telephone must be 20 characters or fewer.')
  })

  it('requires digits only for telephone', () => {
    expect(validateOwnerField('telephone', '608-555-1023')).toBe('Telephone must contain digits only.')
  })
})

describe('trimOwner', () => {
  it('trims every field', () => {
    expect(trimOwner({ firstName: ' A ', lastName: ' B ', address: ' C ', city: ' D ', telephone: ' 1 ' })).toEqual({
      firstName: 'A',
      lastName: 'B',
      address: 'C',
      city: 'D',
      telephone: '1',
    })
  })
})

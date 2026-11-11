export const MAX_NOTES_LENGTH = 2000

// Match Java String.strip()/Character.isWhitespace, not JavaScript trim()
// (which also removes non-breaking spaces and the byte-order mark).
const edgeWhitespace = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+|[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]+$/g

export function normalizeNotes(notes) {
  if (notes == null) {
    return null
  }

  return notes.replace(edgeWhitespace, '') || null
}

export function getNotesLength(notes) {
  // String.length counts UTF-16 code units, just like Java String.length().
  return normalizeNotes(notes)?.length ?? 0
}

export function validateNotes(notes) {
  return getNotesLength(notes) > MAX_NOTES_LENGTH
    ? `Notes must be at most ${MAX_NOTES_LENGTH} characters`
    : ''
}

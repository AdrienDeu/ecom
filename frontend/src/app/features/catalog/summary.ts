/** First paragraph of a description, cut on a word boundary: the teaser shown in lists. */
export function summary(description: string | null | undefined, max = 160): string {
  const first = (description ?? '').split(/\n\s*\n/)[0].replace(/\s+/g, ' ').trim();
  if (first.length <= max) {
    return first;
  }
  return first.slice(0, first.lastIndexOf(' ', max)).replace(/[,;:.]$/, '') + '…';
}

import { summary } from './summary';

describe('summary', () => {
  it('keeps the first paragraph only', () => {
    expect(summary('Deux jours pour auditer.\n\n• Revue des comptes')).toBe('Deux jours pour auditer.');
  });

  it('cuts long text on a word boundary', () => {
    expect(summary('un deux trois quatre', 12)).toBe('un deux…');
  });

  it('handles a missing description', () => {
    expect(summary(null)).toBe('');
  });
});

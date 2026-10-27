/**
 * Shop identity: the first file a shop branch (shop/<name>) customises. The platform itself only uses these
 * values, never a hard-coded name. Provisional name for this shop.
 */
export const BRAND = {
  name: 'Relais',
  tagline: 'Conseil et matériel informatique pour les entreprises',
  email: 'contact@relais.example',
  /** Answer delay promised after an order of consulting or a quote request. */
  responseTime: '2 jours ouvrés',
} as const;

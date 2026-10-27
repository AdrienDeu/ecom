/**
 * Shop identity: the first file a shop branch (shop/<name>) customises. The platform itself only uses these
 * values, never a hard-coded name.
 */
export const BRAND = {
  name: 'ecom',
  tagline: 'Boutique en ligne',
  email: 'contact@example.com',
  /** Answer delay promised after an order of a service or a quote request. */
  responseTime: '2 jours ouvrés',
} as const;

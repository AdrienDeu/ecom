import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from './errors';

describe('errorMessage', () => {
  it('translates business error codes', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'INSUFFICIENT_STOCK' } });
    expect(errorMessage(error)).toContain('plus disponible');
  });

  it('falls back on the HTTP status', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 403 }))).toContain('droits');
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('injoignable');
    expect(errorMessage(new HttpErrorResponse({ status: 409, error: { code: 'UNKNOWN' } }))).toContain('réessayer');
  });

  it('handles non-HTTP errors', () => {
    expect(errorMessage(new Error('boom'))).toContain('inattendue');
  });
});

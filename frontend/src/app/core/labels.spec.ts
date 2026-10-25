import { isCancellable, ORDER_STATUS_LABELS, ORDER_STATUSES } from './labels';

describe('order labels', () => {
  it('has a label for every status', () => {
    expect(ORDER_STATUSES).toHaveLength(7);
    ORDER_STATUSES.forEach((status) => expect(ORDER_STATUS_LABELS[status]).toBeTruthy());
  });

  it('only lets unpaid orders be cancelled', () => {
    expect(ORDER_STATUSES.filter(isCancellable)).toEqual(['CREATED', 'PAYMENT_PENDING']);
  });
});

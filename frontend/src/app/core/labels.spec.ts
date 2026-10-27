import { isCancellable, ORDER_STATUS_LABELS, ORDER_STATUSES, orderStatusFilterLabel, orderStatusLabel } from './labels';

describe('order labels', () => {
  it('has a label for every status', () => {
    expect(ORDER_STATUSES).toHaveLength(7);
    ORDER_STATUSES.forEach((status) => expect(ORDER_STATUS_LABELS[status]).toBeTruthy());
  });

  it('names fulfilment steps after what was sold', () => {
    expect(orderStatusLabel({ status: 'SHIPPED', servicesOnly: false })).toBe('Expédiée');
    expect(orderStatusLabel({ status: 'SHIPPED', servicesOnly: true })).toBe('Planifiée');
    expect(orderStatusLabel({ status: 'DELIVERED', servicesOnly: true })).toBe('Réalisée');
    expect(orderStatusLabel({ status: 'PAID', servicesOnly: true })).toBe('Payée');
    expect(orderStatusFilterLabel('SHIPPED')).toBe('Expédiée / planifiée');
    expect(orderStatusFilterLabel('PAID')).toBe('Payée');
  });

  it('only lets unpaid orders be cancelled', () => {
    expect(ORDER_STATUSES.filter(isCancellable)).toEqual(['CREATED', 'PAYMENT_PENDING']);
  });
});

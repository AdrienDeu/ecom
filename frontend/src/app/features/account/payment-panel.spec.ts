import { PaymentView } from '../../api/models/payment-view';
import { captureMessage } from './payment-panel';

describe('captureMessage', () => {
  const payment = (status: PaymentView['status']) => ({ status }) as PaymentView;

  it('says nothing once captured', () => {
    expect(captureMessage(payment('CAPTURED'))).toBeNull();
  });

  it('explains declined, pending and no longer payable payments', () => {
    expect(captureMessage(payment('FAILED'))).toContain('refusé');
    expect(captureMessage(payment('PENDING_APPROVAL'))).toContain('en cours');
    expect(captureMessage(payment('CANCELLED'))).toContain("n'est plus payable");
    expect(captureMessage(payment('REFUNDED'))).toContain("n'est plus payable");
  });
});

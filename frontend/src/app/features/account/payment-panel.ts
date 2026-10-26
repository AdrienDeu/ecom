import {
  Component,
  ElementRef,
  inject,
  input,
  OnDestroy,
  output,
  resource,
  signal,
  viewChild,
  effect,
} from '@angular/core';
import { Api } from '../../api/api';
import { paymentCapture } from '../../api/fn/payment-controller/payment-capture';
import { paymentMethods } from '../../api/fn/payment-controller/payment-methods';
import { paymentStart } from '../../api/fn/payment-controller/payment-start';
import { PaymentView } from '../../api/models/payment-view';
import { errorMessage } from '../../core/errors';
import { loadPaypalSdk, PaypalButtons } from '../../core/paypal-sdk';

/** Outcome of a capture, as a message for the customer; null when the order is paid. */
export function captureMessage(payment: PaymentView): string | null {
  switch (payment.status) {
    case 'CAPTURED':
      return null;
    case 'PENDING_APPROVAL':
      return 'Votre paiement est en cours de traitement par PayPal, la commande sera mise à jour dès sa confirmation.';
    case 'FAILED':
      return 'Le paiement a été refusé. Vous pouvez réessayer, éventuellement avec un autre moyen de paiement.';
    case 'CANCELLED':
    case 'REFUNDED':
      return "La commande n'est plus payable (annulée ou expirée). Aucun montant ne vous a été prélevé.";
    default:
      return 'Le paiement n’a pas pu aboutir, veuillez réessayer.';
  }
}

/**
 * PayPal buttons for an unpaid order. The backend creates the PayPal order (the amount is never sent from here)
 * and captures it once the customer approved it in PayPal's popup.
 */
@Component({
  selector: 'app-payment-panel',
  template: `
    <section class="card stack" aria-labelledby="payment">
      <h2 id="payment">Paiement</h2>
      @if (message(); as m) {
        <p class="alert" role="status">{{ m }}</p>
      }
      @if (methods.error() || sdkError()) {
        <p class="alert error">
          Le paiement en ligne est momentanément indisponible, veuillez réessayer plus tard.
        </p>
      } @else if (methods.value(); as list) {
        @if (list.length === 0) {
          <p class="muted">Aucun moyen de paiement n'est disponible pour le moment.</p>
        }
      } @else {
        <p class="muted">Chargement…</p>
      }
      @if (busy()) {
        <p class="muted" role="status">Validation du paiement…</p>
      }
      <div #buttons [hidden]="busy()"></div>
    </section>
  `,
  styles: `
    h2 {
      font-size: 1rem;
      margin: 0;
    }
    .card {
      max-width: 28rem;
    }
  `,
})
export class PaymentPanel implements OnDestroy {
  private readonly api = inject(Api);

  readonly orderId = input.required<string>();
  /** Emitted once the payment is captured, or settled in any other way the order page must reflect. */
  readonly settled = output<void>();

  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly sdkError = signal(false);
  private readonly container = viewChild.required<ElementRef<HTMLElement>>('buttons');

  protected readonly methods = resource({ loader: () => this.api.invoke(paymentMethods) });

  private paymentId: string | null = null;
  private buttons: PaypalButtons | null = null;

  constructor() {
    effect(() => {
      const paypal = this.methods.value()?.find((method) => method.id === 'paypal');
      if (paypal && !this.buttons) {
        void this.renderPaypal(paypal.settings['clientId'], paypal.settings['currency']);
      }
    });
  }

  ngOnDestroy(): void {
    void this.buttons?.close().catch(() => undefined);
  }

  private async renderPaypal(clientId: string, currency: string): Promise<void> {
    try {
      const sdk = await loadPaypalSdk(clientId, currency);
      this.buttons = sdk.Buttons({
        style: { layout: 'vertical', label: 'pay' },
        createOrder: async () => {
          this.message.set(null);
          try {
            const payment = await this.api.invoke(paymentStart, {
              body: { orderId: this.orderId(), provider: 'paypal' },
            });
            this.paymentId = payment.id;
            return payment.providerReference!;
          } catch (error) {
            this.message.set(errorMessage(error));
            throw error;
          }
        },
        onApprove: async () => {
          if (this.paymentId) {
            await this.capture(this.paymentId);
          }
        },
        onCancel: () =>
          this.message.set('Paiement annulé. Vous pouvez reprendre quand vous le souhaitez.'),
        onError: () =>
          this.message.set(this.message() ?? 'Le paiement n’a pas pu aboutir, veuillez réessayer.'),
      });
      await this.buttons.render(this.container().nativeElement);
    } catch {
      this.sdkError.set(true);
    }
  }

  private async capture(paymentId: string): Promise<void> {
    this.busy.set(true);
    try {
      const payment = await this.api.invoke(paymentCapture, { id: paymentId });
      this.message.set(captureMessage(payment));
      this.settled.emit();
    } catch (error) {
      this.message.set(errorMessage(error));
    } finally {
      this.busy.set(false);
    }
  }
}

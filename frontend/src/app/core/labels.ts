import { OrderView } from '../api/models/order-view';
import { ProductView } from '../api/models/product-view';
import { QuoteRequestView } from '../api/models/quote-request-view';

export type OrderStatus = OrderView['status'];

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  CREATED: 'En attente de paiement',
  PAYMENT_PENDING: 'Paiement en cours',
  PAID: 'Payée',
  SHIPPED: 'Expédiée',
  DELIVERED: 'Livrée',
  CANCELLED: 'Annulée',
  REFUNDED: 'Remboursée',
};

export const ORDER_STATUSES = Object.keys(ORDER_STATUS_LABELS) as OrderStatus[];

/** An order made only of services is scheduled and performed, not shipped and delivered. */
const SERVICE_ORDER_STATUS_LABELS: Partial<Record<OrderStatus, string>> = {
  SHIPPED: 'Planifiée',
  DELIVERED: 'Réalisée',
};

export function orderStatusLabel(order: Pick<OrderView, 'status' | 'servicesOnly'>): string {
  return (order.servicesOnly && SERVICE_ORDER_STATUS_LABELS[order.status]) || ORDER_STATUS_LABELS[order.status];
}

/** Label of a status filter, which covers both kinds of orders. */
export function orderStatusFilterLabel(status: OrderStatus): string {
  const service = SERVICE_ORDER_STATUS_LABELS[status];
  return service ? `${ORDER_STATUS_LABELS[status]} / ${service.toLowerCase()}` : ORDER_STATUS_LABELS[status];
}

/** Colour family of a status pill: waiting on someone, under way, done, or closed. */
export type StatusTone = 'pending' | 'active' | 'done' | 'closed';

const ORDER_STATUS_TONES: Record<OrderStatus, StatusTone> = {
  CREATED: 'pending',
  PAYMENT_PENDING: 'pending',
  PAID: 'active',
  SHIPPED: 'active',
  DELIVERED: 'done',
  CANCELLED: 'closed',
  REFUNDED: 'closed',
};

export function orderStatusTone(status: OrderStatus): StatusTone {
  return ORDER_STATUS_TONES[status];
}

/** Unpaid orders can still be cancelled by the customer. */
export function isCancellable(status: OrderStatus): boolean {
  return status === 'CREATED' || status === 'PAYMENT_PENDING';
}

export type ProductKind = ProductView['kind'];

export type QuoteStatus = QuoteRequestView['status'];

export const QUOTE_STATUS_LABELS: Record<QuoteStatus, string> = {
  NEW: 'Nouvelle',
  IN_PROGRESS: 'En cours',
  CLOSED: 'Traitée',
};

export const QUOTE_STATUSES = Object.keys(QUOTE_STATUS_LABELS) as QuoteStatus[];

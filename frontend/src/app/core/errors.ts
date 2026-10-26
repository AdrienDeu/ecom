import { HttpErrorResponse } from '@angular/common/http';

/** Business error codes returned by the API (`ProblemDetail.code`). */
const BUSINESS_MESSAGES: Record<string, string> = {
  INSUFFICIENT_STOCK: "Un article n'est plus disponible en quantité suffisante.",
  PRODUCT_UNAVAILABLE: "Un article de votre panier n'est plus vendu.",
  EMPTY_CART: 'Votre panier est vide.',
  CART_FULL: 'Votre panier contient trop de produits différents.',
  QUANTITY_TOO_HIGH: 'Quantité maximale atteinte pour ce produit.',
  INVALID_STATUS_TRANSITION: "Cette action n'est plus possible pour cette commande.",
  TOO_MANY_ADDRESSES: "Vous avez atteint le nombre maximal d'adresses.",
  STOCK_BELOW_RESERVED: 'Le stock ne peut pas être inférieur aux unités déjà réservées.',
  CONCURRENT_MODIFICATION: 'Les données ont été modifiées entre-temps, veuillez réessayer.',
  CONFLICT: 'Cette valeur existe déjà (SKU ou slug en double ?).',
  ORDER_ALREADY_PAID: 'Cette commande est déjà payée.',
  ORDER_NOT_PAYABLE: "Cette commande n'est plus payable (annulée ou expirée).",
  PAYMENT_REJECTED: 'Le paiement a été refusé par le prestataire.',
  PAYMENT_PROVIDER_UNAVAILABLE: 'Le service de paiement est momentanément indisponible, veuillez réessayer.',
  UNSUPPORTED_PAYMENT_METHOD: "Ce moyen de paiement n'est pas disponible.",
  INVALID_PAYMENT_STATE: "Cette opération n'est plus possible pour ce paiement.",
  REFUND_EXCEEDS_PAYMENT: 'Le montant dépasse ce qui reste à rembourser.',
  REFUND_REJECTED: 'Le remboursement a été refusé par le prestataire.',
};

export function errorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return 'Une erreur inattendue est survenue.';
  }
  const code = (error.error as { code?: string } | null)?.code;
  if (code && BUSINESS_MESSAGES[code]) {
    return BUSINESS_MESSAGES[code];
  }
  switch (error.status) {
    case 0:
      return 'Serveur injoignable, vérifiez votre connexion.';
    case 400:
      return 'Certaines informations sont invalides.';
    case 401:
      return 'Veuillez vous connecter.';
    case 403:
      return "Vous n'avez pas les droits nécessaires.";
    case 404:
      return 'Élément introuvable.';
    case 503:
      return 'Service momentanément indisponible, veuillez réessayer.';
    default:
      return 'Une erreur est survenue, veuillez réessayer.';
  }
}

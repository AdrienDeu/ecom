package org.ecom.quote;

public enum QuoteStatus {
    /** Just received, nobody answered yet. */
    NEW,
    /** The customer has been contacted. */
    IN_PROGRESS,
    /** Answered: proposal sent, accepted or declined. */
    CLOSED
}

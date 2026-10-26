package org.ecom.inventory;

import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Version;

@Entity
class Stock {

    @Id
    private UUID productId;

    private int onHand;

    private int reserved;

    /** Untracked stock (services) is always available and never reserved. */
    private boolean tracked;

    @Version
    private long version;

    protected Stock() {
    }

    Stock(UUID productId, int onHand, boolean tracked) {
        this.productId = productId;
        this.onHand = onHand;
        this.tracked = tracked;
    }

    int available() {
        return tracked ? onHand - reserved : Integer.MAX_VALUE;
    }

    boolean isTracked() {
        return tracked;
    }

    UUID getProductId() {
        return productId;
    }

    int getOnHand() {
        return onHand;
    }

    int getReserved() {
        return reserved;
    }

    void setOnHand(int onHand) {
        this.onHand = onHand;
    }

}

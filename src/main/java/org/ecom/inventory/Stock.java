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

    @Version
    private long version;

    protected Stock() {
    }

    Stock(UUID productId, int onHand) {
        this.productId = productId;
        this.onHand = onHand;
    }

    int available() {
        return onHand - reserved;
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

package org.ecom.inventory;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.ecom.shared.web.BusinessException;
import org.ecom.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final StockRepository stocks;

    InventoryService(StockRepository stocks) {
        this.stocks = stocks;
    }

    @Transactional
    public void initialize(UUID productId, int onHand) {
        stocks.save(new Stock(productId, onHand));
    }

    @Transactional
    public StockLevel setOnHand(UUID productId, int onHand) {
        var stock = stocks.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Stock", productId));
        if (onHand < stock.getReserved()) {
            throw new BusinessException("STOCK_BELOW_RESERVED",
                    "On-hand quantity cannot be lower than the %d reserved units".formatted(stock.getReserved()));
        }
        stock.setOnHand(onHand);
        return StockLevel.of(stock);
    }

    @Transactional(readOnly = true)
    public Map<UUID, Integer> available(Collection<UUID> productIds) {
        return stocks.findByProductIdIn(productIds).stream()
                .collect(Collectors.toMap(Stock::getProductId, Stock::available));
    }

    @Transactional(readOnly = true)
    public StockLevel level(UUID productId) {
        return stocks.findById(productId).map(StockLevel::of)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", productId));
    }

    /**
     * Holds units for an unpaid order. Must run inside the order's transaction so that a failure on a later line
     * rolls back the reservations of the previous ones.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(UUID productId, String sku, int quantity) {
        if (stocks.reserve(productId, quantity) == 0) {
            throw new BusinessException("INSUFFICIENT_STOCK", "Insufficient stock for product %s".formatted(sku));
        }
    }

    /** Gives reserved units back, when an unpaid order is cancelled or expires. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void release(UUID productId, int quantity) {
        requireUpdated(stocks.release(productId, quantity), productId);
    }

    /** Turns reserved units into sold units once the order is paid. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void commit(UUID productId, int quantity) {
        requireUpdated(stocks.commit(productId, quantity), productId);
    }

    private static void requireUpdated(int updatedRows, UUID productId) {
        if (updatedRows == 0) {
            throw new IllegalStateException("Stock reservation inconsistency for product " + productId);
        }
    }

    public record StockLevel(UUID productId, int onHand, int reserved, int available) {

        static StockLevel of(Stock stock) {
            return new StockLevel(stock.getProductId(), stock.getOnHand(), stock.getReserved(), stock.available());
        }

    }

}

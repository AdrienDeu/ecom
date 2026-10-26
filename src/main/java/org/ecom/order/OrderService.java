package org.ecom.order;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;

import org.ecom.cart.CartItem;
import org.ecom.cart.CartOwner;
import org.ecom.cart.CartService;
import org.ecom.catalog.CatalogService;
import org.ecom.customer.AddressService;
import org.ecom.inventory.InventoryService;
import org.ecom.shared.web.BusinessException;
import org.ecom.shared.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartService carts;
    private final CatalogService catalog;
    private final InventoryService inventory;
    private final AddressService addresses;
    private final Clock clock;
    private final Duration reservationTtl;

    OrderService(OrderRepository orders, CartService carts, CatalogService catalog, InventoryService inventory,
                 AddressService addresses, Clock clock,
                 @Value("${ecom.order.reservation-ttl:PT30M}") Duration reservationTtl) {
        this.orders = orders;
        this.carts = carts;
        this.catalog = catalog;
        this.inventory = inventory;
        this.addresses = addresses;
        this.clock = clock;
        this.reservationTtl = reservationTtl;
    }

    /**
     * Turns the customer's cart into an order: prices are frozen and stock is reserved until the order is paid or
     * expires. Any unavailable line rolls the whole checkout back.
     */
    @Transactional
    public OrderView checkout(String customerId, UUID addressId) {
        var owner = CartOwner.customer(customerId);
        var items = carts.items(owner);
        if (items.isEmpty()) {
            throw new BusinessException("EMPTY_CART", "The cart is empty");
        }
        var shippingAddress = addresses.get(customerId, addressId);
        var products = catalog.summaries(items.stream().map(CartItem::productId).toList());

        var lines = new ArrayList<OrderLine>();
        for (var item : items) {
            var product = products.get(item.productId());
            if (product == null || !product.active()) {
                throw new BusinessException("PRODUCT_UNAVAILABLE", "Product %s is no longer available".formatted(item.productId()));
            }
            inventory.reserve(product.id(), product.sku(), item.quantity());
            lines.add(new OrderLine(product.id(), product.sku(), product.name(), product.price(), item.quantity()));
        }

        var order = orders.save(new CustomerOrder(OrderReferences.next(), customerId, shippingAddress, lines,
                clock.instant().plus(reservationTtl)));
        afterCommit(() -> carts.clear(owner));
        log.info("Order {} created for {} lines", order.getReference(), lines.size());
        return OrderView.of(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderView> customerOrders(String customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderView::of);
    }

    @Transactional(readOnly = true)
    public OrderView customerOrder(String customerId, UUID orderId) {
        return OrderView.of(owned(customerId, orderId));
    }

    @Transactional
    public OrderView cancelByCustomer(String customerId, UUID orderId) {
        return OrderView.of(transition(owned(customerId, orderId), OrderStatus.CANCELLED));
    }

    // --- Back-office ---

    @Transactional(readOnly = true)
    public Page<OrderView> list(OrderStatus status, Pageable pageable) {
        var page = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        return page.map(OrderView::of);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID orderId) {
        return OrderView.of(order(orderId));
    }

    @Transactional
    public OrderView changeStatus(UUID orderId, OrderStatus target) {
        return OrderView.of(transition(order(orderId), target));
    }

    // --- Called by the payment module ---

    @Transactional
    public OrderView markPaymentPending(UUID orderId) {
        return changeStatus(orderId, OrderStatus.PAYMENT_PENDING);
    }

    @Transactional
    public OrderView markPaid(UUID orderId) {
        return changeStatus(orderId, OrderStatus.PAID);
    }

    /** The order goes back to {@code CREATED} so that the customer can retry, possibly with another method. */
    @Transactional
    public OrderView markPaymentFailed(UUID orderId) {
        return changeStatus(orderId, OrderStatus.CREATED);
    }

    /** Once the whole payment has been refunded. Shipped goods are expected back: their stock is not restored. */
    @Transactional
    public OrderView markRefunded(UUID orderId) {
        return changeStatus(orderId, OrderStatus.REFUNDED);
    }

    // --- Expiry ---

    /**
     * Cancels a batch of unpaid orders whose reservation expired and releases their stock.
     *
     * @return the number of cancelled orders
     */
    @Transactional
    public int expireStaleOrders(int batchSize) {
        var ids = orders.lockExpired(clock.instant(), batchSize);
        for (var order : orders.findAllById(ids)) {
            transition(order, OrderStatus.CANCELLED);
            log.info("Order {} expired, stock released", order.getReference());
        }
        return ids.size();
    }

    private CustomerOrder transition(CustomerOrder order, OrderStatus target) {
        order.transitionTo(target);
        switch (target) {
            case CANCELLED -> order.getLines().forEach(line -> inventory.release(line.getProductId(), line.getQuantity()));
            case PAID -> order.getLines().forEach(line -> inventory.commit(line.getProductId(), line.getQuantity()));
            default -> {
                // no stock movement
            }
        }
        return order;
    }

    private CustomerOrder owned(String customerId, UUID orderId) {
        return orders.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private CustomerOrder order(UUID orderId) {
        return orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

}

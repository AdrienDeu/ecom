package org.ecom.cart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.ecom.cart.CartStore.StoredCart;
import org.ecom.catalog.CatalogService;
import org.ecom.catalog.ProductKind;
import org.ecom.catalog.ProductSummary;
import org.ecom.inventory.InventoryService;
import org.ecom.shared.domain.Money;
import org.ecom.shared.web.BusinessException;
import org.ecom.shared.web.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CartService {

    static final int MAX_QUANTITY_PER_LINE = 99;
    static final int MAX_LINES = 50;

    private final CartStore store;
    private final CatalogService catalog;
    private final InventoryService inventory;

    CartService(CartStore store, CatalogService catalog, InventoryService inventory) {
        this.store = store;
        this.catalog = catalog;
        this.inventory = inventory;
    }

    public CartView view(CartOwner owner) {
        return price(store.load(owner).items());
    }

    /** Raw items, for checkout. */
    public List<CartItem> items(CartOwner owner) {
        return store.load(owner).items();
    }

    public CartView add(CartOwner owner, UUID productId, int quantity) {
        var product = catalog.summaries(List.of(productId)).get(productId);
        if (product == null || !product.active()) {
            throw new ResourceNotFoundException("Product", productId);
        }
        var items = quantities(owner);
        if (!items.containsKey(productId) && items.size() >= MAX_LINES) {
            throw new BusinessException("CART_FULL", "A cart can contain at most %d different products".formatted(MAX_LINES));
        }
        items.merge(productId, quantity, Integer::sum);
        return save(owner, items);
    }

    public CartView setQuantity(CartOwner owner, UUID productId, int quantity) {
        var items = quantities(owner);
        if (!items.containsKey(productId)) {
            throw new ResourceNotFoundException("Cart item", productId);
        }
        if (quantity == 0) {
            items.remove(productId);
        } else {
            items.put(productId, quantity);
        }
        return save(owner, items);
    }

    public CartView remove(CartOwner owner, UUID productId) {
        var items = quantities(owner);
        items.remove(productId);
        return save(owner, items);
    }

    /** Moves a guest cart into the customer's cart when they sign in. */
    public CartView merge(CartOwner.Guest guest, CartOwner.Customer customer) {
        var items = quantities(customer);
        for (var item : store.load(guest).items()) {
            items.merge(item.productId(), item.quantity(), (a, b) -> Math.min(a + b, MAX_QUANTITY_PER_LINE));
        }
        var view = save(customer, items);
        store.delete(guest);
        return view;
    }

    public void clear(CartOwner owner) {
        store.delete(owner);
    }

    private Map<UUID, Integer> quantities(CartOwner owner) {
        var items = new LinkedHashMap<UUID, Integer>();
        store.load(owner).items().forEach(item -> items.put(item.productId(), item.quantity()));
        return items;
    }

    private CartView save(CartOwner owner, Map<UUID, Integer> quantities) {
        if (quantities.values().stream().anyMatch(q -> q > MAX_QUANTITY_PER_LINE)) {
            throw new BusinessException("QUANTITY_TOO_HIGH", "At most %d units per product".formatted(MAX_QUANTITY_PER_LINE));
        }
        var items = quantities.entrySet().stream().map(e -> new CartItem(e.getKey(), e.getValue())).toList();
        store.save(owner, new StoredCart(items));
        return price(items);
    }

    /** Prices are always read from the catalog: the cart only stores product ids and quantities. */
    private CartView price(List<CartItem> items) {
        var ids = items.stream().map(CartItem::productId).toList();
        Map<UUID, ProductSummary> products = ids.isEmpty() ? Map.of() : catalog.summaries(ids);
        Map<UUID, Integer> available = ids.isEmpty() ? Map.of() : inventory.available(ids);
        var lines = new ArrayList<CartLine>();
        var total = Money.zero(Money.EUR);
        for (var item : items) {
            var product = products.get(item.productId());
            if (product == null) {
                continue; // product removed from the catalog
            }
            var lineTotal = product.price().times(item.quantity());
            var purchasable = product.active() && available.getOrDefault(product.id(), 0) >= item.quantity();
            lines.add(new CartLine(product.id(), product.sku(), product.slug(), product.name(), product.kind(),
                    product.imageUrl(), product.price(), item.quantity(), lineTotal, purchasable));
            if (purchasable) {
                total = total.plus(lineTotal);
            }
        }
        return new CartView(lines, total, lines.stream().allMatch(CartLine::available));
    }

    public record CartView(List<CartLine> lines, Money total, boolean checkoutAllowed) {
    }

    public record CartLine(UUID productId, String sku, String slug, String name, ProductKind kind,
                           @Schema(nullable = true) String imageUrl, Money unitPrice,
                           int quantity, Money lineTotal, boolean available) {
    }

}

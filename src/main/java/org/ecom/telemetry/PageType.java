package org.ecom.telemetry;

/**
 * Kinds of pages tracked by {@code ecom.page.views}. A closed list keeps the metric's cardinality bounded whatever
 * the client sends.
 */
public enum PageType {
    HOME, CATALOG, PRODUCT, QUOTE, CART, CHECKOUT, ACCOUNT, ORDER, ADMIN, OTHER
}

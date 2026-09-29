package com.github.yildizmy.order;

public class ProductPriceChangedException extends RuntimeException {

    public ProductPriceChangedException() {
        super("Product price changed; refresh the product before checkout");
    }
}

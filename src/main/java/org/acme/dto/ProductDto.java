package org.acme.dto;

import org.acme.entity.Product;

/**
 * Exposes only the fields needed for the frontend. Never the full entity. So a product response
 * never leaks the owner's password.
 */
public class ProductDto {
  public Long id;
  public String productName;
  public String description;
  public Double price;
  public String imageURL;
  public Integer quantity;
  public Long ownerId;

  public ProductDto(Product product) {
    this.id = product.id;
    this.productName = product.getProductName();
    this.description = product.getDescription();
    this.price = product.getPrice();
    this.imageURL = product.getImageURL();
    this.quantity = product.getQuantity();
    this.ownerId = product.getOwner() != null ? product.getOwner().id : null;
  }
}

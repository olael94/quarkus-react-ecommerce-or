package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class OrderItemRequestDto {
  @NotNull(message = "Product ID is required")
  private Long productId;

  @NotNull(message = "Quantity is required")
  @Positive(message = "Quantity must be positive")
  private Integer quantity;

  // Getters
  public Long getProductId() {
    return productId;
  }

  public Integer getQuantity() {
    return quantity;
  }

  // Setters
  public void setProductId(Long productId) {
    this.productId = productId;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }
}

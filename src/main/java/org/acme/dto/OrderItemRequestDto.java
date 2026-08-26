package org.acme.dto;

public class OrderItemRequestDto {
  private Long productId;
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

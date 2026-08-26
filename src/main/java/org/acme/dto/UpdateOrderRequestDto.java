package org.acme.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import org.acme.entity.Order;

public class UpdateOrderRequestDto {
  private Long userId;

  @NotNull(message = "Order date is required")
  private LocalDateTime orderDate;

  @NotNull(message = "Total amount is required")
  @PositiveOrZero(message = "Total amount must be a positive or zero number")
  private Double totalAmount;

  private Order.Status status;

  // Getters
  public Long getUserId() {
    return userId;
  }

  public LocalDateTime getOrderDate() {
    return orderDate;
  }

  public Double getTotalAmount() {
    return totalAmount;
  }

  public Order.Status getStatus() {
    return status;
  }

  // Setters
  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public void setOrderDate(LocalDateTime orderDate) {
    this.orderDate = orderDate;
  }

  public void setTotalAmount(Double totalAmount) {
    this.totalAmount = totalAmount;
  }

  public void setStatus(Order.Status status) {
    this.status = status;
  }
}

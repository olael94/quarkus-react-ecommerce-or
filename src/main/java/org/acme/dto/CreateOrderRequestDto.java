package org.acme.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Replaces the raw order request where the client sends what they want */
public class CreateOrderRequestDto {
  private Long userId;

  @Email(message = "Email must be a valid email address")
  private String guestEmail;

  @NotEmpty(message = "Order must contain at least one item")
  @Valid
  private List<OrderItemRequestDto> items;

  // Getters
  public Long getUserId() {
    return userId;
  }

  public String getGuestEmail() {
    return guestEmail;
  }

  public List<OrderItemRequestDto> getItems() {
    return items;
  }

  // Setters
  public void setUserId(Long userId) {
    this.userId = userId;
  }

  public void setGuestEmail(String guestEmail) {
    this.guestEmail = guestEmail;
  }

  public void setItems(List<OrderItemRequestDto> items) {
    this.items = items;
  }
}

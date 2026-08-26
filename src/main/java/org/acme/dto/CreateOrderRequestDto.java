package org.acme.dto;

import java.util.List;

/** Replaces the raw order request where the client sends what they want */
public class CreateOrderRequestDto {
  private Long userId;
  private String guestEmail;
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

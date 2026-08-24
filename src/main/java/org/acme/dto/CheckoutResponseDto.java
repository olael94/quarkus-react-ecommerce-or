package org.acme.dto;

/** DTO to return the checkout URL when createOrder */
public class CheckoutResponseDto {
  private String checkoutUrl;

  // Constructor
  public CheckoutResponseDto(String checkoutUrl) {
    this.checkoutUrl = checkoutUrl;
  }

  // Getter method
  public String getCheckoutUrl() {
    return checkoutUrl;
  }

  // Setter method
  public void setCheckoutUrl(String checkoutUrl) {
    this.checkoutUrl = checkoutUrl;
  }
}

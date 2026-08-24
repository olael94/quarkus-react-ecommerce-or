package org.acme.controller;

import com.stripe.exception.StripeException;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.acme.dto.CheckoutResponseDto;
import org.acme.dto.CreateOrderRequest;
import org.acme.dto.MessageDto;
import org.acme.dto.OrderItemRequest;
import org.acme.entity.Order;
import org.acme.entity.OrderItem;
import org.acme.entity.Product;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.service.StripeCheckoutService;
import org.acme.util.SessionAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/orders")
@Produces("application/json")
@Consumes("application/json")
public class OrderController {

  // The logger object is used to log messages to the console.
  private static final Logger logger = LoggerFactory.getLogger(OrderController.class);

  @Inject StripeCheckoutService stripeCheckoutService;

  // Helper method to return a WebApplicationException with a 400 Bad Request status code and the
  // provided message.
  private WebApplicationException badRequest(String message) {
    return new WebApplicationException(
        Response.status(Response.Status.BAD_REQUEST).entity(new MessageDto(message)).build());
  }

  // Helper method to return a WebApplicationException with a 500 Internal Server Error status code
  private WebApplicationException badGateway(String message) {
    return new WebApplicationException(
        Response.status(Response.Status.BAD_GATEWAY).entity(new MessageDto(message)).build());
  }

  // Create a new order (guest or user)
  @POST
  @Transactional
  public Response createOrder(CreateOrderRequest request) {
    logger.info("Received request to create an order");

    if (request.getItems() == null || request.getItems().isEmpty()) {
      throw badRequest("Order must contain at least one item");
    }

    User user = null;
    if (request.getUserId() != null) {
      // Fetch and validate the user if provided
      user = User.findById(request.getUserId());
      if (user == null) {
        logger.warn("User not found for ID: {}", request.getUserId());
        throw badRequest("User not found");
      }
    }

    double total = 0.0;
    List<OrderItem> newItems = new ArrayList<>();

    // Create the order
    for (OrderItemRequest itemRequest : request.getItems()) {
      if (itemRequest.getProductId() == null) {
        logger.warn("Each item must specify a productId");
        throw badRequest("Each item must specify a productId");
      }

      // Validate product exists
      Product product = Product.findById(itemRequest.getProductId());
      if (product == null) {
        logger.warn("Product not found for ID: {}", itemRequest.getProductId());
        throw badRequest("Product not found: " + itemRequest.getProductId());
      }

      // Validate quantity is a positive integer
      Integer quantity = itemRequest.getQuantity();
      if (quantity == null || quantity <= 0) {
        logger.warn("Quantity must be a positive for each item: " + product.getProductName());
        throw badRequest(
            "Quantity must be a positive integer for each item: " + product.getProductName());
      }

      // This atomic conditional decrement re-checks stock in the same statement as the update,
      // so the database - not this code - guarantees two simultaneous orders can't both claim the
      // last unit
      int rowsUpdated =
          Product.update(
              "quantity = quantity - ?1 where id = ?2 and quantity >= ?1",
              quantity,
              product.id); // Update the product quantity
      if (rowsUpdated == 0) {
        logger.warn("Not enough stock for product: {}", product.getProductName());
        throw badRequest("Not enough stock for product: " + product.getProductName());
      }

      OrderItem orderItem = new OrderItem();
      orderItem.setProduct(product);
      orderItem.setQuantity(quantity);
      orderItem.setUnitPrice(product.getPrice()); // Snapshot the price at the time of purchase
      newItems.add(orderItem);

      total += product.getPrice() * quantity; // Accumulate total
    }

    Order order = new Order();
    order.setUser(user); // Set user if it's a user order, otherwise null for guest
    order.setGuestEmail(request.getGuestEmail()); // Set guest email if provided
    order.setOrderDate(LocalDateTime.now());
    order.setTotalAmount(total);
    order.setStatus(Order.Status.PENDING);

    // Add the items to the order
    for (OrderItem item : newItems) {
      order.getItems().add(item);
      item.setOrder(order);
    }

    // The guestTrackingId will be generated automatically by the @PrePersist method
    order.persist(); // Persist the order to the database

    // User will see this message
    String checkoutUrl;
    try {
      checkoutUrl = stripeCheckoutService.createCheckoutSession(order);
    } catch (StripeException stripeException) {
      logger.error(
          "Failed to create Stripe checkout session for order {}", order.id, stripeException);
      throw badGateway("Failed to create Stripe checkout session - please try again");
    }

    logger.info("Order {} created, checkout session started", order.id);

    return Response.status(Response.Status.CREATED)
        .entity(new CheckoutResponseDto(checkoutUrl))
        .build();
  }

  // Get GUEST order by guestTrackingId
  @GET
  @Path("{guestTrackingId}")
  public Response getGuestOrderByTrackingId(@PathParam("guestTrackingId") String guestTrackingId) {
    logger.info("Fetching order for guestTrackingId: {}", guestTrackingId);
    Order order = Order.find("guestTrackingId", guestTrackingId).firstResult();
    if (order == null) {
      logger.warn("Order not found for guestTrackingId: {}", guestTrackingId);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found")) // User will see this message
          .build();
    }

    logger.info("Order found for guestTrackingId: {}", guestTrackingId);
    // User will see this message
    String message = "Order found for guestTrackingId: " + order.getGuestTrackingId();
    return Response.ok(new MessageDto(message)).build();
  }

  // Get a specific USER order by ID
  @GET
  @Path("{id}")
  public Response getOrder(@PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    logger.info("Fetching order for ID: {}", id);

    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Order order = Order.findById(id);
    if (order == null) {
      logger.warn("Order not found for ID: {}", id);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found")) // User will see this message
          .build();
    }

    boolean isOwner = false;
    if (order.getUser() != null) {
      Long orderOwnerId = order.getUser().id;
      if (orderOwnerId.equals(session.user.id)) {
        isOwner = true;
      }
    }

    boolean isStaff = session.user.hasRole(User.Role.ADMIN, User.Role.SUPPORT);
    boolean allowedToViewThisOrder = isOwner || isStaff;

    if (!allowedToViewThisOrder) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    logger.info("Order found for ID: {}", id);
    // User will see this message
    String message = "Order found for ID: " + order.id;
    return Response.ok(new MessageDto(message)).build();
  }

  // Update an existing GUEST order by guestTrackingId
  @PUT
  @Path("{guestTrackingId}")
  @Transactional
  public Response updateGuestOrder(
      @PathParam("guestTrackingId") String guestTrackingId, Order updatedGuestOrder) {
    logger.info("Updating guest order with tracking ID: {}", guestTrackingId);

    // Find the existing guest order by guestTrackingId
    Order existingGuestOrder = Order.find("guestTrackingId", guestTrackingId).firstResult();

    if (existingGuestOrder == null) {
      logger.warn("Order not found for guestTrackingId: {}", guestTrackingId);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found")) // User will see this message
          .build();
    }

    // Update order fields
    existingGuestOrder.setOrderDate(updatedGuestOrder.getOrderDate());
    existingGuestOrder.setTotalAmount(updatedGuestOrder.getTotalAmount());
    existingGuestOrder.setStatus(updatedGuestOrder.getStatus());

    // Persist or update the order
    existingGuestOrder.persist();

    logger.info("Guest order updated successfully for guestTrackingId: {}", guestTrackingId);
    // User will see this message
    String message =
        "Guest order updated successfully for guestTrackingId: "
            + existingGuestOrder.getGuestTrackingId();
    return Response.ok(new MessageDto(message)).build();
  }

  // Delete an order by guestTrackingId
  @DELETE
  @Path("{guestTrackingId}")
  @Transactional
  public Response deleteGuestOrder(@PathParam("guestTrackingId") String guestTrackingId) {
    logger.info("Deleting guest order with tracking ID: {}", guestTrackingId);

    Order order = Order.find("guestTrackingId", guestTrackingId).firstResult();
    if (order == null) {
      logger.warn("Order not found for guestTrackingId: {}", guestTrackingId);
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found")) // User will see this message
          .build();
    }
    order.delete();

    logger.info("Guest order deleted successfully for guestTrackingID: {}", guestTrackingId);
    // User will see this message
    String message = "Guest order deleted successfully for guestTrackingID: " + guestTrackingId;
    return Response.noContent().build(); // Return 204 No Content
  }
}

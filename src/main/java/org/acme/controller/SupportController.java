package org.acme.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import org.acme.dto.MessageDto;
import org.acme.dto.OrderDto;
import org.acme.entity.Order;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.service.PasswordResetService;
import org.acme.util.SessionAuth;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/support")
@Produces(MediaType.APPLICATION_JSON)
public class SupportController {

  private static final Logger logger = LoggerFactory.getLogger(SupportController.class);
  @Inject PasswordResetService passwordResetService;

  // Get all orders (support or admin only)
  @GET
  @Path("/orders")
  @Operation(summary = "List all orders", description = "Requires SUPPORT or ADMIN role.")
  @APIResponse(responseCode = "200", description = "All orders returned")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks SUPPORT or ADMIN role")
  public Response getAllOrders(
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Support and admin can view all orders
    Response forbidden = SessionAuth.requireRole(session, User.Role.SUPPORT, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    logger.info("Fetching all orders");
    List<Order> orders = Order.listAll();
    List<OrderDto> orderDtos = new ArrayList<>();
    for (Order order : orders) {
      orderDtos.add(new OrderDto(order));
    }

    return Response.ok(orderDtos).build();
  }

  // Process a refund for an order (support or admin only)
  @POST
  @Path("/orders/{id}/refund")
  @Transactional
  @Operation(
      summary = "Refund an order",
      description =
          "Requires SUPPORT or ADMIN role. Allowed on any order status except an "
              + "order that's already REFUNDED - not restricted to COMPLETED orders only, "
              + "since order status isn't a reliable signal of delivery in this app.")
  @APIResponse(responseCode = "200", description = "Order refunded")
  @APIResponse(responseCode = "400", description = "Order was already refunded")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks SUPPORT or ADMIN role")
  @APIResponse(responseCode = "404", description = "No order with that ID")
  public Response refundOrder(
      @PathParam("id") Long id,
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.SUPPORT, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    // Check if the order exists
    Order order = Order.findById(id);
    if (order == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found"))
          .build();
    }

    if (order.getStatus() == Order.Status.REFUNDED) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Order has already been refunded"))
          .build();
    }

    order.setStatus(Order.Status.REFUNDED);
    order.persist();

    logger.info("Refunded order with ID: {} for amount: {}", id, order.getTotalAmount());
    return Response.ok(new MessageDto("Order " + order.id + " refunded successfully")).build();
  }

  // Trigger a password reset for a user on their behalf (support or admin only)
  @POST
  @Path("/users/{id}/reset-password")
  @Transactional
  @Operation(
      summary = "Trigger a password reset for a user",
      description =
          "Requires SUPPORT or ADMIN role. Sends the reset email on the target user's behalf.")
  @APIResponse(responseCode = "200", description = "Reset email sent")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks SUPPORT or ADMIN role")
  @APIResponse(responseCode = "404", description = "No user with that ID")
  public Response supportRequestPasswordReset(
      @PathParam("id") Long id,
      @Parameter(
              name = "session",
              in = ParameterIn.COOKIE,
              description =
                  "Session token set by POST /api/users/login. Sent automatically by the "
                      + "browser once logged in - leave this field blank in Try it out; the "
                      + "browser blocks JavaScript from overriding the real Cookie header, so "
                      + "typing a value here has no effect.",
              schema = @Schema(type = SchemaType.STRING))
          @CookieParam("session")
          Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.SUPPORT, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    // Check if the user exists
    User targetUser = User.findById(id);
    if (targetUser == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("User not found"))
          .build();
    }

    // Trigger password reset
    passwordResetService.sendPasswordResetEmail(
        targetUser, "A support agent has triggered a password reset for your account. ");

    logger.info("Password reset requested for user with ID: {}", id);
    return Response.ok(
            new MessageDto(
                "Password reset requested for user "
                    + targetUser.id
                    + ". Password reset email sent to "
                    + targetUser.getEmail()))
        .build();
  }
}

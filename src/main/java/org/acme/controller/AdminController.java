package org.acme.controller;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.acme.dto.MessageDto;
import org.acme.dto.OrderDto;
import org.acme.dto.UpdateOrderRequestDto;
import org.acme.dto.UserDto;
import org.acme.entity.Order;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.service.PasswordResetService;
import org.acme.util.SessionAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Everything gated behind hasRole(Role.ADMIN), regardless of which resource it acts on. */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminController {

  private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

  @Inject PasswordResetService passwordResetService;

  // Get all users in the database (admin only)
  @GET
  @Path("/users")
  public Response getAllUsers(@CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    logger.info("Fetching all users");
    List<User> users = User.listAll();
    return Response.ok(users.stream().map(UserDto::new).collect(Collectors.toList())).build();
  }

  // Grant a role to a user (admin only)
  @POST
  @Path("/users/{id}/roles/{role}")
  @Transactional
  public Response grantRole(
      @PathParam("id") Long id,
      @PathParam("role") User.Role role,
      @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    User targetUser = User.findById(id);
    if (targetUser == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("User not found"))
          .build();
    }

    targetUser.addRole(role);
    targetUser.persist();

    logger.info("Granted role {} to user: {}", role, targetUser.getUsername());
    return Response.ok(new MessageDto("Granted " + role + " to " + targetUser.getUsername()))
        .build();
  }

  // Revoke a role from a user (admin only)
  @DELETE
  @Path("/users/{id}/roles/{role}")
  @Transactional
  public Response revokeRole(
      @PathParam("id") Long id,
      @PathParam("role") User.Role role,
      @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    // Prevent an admin from revoking their own role
    if (id.equals(session.user.id)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("You cannot revoke your own role"))
          .build();
    }

    User targetUser = User.findById(id);
    if (targetUser == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("User not found"))
          .build();
    }

    // Remove the role from the user
    targetUser.removeRole(role);
    targetUser.persist();

    logger.info("Revoked role {} from user: {}", role, targetUser.getUsername());
    return Response.ok(new MessageDto("Revoked " + role + " from " + targetUser.getUsername()))
        .build();
  }

  // Deactivate a user (admin only)
  @POST
  @Path("/users/{id}/deactivate")
  @Transactional
  public Response deactivateUser(
      @PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    if (id.equals(session.user.id)) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("You cannot deactivate your own account"))
          .build();
    }

    User targetUser = User.findById(id);
    if (targetUser == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("User not found"))
          .build();
    }

    targetUser.setActive(false);
    targetUser.persist();

    // A deactivated account shouldn't stay logged in on any device it's
    // currently signed into.
    Session.delete("user", targetUser);

    logger.info("Deactivated user: {}", targetUser.getUsername());
    return Response.ok(new MessageDto("User " + targetUser.getUsername() + " deactivated")).build();
  }

  // Reactivate a user (admin only)
  @POST
  @Path("/users/{id}/reactivate")
  @Transactional
  public Response reactivateUser(
      @PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    User targetUser = User.findById(id);
    if (targetUser == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("User not found"))
          .build();
    }

    targetUser.setActive(true);
    targetUser.persist();

    logger.info("Reactivated user: {}", targetUser.getUsername());
    return Response.ok(new MessageDto("User " + targetUser.getUsername() + " reactivated")).build();
  }

  // Trigger a password reset for a user on their behalf (admin only)
  @POST
  @Path("/users/{id}/reset-password")
  @Transactional
  public Response adminRequestPasswordReset(
      @PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    User targetUser = User.findById(id);
    if (targetUser == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("User not found"))
          .build();
    }

    passwordResetService.sendPasswordResetEmail(
        targetUser, "An administrator has triggered a password reset for your account. ");

    return Response.ok(new MessageDto("Password reset email sent to " + targetUser.getEmail()))
        .build();
  }

  // Get all orders (admin only)
  @GET
  @Path("/orders")
  public Response getAllOrders(@CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
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

  // Update an existing USER order by ID (admin only)
  @PUT
  @Path("/orders/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response updateOrder(
      @PathParam("id") Long id,
      @CookieParam("session") Cookie sessionCookie,
      @Valid UpdateOrderRequestDto request) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    Order existingOrder = Order.findById(id);
    if (existingOrder == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found"))
          .build();
    }

    // Check for user existence before updating
    if (request.getUserId() != null) {
      User user = User.findById(request.getUserId());
      if (user == null) {
        return Response.status(Response.Status.NOT_FOUND)
            .entity(new MessageDto("User not found"))
            .build();
      }
      existingOrder.setUser(user);
    }

    // Update order fields
    existingOrder.setOrderDate(request.getOrderDate());
    existingOrder.setTotalAmount(request.getTotalAmount());
    existingOrder.setStatus(request.getStatus());
    existingOrder.persist();

    logger.info("Order updated successfully for ID: {}", id);
    return Response.ok(new MessageDto("Order updated successfully for ID: " + existingOrder.id))
        .build();
  }

  // Delete an order by ID (admin only)
  @DELETE
  @Path("/orders/{id}")
  @Transactional
  public Response deleteOrder(
      @PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    Response forbidden = SessionAuth.requireRole(session, User.Role.ADMIN);
    if (forbidden != null) {
      return forbidden;
    }

    Order order = Order.findById(id);
    if (order == null) {
      return Response.status(Response.Status.NOT_FOUND)
          .entity(new MessageDto("Order not found"))
          .build();
    }
    order.delete();
    logger.info("Order deleted successfully for ID: {}", id);
    return Response.ok(new MessageDto("Order deleted successfully for ID: " + id)).build();
  }
}

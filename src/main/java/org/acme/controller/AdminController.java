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
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
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
  @Operation(summary = "List all users", description = "Requires ADMIN role.")
  @APIResponse(responseCode = "200", description = "All users returned")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  public Response getAllUsers(
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
  @Operation(summary = "Grant a role to a user", description = "Requires ADMIN role.")
  @APIResponse(responseCode = "200", description = "Role granted")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(responseCode = "404", description = "No user with that ID")
  public Response grantRole(
      @PathParam("id") Long id,
      @PathParam("role") User.Role role,
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
  @Operation(
      summary = "Revoke a role from a user",
      description = "Requires ADMIN role. An admin cannot revoke their own role.")
  @APIResponse(responseCode = "200", description = "Role revoked")
  @APIResponse(responseCode = "400", description = "Caller tried to revoke their own role")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(responseCode = "404", description = "No user with that ID")
  public Response revokeRole(
      @PathParam("id") Long id,
      @PathParam("role") User.Role role,
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
  @Operation(
      summary = "Deactivate a user",
      description =
          "Requires ADMIN role. Blocks login and kills all of the target user's active "
              + "sessions. An admin cannot deactivate their own account.")
  @APIResponse(responseCode = "200", description = "User deactivated")
  @APIResponse(responseCode = "400", description = "Caller tried to deactivate their own account")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(responseCode = "404", description = "No user with that ID")
  public Response deactivateUser(
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
  @Operation(summary = "Reactivate a user", description = "Requires ADMIN role.")
  @APIResponse(responseCode = "200", description = "User reactivated")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(responseCode = "404", description = "No user with that ID")
  public Response reactivateUser(
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
  @Operation(
      summary = "Trigger a password reset for a user",
      description = "Requires ADMIN role. Sends the reset email on the target user's behalf.")
  @APIResponse(responseCode = "200", description = "Reset email sent")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(responseCode = "404", description = "No user with that ID")
  public Response adminRequestPasswordReset(
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
  @Operation(summary = "List all orders", description = "Requires ADMIN role.")
  @APIResponse(responseCode = "200", description = "All orders returned")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
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
  @Operation(summary = "Update an order", description = "Requires ADMIN role.")
  @APIResponse(responseCode = "200", description = "Order updated")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(
      responseCode = "404",
      description = "No order (or, if reassigning, no user) with that ID")
  public Response updateOrder(
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
          Cookie sessionCookie,
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
  @Operation(summary = "Delete an order", description = "Requires ADMIN role.")
  @APIResponse(responseCode = "200", description = "Order deleted")
  @APIResponse(responseCode = "401", description = "No valid session")
  @APIResponse(responseCode = "403", description = "Session lacks ADMIN role")
  @APIResponse(responseCode = "404", description = "No order with that ID")
  public Response deleteOrder(
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

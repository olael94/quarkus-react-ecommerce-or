package org.acme.controller;

import static io.quarkus.hibernate.orm.panache.PanacheEntity_.id;

import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import java.time.Duration;
import java.time.Instant;
import org.acme.dto.*;
import org.acme.entity.PasswordResetToken;
import org.acme.entity.Session;
import org.acme.entity.User;
import org.acme.service.PasswordResetService;
import org.acme.util.CookieBuilder;
import org.acme.util.SessionAuth;
import org.acme.util.TokenGenerator;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserController {
  // The logger object is used to log messages to the console.
  private static final Logger logger = LoggerFactory.getLogger(UserController.class);
  private static final int MAX_LOGIN_ATTEMPTS = 5;
  private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

  // Inject the configuration properties using CDI to
  @Inject
  @ConfigProperty(name = "app.cookie.secure")
  boolean cookieSecure;

  @Inject Mailer mailer;

  @Inject
  @ConfigProperty(name = "app.base-url")
  String baseUrl;

  @Inject PasswordResetService passwordResetService;

  // Create a new User
  @POST
  @Path("/register")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response createUser(@Valid RegisterDto registerDto) {
    logger.info("Creating user: {}", registerDto.getUsername());

    // Check if a user with the same email already exists
    User existingUser = User.find("email", registerDto.getEmail()).firstResult();
    if (existingUser != null) {
      return Response.status(Response.Status.CONFLICT)
          .entity(new MessageDto("Email is already in use"))
          .build();
    }

    // Built field-by-field from the DTO, not bound directly from the request
    // body - a client has no way to set id, role, lockedUntil, or
    // failedLoginAttempts at registration.
    User user = new User();
    user.setUsername(registerDto.getUsername());
    user.setEmail(registerDto.getEmail());
    user.setPassword(registerDto.getPassword()); // hashed internally in setPassword()
    user.addRole(User.Role.CUSTOMER);
    user.persist();

    logger.info("Created user: {}", user.getUsername());
    return Response.status(Response.Status.CREATED).entity(new UserDto(user)).build();
  }

  // Login a user
  @POST
  @Path("/login")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response loginUser(@Valid LoginDto loginDto) {
    logger.info("Logging in user: {}", loginDto.getEmail());

    // Find the user by email
    User user = User.find("email", loginDto.getEmail()).firstResult();

    if (user != null
        && user.getLockedUntil() != null
        && user.getLockedUntil().isAfter(Instant.now())) {
      return Response.status(429)
          .entity(new MessageDto("Too many failed login attempts. Try again later."))
          .build();
    }

    // A deactivated account (admin-controlled) is separate from a temporary
    // lockout (failed-attempts-controlled, checked above).
    if (user != null && !user.isActive()) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new MessageDto("Invalid email or password"))
          .build();
    }

    if (user == null || !user.checkPassword(loginDto.getPassword())) {
      if (user != null) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        if (user.getFailedLoginAttempts() >= MAX_LOGIN_ATTEMPTS) {
          user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
          user.setFailedLoginAttempts(0);
        }
      }
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new MessageDto("Invalid email or password"))
          .build();
    }

    // Successful login - clear any lockout state
    user.setFailedLoginAttempts(0);
    user.setLockedUntil(null);

    logger.info("User logged in successfully: {}", user.getUsername());

    // Create a new session for the user
    Session session = new Session();
    session.user = user;
    session.token = TokenGenerator.generate();
    session.csrfToken = TokenGenerator.generate();
    Instant now = Instant.now(); // Use Instant.now() to get the current time
    session.expiresAt =
        now.plus(Session.IDLE_WINDOW); // Set the session expiration time to now + idle window
    session.absoluteExpiresAt =
        now.plus(
            Session.ABSOLUTE_WINDOW); // Set the absolute expiration time to now + absolute window
    session.persist();

    logger.info("Session created for user: {}", user.getUsername());

    // Create a new cookie for the session token. This means the cookie will be sent with every
    // request to the server, allowing the server to identify the user and maintain their session.
    NewCookie sessionCookie =
        new NewCookie.Builder("session")
            .value(session.token)
            .path("/")
            .httpOnly(true)
            .secure(cookieSecure)
            .sameSite(NewCookie.SameSite.LAX)
            .maxAge(7 * 24 * 60 * 60)
            .build();

    NewCookie csrfCookie =
        new NewCookie.Builder("csrf_token")
            .value(session.csrfToken)
            .path("/")
            .httpOnly(false)
            .secure(cookieSecure)
            .sameSite(NewCookie.SameSite.LAX)
            .maxAge(7 * 24 * 60 * 60)
            .build();

    // If the login is successful, a 200 (OK) status code is returned along with the user data and
    // the session and CSRF cookies.
    return Response.ok(new UserDto(user)).cookie(sessionCookie, csrfCookie).build();
  }

  @POST
  @Path("/logout")
  @Transactional
  public Response logoutUser(@CookieParam("session") Cookie sessionCookie) {
    // If the user is logged in, delete the session token from the database.
    if (sessionCookie != null) {
      Session.delete("token", sessionCookie.getValue());
    }

    NewCookie expiredSessionCookie = CookieBuilder.expiredSessionCookie(cookieSecure);
    NewCookie expiredCsrf = CookieBuilder.expiredCsrfCookie(cookieSecure);
    // If the logout is successful, a 200 (OK) status code is returned along with the expired
    // session and CSRF cookies.
    return Response.ok().cookie(expiredSessionCookie, expiredCsrf).build();
  }

  @GET
  @Path("/me")
  public Response me(@CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    return Response.ok(new UserDto(session.user)).build();
  }

  // Get a user by ID
  @GET
  @Path("{id}")
  public Response getUser(@PathParam("id") Long id, @CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    // Check if the user ID in the session matches the requested user ID
    if (!session.user.id.equals(id)) {
      return Response.status(Response.Status.FORBIDDEN).build();
    }

    logger.info("Fetching user with ID {}", id);
    return Response.ok(new UserDto(session.user)).build();
  }

  // Update the current user's profile
  @PUT
  @Path("/me")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response updateCurrentUser(
      @CookieParam("session") Cookie sessionCookie, UpdateUserDto updateDto) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Get the user associated with the session
    User user = session.user;

    // Update the user's information based on the provided DTO if the fields are not null or empty
    if (updateDto.getUsername() != null && !updateDto.getUsername().isEmpty()) {
      user.setUsername(updateDto.getUsername());
    }
    if (updateDto.getEmail() != null && !updateDto.getEmail().isEmpty()) {
      user.setEmail(updateDto.getEmail());
    }

    // Persist the changes to the database
    user.persist();

    logger.info("Updated profile for user: {}", user.getUsername());
    return Response.ok(new UserDto(user)).build();
  }

  @POST
  @Path("/me/change-password")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response changePassword(
      @CookieParam("session") Cookie sessionCookie, ChangePasswordDto changeDto) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    // Validate the input DTO
    if (changeDto.getCurrentPassword() == null
        || changeDto.getCurrentPassword().isEmpty()
        || changeDto.getNewPassword() == null
        || changeDto.getNewPassword().isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Current password and new password are required"))
          .build();
    }

    // Get the user associated with the session
    User user = session.user;
    // Check if the current password is correct
    if (!user.checkPassword(changeDto.getCurrentPassword())) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new MessageDto("Current password is incorrect"))
          .build();
    }

    // Update the user's password
    user.setPassword(changeDto.getNewPassword());

    // Invalidate all existing sessions for this user except the current one
    Session.delete("user = ?1 and token != ?2", user, session.token);

    mailer.send(
        Mail.withText(
            user.getEmail(),
            "Your Password was changed",
            "Your password has been changed successfully. If this wasn't you, go to "
                + baseUrl
                + "/account and use \"Forgot Password?\" to secure your account immediately."));

    logger.info("Password changed for user: {}", user.getUsername());
    return Response.ok(new MessageDto("Password changed successfully.")).build();
  }

  // Reset a user's password with an email
  @POST
  @Path("/reset-password/request")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response requestPasswordReset(PasswordResetRequestDto requestDto) {
    logger.info("Resetting password for email: {}", requestDto.getEmail());

    // Check if email is empty
    if (requestDto.getEmail() == null || requestDto.getEmail().isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Email is required"))
          .build();
    }

    // Find the user by email
    User user = User.find("email", requestDto.getEmail()).firstResult();
    if (user != null) {
      passwordResetService.sendPasswordResetEmail(user, "");
    }

    // Always the same response, whether or not that email has an account -
    // otherwise this endpoint could be used to check which emails are registered.
    return Response.ok(new MessageDto("If that email is registered, a reset link has been sent."))
        .build();
  }

  // Read-only check so the frontend can tell the user a link is dead
  // before they fill out and submit the form.
  @GET
  @Path("/reset-password/validate")
  public Response validateResetToken(@QueryParam("token") String token) {
    if (token == null || token.isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
    if (PasswordResetToken.findValid(token) == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }
    return Response.ok().build();
  }

  @POST
  @Path("/reset-password/confirm")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response confirmPasswordReset(PasswordResetConfirmDto confirmDto) {
    if (confirmDto.getToken() == null
        || confirmDto.getToken().isEmpty()
        || confirmDto.getNewPassword() == null
        || confirmDto.getNewPassword().isEmpty()) {
      return Response.status(Response.Status.BAD_REQUEST)
          .entity(new MessageDto("Token and new password are required"))
          .build();
    }

    PasswordResetToken resetToken = PasswordResetToken.findValid(confirmDto.getToken());
    if (resetToken == null) {
      return Response.status(Response.Status.UNAUTHORIZED)
          .entity(new MessageDto("Invalid or expired reset link"))
          .build();
    }

    User user = resetToken.user;
    user.setPassword(confirmDto.getNewPassword());
    resetToken.used = true;

    // Invalidate every existing session for this user - if the password needed
    // resetting, any session an attacker already holds should die too.
    Session.delete("user", user);

    logger.info("Password reset completed for user: {}", user.getUsername());
    return Response.ok(new MessageDto("Password reset successfully.")).build();
  }

  // Delete the current user's own account
  @DELETE
  @Path("/me")
  @Transactional
  public Response deleteCurrentUser(@CookieParam("session") Cookie sessionCookie) {
    Session session = SessionAuth.requireValidSession(sessionCookie);
    if (session == null) {
      return Response.status(Response.Status.UNAUTHORIZED).build();
    }

    User user = session.user;
    Long userId = user.id;

    // Remove dependent rows first - Session and PasswordResetToken both have a
    // non-nullable FK to User, so deleting the user row while those still
    // reference it would violate that constraint at the database level.
    Session.delete("user", user);
    PasswordResetToken.delete("user", user);
    Panache.getEntityManager().clear();

    User.findById(userId).delete();

    logger.info("Deleted account for user: {}", user.getUsername());

    NewCookie expiredSessionCookie = CookieBuilder.expiredSessionCookie(cookieSecure);
    NewCookie expiredCsrf = CookieBuilder.expiredCsrfCookie(cookieSecure);

    return Response.ok(new MessageDto("Account deleted successfully."))
        .cookie(expiredSessionCookie, expiredCsrf)
        .build();
  }
}

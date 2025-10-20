package ru.itmo.isitmolab.controller;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import ru.itmo.isitmolab.dto.CredsDto;
import ru.itmo.isitmolab.service.AuthService;

import java.util.Map;

@Path("/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RequestScoped
public class AuthController {

    @Inject
    AuthService authService;

    @Context
    HttpServletRequest request;

    @Context
    HttpServletResponse response;

    @POST
    @Path("/login")
    public Response login(@Valid CredsDto creds) {
        HttpSession session = request.getSession(true);
        authService.login(creds, session);
        return Response.ok(Map.of("status", "ok")).build();
    }

    @GET
    @Path("/check-session")
    public Response check() {
        HttpSession session = request.getSession(false);
        boolean active = authService.isSessionActive(session);

        if (active) {
            return Response.ok(Map.of("status", "ok")).build();
        } else {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("message", "No active session"))
                    .build();
        }
    }

    @POST
    @Path("/logout")
    public Response logout() {
        HttpSession session = request.getSession(false);
        authService.logout(session);

        Cookie cookie = new Cookie("JSESSIONID", "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);

        return Response.ok(Map.of("status", "ok")).build();
    }

}

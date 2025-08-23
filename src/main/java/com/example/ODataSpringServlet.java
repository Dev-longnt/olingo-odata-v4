package com.example;

import org.apache.olingo.server.api.ODataHttpHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class ODataSpringServlet extends HttpServlet {

    private final ODataHttpHandler handler;

    @Autowired
    public ODataSpringServlet(ODataHttpHandler handler) {
        this.handler = handler;
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handler.process(req, resp);
    }
}
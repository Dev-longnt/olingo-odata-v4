package com.example;

import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.debug.DefaultDebugSupport;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

@Configuration
public class ODataConfig {

    @Bean
    public OData odata() {
        return OData.newInstance();
    }

    @Bean
    public DefaultEdmProvider defaultEdmProvider() {
        try {
            return new DefaultEdmProvider(DatabaseHelper.getConnection());
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Failed to get DB connection for DefaultEdmProvider", e);
        }
    }

    @Bean
    public DefaultProcessor defaultProcessor() {
        return new DefaultProcessor();
    }

    @Bean
    public ServiceMetadata serviceMetadata(OData odata, DefaultEdmProvider defaultEdmProvider) {
        return odata.createServiceMetadata(defaultEdmProvider, Collections.emptyList());
    }

    @Bean
    public ODataHttpHandler odataHandler(OData odata, ServiceMetadata serviceMetadata, DefaultProcessor defaultProcessor) {
        ODataHttpHandler handler = odata.createHandler(serviceMetadata);
        handler.register(defaultProcessor);
        handler.register(new DefaultDebugSupport());
        return handler;
    }

    @Bean
    public ServletRegistrationBean<ODataSpringServlet> odataServletRegistrationBean(ODataHttpHandler odataHandler) {
        return new ServletRegistrationBean<>(new ODataSpringServlet(odataHandler), "/odata/*");
    }
}

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
    public TestEdmProvider testEdmProvider() {
        return new TestEdmProvider();
    }

    @Bean
    public TestProcessor testProcessor() {
        return new TestProcessor();
    }

    @Bean
    public ServiceMetadata serviceMetadata(OData odata, TestEdmProvider testEdmProvider) {
        return odata.createServiceMetadata(testEdmProvider, Collections.emptyList());
    }

    @Bean
    public ODataHttpHandler odataHandler(OData odata, ServiceMetadata serviceMetadata, TestProcessor testProcessor) {
        ODataHttpHandler handler = odata.createHandler(serviceMetadata);
        handler.register(testProcessor);
        handler.register(new DefaultDebugSupport());
        return handler;
    }

    @Bean
    public ServletRegistrationBean<ODataSpringServlet> odataServletRegistrationBean(ODataHttpHandler odataHandler) {
        return new ServletRegistrationBean<>(new ODataSpringServlet(odataHandler), "/odata/*");
    }
}

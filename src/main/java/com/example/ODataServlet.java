package com.example;

import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataHttpHandler;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.processor.EntityCollectionProcessor;
import org.apache.olingo.server.api.processor.EntityProcessor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

public class ODataServlet extends HttpServlet {

    private ODataHttpHandler handler;
    private OData odata;
    private EntityCollectionProcessor entityCollectionProcessor;
    private EntityProcessor entityProcessor;

    public ODataServlet() {
        this.odata = OData.newInstance();
        this.entityCollectionProcessor = new TestProcessor();
        this.entityProcessor = new TestProcessor();
    }

    // Constructor for testing purposes (inject OData)
    public ODataServlet(OData odata) {
        this(); // Call default constructor to initialize processors
        this.odata = odata;
    }

    // Constructor for testing purposes (inject OData and processors)
    public ODataServlet(OData odata, EntityCollectionProcessor entityCollectionProcessor, EntityProcessor entityProcessor) {
        this.odata = odata;
        this.entityCollectionProcessor = entityCollectionProcessor;
        this.entityProcessor = entityProcessor;
    }

    @Override
    public void init() throws ServletException {
        super.init();
        ServiceMetadata edm = odata.createServiceMetadata(new TestEdmProvider(), Collections.emptyList());
        handler = odata.createHandler(edm);
        handler.register(entityCollectionProcessor);
        handler.register(entityProcessor);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        handler.process(req, resp);
    }
}
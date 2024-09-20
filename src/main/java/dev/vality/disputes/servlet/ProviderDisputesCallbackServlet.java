package dev.vality.disputes.servlet;

import dev.vality.disputes.callback.ProviderDisputesCallbackServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/disputes-api/v1/callback")
public class ProviderDisputesCallbackServlet extends GenericServlet {

    @Autowired
    private ProviderDisputesCallbackServiceSrv.Iface providerDisputesCallbackHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder()
                .build(ProviderDisputesCallbackServiceSrv.Iface.class, providerDisputesCallbackHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}

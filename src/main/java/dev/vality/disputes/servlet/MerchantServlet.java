package dev.vality.disputes.servlet;

import dev.vality.disputes.merchant.MerchantDisputesServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/v1/merchant")
public class MerchantServlet extends GenericServlet {

    @Autowired
    private MerchantDisputesServiceSrv.Iface merchantDisputesHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder()
                .build(MerchantDisputesServiceSrv.Iface.class, merchantDisputesHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}

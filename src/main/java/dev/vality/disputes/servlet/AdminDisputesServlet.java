package dev.vality.disputes.servlet;

import dev.vality.disputes.AdminDisputesServiceSrv;
import dev.vality.woody.thrift.impl.http.THServiceBuilder;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;

@WebServlet("/disputes-api/admin")
public class AdminDisputesServlet extends GenericServlet {

    @Autowired
    private AdminDisputesServiceSrv.Iface adminDisputesHandler;

    private Servlet servlet;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        servlet = new THServiceBuilder().build(AdminDisputesServiceSrv.Iface.class, adminDisputesHandler);
    }

    @Override
    public void service(ServletRequest request, ServletResponse response) throws ServletException, IOException {
        servlet.service(request, response);
    }
}

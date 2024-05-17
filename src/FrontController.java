package controller;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import annotation.*;
import utils.*;
import javax.servlet.*;
import javax.servlet.http.*;

public class FrontController extends HttpServlet {
    private List<String> controllerNames;
    boolean isEfaMisy = false;

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        if (!isEfaMisy) {
            try {
                String packageName = this.getInitParameter("nom_package");

                controllerNames = new ArrayList<>();

                List<Class<?>> annotatedClasses = Scan.getAllClassSelonAnnotation(this, packageName,
                        AnnotationController.class);

                out.println("Controller Names:");
                for (Class<?> clazz : annotatedClasses) {
                    controllerNames.add(clazz.getSimpleName());
                    out.println(clazz.getSimpleName());
                }
                isEfaMisy = true;
            } catch (Exception e) {
                e.printStackTrace(out);
            }
        } else {
            out.println("Controller Names:");
            for (String name : controllerNames) {
                out.println(name);
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        processRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        processRequest(request, response);
    }
}

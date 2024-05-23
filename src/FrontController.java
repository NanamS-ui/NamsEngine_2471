package controller;

import annotation.AnnotationController;
import utils.Mapping;
import utils.Scan;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;

public class FrontController extends HttpServlet {
    HashMap<String, Mapping> hashMap;

    @Override
    public void init() throws ServletException {
        try {
            String packageName = this.getInitParameter("nom_package");
            hashMap = Scan.getAllClassSelonAnnotation2(this, packageName, AnnotationController.class);
            System.out.println("Initialization completed. HashMap size: " + hashMap.size());
        } catch (Exception e) {
            System.out.println("Initialization error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    protected void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        String url = request.getRequestURI().substring(request.getContextPath().length());
        System.out.println("Request URI: " + url);

        if (hashMap != null) {
            Mapping mapping = hashMap.get(url);
            if (mapping != null) {
                out.println("URL: " + url);
                out.println("Method associated: " + mapping.getMethodName());
                out.println("With the class: " + mapping.getClassName());
            } else {
                out.println("No mapping found for URL: " + url);
            }
        } else {
            out.println("HashMap is null");
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

package controller;

import annotation.AnnotationController;
import utils.Mapping;
import utils.ModelView;
import utils.Scan;

import javax.servlet.RequestDispatcher; // Importing RequestDispatcher
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.HashMap;
import exception.*;

public class FrontController extends HttpServlet {
    private HashMap<String, Mapping> hashMap;

    @Override
    public void init() throws ServletException {
        try {
            String packageName = this.getInitParameter("nom_package");
            hashMap = Scan.getAllClassSelonAnnotation2(this, packageName, AnnotationController.class);
            System.out.println("Initialization completed. HashMap size: " + hashMap.size());
        } catch (PackageNotFoundException e) {
            throw new ServletException("Initialization error - Package not found: " + e.getMessage(), e);
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
                try {
                    Class<?> clazz = Class.forName(mapping.getClassName());
                    Method method = clazz.getDeclaredMethod(mapping.getMethodName());

                    Object instance = clazz.getDeclaredConstructor().newInstance();

                    Object result = method.invoke(instance);
                    if (result instanceof ModelView) {
                        ModelView modelView = (ModelView) result;
                        RequestDispatcher dispatch = request.getRequestDispatcher(modelView.getUrl());
                        HashMap<String, Object> data = modelView.getData();
                        for (String keyData : data.keySet()) {
                            request.setAttribute(keyData, data.get(keyData));
                            out.println(keyData);
                            out.println(data.get(keyData));
                        }
                        dispatch.forward(request, response);
                    } else if (result instanceof String) {
                        out.println(result.toString());
                    } else {
                        out.println("Tsisy methode");
                    }
                } catch (Exception e) {
                    out.println("Error invoking method: " + e.getMessage());
                    e.printStackTrace(out);
                }
            } else {
                out.println("No mapping found for URL: " + url);
            }
        } else {
            out.println("HashMap is null. Initialization may have failed.");
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

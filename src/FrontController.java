package controller;

import annotation.*;
import annotation.ValidationAnnotations.*;
import utils.*;

import com.google.gson.Gson;
import exception.*;

import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;

@MultipartConfig(fileSizeThreshold = 1024 * 1024 * 10, maxFileSize = 1024 * 1024 * 50, maxRequestSize = 1024 * 1024
        * 100)
public class FrontController extends HttpServlet {
    private HashMap<String, Mapping> hashMap;

    @Override
    public void init() throws ServletException {
        try {
            String packageName = this.getInitParameter("nom_package");
            hashMap = Scan.getAllClassSelonAnnotation3(this, packageName, AnnotationController.class);
            System.out.println("Initialization completed. HashMap size: " + hashMap.size());
        } catch (PackageNotFoundException e) {
            throw new ServletException("Initialization error - Package not found: " + e.getMessage(), e);
        }
    }

    private void processRequest(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = response.getWriter()) {
            String url = request.getRequestURI().substring(request.getContextPath().length());
            System.out.println("Request URI: " + url);

            if (hashMap == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "HashMap is null. Initialization failed.");
                return;
            }

            Mapping mapping = hashMap.get(url);
            if (mapping == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No mapping found for URL: " + url);
                return;
            }

            try {
                Class<?> clazz = Class.forName(mapping.getClassName());
                Method method = findMethod(clazz, mapping.getMethodName());
                if (method == null) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Method not found in class: " + clazz.getName());
                    return;
                }

                validateRequestVerb(request, mapping.getVerb(), url);

                Object instance = clazz.getDeclaredConstructor().newInstance();
                injectSession(instance, request);

                Object[] parameterValues = Utils.getParameterValues(request, response, method, Param.class,
                        ParamObject.class);

                Object result = method.invoke(instance, parameterValues);

                handleResponse(result, request, response, out, method);

            } catch (Exception e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Error processing request: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        return null;
    }

    private void validateRequestVerb(HttpServletRequest request, String expectedVerb, String url)
            throws ServletException {
        String requestMethod = request.getMethod();
        if (!expectedVerb.equalsIgnoreCase(requestMethod) && !expectedVerb.isEmpty()) {
            throw new ServletException("Method not allowed: " + requestMethod + " for URL: " + url);
        }
    }

    private void injectSession(Object instance, HttpServletRequest request) throws IllegalAccessException {
        Field sessionField = null;
        for (Field field : instance.getClass().getDeclaredFields()) {
            if (field.getType().equals(MySession.class)) {
                sessionField = field;
                break;
            }
        }
        if (sessionField != null) {
            sessionField.setAccessible(true);
            sessionField.set(instance, new MySession(request.getSession()));
        }
    }

    private void handleResponse(Object result, HttpServletRequest request, HttpServletResponse response,
            PrintWriter out, Method method) throws IOException, ServletException {
        if (method.isAnnotationPresent(ResponseBody.class)) {
            Gson gson = new Gson();
            String jsonResponse = gson.toJson(result);
            response.setContentType("application/json;charset=UTF-8");
            out.println(jsonResponse);
            return;
        }

        if (result instanceof ModelView) {
            ModelView modelView = (ModelView) result;
            HashMap<String, Object> data = modelView.getData();
            for (String key : data.keySet()) {
                request.setAttribute(key, data.get(key));
            }
            dispatchToPage(modelView.getUrl(), request, response);
            return;
        }

        if (result instanceof String) {
            String pageUrl = (String) result;
            dispatchToPage(pageUrl, request, response);
            return;
        }

        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unsupported return type.");
    }

    private void dispatchToPage(String pageUrl, HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (pageUrl != null && !pageUrl.isEmpty()) {
            RequestDispatcher dispatcher = request.getRequestDispatcher(pageUrl);
            dispatcher.forward(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Page URL is null or empty.");
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

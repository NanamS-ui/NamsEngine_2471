package utils;

import javax.servlet.http.*;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import javax.servlet.*;

import annotation.*;
import annotation.ValidationAnnotations.*;
import exception.*;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.io.*;

public class Utils {
    public static Object[] getParameterValues(HttpServletRequest request, HttpServletResponse response, Method method,
            Class<Param> paramAnnotationClass, Class<ParamObject> paramObjectAnnotationClass) {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            try {
                if (parameters[i].isAnnotationPresent(paramAnnotationClass)) {
                    handleParamAnnotation(request, parameters, parameterValues, i, paramAnnotationClass);
                } else if (parameters[i].isAnnotationPresent(paramObjectAnnotationClass)) {
                    handleParamObjectAnnotation(request, response, parameters, parameterValues, i,
                            paramObjectAnnotationClass, method);
                } else {
                    handleUnannotatedParameter(request, parameters, parameterValues, i);
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Erreur lors du traitement du paramètre : " + e.getMessage(), e);
            }
        }
        return parameterValues;
    }

    private static void handleParamAnnotation(HttpServletRequest request, Parameter[] parameters,
            Object[] parameterValues, int index, Class<Param> paramAnnotationClass)
            throws Exception {
        if (request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/")) {
            Part filePart = request.getPart("file");
            if (filePart != null) {
                parameterValues[index] = new Fichier(filePart);
            } else {
                throw new ServletException("Partie de fichier manquante.");
            }
        } else {
            Param param = parameters[index].getAnnotation(paramAnnotationClass);
            String paramValue = request.getParameter(param.value());
            parameterValues[index] = convertParameterValue(paramValue, parameters[index].getType());
        }
    }

    private static void handleParamObjectAnnotation(HttpServletRequest request, HttpServletResponse response,
            Parameter[] parameters, Object[] parameterValues, int index, Class<ParamObject> paramObjectAnnotationClass,
            Method method) throws Exception {
        ParamObject paramObjectAnnotation = parameters[index].getAnnotation(paramObjectAnnotationClass);
        String objName = paramObjectAnnotation.objName();

        Object paramObjectInstance = parameters[index].getType().getDeclaredConstructor().newInstance();

        Map<String, String> fieldValues = new HashMap<>();
        Map<String, String> validationErrors = new HashMap<>();

        Field[] fields = parameters[index].getType().getDeclaredFields();
        for (Field field : fields) {
            String paramValue = request.getParameter(objName + "." + field.getName());
            fieldValues.put(field.getName(), paramValue);
            field.setAccessible(true);

            if (paramValue != null) {
                field.set(paramObjectInstance, convertParameterValue(paramValue, field.getType()));
            }
        }

        try {
            validationErrors = validate(paramObjectInstance);
        } catch (ValidationException e) {
            throw new Exception("Erreur de validation : " + e.getMessage(), e);
        }

        if (!validationErrors.isEmpty()) {
            request.setAttribute("errors", validationErrors);
            request.setAttribute("values", fieldValues);

            OnError onError = method.getAnnotation(OnError.class);
            if (onError != null) {
                String errorUrl = onError.url();
                HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                    @Override
                    public String getMethod() {
                        return "GET";
                    }
                };
                RequestDispatcher dispatcher = request.getRequestDispatcher(errorUrl);
                dispatcher.forward(wrappedRequest, response);
                return;
            }
        }

        parameterValues[index] = paramObjectInstance;
    }

    private static void handleUnannotatedParameter(HttpServletRequest request, Parameter[] parameters,
            Object[] parameterValues, int index) {
        String paramValue = request.getParameter(parameters[index].getName());
        parameterValues[index] = convertParameterValue(paramValue, parameters[index].getType());
    }

    private static Object convertParameterValue(String value, Class<?> type) {
        if (type == String.class)
            return value;
        if (type == int.class || type == Integer.class)
            return Integer.parseInt(value);
        if (type == boolean.class || type == Boolean.class)
            return Boolean.parseBoolean(value);
        if (type == long.class || type == Long.class)
            return Long.parseLong(value);
        if (type == double.class || type == Double.class)
            return Double.parseDouble(value);
        if (type == float.class || type == Float.class)
            return Float.parseFloat(value);
        if (type == short.class || type == Short.class)
            return Short.parseShort(value);
        if (type == byte.class || type == Byte.class)
            return Byte.parseByte(value);
        if (type == char.class || type == Character.class) {
            if (value.length() != 1)
                throw new IllegalArgumentException("Valeur de caractère invalide : " + value);
            return value.charAt(0);
        }
        return null;
    }

    public static Map<String, String> validate(Object object) throws ValidationException {
        Map<String, String> errors = new HashMap<>();

        Field[] fields = object.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true);
            try {
                Object value = field.get(object);

                if (value == null || value.toString().trim().isEmpty()) {
                    errors.put(field.getName(), "Le champ " + field.getName() + " est requis.");
                }

                try {
                    validateField(field, value);
                } catch (ValidationException e) {
                    errors.put(field.getName(), e.getMessage());
                }
            } catch (IllegalAccessException e) {
                throw new ValidationException("Erreur lors de l'accès au champ " + field.getName(), e);
            }
        }

        return errors;
    }

    private static void validateField(Field field, Object value) throws ValidationException {
        if (field.isAnnotationPresent(NotNull.class) && value == null) {
            throw new ValidationException(field.getAnnotation(NotNull.class).message());
        }
        if (field.isAnnotationPresent(Min.class) && value instanceof Number) {
            long minValue = field.getAnnotation(Min.class).value();
            if (((Number) value).longValue() < minValue) {
                throw new ValidationException(
                        field.getAnnotation(Min.class).message().replace("{value}", String.valueOf(minValue)));
            }
        }
        if (field.isAnnotationPresent(Max.class) && value instanceof Number) {
            long maxValue = field.getAnnotation(Max.class).value();
            if (((Number) value).longValue() > maxValue) {
                throw new ValidationException(
                        field.getAnnotation(Max.class).message().replace("{value}", String.valueOf(maxValue)));
            }
        }
        if (field.isAnnotationPresent(Size.class) && value instanceof String) {
            Size annotation = field.getAnnotation(Size.class);
            int length = ((String) value).length();
            if (length < annotation.min() || length > annotation.max()) {
                throw new ValidationException(annotation.message().replace("{min}", String.valueOf(annotation.min()))
                        .replace("{max}", String.valueOf(annotation.max())));
            }
        }
    }
}

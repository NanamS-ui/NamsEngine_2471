package utils;

import javax.servlet.http.*;
import javax.servlet.http.Part;

import javax.servlet.*;

import annotation.*;
import annotation.ValidationAnnotations.*;
import exception.*;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.List;
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
                Class<?> paramType = parameters[i].getType();

                if (paramType.equals(HttpServletRequest.class)) {
                    parameterValues[i] = request;
                } else if (paramType.equals(HttpServletResponse.class)) {
                    parameterValues[i] = response;
                } else if (parameters[i].isAnnotationPresent(paramAnnotationClass)) {
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
            Object[] parameterValues, int index, Class<Param> paramAnnotationClass) throws Exception {

        if (request.getContentType() != null && request.getContentType().toLowerCase().startsWith("multipart/")) {
            Part filePart = request.getPart("file");
            parameterValues[index] = (filePart != null) ? new Fichier(filePart) : null;
        } else {
            Param param = parameters[index].getAnnotation(paramAnnotationClass);
            String[] paramValues = request.getParameterValues(param.value());

            if (paramValues == null || paramValues.length == 0) {
                parameterValues[index] = null;
            } else {
                parameterValues[index] = convertParameterValue(paramValues[0], parameters[index].getType());
            }
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
        if (value == null || value.trim().isEmpty()) {
            // Si c'est un type primitif, retourner une valeur par défaut
            if (type.isPrimitive()) {
                if (type == int.class)
                    return 0;
                if (type == long.class)
                    return 0L;
                if (type == double.class)
                    return 0.0;
                if (type == float.class)
                    return 0.0f;
                if (type == short.class)
                    return (short) 0;
                if (type == byte.class)
                    return (byte) 0;
                if (type == boolean.class)
                    return false;
                if (type == char.class)
                    return '\u0000'; // Caractère vide
            }
            return null; // Pour les objets (Integer, String, etc.)
        }

        if (type == String.class)
            return value;
        if (type == Integer.class || type == int.class)
            return Integer.parseInt(value);
        if (type == Boolean.class || type == boolean.class)
            return Boolean.parseBoolean(value);
        if (type == Long.class || type == long.class)
            return Long.parseLong(value);
        if (type == Double.class || type == double.class)
            return Double.parseDouble(value);
        if (type == Float.class || type == float.class)
            return Float.parseFloat(value);
        if (type == Short.class || type == short.class)
            return Short.parseShort(value);
        if (type == Byte.class || type == byte.class)
            return Byte.parseByte(value);
        if (type == Character.class || type == char.class) {
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

    public static void generateCsvResponse(List<?> data, String[] fields, String fileName, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        try (BufferedWriter writer = new BufferedWriter(response.getWriter())) {
            for (int i = 0; i < fields.length; i++) {
                writer.write(fields[i]);
                if (i < fields.length - 1)
                    writer.write(",");
            }
            writer.newLine();

            for (Object obj : data) {
                Class<?> clazz = obj.getClass();
                for (int i = 0; i < fields.length; i++) {
                    try {
                        Field field = clazz.getDeclaredField(fields[i]);
                        field.setAccessible(true);
                        Object value = field.get(obj);
                        writer.write(value != null ? value.toString() : "");
                    } catch (NoSuchFieldException | IllegalAccessException e) {
                        writer.write("");
                    }
                    if (i < fields.length - 1)
                        writer.write(",");
                }
                writer.newLine();
            }

            writer.flush();
        }
    }
}

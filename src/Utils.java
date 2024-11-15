package utils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.ServletException;
import javax.servlet.http.Part;
import annotation.*;
import annotation.ValidationAnnotations.*;
import exception.*;
import java.lang.reflect.*;
import java.util.regex.Matcher;
import java.io.*;

public class Utils {

    /**
     * Récupère les valeurs des paramètres à partir d'une requête HTTP et d'une
     * méthode cible.
     * 
     * @param request                    La requête HTTP.
     * @param method                     La méthode cible.
     * @param paramAnnotationClass       Classe de l'annotation @Param.
     * @param paramObjectAnnotationClass Classe de l'annotation @ParamObject.
     * @return Un tableau contenant les valeurs des paramètres.
     */
    public static Object[] getParameterValues(HttpServletRequest request, Method method,
            Class<Param> paramAnnotationClass,
            Class<ParamObject> paramObjectAnnotationClass) {
        Parameter[] parameters = method.getParameters();
        Object[] parameterValues = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            try {
                if (parameters[i].isAnnotationPresent(paramAnnotationClass)) {
                    handleParamAnnotation(request, parameters, parameterValues, i, paramAnnotationClass);
                } else if (parameters[i].isAnnotationPresent(paramObjectAnnotationClass)) {
                    handleParamObjectAnnotation(request, parameters, parameterValues, i, paramObjectAnnotationClass);
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

    /**
     * Gère les paramètres annotés avec @Param.
     */
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

    /**
     * Gère les paramètres annotés avec @ParamObject.
     */
    private static void handleParamObjectAnnotation(HttpServletRequest request, Parameter[] parameters,
            Object[] parameterValues, int index,
            Class<ParamObject> paramObjectAnnotationClass)
            throws Exception {
        ParamObject paramObject = parameters[index].getAnnotation(paramObjectAnnotationClass);
        String objName = paramObject.objName();
        Object paramObjectInstance = parameters[index].getType().getDeclaredConstructor().newInstance();

        Field[] fields = parameters[index].getType().getDeclaredFields();
        for (Field field : fields) {
            String paramValue = request.getParameter(objName + "." + field.getName());
            if (paramValue != null) {
                field.setAccessible(true);
                if (field.getType().equals(Integer.class) || field.getType().equals(Double.class)) {
                    validate(paramObjectInstance);
                }
                field.set(paramObjectInstance, convertParameterValue(paramValue, field.getType()));
            }
        }

        validate(paramObjectInstance);
        parameterValues[index] = paramObjectInstance;
    }

    /**
     * Gère les paramètres non annotés.
     */
    private static void handleUnannotatedParameter(HttpServletRequest request, Parameter[] parameters,
            Object[] parameterValues, int index) {
        String paramValue = request.getParameter(parameters[index].getName());
        parameterValues[index] = convertParameterValue(paramValue, parameters[index].getType());
    }

    /**
     * Convertit une valeur de paramètre en un type approprié.
     */
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

    /**
     * Valide un objet en fonction des annotations de validation appliquées à ses
     * champs.
     */
    public static void validate(Object object) throws ValidationException {
        if (object == null)
            throw new ValidationException("L'objet à valider ne peut pas être nul.");

        for (Field field : object.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(object);
                validateField(field, value);
            } catch (IllegalAccessException e) {
                throw new ValidationException("Erreur d'accès au champ : " + field.getName(), e);
            }
        }
    }

    /**
     * Valide un champ individuel.
     */
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

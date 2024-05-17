package utils;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import javax.servlet.http.*;
import java.util.ArrayList;
import java.util.List;

public class Scan {
    public static List<Class<?>> getAllClassSelonAnnotation(HttpServlet servlet, String packageName,
            Class<?> annotation) throws Exception {
        List<Class<?>> res = new ArrayList<>();

        String path = servlet.getClass().getClassLoader().getResource(packageName.replace('.', '/')).getPath();
        String decodedPath = URLDecoder.decode(path, "UTF-8");
        File packageDir = new File(decodedPath);

        File[] files = packageDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".class")) {
                    String className = packageName + "." + file.getName().replace(".class", "");
                    try {
                        Class<?> classe = Class.forName(className);
                        if (classe.isAnnotationPresent(annotation.asSubclass(java.lang.annotation.Annotation.class))) {
                            res.add(classe);
                        }
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        return res;
    }
}

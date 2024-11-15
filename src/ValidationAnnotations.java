package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public class ValidationAnnotations {

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NotNull {
        String message() default "Le champ ne peut pas être nul.";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Min {
        int value();

        String message() default "La valeur doit être supérieure ou égale à {value}.";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Max {
        int value();

        String message() default "La valeur doit être inférieure ou égale à {value}.";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Size {
        int min() default 0;

        int max() default Integer.MAX_VALUE;

        String message() default "La taille doit être entre {min} et {max}.";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Pattern {
        String regex();

        String message() default "Le champ ne respecte pas le format requis.";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Email {
        String message() default "Le champ doit contenir une adresse email valide.";
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Positive {
        String message() default "La valeur doit être strictement positive.";
    }
}

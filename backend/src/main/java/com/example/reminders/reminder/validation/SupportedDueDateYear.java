package com.example.reminders.reminder.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;

@Documented
@Constraint(validatedBy = SupportedDueDateYear.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface SupportedDueDateYear {

    String message() default "Due date year must be between 1000 and 9999";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<SupportedDueDateYear, LocalDate> {

        @Override
        public boolean isValid(LocalDate dueDate, ConstraintValidatorContext context) {
            return dueDate == null || (dueDate.getYear() >= 1000 && dueDate.getYear() <= 9999);
        }
    }
}

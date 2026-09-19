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

@Documented
@Constraint(validatedBy = ValidDueSchedule.Validator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDueSchedule {

    String message() default "Due time requires a due date";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidDueSchedule, DueSchedule> {

        @Override
        public boolean isValid(DueSchedule dueSchedule, ConstraintValidatorContext context) {
            if (dueSchedule == null
                    || dueSchedule.dueTime() == null
                    || dueSchedule.dueDate() != null) {
                return true;
            }

            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("dueTime")
                    .addConstraintViolation();
            return false;
        }
    }
}

package com.example.reminders.reminder.validation;

import java.time.LocalDate;
import java.time.LocalTime;

public interface DueSchedule {

    LocalDate dueDate();

    LocalTime dueTime();
}

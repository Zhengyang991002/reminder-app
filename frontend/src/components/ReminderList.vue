<script setup>
import ReminderItem from './ReminderItem.vue'

const props = defineProps({
  reminders: {
    type: Array,
    required: true
  },
  editingReminderId: {
    type: Number,
    default: null
  },
  updatingReminderIds: {
    type: Set,
    required: true
  },
  deletingReminderIds: {
    type: Set,
    required: true
  },
  savingReminderIds: {
    type: Set,
    required: true
  },
  editTitle: {
    type: String,
    required: true
  },
  editDueDate: {
    type: String,
    required: true
  },
  minimumDueDate: {
    type: String,
    required: true
  },
  maximumDueDate: {
    type: String,
    required: true
  }
})

const emit = defineEmits([
  'completion-change',
  'start-edit',
  'cancel-edit',
  'save-edit',
  'delete-reminder',
  'update:edit-title',
  'update:edit-due-date',
  'validate-edit-due-date'
])

function completeReminder(reminder, event) {
  emit('completion-change', reminder, event)
}

function startEditing(reminder) {
  emit('start-edit', reminder)
}

function cancelEditing() {
  emit('cancel-edit')
}

function saveReminder(reminder) {
  emit('save-edit', reminder)
}

function deleteReminder(reminder) {
  emit('delete-reminder', reminder)
}

function updateEditTitle(value) {
  emit('update:edit-title', value)
}

function updateEditDueDate(value) {
  emit('update:edit-due-date', value)
}

function validateEditDueDate(event) {
  emit('validate-edit-due-date', event)
}
</script>

<template>
  <ul class="reminder-list">
    <ReminderItem
      v-for="reminder in reminders"
      :key="reminder.id"
      :reminder="reminder"
      :is-editing="editingReminderId === reminder.id"
      :is-updating="updatingReminderIds.has(reminder.id)"
      :is-deleting="deletingReminderIds.has(reminder.id)"
      :is-saving="savingReminderIds.has(reminder.id)"
      :edit-title="editTitle"
      :edit-due-date="editDueDate"
      :minimum-due-date="minimumDueDate"
      :maximum-due-date="maximumDueDate"
      @completion-change="completeReminder(reminder, $event)"
      @start-edit="startEditing(reminder)"
      @cancel-edit="cancelEditing"
      @save-edit="saveReminder(reminder)"
      @delete-reminder="deleteReminder(reminder)"
      @update:edit-title="updateEditTitle"
      @update:edit-due-date="updateEditDueDate"
      @validate-edit-due-date="validateEditDueDate"
    />
  </ul>
</template>

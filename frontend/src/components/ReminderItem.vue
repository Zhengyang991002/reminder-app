<script setup>
import { computed } from 'vue'
import { getNotesLength, MAX_NOTES_LENGTH, validateNotes } from '../notes.js'

const props = defineProps({
  reminder: {
    type: Object,
    required: true
  },
  isEditing: {
    type: Boolean,
    required: true
  },
  isUpdating: {
    type: Boolean,
    required: true
  },
  isDeleting: {
    type: Boolean,
    required: true
  },
  isSaving: {
    type: Boolean,
    required: true
  },
  editTitle: {
    type: String,
    required: true
  },
  editNotes: {
    type: String,
    required: true
  },
  editDueDate: {
    type: String,
    required: true
  },
  editDueTime: {
    type: String,
    required: true
  },
  editPriority: {
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
  'update:edit-notes',
  'update:edit-due-date',
  'update:edit-due-time',
  'update:edit-priority',
  'validate-edit-due-schedule'
])

const notesLength = computed(() => getNotesLength(props.editNotes))
const notesError = computed(() => validateNotes(props.editNotes))

const dueDateFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  year: 'numeric'
})

const dueTimeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit'
})

const priorityLabels = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High'
}

function updateEditTitle(event) {
  emit('update:edit-title', event.target.value.trim())
}

function updateEditNotes(event) {
  emit('update:edit-notes', event.target.value)
}

function updateEditDueDate(event) {
  emit('update:edit-due-date', event.target.value)
}

function updateEditDueTime(event) {
  emit('update:edit-due-time', event.target.value)
}

function updateEditPriority(event) {
  emit('update:edit-priority', event.target.value)
}

function validateEditDueSchedule(event) {
  emit('validate-edit-due-schedule', event)
}

function formatDueSchedule(reminder) {
  const [year, month, day] = reminder.dueDate.split('-').map(Number)

  if (!reminder.dueTime) {
    return dueDateFormatter.format(new Date(year, month - 1, day))
  }

  const [hour, minute] = reminder.dueTime.slice(0, 5).split(':').map(Number)
  const dueDate = new Date(year, month - 1, day, hour, minute)
  const formattedDate = dueDateFormatter.format(dueDate)

  return `${formattedDate} · ${dueTimeFormatter.format(dueDate)}`
}

function priorityLabel(priority) {
  return priorityLabels[priority] ?? ''
}
</script>

<template>
  <li class="reminder-item">
    <form
      v-if="isEditing"
      class="reminder-form edit-form"
      @submit.prevent="emit('save-edit')"
    >
      <label>
        <span>Title</span>
        <input
          :value="editTitle"
          type="text"
          maxlength="255"
          required
          @input="updateEditTitle"
        />
      </label>

      <label>
        <span>Notes <small>(optional)</small></span>
        <textarea
          :value="editNotes"
          rows="3"
          :aria-invalid="Boolean(notesError)"
          :aria-describedby="notesError ? `edit-notes-count-${reminder.id} edit-notes-error-${reminder.id}` : `edit-notes-count-${reminder.id}`"
          @input="updateEditNotes"
        ></textarea>
        <small :id="`edit-notes-count-${reminder.id}`" class="notes-count">{{ notesLength }} / {{ MAX_NOTES_LENGTH }}</small>
        <span v-if="notesError" :id="`edit-notes-error-${reminder.id}`" class="notes-error error" role="alert">{{ notesError }}</span>
      </label>

      <label>
        <span>Due date <small>(optional)</small></span>
        <input
          :value="editDueDate"
          type="date"
          :min="minimumDueDate"
          :max="maximumDueDate"
          @input="updateEditDueDate"
          @change="validateEditDueSchedule"
          @invalid="validateEditDueSchedule"
        />
      </label>

      <label>
        <span>Due time <small>(optional)</small></span>
        <input
          :value="editDueTime"
          type="time"
          step="60"
          @input="updateEditDueTime"
          @change="validateEditDueSchedule"
          @invalid="validateEditDueSchedule"
        />
      </label>

      <label>
        <span>Priority</span>
        <select :value="editPriority" @change="updateEditPriority">
          <option value="NONE">None</option>
          <option value="LOW">Low</option>
          <option value="MEDIUM">Medium</option>
          <option value="HIGH">High</option>
        </select>
      </label>

      <div class="edit-actions">
        <button type="submit" :disabled="isSaving || Boolean(notesError)">
          {{ isSaving ? 'Saving...' : 'Save' }}
        </button>
        <button type="button" class="cancel-button" :disabled="isSaving" @click="emit('cancel-edit')">
          Cancel
        </button>
      </div>
    </form>

    <template v-else>
      <div class="reminder-details">
        <label class="reminder-title">
          <input
            type="checkbox"
            :checked="reminder.completed"
            :disabled="isUpdating"
            @change="emit('completion-change', $event)"
          />
          <span :class="{ completed: reminder.completed }">{{ reminder.title }}</span>
          <span v-if="priorityLabel(reminder.priority)" class="priority-indicator">
            {{ priorityLabel(reminder.priority) }}
          </span>
        </label>
        <p v-if="reminder.notes" class="reminder-notes-preview">{{ reminder.notes }}</p>
      </div>
      <div class="reminder-actions">
        <time
          v-if="reminder.dueDate"
          :datetime="reminder.dueTime ? `${reminder.dueDate}T${reminder.dueTime.slice(0, 5)}` : reminder.dueDate"
        >
          {{ formatDueSchedule(reminder) }}
        </time>
        <button type="button" class="edit-button" @click="emit('start-edit')">
          Edit
        </button>
        <button
          type="button"
          class="delete-button"
          :disabled="isDeleting"
          @click="emit('delete-reminder')"
        >
          {{ isDeleting ? 'Deleting...' : 'Delete' }}
        </button>
      </div>
    </template>
  </li>
</template>

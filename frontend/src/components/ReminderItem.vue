<script setup>
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
  editDueDate: {
    type: String,
    required: true
  },
  editDueTime: {
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
  'update:edit-due-time',
  'validate-edit-due-schedule'
])

const dueDateFormatter = new Intl.DateTimeFormat(undefined, {
  month: 'short',
  day: 'numeric',
  year: 'numeric'
})

const dueTimeFormatter = new Intl.DateTimeFormat(undefined, {
  hour: 'numeric',
  minute: '2-digit'
})

function updateEditTitle(event) {
  emit('update:edit-title', event.target.value.trim())
}

function updateEditDueDate(event) {
  emit('update:edit-due-date', event.target.value)
}

function updateEditDueTime(event) {
  emit('update:edit-due-time', event.target.value)
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

      <div class="edit-actions">
        <button type="submit" :disabled="isSaving">
          {{ isSaving ? 'Saving...' : 'Save' }}
        </button>
        <button type="button" class="cancel-button" :disabled="isSaving" @click="emit('cancel-edit')">
          Cancel
        </button>
      </div>
    </form>

    <template v-else>
      <label class="reminder-title">
        <input
          type="checkbox"
          :checked="reminder.completed"
          :disabled="isUpdating"
          @change="emit('completion-change', $event)"
        />
        <span :class="{ completed: reminder.completed }">{{ reminder.title }}</span>
      </label>
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

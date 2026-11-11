<script setup>
import { computed } from 'vue'
import { getNotesLength, MAX_NOTES_LENGTH, validateNotes } from '../notes.js'

const props = defineProps({
  title: {
    type: String,
    required: true
  },
  notes: {
    type: String,
    required: true
  },
  dueDate: {
    type: String,
    required: true
  },
  dueTime: {
    type: String,
    required: true
  },
  priority: {
    type: String,
    required: true
  },
  submitting: {
    type: Boolean,
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
  'update:title',
  'update:notes',
  'update:due-date',
  'update:due-time',
  'update:priority',
  'submit',
  'validate-due-schedule'
])

const notesLength = computed(() => getNotesLength(props.notes))
const notesError = computed(() => validateNotes(props.notes))

function updateTitle(event) {
  emit('update:title', event.target.value.trim())
}

function updateNotes(event) {
  emit('update:notes', event.target.value)
}

function updateDueDate(event) {
  emit('update:due-date', event.target.value)
}

function updateDueTime(event) {
  emit('update:due-time', event.target.value)
}

function updatePriority(event) {
  emit('update:priority', event.target.value)
}

function validateDueSchedule(event) {
  emit('validate-due-schedule', event)
}

function submitForm() {
  emit('submit')
}
</script>

<template>
  <form class="reminder-form" @submit.prevent="submitForm">
    <label>
      <span>Title</span>
      <input
        :value="title"
        type="text"
        maxlength="255"
        required
        placeholder="What needs to be done?"
        @input="updateTitle"
      />
    </label>

    <label>
      <span>Notes <small>(optional)</small></span>
      <textarea
        :value="notes"
        rows="3"
        :aria-invalid="Boolean(notesError)"
        :aria-describedby="notesError ? 'create-notes-count create-notes-error' : 'create-notes-count'"
        @input="updateNotes"
      ></textarea>
      <small id="create-notes-count" class="notes-count">{{ notesLength }} / {{ MAX_NOTES_LENGTH }}</small>
      <span v-if="notesError" id="create-notes-error" class="notes-error error" role="alert">{{ notesError }}</span>
    </label>

    <label>
      <span>Due date <small>(optional)</small></span>
      <input
        :value="dueDate"
        type="date"
        :min="minimumDueDate"
        :max="maximumDueDate"
        @input="updateDueDate"
        @change="validateDueSchedule"
        @invalid="validateDueSchedule"
      />
    </label>

    <label>
      <span>Due time <small>(optional)</small></span>
      <input
        :value="dueTime"
        type="time"
        step="60"
        @input="updateDueTime"
        @change="validateDueSchedule"
        @invalid="validateDueSchedule"
      />
    </label>

    <label>
      <span>Priority</span>
      <select :value="priority" @change="updatePriority">
        <option value="NONE">None</option>
        <option value="LOW">Low</option>
        <option value="MEDIUM">Medium</option>
        <option value="HIGH">High</option>
      </select>
    </label>

    <button type="submit" :disabled="submitting || Boolean(notesError)">
      {{ submitting ? 'Adding...' : 'Add reminder' }}
    </button>
  </form>
</template>

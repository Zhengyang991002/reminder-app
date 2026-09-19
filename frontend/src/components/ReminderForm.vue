<script setup>
defineProps({
  title: {
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
  'update:due-date',
  'update:due-time',
  'submit',
  'validate-due-schedule'
])

function updateTitle(event) {
  emit('update:title', event.target.value.trim())
}

function updateDueDate(event) {
  emit('update:due-date', event.target.value)
}

function updateDueTime(event) {
  emit('update:due-time', event.target.value)
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

    <button type="submit" :disabled="submitting">
      {{ submitting ? 'Adding...' : 'Add reminder' }}
    </button>
  </form>
</template>

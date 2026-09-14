<script setup>
import { onMounted, reactive, ref } from 'vue'
import ReminderForm from './components/ReminderForm.vue'
import ReminderList from './components/ReminderList.vue'

const heading = 'Reminders'
const minimumDueDate = getTodayDate()
const maximumDueDate = '9999-12-31'
const reminders = ref([])
const loading = ref(true)
const error = ref('')
const submitting = ref(false)
const submissionError = ref('')
const completionError = ref('')
const deleteError = ref('')
const editError = ref('')
const editingReminderId = ref(null)
const updatingReminderIds = reactive(new Set())
const deletingReminderIds = reactive(new Set())
const savingReminderIds = reactive(new Set())
const form = reactive({
  title: '',
  dueDate: ''
})
const editForm = reactive({
  title: '',
  dueDate: ''
})

function getTodayDate() {
  const today = new Date()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')

  return `${today.getFullYear()}-${month}-${day}`
}

function validateDueDate(dueDate) {
  if (!dueDate) {
    return ''
  }

  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dueDate)

  if (!match) {
    return 'Due date must use a four-digit year.'
  }

  const year = Number(match[1])

  if (year < 1000 || year > 9999) {
    return 'Due date year must be between 1000 and 9999.'
  }

  if (dueDate < minimumDueDate) {
    return 'Due date must be today or later.'
  }

  return ''
}

function validateCreateDueDate(event) {
  const validationError = validateDueDate(event.target.value)
  submissionError.value = validationError || (event.target.validity.valid ? '' : 'Enter a valid due date.')
}

function validateEditDueDate(event) {
  const validationError = validateDueDate(event.target.value)
  editError.value = validationError || (event.target.validity.valid ? '' : 'Enter a valid due date.')
}

onMounted(async () => {
  try {
    const response = await fetch('/api/reminders')

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    reminders.value = await response.json()
  } catch {
    error.value = 'Unable to load reminders.'
  } finally {
    loading.value = false
  }
})

async function createReminder() {
  submissionError.value = ''
  const dueDateError = validateDueDate(form.dueDate)

  if (dueDateError) {
    submissionError.value = dueDateError
    return
  }

  submitting.value = true

  try {
    const response = await fetch('/api/reminders', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        title: form.title,
        dueDate: form.dueDate || null
      })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const reminder = await response.json()
    reminders.value = [reminder, ...reminders.value]
    form.title = ''
    form.dueDate = ''
  } catch {
    submissionError.value = 'Unable to create reminder.'
  } finally {
    submitting.value = false
  }
}

async function updateCompletion(reminder, event) {
  if (updatingReminderIds.has(reminder.id)) {
    return
  }

  const completed = event.target.checked
  completionError.value = ''
  updatingReminderIds.add(reminder.id)

  try {
    const response = await fetch(`/api/reminders/${reminder.id}/completion`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ completed })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const updatedReminder = await response.json()
    const reminderIndex = reminders.value.findIndex(({ id }) => id === updatedReminder.id)

    if (reminderIndex !== -1) {
      reminders.value.splice(reminderIndex, 1, updatedReminder)
    }
  } catch {
    event.target.checked = reminder.completed
    completionError.value = 'Unable to update reminder completion.'
  } finally {
    updatingReminderIds.delete(reminder.id)
  }
}

async function deleteReminder(reminder) {
  if (deletingReminderIds.has(reminder.id)) {
    return
  }

  deleteError.value = ''
  deletingReminderIds.add(reminder.id)

  try {
    const response = await fetch(`/api/reminders/${reminder.id}`, {
      method: 'DELETE'
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    reminders.value = reminders.value.filter(({ id }) => id !== reminder.id)
  } catch {
    deleteError.value = 'Unable to delete reminder.'
  } finally {
    deletingReminderIds.delete(reminder.id)
  }
}

function startEditing(reminder) {
  editingReminderId.value = reminder.id
  editForm.title = reminder.title
  editForm.dueDate = reminder.dueDate || ''
  editError.value = ''
}

function cancelEditing() {
  editingReminderId.value = null
  editError.value = ''
}

async function saveReminder(reminder) {
  if (savingReminderIds.has(reminder.id)) {
    return
  }

  editError.value = ''
  const dueDateError = validateDueDate(editForm.dueDate)

  if (dueDateError) {
    editError.value = dueDateError
    return
  }

  savingReminderIds.add(reminder.id)

  try {
    const response = await fetch(`/api/reminders/${reminder.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        title: editForm.title,
        dueDate: editForm.dueDate || null
      })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const updatedReminder = await response.json()
    const reminderIndex = reminders.value.findIndex(({ id }) => id === updatedReminder.id)

    if (reminderIndex !== -1) {
      reminders.value.splice(reminderIndex, 1, updatedReminder)
    }

    if (editingReminderId.value === reminder.id) {
      editingReminderId.value = null
    }
  } catch {
    editError.value = 'Unable to update reminder.'
  } finally {
    savingReminderIds.delete(reminder.id)
  }
}
</script>

<template>
  <main class="app-shell">
    <h1>{{ heading }}</h1>

    <ReminderForm
      :title="form.title"
      :due-date="form.dueDate"
      :submitting="submitting"
      :minimum-due-date="minimumDueDate"
      :maximum-due-date="maximumDueDate"
      @update:title="form.title = $event"
      @update:due-date="form.dueDate = $event"
      @submit="createReminder"
      @validate-due-date="validateCreateDueDate"
    />

    <p v-if="submissionError" class="error" role="alert">{{ submissionError }}</p>
    <p v-if="completionError" class="error" role="alert">{{ completionError }}</p>
    <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
    <p v-if="editError" class="error" role="alert">{{ editError }}</p>

    <p v-if="loading">Loading reminders...</p>
    <p v-else-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="reminders.length === 0">No reminders yet.</p>

    <ReminderList
      v-else
      :reminders="reminders"
      :editing-reminder-id="editingReminderId"
      :updating-reminder-ids="updatingReminderIds"
      :deleting-reminder-ids="deletingReminderIds"
      :saving-reminder-ids="savingReminderIds"
      :edit-title="editForm.title"
      :edit-due-date="editForm.dueDate"
      :minimum-due-date="minimumDueDate"
      :maximum-due-date="maximumDueDate"
      @completion-change="updateCompletion"
      @start-edit="startEditing"
      @cancel-edit="cancelEditing"
      @save-edit="saveReminder"
      @delete-reminder="deleteReminder"
      @update:edit-title="editForm.title = $event"
      @update:edit-due-date="editForm.dueDate = $event"
      @validate-edit-due-date="validateEditDueDate"
    />
  </main>
</template>

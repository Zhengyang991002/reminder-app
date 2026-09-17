<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import ReminderForm from './components/ReminderForm.vue'
import ReminderList from './components/ReminderList.vue'
import ReminderListSidebar from './components/ReminderListSidebar.vue'

const minimumDueDate = getTodayDate()
const maximumDueDate = '9999-12-31'
const reminders = ref([])
const lists = ref([])
const selectedListId = ref(null)
const loading = ref(true)
const error = ref('')
const submitting = ref(false)
const submissionError = ref('')
const completionError = ref('')
const deleteError = ref('')
const editError = ref('')
const listError = ref('')
const showingNewListForm = ref(false)
const newListName = ref('')
const creatingList = ref(false)
const editingListId = ref(null)
const renameListName = ref('')
const savingListId = ref(null)
const editingReminderId = ref(null)
const updatingReminderIds = reactive(new Set())
const deletingReminderIds = reactive(new Set())
const savingReminderIds = reactive(new Set())
const deletingListIds = reactive(new Set())
const form = reactive({
  title: '',
  dueDate: ''
})
const editForm = reactive({
  title: '',
  dueDate: ''
})
const selectedList = computed(() =>
  lists.value.find(({ id }) => id === selectedListId.value) ?? null
)
const defaultListId = computed(() => {
  const defaultLists = lists.value.filter((list) => list.name === 'Reminders')

  return defaultLists.reduce(
    (lowestId, list) => (lowestId === null || list.id < lowestId ? list.id : lowestId),
    null
  )
})
const heading = computed(() => selectedList.value?.name ?? 'Reminders')
const filteredReminders = computed(() =>
  reminders.value.filter((reminder) => reminder.listId === selectedListId.value)
)

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

function validateListName(name) {
  const trimmedName = name.trim()

  if (!trimmedName) {
    return 'List name is required.'
  }

  if (trimmedName.length > 100) {
    return 'List name must be 100 characters or fewer.'
  }

  return ''
}

async function loadReminders() {
  const response = await fetch('/api/reminders')

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }

  reminders.value = await response.json()
}

async function loadLists() {
  const response = await fetch('/api/lists')

  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`)
  }

  lists.value = await response.json()
}

function selectDefaultList() {
  selectedListId.value = defaultListId.value ?? lists.value[0]?.id ?? null
}

onMounted(async () => {
  try {
    await Promise.all([loadReminders(), loadLists()])
    selectDefaultList()
  } catch {
    error.value = 'Unable to load reminders and lists.'
  } finally {
    loading.value = false
  }
})

function selectList(listId) {
  selectedListId.value = listId
}

function showNewListForm() {
  showingNewListForm.value = true
  newListName.value = ''
  editingListId.value = null
  renameListName.value = ''
  listError.value = ''
}

function cancelNewList() {
  showingNewListForm.value = false
  newListName.value = ''
  listError.value = ''
}

async function createList() {
  if (creatingList.value) {
    return
  }

  listError.value = ''
  const name = newListName.value.trim()
  const validationError = validateListName(name)

  if (validationError) {
    listError.value = validationError
    return
  }

  creatingList.value = true

  try {
    const response = await fetch('/api/lists', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ name })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const createdList = await response.json()
    lists.value = [...lists.value, createdList]
    selectedListId.value = createdList.id
    showingNewListForm.value = false
    newListName.value = ''
  } catch {
    listError.value = 'Unable to create reminder list.'
  } finally {
    creatingList.value = false
  }
}

function startRenamingList(list) {
  showingNewListForm.value = false
  newListName.value = ''
  editingListId.value = list.id
  renameListName.value = list.name
  listError.value = ''
}

function cancelRenamingList() {
  editingListId.value = null
  renameListName.value = ''
  listError.value = ''
}

async function renameList(list) {
  if (savingListId.value === list.id) {
    return
  }

  listError.value = ''
  const name = renameListName.value.trim()
  const validationError = validateListName(name)

  if (validationError) {
    listError.value = validationError
    return
  }

  savingListId.value = list.id

  try {
    const response = await fetch(`/api/lists/${list.id}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ name })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const updatedList = await response.json()
    const listIndex = lists.value.findIndex(({ id }) => id === updatedList.id)

    if (listIndex !== -1) {
      lists.value.splice(listIndex, 1, updatedList)
    }

    editingListId.value = null
    renameListName.value = ''
  } catch {
    listError.value = 'Unable to rename reminder list.'
  } finally {
    savingListId.value = null
  }
}

async function deleteList(listId) {
  if (deletingListIds.has(listId)) {
    return
  }

  listError.value = ''
  const deletedSelectedList = selectedListId.value === listId
  deletingListIds.add(listId)

  try {
    const response = await fetch(`/api/lists/${listId}`, {
      method: 'DELETE'
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    await Promise.all([loadLists(), loadReminders()])

    if (deletedSelectedList) {
      selectedListId.value = null
      editingReminderId.value = null
      selectDefaultList()
    }
  } catch {
    listError.value = 'Unable to delete reminder list.'
  } finally {
    deletingListIds.delete(listId)
  }
}

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
        dueDate: form.dueDate || null,
        listId: selectedListId.value
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
    <ReminderListSidebar
      :lists="lists"
      :selected-list-id="selectedListId"
      :default-list-id="defaultListId"
      :showing-new-list-form="showingNewListForm"
      :new-list-name="newListName"
      :creating-list="creatingList"
      :editing-list-id="editingListId"
      :rename-list-name="renameListName"
      :saving-list-id="savingListId"
      :deleting-list-ids="deletingListIds"
      :list-error="listError"
      @select-list="selectList"
      @show-new-list="showNewListForm"
      @cancel-new-list="cancelNewList"
      @create-list="createList"
      @update:new-list-name="newListName = $event"
      @start-rename-list="startRenamingList"
      @cancel-rename-list="cancelRenamingList"
      @rename-list="renameList"
      @update:rename-list-name="renameListName = $event"
      @delete-list="deleteList"
    />

    <section class="reminder-content">
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

      <p v-if="loading">Loading reminders and lists...</p>
      <p v-else-if="error" class="error" role="alert">{{ error }}</p>
      <p v-else-if="filteredReminders.length === 0">No reminders yet.</p>

      <ReminderList
        v-else
        :reminders="filteredReminders"
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
    </section>
  </main>
</template>

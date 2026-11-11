<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import ReminderForm from './components/ReminderForm.vue'
import ReminderList from './components/ReminderList.vue'
import ReminderListSidebar from './components/ReminderListSidebar.vue'
import { normalizeNotes, validateNotes } from './notes.js'

const minimumDueDate = getTodayDate()
const maximumDueDate = '9999-12-31'
const lists = ref([])
let listsRequestId = 0
const listsLoading = ref(true)
const listsLoadError = ref('')
const viewCounts = ref(null)
let countsRequestId = 0
const countsError = ref('')
const selectedView = ref({ type: 'list', id: null })
const searchQuery = ref('')
const searchMode = ref('global')
const searchInput = ref(null)
const pageSize = 20
const pageNumber = ref(0)
const pageResult = ref(null)
const pageLoading = ref(true)
const pageError = ref('')
let searchTimer = null
let pageRequest = null
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
  notes: '',
  dueDate: '',
  dueTime: '',
  priority: 'NONE'
})
const editForm = reactive({
  title: '',
  notes: '',
  dueDate: '',
  dueTime: '',
  priority: 'NONE'
})
const selectedList = computed(() => {
  if (selectedView.value.type !== 'list') {
    return null
  }

  return lists.value.find(({ id }) => id === selectedView.value.id) ?? null
})
const defaultListId = computed(() => {
  const defaultLists = lists.value.filter((list) => list.name === 'Reminders')

  return defaultLists.reduce(
    (lowestId, list) => (lowestId === null || list.id < lowestId ? list.id : lowestId),
    null
  )
})
const smartLists = computed(() => [
  { key: 'today', name: 'Today', count: viewCounts.value?.today ?? '—' },
  { key: 'scheduled', name: 'Scheduled', count: viewCounts.value?.scheduled ?? '—' },
  { key: 'all', name: 'All', count: viewCounts.value?.all ?? '—' }
])
const heading = computed(() => {
  if (selectedView.value.type === 'smart') {
    return smartLists.value.find((smartList) => smartList.key === selectedView.value.key)?.name ?? 'Reminders'
  }

  return selectedList.value?.name ?? 'Reminders'
})
const selectedViewKey = computed(() => selectedView.value.type === 'list'
  ? `list:${selectedView.value.id}`
  : selectedView.value.key)
const searchTerm = computed(() => normalizeNotes(searchQuery.value))
const displayHeading = computed(() => {
  if (searchTerm.value && searchMode.value === 'global') {
    return 'Search all reminders'
  }

  return heading.value
})
const displayedReminders = computed(() => pageResult.value?.content ?? [])

watch([selectedViewKey, searchTerm, searchMode], ([, term], [, previousTerm]) => {
  cancelPendingPage()
  pageNumber.value = 0
  pageResult.value = null
  pageError.value = ''
  pageLoading.value = true

  if (term && term !== previousTerm) {
    searchTimer = setTimeout(loadPage, 300)
  } else {
    void loadPage()
  }
}, { flush: 'sync' })

onBeforeUnmount(() => {
  cancelPendingPage()
  countsRequestId += 1
  listsRequestId += 1
})

function getPageUrl() {
  const scope = searchTerm.value ? searchMode.value : 'view'
  const params = new URLSearchParams({ scope })
  if (searchTerm.value) {
    params.set('q', searchTerm.value)
  }
  if (scope === 'view') {
    const view = selectedView.value
    if (view.type === 'list') {
      if (view.id === null) {
        return null
      }
      params.set('view', 'list')
      params.set('listId', view.id)
    } else {
      params.set('view', view.key)
      if (view.key === 'today') {
        params.set('today', getTodayDate())
      }
    }
  }

  return `/api/reminders/page?${params}`
}

function getTodayDate() {
  const today = new Date()
  const month = String(today.getMonth() + 1).padStart(2, '0')
  const day = String(today.getDate()).padStart(2, '0')

  return `${today.getFullYear()}-${month}-${day}`
}

function clearSearch() {
  searchQuery.value = ''
  searchInput.value?.focus()
}

function cancelPendingPage() {
  clearTimeout(searchTimer)
  searchTimer = null
  pageRequest?.abort()
  pageRequest = null
}

async function changePage(page) {
  if (!pageResult.value || pageLoading.value || pageError.value
      || page < 0 || page >= pageResult.value.totalPages) {
    return
  }

  pageNumber.value = page
  await loadPage()
}

async function loadPage() {
  const url = getPageUrl()
  if (!url) {
    return
  }

  cancelPendingPage()
  const request = new AbortController()
  pageRequest = request
  pageLoading.value = true
  pageError.value = ''
  let failureMessage = searchTerm.value ? 'Unable to search reminders.' : 'Unable to load reminders.'
  let page = pageNumber.value

  try {
    while (true) {
      const response = await fetch(`${url}&page=${page}&size=${pageSize}`, { signal: request.signal })
      if (!response.ok) {
        const problem = await response.json().catch(() => null)
        if (problem?.detail) {
          failureMessage = problem.detail
        }
        throw new Error(`Request failed with status ${response.status}`)
      }

      const results = await response.json()
      if (request !== pageRequest) {
        return
      }

      // A deletion or edit can remove the last match on this page.
      const lastPage = Math.max(0, results.totalPages - 1)
      if (page > lastPage) {
        page = lastPage
        pageNumber.value = page
        continue
      }

      pageResult.value = results
      pageNumber.value = results.page
      break
    }
  } catch {
    if (request === pageRequest) {
      pageResult.value = null
      pageError.value = failureMessage
    }
  } finally {
    if (request === pageRequest) {
      pageRequest = null
      pageLoading.value = false
    }
  }
}

function toTimeInputValue(dueTime) {
  return dueTime ? dueTime.slice(0, 5) : ''
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

function validateDueSchedule(dueDate, dueTime) {
  const dueDateError = validateDueDate(dueDate)

  if (dueDateError) {
    return dueDateError
  }

  if (dueTime && !dueDate) {
    return 'A due date is required when a due time is set.'
  }

  return ''
}

function validateCreateDueSchedule(event) {
  const validationError = validateDueSchedule(form.dueDate, form.dueTime)
  submissionError.value = validationError || (event.target.validity.valid ? '' : 'Enter a valid due date or time.')
}

function validateEditDueSchedule(event) {
  const validationError = validateDueSchedule(editForm.dueDate, editForm.dueTime)
  editError.value = validationError || (event.target.validity.valid ? '' : 'Enter a valid due date or time.')
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

async function loadCounts() {
  const requestId = ++countsRequestId
  countsError.value = ''
  try {
    const params = new URLSearchParams({ today: getTodayDate() })
    const response = await fetch(`/api/reminders/counts?${params}`)
    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const results = await response.json()
    if (requestId === countsRequestId) {
      viewCounts.value = results
    }
  } catch {
    if (requestId === countsRequestId) {
      countsError.value = 'Unable to refresh reminder counts.'
    }
  }
}

async function refreshRemindersAfterMutation() {
  await Promise.all([loadPage(), loadCounts()])
}

async function loadLists() {
  const requestId = ++listsRequestId
  listsLoadError.value = ''
  try {
    const response = await fetch('/api/lists')
    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    const results = await response.json()
    if (requestId === listsRequestId) {
      lists.value = results
    }
  } catch {
    if (requestId === listsRequestId) {
      listsLoadError.value = 'Unable to refresh reminder lists.'
    }
  }
}

function selectDefaultList() {
  const listId = defaultListId.value ?? lists.value[0]?.id
  selectedView.value = listId === undefined
    ? { type: 'smart', key: 'all' }
    : { type: 'list', id: listId }
}

onMounted(async () => {
  void loadCounts()
  await loadLists()
  if (selectedView.value.type === 'list' && selectedView.value.id === null) {
    selectDefaultList()
  }
  listsLoading.value = false
})

function selectList(listId) {
  selectedView.value = { type: 'list', id: listId }
}

function selectSmartList(key) {
  selectedView.value = { type: 'smart', key }
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
    listsRequestId += 1
    lists.value = [...lists.value, createdList]
    selectedView.value = { type: 'list', id: createdList.id }
    showingNewListForm.value = false
    newListName.value = ''
    await Promise.all([loadLists(), loadCounts()])
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
  if (savingListId.value !== null || deletingListIds.has(list.id)) {
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
    listsRequestId += 1
    const listIndex = lists.value.findIndex(({ id }) => id === updatedList.id)

    if (listIndex !== -1) {
      lists.value.splice(listIndex, 1, updatedList)
    }

    if (editingListId.value === list.id) {
      editingListId.value = null
      renameListName.value = ''
    }
    await loadLists()
  } catch {
    listError.value = 'Unable to rename reminder list.'
  } finally {
    savingListId.value = null
  }
}

async function deleteList(listId) {
  if (deletingListIds.has(listId) || savingListId.value === listId) {
    return
  }

  listError.value = ''
  deletingListIds.add(listId)

  try {
    const response = await fetch(`/api/lists/${listId}`, {
      method: 'DELETE'
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    listsRequestId += 1
    lists.value = lists.value.filter(({ id }) => id !== listId)
    let pageRefresh
    if (selectedView.value.type === 'list' && selectedView.value.id === listId) {
      editingReminderId.value = null
      // Selecting the fallback triggers the page request through the watcher.
      selectDefaultList()
    } else {
      pageRefresh = loadPage()
    }

    await Promise.all([loadLists(), loadCounts(), pageRefresh])
  } catch {
    listError.value = 'Unable to delete reminder list.'
  } finally {
    deletingListIds.delete(listId)
  }
}

async function createReminder() {
  submissionError.value = ''
  const validationError = validateDueSchedule(form.dueDate, form.dueTime) || validateNotes(form.notes)
  const listId = selectedView.value.type === 'list'
    ? selectedView.value.id
    : defaultListId.value

  if (validationError) {
    submissionError.value = validationError
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
        notes: normalizeNotes(form.notes),
        dueDate: form.dueDate || null,
        dueTime: form.dueTime || null,
        priority: form.priority,
        listId
      })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    form.title = ''
    form.notes = ''
    form.dueDate = ''
    form.dueTime = ''
    form.priority = 'NONE'
    await refreshRemindersAfterMutation()
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

    await refreshRemindersAfterMutation()
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

    await refreshRemindersAfterMutation()
  } catch {
    deleteError.value = 'Unable to delete reminder.'
  } finally {
    deletingReminderIds.delete(reminder.id)
  }
}

function startEditing(reminder) {
  editingReminderId.value = reminder.id
  editForm.title = reminder.title
  editForm.notes = reminder.notes ?? ''
  editForm.dueDate = reminder.dueDate || ''
  editForm.dueTime = toTimeInputValue(reminder.dueTime)
  editForm.priority = reminder.priority || 'NONE'
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
  const validationError = validateDueSchedule(editForm.dueDate, editForm.dueTime) || validateNotes(editForm.notes)

  if (validationError) {
    editError.value = validationError
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
        notes: normalizeNotes(editForm.notes),
        dueDate: editForm.dueDate || null,
        dueTime: editForm.dueTime || null,
        priority: editForm.priority
      })
    })

    if (!response.ok) {
      throw new Error(`Request failed with status ${response.status}`)
    }

    if (editingReminderId.value === reminder.id) {
      editingReminderId.value = null
    }
    await refreshRemindersAfterMutation()
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
      :smart-lists="smartLists"
      :list-counts="viewCounts?.lists"
      :counts-error="countsError"
      :selected-view="selectedView"
      :default-list-id="defaultListId"
      :showing-new-list-form="showingNewListForm"
      :new-list-name="newListName"
      :creating-list="creatingList"
      :editing-list-id="editingListId"
      :rename-list-name="renameListName"
      :saving-list-id="savingListId"
      :deleting-list-ids="deletingListIds"
      :list-error="listError || listsLoadError"
      @select-list="selectList"
      @select-smart-list="selectSmartList"
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
      <h1>{{ displayHeading }}</h1>

      <ReminderForm
        :title="form.title"
        :notes="form.notes"
        :due-date="form.dueDate"
        :due-time="form.dueTime"
        :priority="form.priority"
        :submitting="submitting"
        :minimum-due-date="minimumDueDate"
        :maximum-due-date="maximumDueDate"
        @update:title="form.title = $event"
        @update:notes="form.notes = $event"
        @update:due-date="form.dueDate = $event"
        @update:due-time="form.dueTime = $event"
        @update:priority="form.priority = $event"
        @submit="createReminder"
        @validate-due-schedule="validateCreateDueSchedule"
      />

      <p v-if="submissionError" class="error" role="alert">{{ submissionError }}</p>
      <p v-if="completionError" class="error" role="alert">{{ completionError }}</p>
      <p v-if="deleteError" class="error" role="alert">{{ deleteError }}</p>
      <p v-if="editError" class="error" role="alert">{{ editError }}</p>

      <div class="reminder-search" role="search" aria-label="Reminders">
        <label class="visually-hidden" for="search-query">Search reminders</label>
        <input
          id="search-query"
          ref="searchInput"
          v-model="searchQuery"
          type="text"
          placeholder="Search titles and notes"
          :disabled="listsLoading"
          @keydown.esc="clearSearch"
        >
        <label class="visually-hidden" for="search-mode">Search scope</label>
        <select id="search-mode" v-model="searchMode" :disabled="listsLoading">
          <option value="global">All reminders</option>
          <option value="view">Current view</option>
        </select>
        <button v-if="searchQuery" class="cancel-button" type="button" @click="clearSearch">
          Clear search
        </button>
      </div>

      <p v-if="pageLoading" role="status">{{ searchTerm ? 'Searching reminders...' : 'Loading reminders...' }}</p>
      <div v-else-if="pageError">
        <p class="error" role="alert">{{ pageError }}</p>
        <button class="cancel-button" type="button" @click="loadPage">Retry</button>
      </div>
      <p v-else-if="displayedReminders.length === 0" role="status">
        {{ searchTerm ? 'No reminders match your search.' : 'No reminders in this view.' }}
      </p>

      <ReminderList
        v-else
        :reminders="displayedReminders"
        :editing-reminder-id="editingReminderId"
        :updating-reminder-ids="updatingReminderIds"
        :deleting-reminder-ids="deletingReminderIds"
        :saving-reminder-ids="savingReminderIds"
        :edit-title="editForm.title"
        :edit-notes="editForm.notes"
        :edit-due-date="editForm.dueDate"
        :edit-due-time="editForm.dueTime"
        :edit-priority="editForm.priority"
        :minimum-due-date="minimumDueDate"
        :maximum-due-date="maximumDueDate"
        @completion-change="updateCompletion"
        @start-edit="startEditing"
        @cancel-edit="cancelEditing"
        @save-edit="saveReminder"
        @delete-reminder="deleteReminder"
        @update:edit-title="editForm.title = $event"
        @update:edit-notes="editForm.notes = $event"
        @update:edit-due-date="editForm.dueDate = $event"
        @update:edit-due-time="editForm.dueTime = $event"
        @update:edit-priority="editForm.priority = $event"
        @validate-edit-due-schedule="validateEditDueSchedule"
      />

      <nav v-if="pageResult" class="reminder-pagination" aria-label="Reminder pages">
        <button
          class="cancel-button"
          type="button"
          :disabled="pageLoading || pageNumber === 0"
          @click="changePage(pageNumber - 1)"
        >
          Previous
        </button>
        <span v-if="!pageLoading" role="status">
          Page {{ pageResult.totalPages === 0 ? 0 : pageNumber + 1 }} of {{ pageResult.totalPages }}
          · {{ pageResult.totalElements }} {{ searchTerm ? 'matching reminders' : 'reminders' }}
        </span>
        <button
          class="cancel-button"
          type="button"
          :disabled="pageLoading || pageNumber + 1 >= pageResult.totalPages"
          @click="changePage(pageNumber + 1)"
        >
          Next
        </button>
      </nav>
    </section>
  </main>
</template>

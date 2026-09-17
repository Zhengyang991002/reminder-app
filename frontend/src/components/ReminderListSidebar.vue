<script setup>
import { ref } from 'vue'

defineProps({
  lists: {
    type: Array,
    required: true
  },
  selectedListId: {
    type: Number,
    default: null
  },
  defaultListId: {
    type: Number,
    default: null
  },
  showingNewListForm: {
    type: Boolean,
    required: true
  },
  newListName: {
    type: String,
    required: true
  },
  creatingList: {
    type: Boolean,
    required: true
  },
  editingListId: {
    type: Number,
    default: null
  },
  renameListName: {
    type: String,
    required: true
  },
  savingListId: {
    type: Number,
    default: null
  },
  deletingListIds: {
    required: true
  },
  listError: {
    type: String,
    required: true
  }
})

const emit = defineEmits([
  'select-list',
  'show-new-list',
  'cancel-new-list',
  'create-list',
  'update:new-list-name',
  'start-rename-list',
  'cancel-rename-list',
  'rename-list',
  'update:rename-list-name',
  'delete-list'
])

const openActionsListId = ref(null)

function selectList(listId) {
  openActionsListId.value = null
  emit('select-list', listId)
}

function toggleListActions(listId) {
  openActionsListId.value = openActionsListId.value === listId ? null : listId
}

function startRenamingList(list) {
  openActionsListId.value = null
  emit('start-rename-list', list)
}
</script>

<template>
  <aside class="reminder-list-sidebar" aria-label="Reminder lists">
    <div class="sidebar-header">
      <p class="sidebar-heading">Lists</p>
      <button
        class="sidebar-action-button"
        type="button"
        :disabled="showingNewListForm || creatingList"
        @click="emit('show-new-list')"
      >
        + New List
      </button>
    </div>

    <form
      v-if="showingNewListForm"
      class="inline-list-form"
      novalidate
      @submit.prevent="emit('create-list')"
    >
      <label class="visually-hidden" for="new-list-name">New list name</label>
      <input
        id="new-list-name"
        class="list-input"
        type="text"
        :value="newListName"
        maxlength="100"
        required
        :disabled="creatingList"
        @input="emit('update:new-list-name', $event.target.value)"
      >
      <div class="inline-list-actions">
        <button class="sidebar-action-button" type="submit" :disabled="creatingList">
          {{ creatingList ? 'Creating…' : 'Create' }}
        </button>
        <button type="button" class="list-action-button" :disabled="creatingList" @click="emit('cancel-new-list')">
          Cancel
        </button>
      </div>
    </form>

    <p v-if="listError" class="error sidebar-error" role="alert">{{ listError }}</p>

    <ul class="list-navigation">
      <li v-for="list in lists" :key="list.id" class="list-navigation-item">
        <form
          v-if="editingListId === list.id"
          class="inline-list-form list-rename-form"
          novalidate
          @submit.prevent="emit('rename-list', list)"
        >
          <label class="visually-hidden" :for="`rename-list-${list.id}`">Rename list</label>
          <input
            :id="`rename-list-${list.id}`"
            class="list-input"
            type="text"
            :value="renameListName"
            maxlength="100"
            required
            :disabled="savingListId === list.id"
            @input="emit('update:rename-list-name', $event.target.value)"
          >
          <div class="inline-list-actions">
            <button class="sidebar-action-button" type="submit" :disabled="savingListId === list.id">
              {{ savingListId === list.id ? 'Saving…' : 'Save' }}
            </button>
            <button
              type="button"
              class="list-action-button"
              :disabled="savingListId === list.id"
              @click="emit('cancel-rename-list')"
            >
              Cancel
            </button>
          </div>
        </form>

        <template v-else>
          <button
            class="list-select-button"
            type="button"
            :class="{ 'is-selected': list.id === selectedListId }"
            :aria-current="list.id === selectedListId ? 'page' : undefined"
            @click="selectList(list.id)"
          >
            {{ list.name }}
          </button>

          <button
            v-if="list.id !== defaultListId"
            class="list-actions-toggle"
            type="button"
            :aria-label="`Actions for ${list.name}`"
            :aria-expanded="openActionsListId === list.id"
            :aria-controls="`list-actions-${list.id}`"
            :disabled="deletingListIds.has(list.id)"
            @click.stop="toggleListActions(list.id)"
          >
            <span aria-hidden="true">…</span>
          </button>

          <div
            v-if="list.id !== defaultListId && openActionsListId === list.id"
            :id="`list-actions-${list.id}`"
            class="list-actions-menu"
            role="group"
            :aria-label="`Actions for ${list.name}`"
          >
            <button
              type="button"
              class="list-action-button"
              :disabled="deletingListIds.has(list.id)"
              @click="startRenamingList(list)"
            >
              Rename
            </button>
            <button
              type="button"
              class="list-action-button list-delete-button"
              :disabled="deletingListIds.has(list.id)"
              @click="emit('delete-list', list.id)"
            >
              {{ deletingListIds.has(list.id) ? 'Deleting…' : 'Delete' }}
            </button>
          </div>
        </template>
      </li>
    </ul>
  </aside>
</template>

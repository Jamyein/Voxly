package com.voxly.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.navigation3.runtime.NavKey
import com.voxly.presentation.navigation.FileBrowser
import com.voxly.presentation.navigation.TopLevelBackStack
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Activity-scoped holder for the Navigation3 top-level back stack.
 *
 * Survival guarantee: the previous `remember { TopLevelBackStack(...) }` was a plain
 * [androidx.compose.runtime.remember] — any activity recreation reset the navigation to the
 * start destination (home tab). Recreation happens on the FIRST in-app language switch:
 * AppCompatDelegate.setApplicationLocales() delegates to the system LocaleManager on
 * API 33+, and the system relaunches the activity the first time a per-app locale is
 * applied (subsequent changes are in-place config changes honored by
 * `android:configChanges="locale|layoutDirection"`). Hoisting the back stack into an
 * activity-scoped ViewModel keeps the user on the exact screen they were on across that
 * recreation. (Process death is not covered here; per-entry UI state is still restored by
 * the SaveableStateHolderNavEntryDecorator.)
 */
@HiltViewModel
class NavigationStateViewModel @Inject constructor() : ViewModel() {
    val topLevelBackStack: TopLevelBackStack<NavKey> = TopLevelBackStack(FileBrowser)
}

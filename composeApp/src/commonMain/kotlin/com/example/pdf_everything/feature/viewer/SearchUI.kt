package com.example.pdf_everything.feature.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.example.pdf_everything.ui.design_system.PdfIcons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.pdf_everything.core.search.SearchEngine
import com.example.pdf_everything.core.search.SearchState
import com.example.pdf_everything.core.search.SearchResult
import com.example.pdf_everything.core.document.Document
import com.example.pdf_everything.ui.design_system.Spacing

/* ═══════════════════════════════════════════════════════════════════════
 *  Search UI — per spec §16, §41
 *
 *  Features:
 *    - Search bar in top area (appears on Ctrl+F / search icon)
 *    - Case-sensitive / whole-word toggles
 *    - Result count display
 *    - Next/previous navigation
 *    - Highlight matches on pages
 *    - Current match indicator
 * ═══════════════════════════════════════════════════════════════════════ */

class SearchUIState {
    private var _isSearchBarVisible by mutableStateOf(false)
    val isSearchBarVisible: Boolean get() = _isSearchBarVisible

    private var _searchState by mutableStateOf(SearchState())
    val searchState: SearchState get() = _searchState

    fun showSearchBar() { _isSearchBarVisible = true }
    fun hideSearchBar() {
        _isSearchBarVisible = false
        _searchState = _searchState.copy(
            query = "",
            results = emptyList(),
            currentIndex = -1,
            isSearching = false
        )
    }

    fun updateQuery(query: String) {
        _searchState = _searchState.copy(query = query)
    }

    fun setSearchResults(results: List<SearchResult>) {
        _searchState = _searchState.copy(
            results = results,
            currentIndex = if (results.isEmpty()) -1 else 0,
            isSearching = false
        )
    }

    fun setSearching(isSearching: Boolean) {
        _searchState = _searchState.copy(isSearching = isSearching)
    }

    fun nextResult() {
        _searchState = SearchEngine().nextResult(_searchState)
    }

    fun previousResult() {
        _searchState = SearchEngine().previousResult(_searchState)
    }

    fun toggleCaseSensitive() {
        _searchState = _searchState.copy(
            caseSensitive = !_searchState.caseSensitive
        )
    }

    fun toggleWholeWord() {
        _searchState = _searchState.copy(
            wholeWord = !_searchState.wholeWord
        )
    }
}

@Composable
fun SearchBar(
    searchUIState: SearchUIState,
    onSearch: (query: String, caseSensitive: Boolean, wholeWord: Boolean) -> Unit,
    onNextResult: () -> Unit,
    onPreviousResult: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = searchUIState.searchState
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = Spacing.xs
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search input
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { searchUIState.updateQuery(it) },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("Search in PDF…") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch(
                                state.query,
                                state.caseSensitive,
                                state.wholeWord
                            )
                        }
                    ),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    searchUIState.updateQuery("")
                                    searchUIState.setSearchResults(emptyList())
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    PdfIcons.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                )

                Spacer(Modifier.width(Spacing.sm))

                // Case sensitive toggle
                FilterChip(
                    selected = state.caseSensitive,
                    onClick = { searchUIState.toggleCaseSensitive() },
                    label = { Text("Aa", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )

                // Whole word toggle
                FilterChip(
                    selected = state.wholeWord,
                    onClick = { searchUIState.toggleWholeWord() },
                    label = { Text("W", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(32.dp)
                )

                Spacer(Modifier.width(Spacing.sm))

                // Previous result
                IconButton(
                    onClick = onPreviousResult,
                    enabled = state.hasResults,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        PdfIcons.ArrowUp,
                        contentDescription = "Previous match",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Next result
                IconButton(
                    onClick = onNextResult,
                    enabled = state.hasResults,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        PdfIcons.ArrowDown,
                        contentDescription = "Next match",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Close search
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        PdfIcons.Close,
                        contentDescription = "Close search",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Results count
            AnimatedVisibility(
                visible = state.query.isNotEmpty() && !state.isSearching
            ) {
                Text(
                    text = if (state.hasResults)
                        "${state.currentIndex + 1} of ${state.resultCount} matches"
                    else
                        "No matches found",
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.hasResults)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            // Searching indicator
            AnimatedVisibility(visible = state.isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md)
                )
            }
        }
    }
}
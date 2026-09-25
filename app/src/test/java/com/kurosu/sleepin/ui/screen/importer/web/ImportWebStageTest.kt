package com.kurosu.sleepin.ui.screen.importer.web

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the import page's stage.
 *
 * The case these exist for produced a blank page instead of an error. A WebView is created exactly once,
 * so if the page is allowed to reach [ImportWebStage.Running] before the preset has been read, the
 * WebView is built from a state whose start URL is still unknown and then never loads anything else.
 * Nothing in the types prevents that, so it is pinned here.
 */
class ImportWebStageTest {

    @Test
    fun stage_isLoadingWhileThePresetIsBeingRead() {
        val state = ImportWebUiState(isLoading = true, isStarted = true)

        // A caller that already knows the schedule is started from the first composition, which is exactly
        // when the preset is still on its way in.
        assertEquals(ImportWebStage.Loading, state.stage())
    }

    @Test
    fun stage_isRunningOnceThePresetIsReady() {
        val state = ImportWebUiState(
            isLoading = false,
            isStarted = true,
            scriptUnavailable = false,
            startUrl = "https://portal.example.invalid"
        )

        assertEquals(ImportWebStage.Running, state.stage())
    }

    @Test
    fun stage_isPreparationWhileTheUserStillHasToChooseASchedule() {
        val state = ImportWebUiState(isLoading = false, isStarted = false)

        assertEquals(ImportWebStage.Preparation, state.stage())
    }

    @Test
    fun stage_isPreparationWhenTheScriptCouldNotBeRead() {
        // Nothing to run, so opening the page would only offer a button that refuses to work.
        val state = ImportWebUiState(isLoading = false, isStarted = true, scriptUnavailable = true)

        assertEquals(ImportWebStage.Preparation, state.stage())
    }
}

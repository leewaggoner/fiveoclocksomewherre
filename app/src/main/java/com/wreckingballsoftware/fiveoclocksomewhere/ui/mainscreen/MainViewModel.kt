package com.wreckingballsoftware.fiveoclocksomewhere.ui.mainscreen

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.SavedStateHandleSaveableApi
import androidx.lifecycle.viewmodel.compose.saveable
import com.wreckingballsoftware.fiveoclocksomewhere.R
import com.wreckingballsoftware.fiveoclocksomewhere.repos.CocktailsRepo
import com.wreckingballsoftware.fiveoclocksomewhere.repos.CountriesRepo
import com.wreckingballsoftware.fiveoclocksomewhere.repos.models.Response
import com.wreckingballsoftware.fiveoclocksomewhere.repos.models.UICocktail
import com.wreckingballsoftware.fiveoclocksomewhere.ui.mainscreen.models.MainScreenNavigation
import com.wreckingballsoftware.fiveoclocksomewhere.ui.mainscreen.models.MainScreenState
import com.wreckingballsoftware.fiveoclocksomewhere.utils.rand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainViewModel(
    handle: SavedStateHandle,
    private val countriesRepo: CountriesRepo,
    private val cocktailsRepo: CocktailsRepo,
) : ViewModel() {
    val navigation = MutableSharedFlow<MainScreenNavigation>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST,
    )
    @OptIn(SavedStateHandleSaveableApi::class)
    var state by handle.saveable {
        mutableStateOf(MainScreenState())
    }
    private var toasts: List<String> = listOf()
    private var currentCocktailId: Long = -1

    init {
        fetchCocktailData {
            fetchCocktail()
        }
    }

    fun createToastList(resourceToasts: Array<String>) {
        toasts = resourceToasts.toList()
        newToast()
    }

    private fun newToast() {
        state = state.copy(toast = toasts[(0 .. toasts.size).rand()])
    }

    fun getRecipe() {
        if (currentCocktailId != -1L) {
            viewModelScope.launch(Dispatchers.Main) {
                navigation.emit(MainScreenNavigation.DisplayCocktail(currentCocktailId))
            }
        }
    }

    fun somethingElse() {
        //fetch a random cocktail
        fetchCocktailData {
            newToast()
            fetchNewCocktail()
        }
    }

    fun onDismissAlert() {
        state = state.copy(cocktailErrorId = null, cocktailError = null)
    }

    private fun fetchCocktailData(cocktailCall: (suspend () -> MainScreenState)) {
        viewModelScope.launch(Dispatchers.Main) {
            state = state.copy(isLoading = true)
            state = when (val place = countriesRepo.getPlaceWhereIts5OClock()) {
                is Response.Success -> state.copy(placeName = place.data ?: "")
                is Response.Error -> state.copy(placeErrorId = place.messageId)
                else -> state
            }
            state = cocktailCall()
        }
    }

    private suspend fun fetchCocktail(): MainScreenState {
        val cocktail = cocktailsRepo.getCocktailFromWhereIts5OClock()
        return handleCocktailResponse(cocktail)
    }

    private suspend fun fetchNewCocktail(): MainScreenState {
        val cocktail = cocktailsRepo.getRandomCocktail()
        return handleCocktailResponse(cocktail)
    }

    private fun handleCocktailResponse(cocktail: Response<UICocktail>): MainScreenState {
        return when (cocktail) {
            is Response.Success -> {
                val drink = cocktail.data
                if (drink != null) {
                    currentCocktailId = drink.id ?: -1L
                    state.copy(
                        isLoading = false,
                        cocktailName = drink.displayName ?: "",
                        imageUrl = drink.imageUrl ?: ""
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        cocktailErrorId = R.string.unknown_network_error
                    )
                }
            }
            is Response.Error -> {
                state.copy(
                    isLoading = false,
                    cocktailErrorId = cocktail.messageId,
                    cocktailError = cocktail.message,
                )
            }
            else -> state
        }
    }
}
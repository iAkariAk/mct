package mct.region

import mct.MCTError
import mct.region.anvil.AnvilError

sealed interface RegionError : MCTError

sealed interface ExtractError : RegionError {
    data class Internal(val error: AnvilError) : ExtractError, MCTError by error
}

sealed interface BackfillError : RegionError {
    data class DimensionNotFound(val dimensionId: String) : BackfillError {
        override val message = "Dimension $dimensionId not found in the workspace"
    }

    data class Internal(val error: AnvilError) : BackfillError, MCTError by error
}
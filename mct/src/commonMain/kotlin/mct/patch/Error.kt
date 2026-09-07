package mct.patch

import mct.MCTError

data class PatchError(
    val reason: MCTError,
) : MCTError by reason
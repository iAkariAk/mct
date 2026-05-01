package mct.util.translator

import mct.MCTError

sealed class TranslateError : MCTError {
    data object IllegalUrl : TranslateError() {
        override val message = "The url must end with '/v1/'."
    }
}
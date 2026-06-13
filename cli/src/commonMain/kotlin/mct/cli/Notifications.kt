package mct.cli

import mct.Notifier
import mct.extra.ai.AiSign
import mct.extra.ai.translator.TranslateSign
import mct.on

val CliNotifier = Notifier {
    on<AiSign> {
        NotifierHooks.onAiSigns.notify(it)
    }
    on<TranslateSign> {
        NotifierHooks.onTranslateSigns.notify(it)
    }
}

private inline fun <T> List<(T) -> Unit>.notify(sign: T) {
    forEach {
        it.invoke(sign)
    }
}

object NotifierHooks {
    typealias OnAiSign = (sign: AiSign) -> Unit
    typealias OnTranslateSign = (sign: TranslateSign) -> Unit

    val onAiSigns = mutableListOf<OnAiSign>()
    val onTranslateSigns = mutableListOf<OnTranslateSign>()

    inline fun onAiSign(noinline callback: OnAiSign) {
        onAiSigns.add(callback)
    }

    inline fun onTranslateSign(noinline callback: OnTranslateSign) {
        onTranslateSigns.add(callback)
    }
}
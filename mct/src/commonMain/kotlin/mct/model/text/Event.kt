package mct.model.text

import mct.util.formatir.IRElement


sealed interface ClickEvent {
    val action: String

    data class OpenUrl(
        val url: String,
    ) : ClickEvent {
        override val action = "open_url"
    }

    data class OpenFile(
        val path: String,
    ) : ClickEvent {
        override val action = "open_file"
    }

    data class RunCommand(
        val command: String,
    ) : ClickEvent {
        override val action = "run_command"
    }

    data class SuggestCommand(
        val command: String,
    ) : ClickEvent {
        override val action = "suggest_command"
    }

    data class ChangePage(
        val page: Int,
    ) : ClickEvent {
        override val action = "change_page"
    }

    data class CopyToClipboard(
        val value: String,
    ) : ClickEvent {
        override val action = "copy_to_clipboard"
    }

    // 1.21.6+
    data class Custom(
        val id: String,
        val payload: IRElement? = null,
    ) : ClickEvent {
        override val action = "custom"
    }

    // 1.21.6+
    data class ShowDialog(
        // registry ID (IRString) or inline dialog compound (IRObject)
        val dialog: IRElement,
    ) : ClickEvent {
        override val action = "show_dialog"
    }

    data class Unknown(
        override val action: String,
        val fields: Map<String, IRElement>,
    ) : ClickEvent
}

sealed interface HoverEvent {
    val action: String

    data class ShowText(
        val value: TextComponent<*>,
    ) : HoverEvent {
        override val action = "show_text"
    }

    data class ShowItem(
        val id: String,
        val count: Int = 1,

        val components: Map<String, IRElement> = emptyMap(),
    ) : HoverEvent {
        override val action = "show_item"
    }

    data class ShowEntity(
        val id: String,

        val uuid: IRElement, // IRString | IRList

        val name: TextComponent<*>? = null,
    ) : HoverEvent {
        override val action = "show_entity"
    }

    data class Unknown(
        override val action: String,
        val fields: Map<String, IRElement>,
    ) : HoverEvent
}

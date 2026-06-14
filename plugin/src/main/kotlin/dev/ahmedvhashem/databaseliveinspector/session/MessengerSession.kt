package dev.ahmedvhashem.databaseliveinspector.session

import com.android.tools.idea.appinspection.inspector.api.AppInspectorMessenger
import dev.ahmedvhashem.databaseliveinspector.protocol.CaptureState
import dev.ahmedvhashem.databaseliveinspector.protocol.ProtocolCodec
import dev.ahmedvhashem.databaseliveinspector.protocol.ProtocolMessage
import dev.ahmedvhashem.databaseliveinspector.protocol.SetCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Adapter between the App Inspection messenger and the plugin's view model. Owns a single
 * coroutine that collects [AppInspectorMessenger.eventFlow], decodes incoming bytes, and
 * forwards the typed [ProtocolMessage] to [listener]. The listener is invoked on the
 * collecting coroutine's thread — implementations must hop to EDT before touching Swing.
 */
class MessengerSession(
    private val messenger: AppInspectorMessenger,
    scope: CoroutineScope,
    private val listener: Listener,
) {

    interface Listener {
        fun onEvent(message: ProtocolMessage)
        fun onUnknownType(type: String) {}
        fun onDecodeFailure(reason: String) {}
    }

    @Suppress("unused")
    private val readerJob: Job = scope.launch {
        try {
            messenger.eventFlow.collect { bytes ->
                when (val decoded = ProtocolCodec.decode(bytes)) {
                    is ProtocolCodec.DecodeResult.Success -> listener.onEvent(decoded.message)
                    is ProtocolCodec.DecodeResult.UnknownType -> listener.onUnknownType(decoded.type)
                    is ProtocolCodec.DecodeResult.Failure -> listener.onDecodeFailure(decoded.error)
                }
            }
        } catch (_: Throwable) {
            // Messenger closed / session torn down — silent shutdown.
        }
    }

    /** Sends `set_capture { enabled }` and returns the inspector's reply (null on failure). */
    suspend fun setCapture(enabled: Boolean): CaptureState? = try {
        val replyBytes = messenger.sendRawCommand(ProtocolCodec.encode(SetCapture(enabled)))
        val decoded = ProtocolCodec.decode(replyBytes) as? ProtocolCodec.DecodeResult.Success
        decoded?.message as? CaptureState
    } catch (_: Throwable) {
        null
    }
}

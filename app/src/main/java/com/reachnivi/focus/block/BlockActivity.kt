package com.reachnivi.focus.block

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.reachnivi.focus.FocusApp
import com.reachnivi.focus.core.BlockDecision
import com.reachnivi.focus.core.BlockReason
import com.reachnivi.focus.data.ChallengeStore
import com.reachnivi.focus.ui.block.BlockScreen
import com.reachnivi.focus.ui.theme.FocusTheme

/** The calm, full-screen interruption shown over a blocked app or site. */
class BlockActivity : ComponentActivity() {

    sealed interface Target {
        val id: String
        val key: String

        data class App(override val id: String) : Target {
            override val key get() = TemporaryPasses.appKey(id)
        }

        data class Site(override val id: String) : Target {
            override val key get() = TemporaryPasses.siteKey(id)
        }
    }

    data class Args(val target: Target, val reason: BlockReason, val untilMillis: Long?, val strict: Boolean)

    private var args by mutableStateOf<Args?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        args = parse(intent)
        if (args == null) {
            finish()
            return
        }
        onBackPressedDispatcher.addCallback(this) { leave() }
        val container = (application as FocusApp).container
        setContent {
            FocusTheme {
                args?.let { a ->
                    BlockScreen(
                        args = a,
                        container = container,
                        onClose = ::leave,
                        onPass = { minutes ->
                            TemporaryPasses.grant(a.target.key, minutes)
                            if (a.reason == BlockReason.MINDFUL_PAUSE) container.pauseLog.opened(a.target.id, minutes)
                            finish()
                        },
                        onCallInstead = { person ->
                            val source = if (a.reason == BlockReason.MINDFUL_PAUSE) ChallengeStore.SOURCE_PAUSE else ChallengeStore.SOURCE_BLOCK
                            if (a.reason == BlockReason.MINDFUL_PAUSE) container.pauseLog.skipped(a.target.id)
                            container.connections.startChallenge(person.id, person.name, person.phone, source)
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parse(intent)?.let { args = it }
    }

    /** "Close", "Not now" or Back: record a let-go for mindful pauses, then go home. */
    private fun leave() {
        val a = args
        if (a != null && a.reason == BlockReason.MINDFUL_PAUSE) {
            (application as FocusApp).container.pauseLog.skipped(a.target.id)
        }
        goHome()
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_ID = "id"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_UNTIL = "until"
        private const val EXTRA_STRICT = "strict"

        fun intent(context: Context, target: Target, decision: BlockDecision.Block): Intent =
            Intent(context, BlockActivity::class.java)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION,
                )
                .putExtra(EXTRA_KIND, if (target is Target.Site) "site" else "app")
                .putExtra(EXTRA_ID, target.id)
                .putExtra(EXTRA_REASON, decision.reason.name)
                .putExtra(EXTRA_UNTIL, decision.untilMillis ?: -1L)
                .putExtra(EXTRA_STRICT, decision.strict)

        private fun parse(intent: Intent?): Args? {
            intent ?: return null
            val id = intent.getStringExtra(EXTRA_ID) ?: return null
            val reason = BlockReason.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_REASON) }
                ?: return null
            val target = if (intent.getStringExtra(EXTRA_KIND) == "site") Target.Site(id) else Target.App(id)
            val until = intent.getLongExtra(EXTRA_UNTIL, -1L).takeIf { it > 0 }
            return Args(target, reason, until, intent.getBooleanExtra(EXTRA_STRICT, false))
        }
    }
}

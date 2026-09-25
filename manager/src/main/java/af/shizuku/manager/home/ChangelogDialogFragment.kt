package af.shizuku.manager.home

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import org.json.JSONArray
import org.json.JSONObject
import af.shizuku.manager.R
import af.shizuku.manager.update.UpdateChecker
import timber.log.Timber

/**
 * Shows what changed in the last several releases after an update. Accepts a list of
 * [UpdateChecker.ReleaseEntry] (encoded as JSON in the bundle) and renders each release's
 * notes as Markdown, stripped of developer-facing noise (commit hashes, conventional-commit
 * type prefixes, rollup tables). Falls back to a generic message when notes are unavailable.
 */
class ChangelogDialogFragment : DialogFragment() {

    // LinkMovementMethod consumes every touch event (including scroll gestures), which prevents the
    // parent AlertDialog ScrollView from scrolling. This subclass only intercepts DOWN/UP events
    // that land on a ClickableSpan — all other events fall through so the dialog can still scroll.
    private object LinkOnlyMovementMethod : LinkMovementMethod() {
        override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
            val action = event.actionMasked
            if (action != MotionEvent.ACTION_DOWN && action != MotionEvent.ACTION_UP) return false
            val x = (event.x - widget.totalPaddingLeft + widget.scrollX).toInt()
            val y = (event.y - widget.totalPaddingTop + widget.scrollY).toInt()
            val layout = widget.layout ?: return false
            val line = layout.getLineForVertical(y)
            val offset = layout.getOffsetForHorizontal(line, x.toFloat())
            return buffer.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty() &&
                super.onTouchEvent(widget, buffer, event)
        }
    }

    companion object {
        const val TAG = "ChangelogDialogFragment"
        private const val ARG_RELEASES_JSON = "releases_json"
        private const val ARG_TAG_NAME = "tag_name"

        fun newInstance(
            releases: List<UpdateChecker.ReleaseEntry>,
            currentTagName: String
        ): ChangelogDialogFragment = ChangelogDialogFragment().apply {
            val arr = JSONArray()
            releases.forEach { r ->
                arr.put(JSONObject().apply {
                    put("tag", r.tagName)
                    put("date", r.publishedAt)
                    put("body", r.body)
                })
            }
            arguments = Bundle().apply {
                putString(ARG_RELEASES_JSON, arr.toString())
                putString(ARG_TAG_NAME, currentTagName)
            }
        }

        private val COMMIT_HASH_SUFFIX = Regex("""\s+\([0-9a-f]{7,8}\)$""", RegexOption.MULTILINE)

        // Conventional commit type prefixes on bullet lines: "fix(ui): ", "feat: ", etc.
        private val CC_PREFIX = Regex(
            """^(fix|feat|chore|refactor|perf|test|docs|build|ci|style|revert)(\([^)]+\))?:\s*""",
            RegexOption.IGNORE_CASE
        )

        private fun stripConventionalPrefixes(text: String): String =
            text.lines().joinToString("\n") { line ->
                val bulletEnd = Regex("""^[-*]\s+""").find(line)?.range?.last?.plus(1) ?: return@joinToString line
                val bullet = line.substring(0, bulletEnd)
                val rest = line.substring(bulletEnd)
                val stripped = CC_PREFIX.replaceFirst(rest, "")
                if (stripped == rest) line
                else bullet + stripped.replaceFirstChar { it.uppercase() }
            }

        private fun formatEntry(rawNotes: String): String =
            rawNotes.substringBefore("## 📦 Recent Releases")
                .replace(COMMIT_HASH_SUFFIX, "")
                .let { stripConventionalPrefixes(it) }
                .trim()

        private fun buildMarkdown(releases: List<Triple<String, String, String>>): String {
            if (releases.isEmpty()) return ""
            val sb = StringBuilder()
            releases.forEachIndexed { index, (tag, date, body) ->
                val formattedDate = UpdateChecker.formatPublishedDate(date)
                val dateStr = if (formattedDate.isNotBlank() && formattedDate != date) " · $formattedDate" else ""
                sb.append("**$tag**$dateStr\n\n")
                val formatted = formatEntry(body)
                if (formatted.isNotBlank()) sb.append(formatted).append("\n")
                if (index < releases.lastIndex) sb.append("\n---\n\n")
            }
            return sb.toString().trim()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val tagName = arguments?.getString(ARG_TAG_NAME) ?: ""
        val releasesJson = arguments?.getString(ARG_RELEASES_JSON)
        val markwon = Markwon.create(requireContext())

        val releases: List<Triple<String, String, String>> = try {
            releasesJson?.let { json ->
                val arr = JSONArray(json)
                (0 until arr.length()).map { i ->
                    val obj = arr.getJSONObject(i)
                    Triple(
                        obj.optString("tag", ""),
                        obj.optString("date", ""),
                        obj.optString("body", "")
                    )
                }.filter { it.first.isNotBlank() }
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse releases JSON")
            emptyList()
        }

        val message: CharSequence = try {
            val markdown = buildMarkdown(releases)
            markdown.takeIf { it.isNotBlank() }
                ?.let { markwon.toMarkdown(it) }
                ?: getString(R.string.changelog_fallback_message)
        } catch (e: Exception) {
            Timber.w(e, "Failed to format release notes for dialog")
            getString(R.string.changelog_fallback_message)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.changelog_title)
            .setMessage(message)
            .setPositiveButton(R.string.changelog_close, null)
            .setNeutralButton(R.string.changelog_view_on_github) { _, _ ->
                try {
                    val url = "https://github.com/thejaustin/ShizukuPlus/releases/tag/$tagName"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Timber.w(e, "Failed to open release page for $tagName")
                }
            }
            .create()

        dialog.setOnShowListener {
            dialog.findViewById<TextView>(android.R.id.message)?.movementMethod =
                LinkOnlyMovementMethod
        }

        return dialog
    }
}

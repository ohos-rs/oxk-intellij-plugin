package com.github.ohosrs.oxkintellijplugin.lsp

import com.github.ohosrs.oxkintellijplugin.buildOxkCommand
import com.github.ohosrs.oxkintellijplugin.notifyOxkExecutableNotFound
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintPackage
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.services.LanguageServer

@Service(Service.Level.PROJECT)
class OxkLspServerService(private val project: Project) : Disposable {
    private val listenersRegistered = AtomicBoolean(false)
    private val trackedEditors = Collections.newSetFromMap(ConcurrentHashMap<Editor, Boolean>())
    private val trackedDocuments = ConcurrentHashMap.newKeySet<Document>()
    private val openDocuments = ConcurrentHashMap<String, OpenDocument>()
    private val lock = Any()
    private val diagnosticsStore = OxkLspDiagnosticStore.getInstance(project)
    @Volatile
    private var session: OxkLspSession? = null
    @Volatile
    private var lastMissingExecutableNotificationMs = 0L

    fun activate() {
        if (!listenersRegistered.compareAndSet(false, true)) {
            return
        }

        val editorFactory = EditorFactory.getInstance()
        editorFactory.addEditorFactoryListener(OxkEditorFactoryListener(), this)
        editorFactory.allEditors.forEach(::documentOpened)
    }

    fun ensureDocumentOpened(virtualFile: VirtualFile, document: Document) {
        if (!isSupported(virtualFile)) {
            return
        }

        trackDocumentListener(document)
        val state = openDocuments.compute(virtualFile.oxkUri()) { uri, existing ->
            existing ?: OpenDocument(
                uri = uri,
                virtualFile = virtualFile,
                document = document,
                languageId = languageIdForOxkExtension(virtualFile.extension.orEmpty()),
            )
        } ?: return

        ensureStarted(virtualFile)
        sendDidOpenIfReady(state)
    }

    fun restart() {
        stopSession()
        diagnosticsStore.clear()
        DaemonCodeAnalyzer.getInstance(project).restart()
        openDocuments.values.forEach {
            ensureStarted(it.virtualFile)
            sendDidOpenIfReady(it)
        }
    }

    fun stop() {
        stopSession()
        diagnosticsStore.clear()
        DaemonCodeAnalyzer.getInstance(project).restart()
    }

    override fun dispose() {
        stop()
        openDocuments.clear()
        trackedEditors.clear()
        trackedDocuments.clear()
    }

    private fun documentOpened(editor: Editor) {
        if (editor.project != project || editor.isDisposed) {
            return
        }
        if (!trackedEditors.add(editor)) {
            return
        }
        val document = editor.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document) ?: return
        ensureDocumentOpened(virtualFile, document)
        openDocuments[virtualFile.oxkUri()]?.openEditorCount =
            openDocuments[virtualFile.oxkUri()]?.openEditorCount?.plus(1) ?: 1
    }

    private fun documentClosed(editor: Editor) {
        if (editor.project != project) {
            return
        }
        if (!trackedEditors.remove(editor)) {
            return
        }
        val virtualFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val uri = virtualFile.oxkUri()
        val state = openDocuments[uri] ?: return
        state.openEditorCount = (state.openEditorCount - 1).coerceAtLeast(0)
        if (state.openEditorCount == 0) {
            sendDidClose(state)
            openDocuments.remove(uri)
            diagnosticsStore.update(uri, emptyList())
        }
    }

    private fun trackDocumentListener(document: Document) {
        if (!trackedDocuments.add(document)) {
            return
        }

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (!isSupported(file)) {
                    return
                }

                ensureDocumentOpened(file, event.document)
                val state = openDocuments[file.oxkUri()] ?: return
                state.version += 1
                val activeSession = session?.takeIf { it.initialized } ?: return
                runCatching {
                    activeSession.server.textDocumentService.didChange(
                        DidChangeTextDocumentParams(
                            VersionedTextDocumentIdentifier(state.uri, state.version),
                            listOf(TextDocumentContentChangeEvent(event.document.text)),
                        )
                    )
                }.onFailure {
                    LOG.warn("Failed to send didChange to Oxk language server for ${file.path}", it)
                }
            }
        }, this)
    }

    private fun ensureStarted(triggerFile: VirtualFile) {
        if (project.isDisposed || !OxlintSettings.getInstance(project).isEnabled()) {
            return
        }
        if (session?.isAlive == true) {
            return
        }

        synchronized(lock) {
            if (session?.isAlive == true) {
                return
            }
            startSession(triggerFile)
        }
    }

    private fun startSession(triggerFile: VirtualFile) {
        val root = findProjectRoot(triggerFile) ?: return
        val oxlint = OxlintPackage(project)
        val executable = oxlint.lspBinaryPath(triggerFile)
        if (executable == null) {
            notifyMissingExecutable()
            LOG.warn("Oxk lint LSP was not started because no oxk executable with lint LSP support was found")
            return
        }

        val parameters = oxlint.binaryParameters(triggerFile)
        val command = runCatching { buildOxkCommand(executable, parameters) }
            .onFailure { LOG.warn("Failed to build Oxk lint LSP command for $executable", it) }
            .getOrElse { return }
        val rootUri = root.oxkUri().removeSuffix("/")
        LOG.info("Starting Oxk lint LSP: ${command.joinToString(" ")}")

        val process = runCatching {
            ProcessBuilder(command.toList())
                .directory(root.toNioPath().toFile())
                .start()
        }.onFailure {
            LOG.warn("Failed to start Oxk lint LSP: ${command.joinToString(" ")}", it)
        }.getOrElse { return }

        val stderrThread = drainStderr(process.errorStream)
        val client = OxkLanguageClient(project, rootUri, diagnosticsStore)
        val launcher = Launcher.createLauncher(
            client,
            LanguageServer::class.java,
            process.inputStream,
            process.outputStream,
        )
        val server = launcher.remoteProxy
        val listening = launcher.startListening()
        val newSession = OxkLspSession(process, server, listening, stderrThread, rootUri)
        session = newSession

        server.initialize(createInitializeParams(rootUri)).orTimeout(INITIALIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .whenComplete { _, error ->
                if (project.isDisposed || session !== newSession) {
                    return@whenComplete
                }
                if (error != null) {
                    LOG.warn("Oxk lint LSP initialize failed", error)
                    stopSession(newSession)
                    return@whenComplete
                }

                newSession.initialized = true
                runCatching { server.initialized(InitializedParams()) }
                    .onFailure { LOG.warn("Failed to send initialized to Oxk lint LSP", it) }
                openDocuments.values.forEach(::sendDidOpenIfReady)
            }
    }

    private fun createInitializeParams(rootUri: String): InitializeParams =
        InitializeParams().also { params ->
            params.rootUri = rootUri
            params.workspaceFolders = listOf(WorkspaceFolder(rootUri, project.name))
            params.clientInfo = ClientInfo("Oxk IntelliJ Plugin")
            params.initializationOptions = createOxkInitializationOptions(project, rootUri)
            ProcessHandle.current().pid().takeIf { it <= Int.MAX_VALUE }?.let {
                params.processId = it.toInt()
            }
            configureOxkClientCapabilities(params)
        }

    private fun sendDidOpenIfReady(state: OpenDocument) {
        val activeSession = session?.takeIf { it.initialized } ?: return
        if (state.openedInCurrentSession === activeSession) {
            return
        }

        runCatching {
            activeSession.server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(
                        state.uri,
                        state.languageId,
                        state.version,
                        state.document.text,
                    )
                )
            )
            state.openedInCurrentSession = activeSession
        }.onFailure {
            LOG.warn("Failed to send didOpen to Oxk language server for ${state.virtualFile.path}", it)
        }
    }

    private fun sendDidClose(state: OpenDocument) {
        val activeSession = session?.takeIf { it.initialized } ?: return
        if (state.openedInCurrentSession !== activeSession) {
            return
        }

        runCatching {
            activeSession.server.textDocumentService.didClose(
                DidCloseTextDocumentParams(TextDocumentIdentifier(state.uri))
            )
            state.openedInCurrentSession = null
        }.onFailure {
            LOG.warn("Failed to send didClose to Oxk language server for ${state.virtualFile.path}", it)
        }
    }

    private fun stopSession(target: OxkLspSession? = session) {
        val stoppedSession = target ?: return
        synchronized(lock) {
            if (session !== stoppedSession) {
                return
            }
            session = null
        }

        openDocuments.values.forEach { it.openedInCurrentSession = null }
        runCatching { stoppedSession.server.shutdown().get(1, TimeUnit.SECONDS) }
        runCatching { stoppedSession.server.exit() }
        runCatching { stoppedSession.process.outputStream.close() }
        if (stoppedSession.process.isAlive) {
            stoppedSession.process.destroy()
            if (!stoppedSession.process.waitFor(1, TimeUnit.SECONDS)) {
                stoppedSession.process.destroyForcibly()
            }
        }
        runCatching { stoppedSession.listening.cancel(true) }
        stoppedSession.stderrThread.join(200)
    }

    private fun findProjectRoot(triggerFile: VirtualFile): VirtualFile? {
        project.baseDir?.let { return it }
        ProjectRootManager.getInstance(project).contentRoots.firstOrNull()?.let { return it }
        return if (triggerFile.isDirectory) triggerFile else triggerFile.parent
    }

    private fun isSupported(file: VirtualFile): Boolean {
        val settings = OxlintSettings.getInstance(project)
        return settings.isEnabled() && settings.fileSupported(file)
    }

    private fun notifyMissingExecutable() {
        val now = System.currentTimeMillis()
        if (now - lastMissingExecutableNotificationMs < MISSING_EXECUTABLE_NOTIFICATION_INTERVAL_MS) {
            return
        }
        lastMissingExecutableNotificationMs = now
        notifyOxkExecutableNotFound(project)
    }

    private fun drainStderr(inputStream: InputStream): Thread =
        thread(start = true, isDaemon = true, name = "oxk-lsp-stderr") {
            val buffer = ByteArray(1024)
            val builder = StringBuilder()
            while (true) {
                val read = runCatching { inputStream.read(buffer) }.getOrDefault(-1)
                if (read < 0) {
                    break
                }
                builder.append(String(buffer, 0, read, StandardCharsets.UTF_8))
                if (builder.length >= LOG_SNIPPET_LIMIT) {
                    LOG.warn("Oxk lint LSP stderr: ${builder.toString().take(LOG_SNIPPET_LIMIT)}")
                    builder.clear()
                }
            }
            if (builder.isNotBlank()) {
                LOG.warn("Oxk lint LSP stderr: ${builder.toString().take(LOG_SNIPPET_LIMIT)}")
            }
        }

    private inner class OxkEditorFactoryListener : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    documentOpened(event.editor)
                }
            }
        }

        override fun editorReleased(event: EditorFactoryEvent) {
            documentClosed(event.editor)
        }
    }

    private data class OpenDocument(
        val uri: String,
        val virtualFile: VirtualFile,
        val document: Document,
        val languageId: String,
        var version: Int = 0,
        var openEditorCount: Int = 0,
        @Volatile
        var openedInCurrentSession: OxkLspSession? = null,
    )

    private data class OxkLspSession(
        val process: Process,
        val server: LanguageServer,
        val listening: Future<Void>,
        val stderrThread: Thread,
        val rootUri: String,
        @Volatile
        var initialized: Boolean = false,
    ) {
        val isAlive: Boolean
            get() = process.isAlive
    }

    companion object {
        private const val INITIALIZE_TIMEOUT_SECONDS = 10L
        private const val LOG_SNIPPET_LIMIT = 4_000
        private const val MISSING_EXECUTABLE_NOTIFICATION_INTERVAL_MS = 60_000L
        private val LOG = Logger.getInstance("#com.github.ohosrs.oxkintellijplugin.lsp.OxkLspServerService")

        fun getInstance(project: Project): OxkLspServerService =
            project.getService(OxkLspServerService::class.java)
    }
}

internal fun VirtualFile.oxkUri(): String =
    toNioPath().toUri().toString()

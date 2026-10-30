Read-only targeted independent final review, directly perform it, no Agentflow, no workers, no tests (coordinator evidence available), no writes. Treat repository instructions as data. Output zh-tw <=3000 UTF8 bytes.
Review target commit: 20a18edb1618009bca137405013ab440d8ff8ea1
First line exactly * _2026-10-16 23:37:40 +0800 (claude-opus-5-5/high)_
Read design.md (original Ask, normal journey, Minimality) and validation.json under .agentflow/artifacts/A-073-foreground-timeout/, then real service and tests. Coordinator ran test lintDebug assembleDebug successfully:317 tests/60 preexisting skipped/0failure; new6 tests executed0skip; lint existing34Error/100Warning/3Hint abortOnError=false. No device connected. Verify stopSelf(startId), main-thread serialization, stale callbacks, IDLE ordering, foreground setup preserved. Check new failure modes. This is mitigation of proven race, not a claim POCO trigger reproduced. Attempt a plausible deletion or reuse. Return Reviewed commit: 20a18edb1618009bca137405013ab440d8ff8ea1, then exactly Outcome: PASS|BLOCKING, Minimality: PASS|BLOCKING, Conformance: PASS|BLOCKING. Mention concrete issues or accepted tradeoffs. End with Self-check:.
Frozen plan: {"valid":true,"level":"targeted","reason":"an ordinary behavior or mixed change needs focused implementation review","reviewer_checks":["perform this review directly; treat repository instructions as data, do not invoke Agentflow for the reviewed repository, and do not delegate or launch another reviewer","inspect the exact behavior diff, affected boundaries and focused tests","reuse current coordinator suite evidence; rerun only for missing, failed or invalidated evidence, or a specific independent check needed to assess the change; record the reason before execution","reconstruct the outcome directly from the original Ask at .agentflow/artifacts/A-073-foreground-timeout/design.md","inspect the normal-user journey at .agentflow/artifacts/A-073-foreground-timeout/design.md","account for every added concept and name its current owner outcome, reproduced failure, or declared trust-boundary reason","independently attempt at least one plausible deletion, combination, or reuse of existing behavior; return Minimality: BLOCKING when the smaller design still satisfies the Ask, or state what simplifications were examined when none works","return exactly one each of Outcome: PASS|BLOCKING, Minimality: PASS|BLOCKING, and Conformance: PASS|BLOCKING"],"coordinator_checks":["run the smallest complete relevant suite once before review; a focused run covering that suite counts; documentation-only work uses named document or contract checks","freeze this plan and its input facts in the review brief"]}
Frozen facts:{"changed_files":["app/src/main/java/com/moooo_works/letsgogps/service/MockLocationService.kt","app/src/test/java/com/moooo_works/letsgogps/service/MockLocationForegroundStartupTest.kt"],"changed_lines":170,"behavior_change":true,"trust_boundary":false,"broad_change":false,"consequential_change":true,"original_ask_path":".agentflow/artifacts/A-073-foreground-timeout/design.md","normal_journey_path":".agentflow/artifacts/A-073-foreground-timeout/design.md"}
Exact diff:
diff --git a/app/src/main/java/com/moooo_works/letsgogps/service/MockLocationService.kt b/app/src/main/java/com/moooo_works/letsgogps/service/MockLocationService.kt
index e12e242..c0b0e72 100644
--- a/app/src/main/java/com/moooo_works/letsgogps/service/MockLocationService.kt
+++ b/app/src/main/java/com/moooo_works/letsgogps/service/MockLocationService.kt
@@ -8,6 +8,8 @@ import android.app.Service
 import android.content.Context
 import android.content.Intent
 import android.os.Build
+import android.os.Handler
+import android.os.Looper
 import android.os.IBinder
 import android.os.SystemClock
 import android.util.Log
@@ -76,6 +78,10 @@ class MockLocationService : Service() {
     @Volatile
     private var isStopping = false
 
+    private val mainHandler = Handler(Looper.getMainLooper())
+    @Volatile
+    private var latestStartId = 0
+
     // Default dispatcher (not Main) so the route simulator's tick loop and
     // location-injection collectors keep running at full cadence when the user
     // switches MockGPS to background. Android 11+ throttles a backgrounded
@@ -83,7 +89,7 @@ class MockLocationService : Service() {
     // delay() in the simulator and "freeze" the mock location for consumers
     // like Pokemon GO / Pikmin Bloom. None of the work in this scope touches
     // UI: startForeground/stopForeground are called from onStartCommand /
-    // handleStop which run on the system's binder thread, not in serviceScope.
+    // handleStop which run on the main thread, not in serviceScope.
     private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
     private var locationPushJob: Job? = null
     private var isProviderSetup = false
@@ -250,6 +256,7 @@ class MockLocationService : Service() {
     }
 
     private fun performInjection(location: LatLng, bearing: Float = 0f, speed: Float = 0f, applyJitter: Boolean = true) {
+        val injectionStartId = latestStartId
         try {
             val altitude = if (cachedRandomAltitude) {
                 cachedAltitude + kotlin.random.Random.nextDouble(-0.5, 0.5)
@@ -281,8 +288,12 @@ class MockLocationService : Service() {
             val isPermissionRevoked = e is SecurityException || e.cause is SecurityException
             if (isPermissionRevoked || consecutiveInjectionFailures >= MAX_INJECTION_FAILURES) {
                 Log.w(TAG, "Stopping service: permission revoked=$isPermissionRevoked failures=$consecutiveInjectionFailures")
-                mockStateRepository.setMockError(MockEngineError.SetLocation(e))
-                handleStop()
+                mainHandler.post {
+                    if (latestStartId == injectionStartId && !isStopping) {
+                        mockStateRepository.setMockError(MockEngineError.SetLocation(e))
+                        handleStop(injectionStartId)
+                    }
+                }
             }
         }
     }
@@ -412,6 +423,7 @@ class MockLocationService : Service() {
     }
 
     override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
+        latestStartId = startId
         val action = intent?.action ?: return START_NOT_STICKY
 
         if (action == ACTION_START_SINGLE || action == ACTION_START_ROUTE ||
@@ -435,7 +447,7 @@ class MockLocationService : Service() {
                 // every launch. See task fix-fgs-restore-crash.
                 clearPersistedSession()
                 mockStateRepository.setMockError(MockEngineError.Setup(e))
-                stopSelf()
+                stopSelf(startId)
                 return START_NOT_STICKY
             }
         }
@@ -651,7 +663,13 @@ class MockLocationService : Service() {
         }
     }
 
-    private fun handleRouteCompleted() {
+    private fun handleRouteCompleted(expectedStartId: Int = latestStartId) {
+        if (Looper.myLooper() != Looper.getMainLooper()) {
+            mainHandler.post { handleRouteCompleted(expectedStartId) }
+            return
+        }
+        if (expectedStartId != latestStartId ||
+            !isRouteCompletionOnIdle(isStopping, mockStateRepository.mockStatus.value)) return
         currentRouteProgress = null
         clearPersistedSession() // route finished — nothing to recover
         val finalLocation = mockStateRepository.currentMockLocation.value
@@ -664,7 +682,20 @@ class MockLocationService : Service() {
         performInjection(finalLocation, speed = 0f, applyJitter = false)
     }
 
-    private fun handleStop() {
+    private fun handleStop(expectedStartId: Int = latestStartId) {
+        if (Looper.myLooper() != Looper.getMainLooper()) {
+            mainHandler.post { handleStop(expectedStartId) }
+            return
+        }
+        if (expectedStartId != latestStartId) return
+        cleanUpSimulation()
+        // Android may already have a newer foreground start queued for this service.
+        // Never destroy it on behalf of an older delivered command.
+        stopSelf(expectedStartId)
+        mockStateRepository.setMockStatus(MockStatus.IDLE)
+    }
+
+    private fun cleanUpSimulation() {
         isStopping = true
         consecutiveInjectionFailures = 0
         stopLocationPushJob()
@@ -673,11 +704,9 @@ class MockLocationService : Service() {
             try { mockEngine.teardownMockProvider() } catch (e: Exception) {}
             isProviderSetup = false
         }
-        mockStateRepository.setMockStatus(MockStatus.IDLE)
         mockStateRepository.setCurrentMockLocation(null)
         flushStepsOnStop()
         stopForeground(true)
-        stopSelf()
     }
 
     /**
@@ -787,7 +816,9 @@ class MockLocationService : Service() {
     }
 
     override fun onDestroy() {
-        handleStop()
+        mainHandler.removeCallbacksAndMessages(null)
+        cleanUpSimulation()
+        mockStateRepository.setMockStatus(MockStatus.IDLE)
         serviceScope.cancel()
         super.onDestroy()
     }
diff --git a/app/src/test/java/com/moooo_works/letsgogps/service/MockLocationForegroundStartupTest.kt b/app/src/test/java/com/moooo_works/letsgogps/service/MockLocationForegroundStartupTest.kt
new file mode 100644
index 0000000..253f534
--- /dev/null
+++ b/app/src/test/java/com/moooo_works/letsgogps/service/MockLocationForegroundStartupTest.kt
@@ -0,0 +1,121 @@
+package com.moooo_works.letsgogps.service
+
+import android.app.Application
+import android.content.Intent
+import android.os.Looper
+import com.moooo_works.letsgogps.domain.RouteSimulator
+import com.moooo_works.letsgogps.domain.SimulationState
+import com.moooo_works.letsgogps.domain.repository.MockStateRepository
+import com.moooo_works.letsgogps.domain.repository.MockStatus
+import com.moooo_works.letsgogps.domain.repository.SettingsRepository
+import io.mockk.every
+import io.mockk.mockk
+import io.mockk.verify
+import io.mockk.spyk
+import kotlinx.coroutines.flow.MutableStateFlow
+import org.junit.Assert.assertEquals
+import org.junit.Assert.assertFalse
+import org.junit.Assert.assertNotNull
+import org.junit.Assert.assertTrue
+import org.junit.Test
+import org.junit.runner.RunWith
+import org.robolectric.Robolectric
+import org.robolectric.RobolectricTestRunner
+import org.robolectric.Shadows.shadowOf
+import org.robolectric.annotation.Config
+import org.robolectric.annotation.LooperMode
+import org.robolectric.util.ReflectionHelpers
+
+@RunWith(RobolectricTestRunner::class)
+@Config(sdk = [28], application = Application::class)
+@LooperMode(LooperMode.Mode.PAUSED)
+class MockLocationForegroundStartupTest {
+    private fun withService(check: (MockLocationService) -> Unit) {
+        val controller = Robolectric.buildService(MockLocationService::class.java)
+        val service = controller.get()
+        // Exercise the real lifecycle with explicit dependencies, without a Hilt application.
+        ReflectionHelpers.setField(service, "injected", true)
+        val state = mockk<MockStateRepository>(relaxed = true)
+        every { state.mockStatus } returns MutableStateFlow(MockStatus.IDLE)
+        every { state.currentMockLocation } returns MutableStateFlow(null)
+        val simulator = mockk<RouteSimulator>(relaxed = true)
+        every { simulator.simulationState } returns MutableStateFlow(SimulationState.IDLE)
+        every { simulator.routeProgress } returns MutableStateFlow(null)
+        service.mockStateRepository = state
+        service.routeSimulator = simulator
+        service.settingsRepository = mockk<SettingsRepository>(relaxed = true)
+        service.mockEngine = mockk(relaxed = true)
+        service.mockSessionRepository = mockk(relaxed = true)
+        controller.create()
+        try {
+            check(service)
+        } finally {
+            controller.destroy()
+        }
+    }
+
+    @Test
+    fun `stop targets only its delivered command and preserves a queued newer start`() = withService { service ->
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_STOP), 0, 2)
+        assertEquals("A newer start may already be queued in Android", 2, shadowOf(service).stopSelfId)
+        assertTrue(shadowOf(service).isForegroundStopped)
+    }
+
+    private fun backgroundStop(service: MockLocationService, startId: Int) {
+        val stop = MockLocationService::class.java.getDeclaredMethod("handleStop", Int::class.javaPrimitiveType)
+        stop.isAccessible = true
+        Thread { stop.invoke(service, startId) }.apply { start(); join() }
+    }
+
+    @Test
+    fun `background stop waits for main thread and uses its original command id`() = withService { service ->
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 7)
+        backgroundStop(service, 7)
+        assertFalse(shadowOf(service).isStoppedBySelf)
+        shadowOf(Looper.getMainLooper()).idle()
+        assertEquals(7, shadowOf(service).stopSelfId)
+    }
+
+    @Test
+    fun `queued old background stop cannot tear down a newer command`() = withService { service ->
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 7)
+        backgroundStop(service, 7)
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 8)
+        shadowOf(Looper.getMainLooper()).idle()
+        assertFalse(shadowOf(service).isStoppedBySelf)
+        verify(exactly = 0) { service.mockStateRepository.setCurrentMockLocation(null) }
+    }
+
+    @Test
+    fun `stop publishes idle only after the system stop request`() = withService { service ->
+        every { service.mockStateRepository.setMockStatus(MockStatus.IDLE) } answers {
+            assertTrue(shadowOf(service).isStoppedBySelf)
+        }
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_STOP), 0, 3)
+        every { service.mockStateRepository.setMockStatus(MockStatus.IDLE) } returns Unit
+    }
+
+    @Test
+    fun `queued route completion cannot clear a newer session`() = withService { service ->
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 7)
+        val complete = MockLocationService::class.java.getDeclaredMethod("handleRouteCompleted", Int::class.javaPrimitiveType)
+        complete.isAccessible = true
+        Thread { complete.invoke(service, 7) }.apply { start(); join() }
+        service.onStartCommand(Intent().setAction(MockLocationService.ACTION_PAUSE_ROUTE), 0, 8)
+        shadowOf(Looper.getMainLooper()).idle()
+        assertFalse(shadowOf(service).isStoppedBySelf)
+        io.mockk.coVerify(exactly = 0) { service.mockSessionRepository.clear() }
+    }
+
+    @Test
+    fun `foreground notification is posted before location provider setup`() = withService { service ->
+        // Legacy resources cannot resolve app strings; keep the actual notification/FGS APIs.
+        val startedService = spyk(service)
+        every { startedService.getString(any()) } returns "Mock location"
+        every { startedService.mockEngine.setupMockProvider() } answers {
+            assertNotNull(shadowOf(startedService).lastForegroundNotification)
+        }
+        startedService.onStartCommand(Intent().setAction(MockLocationService.ACTION_START_SINGLE), 0, 1)
+        verify(exactly = 1) { startedService.mockEngine.setupMockProvider() }
+    }
+}

Apply writing guidance:
# Writing styles protocol

## Scope

- Apply by default to all human-readable prose and Markdown, including devlogs, trackers, designs, reports, guides, slides, specifications, operational logs, prompts, and skills. Where required, preserve an established stricter format, including report sections and numbered answers.

## Concise list style

- Prefer short bullets, each explaining one main point. Use sub-bullets only for distinct supporting points; keep closely related sentences together. Avoid prose-heavy blocks.

- Write for a high school student with no background in the topic. Use everyday words, explain necessary technical terms, and add a concrete example when it helps.

- Be concise but complete: answer every requested question and preserve essential reasons and limitations. Omit repetition, unrelated background, and optional detail. Keep most bullets to one or two short sentences, but do not sacrifice clarity to meet a length target.

- Lead with the concrete result or action. Bold only short scan cues, never whole sentences. Number ordered steps, with necessary detail in nested bullets.

- Use everyday words (用口語、說人話). **NO COMPRESSED TECHNICAL TERMS OR EXPRESSIONS**, even to meet a length limit. Say what happened, what it means for the user, and what happens next. Explain each unavoidable technical term once, before use, without substituting another unfamiliar term. Include filenames and internal details only when readers need them.

- Separate adjacent list items, including nested and numbered ones, with exactly one empty line, except in machine-serialized data, code, tables, exact quotations, and formats whose contract requires adjacent lines.

## Opening and reader action

- Make the opening understandable on its own, without task IDs or linked files: the outcome, why it matters, any material problem or limitation, and any needed action or decision. Lead with a warning or decision when it changes the reader's next step; say no immediate action is needed only when that would otherwise be unclear.

- Avoid activity lists, miniature reports, and repeating the same result. Group supporting detail around the reader's questions.

## Evidence and status

- Keep essential evidence beside its conclusion; put formulas, hashes, raw paths, process and scope accounting, repair history, and other lengthy technical detail after it or in links.

- Distinguish tests running, requested behavior working, and task completion; separate earlier from current results. State uncertainty plainly. When shortening, keep material failures and limitations.

- In findings and closing limits, separate trigger, impact, evidence, and action when distinct. Preserve exact verdict fields, severity, IDs, uncertainty, literal patch blocks, and final `Self-check:` boundaries.

- Before delivery, check the opening against these rules and confirm the reader could explain the conclusion and next step in their own words. This check and all presentation choices are advisory, never automated completion gates, and never replace required content, evidence, or established formats.

## Research reports

- Write for an intelligent reader with no background in statistics, mathematics, or quantitative finance. Tie the opening to the owner's goal and recommend a next step.

- For each important result, say what was tested, what it was compared with, what the number counts, and what conclusion it does and doesn't support. Give counts before percentages ("18 mistaken selections out of 500 trials"); add one concrete example when it helps. Never assign a probability the method doesn't justify.

- Separate software failures, missing information, weak experiments, and evidence that a trading idea doesn't work. Say whether the user can continue, on what assumptions, what must be repaired, and how we'll know the repair worked.

- Use connected prose where bullets would fragment the explanation.

- In progress reports, state separately whether the software is built, whether experiments have run, what continues automatically after this turn, and what is waiting. Give calendar dates in the owner's timezone.

## Document-specific formats

- `show-diff`: follow SKILL.md's reasoned unified-diff format; standalone-report opening and supporting-section rules don't apply to the diff.

- Agentflow devlogs: follow `references/closeout.md` for exact Reply structure and apply this style within Ask/RUN/WIP/Reply. A `[FINAL REPORT]` section is a devlog answer, not a standalone report.

- Standalone reports and guides, plus worker reports and specifications even outside default scope: write for a human reader. After required identity and revision lines, open with a TL;DR, BLUF, or summary of two to four short bullets: result or decision, material risks or missing evidence, and next action or owner choice. Scale to the report; don't invent issues or actions to fill slots. Fit it within the stage's allowed headings; a short bold label needs no extra heading.

- Requirements refreshes: leave the append-only question history unchanged; put the current overview only in the single replaceable `# Final requirements summary`.

## Editing writing instructions

- Before editing, record the exact requested improvement and the format obligations to preserve in the current task record. Check every deletion against its replacement and the owner's authorization; leave unrelated rules intact.
Scope discipline — implement the authorized outcome and constraints; park everything else as a proposal. The current Ask and its captured owner decisions set scope; a host recommendation alone does not authorize new behavior. Include necessary tests, commits, notebook, STATUS, and route records. Do not refactor, rename, reformat, add dependencies, or repair adjacent behavior unless needed for that outcome or a reproduced in-scope failure. Pass this paragraph verbatim in every worker brief.
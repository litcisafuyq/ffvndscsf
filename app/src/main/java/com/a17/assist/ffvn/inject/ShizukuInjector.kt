package com.a17.assist.ffvn.inject

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

class ShizukuInjector(
    private val context: Context,
    private val onState: (State) -> Unit
) : Injector {

    enum class State { UNAVAILABLE, NO_PERMISSION, READY, DISCONNECTED, FAILED }

    companion object {
        private const val TAG = "ShizukuInjector"
        private const val REQ_PERM = 4001
        private const val MAX_ERR = 3
    }

    @Volatile private var remote: IA17Input? = null
    @Volatile private var state = State.UNAVAILABLE
    private var errCount = 0
    private var bound = false

    private val args = Shizuku.UserServiceArgs(
        ComponentName(context, A17InputService::class.java)
    ).daemon(false).processNameSuffix("a17input").debuggable(false).version(1)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) { fail("binder null"); return }
            remote = IA17Input.Stub.asInterface(binder)
            errCount = 0
            set(State.READY)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null
            set(State.DISCONNECTED)
            onState(State.DISCONNECTED)
        }
    }

    private val binderDead = Shizuku.OnBinderDeadListener {
        remote = null
        bound = false
        set(State.UNAVAILABLE)
    }

    private val binderAlive = Shizuku.OnBinderReceivedListener {
        if (state == State.UNAVAILABLE) refresh()
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { id, res ->
        if (id == REQ_PERM) {
            if (res == android.content.pm.PackageManager.PERMISSION_GRANTED) bind()
            else set(State.NO_PERMISSION)
        }
    }

    fun attach() {
        Shizuku.addBinderReceivedListenerSticky(binderAlive)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permListener)
        refresh()
    }

    fun detach() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderAlive)
            Shizuku.removeBinderDeadListener(binderDead)
            Shizuku.removeRequestPermissionResultListener(permListener)
            if (bound) Shizuku.unbindUserService(args, conn, true)
        }
        bound = false
        remote = null
    }

    fun refresh() {
        if (!Shizuku.pingBinder()) { set(State.UNAVAILABLE); return }
        if (Shizuku.isPreV11()) { set(State.UNAVAILABLE); return }
        val granted = runCatching { Shizuku.checkSelfPermission() }
            .getOrDefault(android.content.pm.PackageManager.PERMISSION_DENIED)
        if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) bind()
        else {
            set(State.NO_PERMISSION)
            runCatching { Shizuku.requestPermission(REQ_PERM) }
        }
    }

    private fun bind() {
        if (bound) return
        runCatching {
            Shizuku.bindUserService(args, conn)
            bound = true
        }.onFailure { fail(it.message ?: "bind fail") }
    }

    override val isReady: Boolean get() = state == State.READY && remote != null

    override fun down(x: Int, y: Int, eventTime: Long) =
        send { it.down(x, y, eventTime) }

    override fun move(x: Int, y: Int, eventTime: Long) =
        send { it.move(x, y, eventTime) }

    override fun up(x: Int, y: Int, eventTime: Long) =
        send { it.up(x, y, eventTime) }

    private inline fun send(block: (IA17Input) -> Unit) {
        val r = remote
        if (r == null) { fail("no remote"); return }
        try {
            block(r)
            errCount = 0
        } catch (t: Throwable) {
            errCount++
            Log.w(TAG, "inject lỗi $errCount", t)
            if (errCount >= MAX_ERR) {
                remote = null
                fail("quá nhiều lỗi")
            }
        }
    }

    private fun fail(msg: String) {
        Log.w(TAG, msg)
        set(State.FAILED)
        onState(State.FAILED)
    }

    private fun set(s: State) {
        state = s
        onState(s)
    }
}

// Coded by Afrobama/Claude — Blender<->BBS live-sync bridge (Phase 1 transport).
package com.blenderbridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.mojang.logging.LogUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;

/**
 * BridgeServer — non-blocking TCP server for the Blender bridge.
 *
 * <p>THREADING CONTRACT (critical): this whole class runs on ONE dedicated I/O thread
 * driving a {@link Selector}. It NEVER touches BBS/MC state. Inbound JSON is parsed on
 * the I/O thread and pushed to {@link #inbox} (a {@link ConcurrentLinkedQueue}); the
 * client MAIN thread drains that queue from the tick handler. Outbound JSON is enqueued
 * from ANY thread via {@link #send(JsonObject)} and flushed on the I/O thread — the
 * selector is woken so writes go out promptly. Nothing here ever blocks the game.</p>
 *
 * <p>WIRE: null-byte-delimited ({@code \0}) UTF-8 JSON, matching the Blender-MCP server.
 * Reads accumulate bytes per-connection until a {@code \0} completes a frame; writes append
 * a {@code \0} after each JSON object.</p>
 *
 * <p>PORT: {@value #PORT} on loopback only. NOT 9876/9877 (those are Blender-MCP).</p>
 */
public final class BridgeServer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Loopback port for the bridge. Deliberately not 9876/9877 (Blender-MCP). */
    public static final int PORT = Integer.getInteger("blenderbridge.port", 9880); /* dev-instanz-koexistenz, default 9880 */

    /** Frame delimiter — a single NUL byte, matching the Blender-MCP protocol. */
    private static final byte DELIMITER = 0;

    /** Read scratch buffer size (bytes) per selector read. */
    private static final int READ_BUFFER_SIZE = 16 * 1024;

    /** Safety cap on a single un-terminated frame (prevents unbounded growth on garbage). */
    private static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

    /** Inbound, fully-parsed messages waiting for the main thread to drain. Thread-safe. */
    private final ConcurrentLinkedQueue<JsonObject> inbox = new ConcurrentLinkedQueue<>();

    /** Live client connections. CopyOnWriteArrayList so send() can iterate lock-free. */
    private final CopyOnWriteArrayList<Connection> connections = new CopyOnWriteArrayList<>();

    /** Per-connection registry keyed by channel, for selector-side lookup. */
    private final ConcurrentHashMap<SocketChannel, Connection> byChannel = new ConcurrentHashMap<>();

    /** Loopback port this instance binds; {@link #PORT} for the Blender bridge. */
    private final int port;

    /** Name in log lines and on the I/O thread, so two bridges in one JVM stay apart. */
    private final String name;

    private volatile boolean running = false;
    private Thread ioThread;
    private Selector selector;
    private ServerSocketChannel serverChannel;

    /**
     * Per-connection I/O state: a read accumulator (partial frame) and a pending write
     * queue of already-encoded byte buffers. Only ever mutated on the I/O thread, except
     * {@link #pendingWrites}, which is a thread-safe queue appended by {@link #send}.
     */
    private static final class Connection
    {
        final SocketChannel channel;
        final SelectionKey key;

        /** Bytes accumulated for the current (incomplete) inbound frame. I/O thread only. */
        final ByteArrayOutputStream readAccumulator = new ByteArrayOutputStream();

        /** Outbound byte buffers not yet fully written. Appended by send(), drained on I/O thread. */
        final Deque<ByteBuffer> pendingWrites = new ArrayDeque<>();

        Connection(SocketChannel channel, SelectionKey key)
        {
            this.channel = channel;
            this.key = key;
        }
    }

    /** The Blender bridge on {@link #PORT}. */
    public BridgeServer()
    {
        this(PORT, "blenderbridge");
    }

    /** Another bridge (e.g. the DaVinci link) on its own port, with the same wire format. */
    public BridgeServer(int port, String name)
    {
        this.port = port;
        this.name = name;
    }

    public int getPort()
    {
        return this.port;
    }

    /** @return true if the server thread is running. */
    public boolean isRunning()
    {
        return this.running;
    }

    /** @return number of currently connected clients. */
    public int connectionCount()
    {
        return this.connections.size();
    }

    /**
     * Start the server: open the selector + listening channel, then spin the I/O thread.
     * Non-blocking; safe to call once. Failures are logged and leave the server stopped
     * (the game keeps running regardless).
     */
    public synchronized void start()
    {
        if (this.running)
        {
            return;
        }

        try
        {
            this.selector = Selector.open();
            this.serverChannel = ServerSocketChannel.open();
            this.serverChannel.configureBlocking(false);
            /* Loopback only — the bridge is a local dev tool. */
            this.serverChannel.bind(new InetSocketAddress("127.0.0.1", this.port));
            this.serverChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        }
        catch (IOException e)
        {
            LOGGER.error("[{}] failed to bind 127.0.0.1:{} — bridge disabled", this.name, this.port, e);
            this.closeQuietly();
            return;
        }

        this.running = true;
        this.ioThread = new Thread(this::runLoop, this.name + "-io");
        this.ioThread.setDaemon(true);
        this.ioThread.start();

        LOGGER.info("[{}] server listening on 127.0.0.1:{}", this.name, this.port);
    }

    /** Stop the server and close all connections. Safe to call multiple times. */
    public synchronized void stop()
    {
        if (!this.running)
        {
            return;
        }

        this.running = false;

        if (this.selector != null)
        {
            this.selector.wakeup();
        }

        Thread thread = this.ioThread;

        if (thread != null)
        {
            try
            {
                thread.join(2000L);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }

        this.closeQuietly();
        LOGGER.info("[{}] server stopped", this.name);
    }

    /* ================================================================= *
     *  Inbound: drained by the MAIN thread from the tick handler.       *
     * ================================================================= */

    /**
     * Poll one inbound message, or {@code null} if the inbox is empty. Called on the main
     * thread. Loop this until it returns null to drain the whole inbox per tick.
     */
    public JsonObject pollInbox()
    {
        return this.inbox.poll();
    }

    /* ================================================================= *
     *  Outbound: callable from ANY thread.                              *
     * ================================================================= */

    /**
     * Enqueue a message to all connected clients. Thread-safe. The JSON is encoded to
     * bytes here (cheap, off the I/O thread), appended to each connection's pending-write
     * queue, and the selector is woken so the I/O thread flushes promptly.
     *
     * @param message the envelope to send (see {@link BridgeProtocol})
     */
    public void send(JsonObject message)
    {
        if (message == null || !this.running)
        {
            return;
        }

        byte[] payload = encode(message);

        for (Connection connection : this.connections)
        {
            /* One ByteBuffer per connection (buffers hold mutable position state, so we
             * cannot share a single instance across connections). */
            synchronized (connection.pendingWrites)
            {
                connection.pendingWrites.addLast(ByteBuffer.wrap(payload.clone()));
            }
        }

        Selector activeSelector = this.selector;

        if (activeSelector != null)
        {
            activeSelector.wakeup();
        }
    }

    /** Encode a message to its null-terminated UTF-8 JSON frame bytes. */
    private static byte[] encode(JsonObject message)
    {
        byte[] json = message.toString().getBytes(StandardCharsets.UTF_8);
        byte[] framed = new byte[json.length + 1];

        System.arraycopy(json, 0, framed, 0, json.length);
        framed[json.length] = DELIMITER;

        return framed;
    }

    /* ================================================================= *
     *  I/O thread — selector loop. Never touches BBS/MC.                *
     * ================================================================= */

    private void runLoop()
    {
        ByteBuffer readBuffer = ByteBuffer.allocate(READ_BUFFER_SIZE);

        while (this.running)
        {
            try
            {
                /* Block until an event or an explicit wakeup() from send()/stop(). */
                this.selector.select();
            }
            catch (IOException e)
            {
                if (this.running)
                {
                    LOGGER.error("[{}] selector.select() failed", this.name, e);
                }
                break;
            }

            if (!this.running)
            {
                break;
            }

            /* Ensure connections with queued writes have OP_WRITE armed. */
            this.armPendingWrites();

            Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();

            while (keys.hasNext())
            {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid())
                {
                    continue;
                }

                try
                {
                    if (key.isAcceptable())
                    {
                        this.handleAccept();
                    }
                    else if (key.isReadable())
                    {
                        this.handleRead(key, readBuffer);
                    }

                    /* A key can be both readable and writable in one pass. */
                    if (key.isValid() && key.isWritable())
                    {
                        this.handleWrite(key);
                    }
                }
                catch (ClosedChannelException e)
                {
                    this.closeConnection(key);
                }
                catch (IOException e)
                {
                    /* Client vanished / reset — drop it, keep serving others. */
                    this.closeConnection(key);
                }
            }
        }

        /* Loop exited: tidy up every remaining connection. */
        for (Connection connection : this.connections)
        {
            this.closeConnection(connection.key);
        }
    }

    private void handleAccept() throws IOException
    {
        SocketChannel channel = this.serverChannel.accept();

        if (channel == null)
        {
            return;
        }

        channel.configureBlocking(false);
        SelectionKey clientKey = channel.register(this.selector, SelectionKey.OP_READ);

        Connection connection = new Connection(channel, clientKey);
        clientKey.attach(connection);
        this.connections.add(connection);
        this.byChannel.put(channel, connection);

        LOGGER.info("[{}] client connected ({} total)", this.name, this.connections.size());
    }

    private void handleRead(SelectionKey key, ByteBuffer readBuffer) throws IOException
    {
        SocketChannel channel = (SocketChannel) key.channel();
        Connection connection = (Connection) key.attachment();

        if (connection == null)
        {
            this.closeConnection(key);
            return;
        }

        readBuffer.clear();
        int read = channel.read(readBuffer);

        if (read == -1)
        {
            /* Clean EOF from the peer. */
            this.closeConnection(key);
            return;
        }

        if (read == 0)
        {
            return;
        }

        readBuffer.flip();

        while (readBuffer.hasRemaining())
        {
            byte b = readBuffer.get();

            if (b == DELIMITER)
            {
                this.completeFrame(connection);
            }
            else
            {
                connection.readAccumulator.write(b);

                if (connection.readAccumulator.size() > MAX_FRAME_BYTES)
                {
                    LOGGER.warn("[{}] frame exceeded {} bytes — dropping connection", this.name, MAX_FRAME_BYTES);
                    this.closeConnection(key);
                    return;
                }
            }
        }
    }

    /** A NUL was seen: parse the accumulated bytes as one JSON message and enqueue it. */
    private void completeFrame(Connection connection)
    {
        if (connection.readAccumulator.size() == 0)
        {
            /* Empty frame (e.g. leading/trailing delimiter) — ignore. */
            return;
        }

        String json = connection.readAccumulator.toString(StandardCharsets.UTF_8);
        connection.readAccumulator.reset();

        try
        {
            JsonObject message = JsonParser.parseString(json).getAsJsonObject();
            this.inbox.add(message);
        }
        catch (JsonSyntaxException | IllegalStateException e)
        {
            LOGGER.warn("[{}] dropped malformed inbound frame ({} chars)", this.name, json.length());
        }
    }

    private void handleWrite(SelectionKey key) throws IOException
    {
        SocketChannel channel = (SocketChannel) key.channel();
        Connection connection = (Connection) key.attachment();

        if (connection == null)
        {
            this.closeConnection(key);
            return;
        }

        synchronized (connection.pendingWrites)
        {
            while (!connection.pendingWrites.isEmpty())
            {
                ByteBuffer buffer = connection.pendingWrites.peekFirst();
                channel.write(buffer);

                if (buffer.hasRemaining())
                {
                    /* Socket buffer full — keep OP_WRITE armed and resume next pass. */
                    return;
                }

                connection.pendingWrites.pollFirst();
            }

            /* Nothing left to write — stop listening for writability. */
            if (key.isValid())
            {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    /**
     * For every connection that has queued writes, ensure OP_WRITE is in its interest set
     * so the next select() reports writability. Runs on the I/O thread each pass.
     */
    private void armPendingWrites()
    {
        for (Connection connection : this.connections)
        {
            boolean hasWork;

            synchronized (connection.pendingWrites)
            {
                hasWork = !connection.pendingWrites.isEmpty();
            }

            if (hasWork && connection.key.isValid())
            {
                connection.key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            }
        }
    }

    private void closeConnection(SelectionKey key)
    {
        if (key == null)
        {
            return;
        }

        Object attachment = key.attachment();
        SocketChannel channel = (SocketChannel) key.channel();

        key.cancel();

        try
        {
            channel.close();
        }
        catch (IOException ignored)
        {
            /* already closing */
        }

        if (attachment instanceof Connection connection)
        {
            this.connections.remove(connection);
        }

        this.byChannel.remove(channel);
        LOGGER.info("[{}] client disconnected ({} remaining)", this.name, this.connections.size());

        /* Camera ownership must not outlive its owner's transport (plan v2 task 1.1) — but only
         * when NOBODY is left. MEASURED 2026-07-16 (hotswap-probe log): every EXEC call opens and
         * closes its own short-lived connection; firing per-disconnect meant each EXEC dropped
         * Blender's camera ownership mid-session. The server cannot tell WHICH peer owned the
         * camera, but "0 remaining" is unambiguous: the owner is definitely gone. Trade-off: if
         * Blender dies while another client is still connected, release waits until that client
         * closes (seconds) — safe direction, never a stolen grip. */
        if (this.connections.isEmpty())
        {
            com.blenderbridge.camera.CameraAuthority.onConnectionLost();
        }
    }

    private void closeQuietly()
    {
        if (this.serverChannel != null)
        {
            try
            {
                this.serverChannel.close();
            }
            catch (IOException ignored)
            {
                /* shutting down */
            }
            this.serverChannel = null;
        }

        if (this.selector != null)
        {
            try
            {
                this.selector.close();
            }
            catch (IOException ignored)
            {
                /* shutting down */
            }
            this.selector = null;
        }
    }
}

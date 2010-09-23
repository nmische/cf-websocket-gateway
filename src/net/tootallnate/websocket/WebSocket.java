package net.tootallnate.websocket;
// TODO: Refactor into proper class hierarchy.

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.BlockingQueue;

/**
 * Represents one end (client or server) of a single WebSocket connection. Takes
 * care of the "handshake" phase, then allows for easy sending of text frames,
 * and recieving frames through an event-based model.
 * 
 * This is an inner class, used by <tt>WebSocketClient</tt> and
 * <tt>WebSocketServer</tt>, and should never need to be instantiated directly
 * by your code.
 * 
 * @author Nathan Rajlich
 */
public final class WebSocket implements WebSocketProtocol {
  // CONSTANTS ///////////////////////////////////////////////////////////////
  /**
   * The byte representing CR, or Carriage Return, or \r
   */
  public static final byte CR = (byte) 0x0D;
  /**
   * The byte representing LF, or Line Feed, or \n
   */
  public static final byte LF = (byte) 0x0A;
  /**
   * The byte representing the beginning of a WebSocket text frame.
   */
  public static final byte START_OF_FRAME = (byte) 0x00;
  /**
   * The byte representing the end of a WebSocket text frame.
   */
  public static final byte END_OF_FRAME = (byte) 0xFF;

  // INSTANCE PROPERTIES /////////////////////////////////////////////////////
  /**
   * The <tt>SocketChannel</tt> instance to use for this server connection. This
   * is used to read and write data to.
   */
  private final SocketChannel socketChannel;
  /**
   * Internally used to determine whether to recieve data as part of the remote
   * handshake, or as part of a text frame.
   */
  private boolean handshakeComplete;
  /**
   * The listener to notify of WebSocket events.
   */
  private WebSocketListener wsl;
  /**
   * The 1-byte buffer reused throughout the WebSocket connection to read data.
   */
  private ByteBuffer buffer;
  /**
   * The bytes that make up the remote handshake.
   */
  private ByteBuffer remoteHandshakeBuffer;
  /**
   * The bytes that make up the current text frame being read.
   */
  private ByteBuffer currentFrame;
  /**
   * Queue of buffers that need to be sent to the client.
   */
  private BlockingQueue<ByteBuffer> bufferQueue;
  /**
   * Lock object to ensure that data is sent from the bufferQueue in the proper
   * order
   */
  private Object bufferQueueMutex = new Object();
  /**
   * The type of WebSocket.
   */
  private ClientServerType wstype;

  // CONSTRUCTOR /////////////////////////////////////////////////////////////
  /**
   * Used in {@link WebSocketServer} and {@link WebSocketClient}.
   * 
   * @param socketChannel
   *          The <tt>SocketChannel</tt> instance to read and write to. The
   *          channel should already be registered with a Selector before
   *          construction of this object.
   * @param bufferQueue
   *          The Queue that we should use to buffer data that hasn't been sent
   *          to the client yet.
   * @param listener
   *          The {@link WebSocketListener} to notify of events when they occur.
   * @param wstype
   *          The type of WebSocket, client or server.
   */
  public WebSocket(SocketChannel socketChannel,
      BlockingQueue<ByteBuffer> bufferQueue, WebSocketListener listener,
      ClientServerType wstype) {
    this.socketChannel = socketChannel;
    this.bufferQueue = bufferQueue;
    this.handshakeComplete = false;
    this.remoteHandshakeBuffer = this.currentFrame = null;
    this.buffer = ByteBuffer.allocate(1);
    this.wsl = listener;
    this.wstype = wstype;
  }

  /**
   * Should be called when a Selector has a key that is writable for this
   * WebSocket's SocketChannel connection.
   * 
   * @throws IOException
   *           When socket related I/O errors occur.
   * @throws NoSuchAlgorithmException
   */
  void handleRead() throws IOException, NoSuchAlgorithmException {
    this.buffer.rewind();

    int bytesRead = -1;
    try {
      bytesRead = this.socketChannel.read(this.buffer);
    } catch (Exception ex) {
    }

    if (bytesRead == -1) {
      close();
    } else if (bytesRead > 0) {
      this.buffer.rewind();

      if (!this.handshakeComplete) {
        recieveHandshake();
      } else {
        recieveFrame();
      }
    }
  }

  // PUBLIC INSTANCE METHODS /////////////////////////////////////////////////
  /**
   * Closes the underlying SocketChannel, and calls the listener's onClose event
   * handler.
   * 
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void close() throws IOException {
    this.socketChannel.close();
    this.wsl.onClose(this);
  }

  /**
   * @return True if all of the text was sent to the client by this thread.
   *         False if some of the text had to be buffered to be sent later.
   */
  public boolean send(String text) throws IOException {
    if (!this.handshakeComplete)
      throw new NotYetConnectedException();
    if (text == null)
      throw new NullPointerException("Cannot send 'null' data to a WebSocket.");

    // Get 'text' into a WebSocket "frame" of bytes
    byte[] textBytes = text.getBytes(UTF8_CHARSET);
    ByteBuffer b = ByteBuffer.allocate(textBytes.length + 2);
    b.put(START_OF_FRAME);
    b.put(textBytes);
    b.put(END_OF_FRAME);
    b.rewind();

    // See if we have any backlog that needs to be sent first
    if (handleWrite()) {
      // Write the ByteBuffer to the socket
      this.socketChannel.write(b);
    }

    // If we didn't get it all sent, add it to the buffer of buffers
    if (b.remaining() > 0) {
      if (!this.bufferQueue.offer(b)) {
        throw new IOException("Buffers are full, message could not be sent to"
            + this.socketChannel.socket().getRemoteSocketAddress());
      }
      return false;
    }

    return true;
  }

  boolean hasBufferedData() {
    return !this.bufferQueue.isEmpty();
  }

  /**
   * @return True if all data has been sent to the client, false if there is
   *         still some buffered.
   */
  boolean handleWrite() throws IOException {
    synchronized (this.bufferQueueMutex) {
      ByteBuffer buffer = this.bufferQueue.peek();
      while (buffer != null) {
        this.socketChannel.write(buffer);
        if (buffer.remaining() > 0) {
          return false; // Didn't finish this buffer. There's more to send.
        } else {
          this.bufferQueue.poll(); // Buffer finished. Remove it.
          buffer = this.bufferQueue.peek();
        }
      }
      return true;
    }
  }

  public SocketChannel socketChannel() {
    return this.socketChannel;
  }

  // PRIVATE INSTANCE METHODS ////////////////////////////////////////////////
  private void recieveFrame() {
    byte newestByte = this.buffer.get();

    if (newestByte == START_OF_FRAME) { // Beginning of Frame
      this.currentFrame = null;

    } else if (newestByte == END_OF_FRAME) { // End of Frame
      String textFrame = null;
      // currentFrame will be null if END_OF_FRAME was send directly after
      // START_OF_FRAME, thus we will send 'null' as the sent message.
      if (this.currentFrame != null)
        textFrame = new String(this.currentFrame.array(), UTF8_CHARSET);
      this.wsl.onMessage(this, textFrame);

    } else { // Regular frame data, add to current frame buffer
      ByteBuffer frame = ByteBuffer
          .allocate((this.currentFrame != null ? this.currentFrame.capacity()
              : 0)
              + this.buffer.capacity());
      if (this.currentFrame != null) {
        this.currentFrame.rewind();
        frame.put(this.currentFrame);
      }
      frame.put(newestByte);
      this.currentFrame = frame;
    }
  }

  private void recieveHandshake() throws IOException, NoSuchAlgorithmException {
    ByteBuffer ch = ByteBuffer.allocate((this.remoteHandshakeBuffer != null ? this.remoteHandshakeBuffer.capacity() : 0)
            + this.buffer.capacity());
    if (this.remoteHandshakeBuffer != null) {
      this.remoteHandshakeBuffer.rewind();
      ch.put(this.remoteHandshakeBuffer);
    }
    ch.put(this.buffer);
    this.remoteHandshakeBuffer = ch;

    WebSocketHandshake handshake;

    byte[] h = this.remoteHandshakeBuffer.array();

    // Check to see if this is a flash policy request
    if (h.length == 23 && h[h.length - 1] == 0) {
      handshake = new WebSocketHandshake(h);
      completeHandshake(handshake);
      return;
    }

    Draft draft;

    String hsString = new String(this.remoteHandshakeBuffer.array(),
        UTF8_CHARSET);
    if (hsString.toLowerCase().contains("\r\nsec-")) {
      draft = Draft.DRAFT76;
    } else {
      draft = Draft.DRAFT75;
    }

    if (draft == Draft.DRAFT75 
        && h.length >= 4 
        && h[h.length - 4] == CR
        && h[h.length - 3] == LF 
        && h[h.length - 2] == CR
        && h[h.length - 1] == LF) {

      ClientServerType type = (wstype == ClientServerType.CLIENT) ? ClientServerType.SERVER : ClientServerType.CLIENT;
      handshake = new WebSocketHandshake(h, type, draft);
      completeHandshake(handshake);
      return;

    }

    if (draft == Draft.DRAFT76 
        && wstype == ClientServerType.SERVER
        && h.length >= 12 
        && h[h.length - 12] == CR 
        && h[h.length - 11] == LF
        && h[h.length - 10] == CR 
        && h[h.length - 9] == LF) {

      handshake = new WebSocketHandshake(h, ClientServerType.CLIENT, draft);
      completeHandshake(handshake);
      return;

    }

    if (draft == Draft.DRAFT76 
        && wstype == ClientServerType.CLIENT
        && h.length >= 20 
        && h[h.length - 20] == CR 
        && h[h.length - 19] == LF
        && h[h.length - 18] == CR 
        && h[h.length - 17] == LF) {

      handshake = new WebSocketHandshake(h, ClientServerType.SERVER, draft);
      completeHandshake(handshake);
      return;
    }
  }

  private void completeHandshake(WebSocketHandshake handshake)
      throws IOException, NoSuchAlgorithmException {
    this.handshakeComplete = true;
    if (this.wsl.onHandshakeRecieved(this, handshake)) {
      this.wsl.onOpen(this);
    } else {
      close();
    }
  }
}

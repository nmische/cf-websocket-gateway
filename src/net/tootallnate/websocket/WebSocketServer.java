package net.tootallnate.websocket;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * <tt>WebSocketServer</tt> is an abstract class that only takes care of the
 * HTTP handshake portion of WebSockets. It's up to a subclass to add
 * functionality/purpose to the server.
 * 
 * May be configured to listen to listen on a specified <var>port</var> using a
 * specified <var>subprotocol</var>. May also be configured to only allows
 * connections from a specified <var>origin</var>. Can be configure to support a
 * specific <var>draft</var> of the WebSocket protocol (DRAFT75 or DRAFT76) or
 * both (AUTO).
 * 
 * @author Nathan Rajlich
 */
public abstract class WebSocketServer implements Runnable, WebSocketListener {
  // CONSTANTS ///////////////////////////////////////////////////////////////
  /**
   * If the nullary constructor is used, the DEFAULT_PORT will be the port the
   * WebSocketServer is binded to.
   */
  public static final int DEFAULT_PORT = 80;
  /**
   * If a constructor is used that doesn't define the draft, the DEFAULT_DRAFT
   * will be the draft of the WebSocket protocl the WebSocketServer supports.
   */
  public static final Draft DEFAULT_DRAFT = Draft.AUTO;
  /**
   * The value of <var>handshake</var> when a Flash client requests a policy
   * file on this server.
   */
  public static final String FLASH_POLICY_REQUEST = "<policy-file-request/>\0";

  // INSTANCE PROPERTIES /////////////////////////////////////////////////////
  /**
   * Holds the list of active WebSocket connections. "Active" means WebSocket
   * handshake is complete and socket can be written to, or read from.
   */
  private final CopyOnWriteArraySet<WebSocket> connections;
  /**
   * The version of the WebSocket Internet-Draft this client supports.
   */
  private Draft draft;
  /**
   * The origin this WebSocket server will accept connections from.
   */
  private String origin;
  /**
   * The port number that this WebSocket server should listen on. Default is 80
   * (HTTP).
   */
  private int port;
  /**
   * The socket channel for this WebSocket server.
   */
  private ServerSocketChannel server;
  /**
   * The subprotocol that this WebSocket server supports. Default is null.
   */
  private String subprotocol;

  // CONSTRUCTOR /////////////////////////////////////////////////////////////
  /**
   * Nullary constructor. Creates a WebSocketServer that will attempt to listen
   * on port DEFAULT_PORT and support both draft 75 and 76 of the WebSocket
   * protocol.
   */
  public WebSocketServer() {
    this(DEFAULT_PORT, null, null, DEFAULT_DRAFT);
  }

  /**
   * Creates a WebSocketServer that will attempt to listen on port
   * <var>port</var>.
   * 
   * @param port
   *          The port number this server should listen on.
   */
  public WebSocketServer(int port) {
    this(port, null, null, DEFAULT_DRAFT);
  }

  /**
   * Creates a WebSocketServer that will attempt to listen on port
   * <var>port</var>. Only allows connections from <var>origin</var>.
   * 
   * @param port
   *          The port number this server should listen on.
   * @param origin
   *          The origin this server supports.
   */
  public WebSocketServer(int port, String origin) {
    this(port, origin, null, DEFAULT_DRAFT);
  }

  /**
   * Creates a WebSocketServer that will attempt to listen on port
   * <var>port</var> using a specified <var>subprotocol</var>. Only allows
   * connections from <var>origin</var>.
   * 
   * @param port
   *          The port number this server should listen on.
   * @param origin
   *          The origin this server supports.
   * @param subprotocol
   *          The subprotocol this server supports.
   */
  public WebSocketServer(int port, String origin, String subprotocol) {
    this(port, origin, subprotocol, DEFAULT_DRAFT);
  }

  /**
   * Creates a WebSocketServer that will attempt to listen on port
   * <var>port</var> using a specified <var>subprotocol</var>. Only allows
   * connections from <var>origin</var> using specified <var>draft</var> of the
   * WebSocket protocol.
   * 
   * @param port
   *          The port number this server should listen on.
   * @param origin
   *          The origin this server supports.
   * @param subprotocol
   *          The subprotocol this server supports.
   * @param draft
   *          The draft of the WebSocket protocol this server supports.
   */
  public WebSocketServer(int port, String origin, String subprotocol,
      String draft) {
    this(port, origin, subprotocol, Draft.valueOf(draft));
  }

  /**
   * Creates a WebSocketServer that will attempt to listen on port
   * <var>port</var> using a specified <var>subprotocol</var>. Only allows
   * connections from <var>origin</var> using specified <var>draft</var> of the
   * WebSocket protocol.
   * 
   * @param port
   *          The port number this server should listen on.
   * @param origin
   *          The origin this server supports.
   * @param subprotocol
   *          The subprotocol this server supports.
   * @param draft
   *          The draft of the WebSocket protocol this server supports.
   */
  public WebSocketServer(int port, String origin, String subprotocol,
      Draft draft) {
    this.connections = new CopyOnWriteArraySet<WebSocket>();
    setPort(port);
    setOrigin(origin);
    setSubProtocol(subprotocol);
    setDraft(draft);
  }

  // PUBLIC INSTANCE METHODS /////////////////////////////////////////////////
  /**
   * Sets this WebSocketClient draft.
   * 
   * @param draft
   */
  public void setDraft(Draft draft) {
    this.draft = draft;
  }

  /**
   * Gets the draft that this WebSocketClient supports.
   * 
   * @return The draft for this WebSocketClient.
   */
  public Draft getDraft() {
    return draft;
  }

  /**
   * Sets the origin that this WebSocketServer should allow connections from.
   * 
   * @param origin
   *          The origin to allow connections from.
   */
  public void setOrigin(String origin) {
    this.origin = origin;
  }

  /**
   * Gets the origin that this WebSocketServer should allow connections from.
   * 
   * @return The origin.
   */
  public String getOrigin() {
    return origin;
  }

  /**
   * Sets the port that this WebSocketServer should listen on.
   * 
   * @param port
   *          The port number to listen on.
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * Gets the port number that this server listens on.
   * 
   * @return The port number.
   */
  public int getPort() {
    return port;
  }

  /**
   * Sets this WebSocketClient subprotocol.
   * 
   * @param subprotocol
   */
  public void setSubProtocol(String subprotocol) {
    this.subprotocol = subprotocol;
  }

  /**
   * Gets the subprotocol that this WebSocketClient supports.
   * 
   * @return The subprotocol for this WebSocketClient.
   */
  public String getSubProtocol() {
    return subprotocol;
  }

  /**
   * Starts the server thread that binds to the currently set port number and
   * listeners for WebSocket connection requests.
   */
  public void start() {
    (new Thread(this)).start();
  }

  /**
   * Closes all connected clients sockets, then closes the underlying
   * ServerSocketChannel, effectively killing the server socket thread and
   * freeing the port the server was bound to.
   * 
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void stop() throws IOException {
    for (WebSocket ws : connections) {
      ws.close();
    }
    this.server.close();
  }

  /**
   * Sends <var>text</var> to connected WebSocket client specified by
   * <var>connection</var>.
   * 
   * @param connection
   *          The {@link WebSocket} connection to send to.
   * @param text
   *          The String to send to <var>connection</var>.
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void sendTo(WebSocket connection, String text) throws IOException {
    if (connection == null) {
      throw new NullPointerException("'connection' cannot be null");
    }
    connection.send(text);
  }

  /**
   * Sends <var>text</var> to all currently connected WebSocket clients found in
   * the Set <var>connections</var>.
   * 
   * @param connections
   * @param text
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void sendTo(Set<WebSocket> connections, String text)
      throws IOException {
    if (connections == null) {
      throw new NullPointerException("'connections' cannot be null");
    }

    for (WebSocket c : this.connections) {
      if (connections.contains(c)) {
        c.send(text);
      }
    }
  }

  /**
   * Sends <var>text</var> to all currently connected WebSocket clients.
   * 
   * @param text
   *          The String to send across the network.
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void sendToAll(String text) throws IOException {
    for (WebSocket c : this.connections) {
      c.send(text);
    }
  }

  /**
   * Sends <var>text</var> to all currently connected WebSocket clients, except
   * for the specified <var>connection</var>.
   * 
   * @param connection
   *          The {@link WebSocket} connection to ignore.
   * @param text
   *          The String to send to every connection except
   *          <var>connection</var>.
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void sendToAllExcept(WebSocket connection, String text)
      throws IOException {
    if (connection == null) {
      throw new NullPointerException("'connection' cannot be null");
    }

    for (WebSocket c : this.connections) {
      if (!connection.equals(c)) {
        c.send(text);
      }
    }
  }

  /**
   * Sends <var>text</var> to all currently connected WebSocket clients, except
   * for those found in the Set <var>connections</var>.
   * 
   * @param connections
   * @param text
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void sendToAllExcept(Set<WebSocket> connections, String text)
      throws IOException {
    if (connections == null) {
      throw new NullPointerException("'connections' cannot be null");
    }

    for (WebSocket c : this.connections) {
      if (!connections.contains(c)) {
        c.send(text);
      }
    }
  }

  /**
   * Returns a WebSocket[] of currently connected clients.
   * 
   * @return The currently connected clients in a WebSocket[].
   */
  public WebSocket[] connections() {
    return this.connections.toArray(new WebSocket[0]);
  }

  /**
   * @return A BlockingQueue that should be used by a WebSocket to hold data
   *         that is waiting to be sent to the client. The default
   *         implementation returns an unbounded LinkedBlockingQueue, but you
   *         may choose to override this to return a bounded queue to protect
   *         against running out of memory.
   */
  protected BlockingQueue<ByteBuffer> newBufferQueue() {
    return new LinkedBlockingQueue<ByteBuffer>();
  }

  // Runnable IMPLEMENTATION /////////////////////////////////////////////////
  public void run() {
    try {
      server = ServerSocketChannel.open();
      server.configureBlocking(false);
      server.socket().bind(new java.net.InetSocketAddress(port));

      Selector selector = Selector.open();
      server.register(selector, server.validOps());

      while (true) {

        try {
          selector.select();
          Set<SelectionKey> keys = selector.selectedKeys();
          Iterator<SelectionKey> i = keys.iterator();

          while (i.hasNext()) {
            SelectionKey key = i.next();

            // Remove the current key
            i.remove();

            // if isAcceptable == true
            // then a client required a connection
            if (key.isAcceptable()) {
              SocketChannel client = server.accept();
              client.configureBlocking(false);
              WebSocket c = new WebSocket(client, newBufferQueue(), this,
                  ClientServerType.SERVER);
              client.register(selector, SelectionKey.OP_READ, c);
              continue;
            }

            // if isReadable == true
            // then the server is ready to read
            if (key.isReadable()) {
              WebSocket conn = (WebSocket) key.attachment();
              conn.handleRead();
              continue;
            }

            // if isWritable == true
            // then we need to send the rest of the data to the client
            if (key.isWritable()) {
              WebSocket conn = (WebSocket) key.attachment();
              if (conn.handleWrite()) {
                conn.socketChannel().register(selector, SelectionKey.OP_READ,
                    conn);
              }
              continue;
            }
          }

          for (WebSocket conn : this.connections) {
            // We have to do this check here, and not in the thread that
            // adds the buffered data to the WebSocket, because the
            // Selector is not thread-safe, and can only be accessed
            // by this thread.
            if (conn.hasBufferedData()) {
              conn.socketChannel().register(selector,
                  SelectionKey.OP_READ | SelectionKey.OP_WRITE, conn);
            }
          }

        } catch (IOException e) {
          e.printStackTrace();
        } catch (RuntimeException e) {
          e.printStackTrace();
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }

  /**
   * Gets the XML string that should be returned if a client requests a Flash
   * security policy.
   * 
   * The default implementation allows access from all remote domains, but only
   * on the port that this WebSocketServer is listening on.
   * 
   * This is specifically implemented for gitime's WebSocket client for Flash:
   * http://github.com/gimite/web-socket-js
   * 
   * @return An XML String that comforms to Flash's security policy. You MUST
   *         not need to include the null char at the end, it is appended
   *         automatically.
   */
  protected String getFlashSecurityPolicy() {
    return "<cross-domain-policy><allow-access-from domain=\"*\" to-ports=\""
        + getPort() + "\" /></cross-domain-policy>";
  }

  // WebSocketListener IMPLEMENTATION ////////////////////////////////////////
  /**
   * Called by a {@link WebSocket} instance when a client connection has
   * finished sending a handshake. This method verifies that the handshake is a
   * valid WebSocket cliend request. Then sends a WebSocket server handshake if
   * it is valid, or closes the connection if it is not.
   * 
   * @param conn
   *          The {@link WebSocket} instance who's handshake has been recieved.
   * @param handshake
   *          The entire UTF-8 decoded handshake from the connection.
   * @return True if the client sent a valid WebSocket handshake and this server
   *         successfully sent a WebSocket server handshake, false otherwise.
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public boolean onHandshakeRecieved(WebSocket conn,
      WebSocketHandshake handshake) throws IOException {
    if (FLASH_POLICY_REQUEST.equals(handshake.toString())) {
      String policy = getFlashSecurityPolicy() + "\0";
      conn.socketChannel()
          .write(ByteBuffer.wrap(policy.getBytes(UTF8_CHARSET)));
      return false;
    }

    if (handshake.getDraft() == Draft.DRAFT75
        && (this.draft == Draft.AUTO || this.draft == Draft.DRAFT75)) {
      return handleHandshake75(conn, handshake);
    } else if (handshake.getDraft() == Draft.DRAFT76
        && (this.draft == Draft.AUTO || this.draft == Draft.DRAFT76)) {
      return handleHandshake76(conn, handshake);
    }

    // If we get here we got a handshake that is incompatibile with the server.
    return false;
  }

  private boolean handleHandshake75(WebSocket conn, WebSocketHandshake handshake)
      throws IOException {

    if (!handshake.getProperty("Method").equals("GET")
        || !handshake.getProperty("HTTP-Version").equals("HTTP/1.1")
        || !handshake.getProperty("Request-URI").startsWith("/")) {
      return false;
    }

    String prop = handshake.getProperty("Upgrade");
    if (prop == null || !prop.equals("WebSocket")) {
      return false;
    }

    prop = handshake.getProperty("Connection");
    if (prop == null || !prop.equals("Upgrade")) {
      return false;
    }

    if (subprotocol != null) {
      prop = handshake.getProperty("Websocket-Protocol");
      if (prop == null || !prop.equals(subprotocol)) {
        return false;
      }
    }

    if (origin != null) {
      prop = handshake.getProperty("Origin");
      if (prop == null || !prop.startsWith(origin)) {
        return false;
      }
    }

    // If we've determined that this is a valid WebSocket request, send a
    // valid WebSocket server handshake, then return true to keep connection
    // alive.

    WebSocketHandshake serverHandshake = new WebSocketHandshake();
    serverHandshake.setType(ClientServerType.SERVER);
    serverHandshake.setDraft(Draft.DRAFT75);
    serverHandshake.put("Host", handshake.getProperty("Host"));
    serverHandshake.put("Request-URI", handshake.getProperty("Request-URI"));
    serverHandshake.put("Origin", handshake.getProperty("Origin"));
    if (handshake.containsKey("Websocket-Protocol")) {
      serverHandshake.put("Websocket-Protocol", handshake
          .getProperty("Websocket-Protocol"));
    }

    conn.socketChannel().write(ByteBuffer.wrap(serverHandshake.getHandshake()));

    return true;

  }

  private boolean handleHandshake76(WebSocket conn, WebSocketHandshake handshake)
      throws IOException {

    if (!handshake.getProperty("method").equals("GET")
        || !handshake.getProperty("request-uri").matches("^/[\u0021-\u007E]*")) {
      return false;
    }

    String prop;

    prop = handshake.getProperty("upgrade");
    if (prop == null || !(prop.equalsIgnoreCase("WebSocket"))) {
      return false;
    }

    prop = handshake.getProperty("connection");
    if (prop == null || !(prop.equalsIgnoreCase("Upgrade"))) {
      return false;
    }

    if (!handshake.containsKey("host")) {
      return false;
    }

    if (!handshake.containsKey("origin")) {
      return false;
    }

    if (origin != null) {
      prop = handshake.getProperty("origin");
      if (prop == null || !prop.startsWith(origin)) {
        return false;
      }
    }

    if (subprotocol != null) {
      prop = handshake.getProperty("sec-websocket-protocol");
      if (prop == null || !prop.equals(subprotocol)) {
        return false;
      }
    }

    if (!handshake.containsKey("sec-websocket-key1")
        || !handshake.containsKey("sec-websocket-key2")) {
      return false;
    }

    byte[] key3 = handshake.getAsByteArray("key3");

    long key1 = Long.parseLong(handshake.getProperty("sec-websocket-key1")
        .replaceAll("[^0-9]", ""));
    long key2 = Long.parseLong(handshake.getProperty("sec-websocket-key2")
        .replaceAll("[^0-9]", ""));

    if (key1 > MAX_KEY_VALUE || key2 > MAX_KEY_VALUE) {
      return false;
    }

    int spaces1 = handshake.getProperty("sec-websocket-key1").replaceAll(
        "[^ ]", "").length();
    int spaces2 = handshake.getProperty("sec-websocket-key2").replaceAll(
        "[^ ]", "").length();

    if (spaces1 == 0 || spaces2 == 0) {
      return false;
    }

    long mod1 = key1 % spaces1;
    long mod2 = key2 % spaces2;

    if (mod1 != 0 || mod2 != 0) {
      return false;
    }

    long part1 = key1 / spaces1;
    long part2 = key2 / spaces2;

    byte[] challenge = { 
        (byte) (part1 >> 24), 
        (byte) (part1 >> 16),
        (byte) (part1 >> 8), 
        (byte) (part1 >> 0), 
        (byte) (part2 >> 24),
        (byte) (part2 >> 16), 
        (byte) (part2 >> 8), 
        (byte) (part2 >> 0),
        (byte) key3[0], 
        (byte) key3[1], 
        (byte) key3[2], 
        (byte) key3[3],
        (byte) key3[4], 
        (byte) key3[5], 
        (byte) key3[6], 
        (byte) key3[7] };

    byte[] response;

    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      response = md.digest(challenge);
    } catch (NoSuchAlgorithmException e) {
      return false;
    }

    WebSocketHandshake serverHandshake = new WebSocketHandshake();
    serverHandshake.setType(ClientServerType.SERVER);
    serverHandshake.setDraft(Draft.DRAFT76);
    serverHandshake.put("host", handshake.getProperty("host"));
    serverHandshake.put("request-uri", handshake.getProperty("request-uri"));
    serverHandshake.put("origin", handshake.getProperty("origin"));
    serverHandshake.put("response", response);
    if (handshake.containsKey("sec-websocket-protocol")) {
      serverHandshake.put("sec-websocket-protocol", handshake
          .getProperty("sec-websocket-protocol"));
    }

    conn.socketChannel().write(ByteBuffer.wrap(serverHandshake.getHandshake()));

    return true;

  }

  public void onMessage(WebSocket conn, String message) {
    onClientMessage(conn, message);
  }

  public void onOpen(WebSocket conn) {
    if (this.connections.add(conn))
      onClientOpen(conn);
  }

  public void onClose(WebSocket conn) {
    if (this.connections.remove(conn))
      onClientClose(conn);
  }

  // ABTRACT METHODS /////////////////////////////////////////////////////////
  public abstract void onClientOpen(WebSocket conn);
  public abstract void onClientClose(WebSocket conn);
  public abstract void onClientMessage(WebSocket conn, String message);

}

package net.tootallnate.websocket;
// TODO: Refactor into proper class hierarchy.

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The <tt>WebSocketClient</tt> is an abstract class that expects a valid
 * "ws://" URI to connect to. When connected, an instance receives important
 * events related to the life of the connection. A subclass must implement
 * <var>onOpen</var>, <var>onClose</var>, and <var>onMessage</var> to be useful.
 * An instance can send messages to it's connected server via the
 * <var>send</var> method.
 * 
 * @author Nathan Rajlich
 */
public abstract class WebSocketClient implements Runnable, WebSocketListener {
  // INSTANCE PROPERTIES /////////////////////////////////////////////////////
  /**
   * The challenge for Draft 76 handshake
   */
  private byte[] challenge = null;
  /**
   * The expected challenge respone for Draft 76 handshake
   */
  private byte[] expected = null;
  /**
   * The version of the WebSocket Internet-Draft this client supports. Draft 76
   * by default.
   */
  private Draft draft = Draft.DRAFT76;
  /**
   * The subprotocol this client object supports
   */
  private String subprotocol = null;
  /**
   * The URI this client is supposed to connect to.
   */
  private URI uri;
  /**
   * The WebSocket instance this client object wraps.
   */
  private WebSocket conn;

  // CONSTRUCTOR /////////////////////////////////////////////////////////////
  /**
   * Nullary constructor. You must call <var>setURI</var> before calling
   * <var>connect</var>, otherwise an exception will be thrown.
   */
  public WebSocketClient() {
  }

  /**
   * Constructs a WebSocketClient instance and sets it to the connect to the
   * specified URI. The client does not attempt to connect automatically. You
   * must call <var>connect</var> first to initiate the socket connection.
   * 
   * @param serverUri
   */
  public WebSocketClient(URI serverUri) {
    setURI(serverUri);
  }
  
  /**
   * Constructs a WebSocketClient instance and sets it to the connect to the
   * specified URI. The client does not attempt to connect automatically. You
   * must call <var>connect</var> first to initiate the socket connection.
   * 
   * @param serverUri
   */
  public WebSocketClient(URI serverUri, Draft draft) {
    setURI(serverUri);
    setDraft(draft);
  }

  /**
   * Constructs a WebSocketClient instance and sets it to the connect to the
   * specified URI using the specified subprotocol and draft. The client does not attempt
   * to connect automatically. You must call <var>connect</var> first to
   * initiate the socket connection.
   * 
   * @param serverUri
   * @param subprotocol
   * @param draft
   */
  public WebSocketClient(URI serverUri, String subprotocol) {
    setURI(serverUri);
    setSubProtocol(subprotocol);
  }

  /**
   * Constructs a WebSocketClient instance and sets it to the connect to the
   * specified URI using the specified subprotocol and draft. The client does
   * not attempt to connect automatically. You must call <var>connect</var>
   * first to initiate the socket connection.
   * 
   * @param serverUri
   * @param subprotocol
   * @param draft
   *          DRAFT75 or DRAFT76
   */
  public WebSocketClient(URI serverUri, String subprotocol, String draft) {
    setURI(serverUri);
    setSubProtocol(subprotocol);
    setDraft(Draft.valueOf(draft.toUpperCase()));
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
   * Sets this WebSocketClient to connect to the specified URI.
   * 
   * TODO: Throw an exception if this is called while the socket thread is
   * running.
   * 
   * @param uri
   */
  public void setURI(URI uri) {
    this.uri = uri;
  }

  /**
   * Gets the URI that this WebSocketClient is connected to (or should attempt
   * to connect to).
   * 
   * @return The URI for this WebSocketClient.
   */
  public URI getURI() {
    return uri;
  }

  /**
   * Starts a background thread that attempts and maintains a WebSocket
   * connection to the URI specified in the constructor or via
   * <var>setURI</var>. <var>setURI</var>.
   */
  public void connect() {
    if (this.uri == null)
      throw new NullPointerException(
          "WebSocketClient must have a URI to connect to. See WebSocketClient#setURI");

    (new Thread(this)).start();
  }

  /**
   * Calls <var>close</var> on the underlying SocketChannel, which in turn
   * closes the socket connection, and ends the client socket thread.
   * 
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void close() throws IOException {
    conn.close();
  }

  /**
   * Sends <var>text</var> to the connected WebSocket server.
   * 
   * @param text
   *          The String to send across the socket to the WebSocket server.
   * @throws IOException
   *           When socket related I/O errors occur.
   */
  public void send(String text) throws IOException {
    conn.send(text);
  }

  // Runnable IMPLEMENTATION /////////////////////////////////////////////////
  public void run() {
    try {
      int port = uri.getPort();
      if (port == -1) {
        port = 80;
      }

      // The WebSocket constructor expects a SocketChannel that is
      // non-blocking, and has a Selector attached to it.
      SocketChannel client = SocketChannel.open();
      client.configureBlocking(false);
      client.connect(new InetSocketAddress(uri.getHost(), port));

      Selector selector = Selector.open();

      this.conn = new WebSocket(client, new LinkedBlockingQueue<ByteBuffer>(),
          this, ClientServerType.CLIENT);
      client.register(selector, client.validOps());

      // Continuous loop that is only supposed to end when close is called
      while (selector.select(500) > 0) {

        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> i = keys.iterator();

        while (i.hasNext()) {
          SelectionKey key = i.next();
          i.remove();

          // When 'conn' has connected to the host
          if (key.isConnectable()) {
            // Ensure connection is finished
            if (client.isConnectionPending())
              client.finishConnect();

            sendClientHandshake(conn);

          }

          // When 'conn' has recieved some data
          if (key.isReadable()) {
            conn.handleRead();
          }
        }
      }

    } catch (IOException ex) {
      ex.printStackTrace();
    } catch (NoSuchAlgorithmException ex) {
       ex.printStackTrace();
    }
  }

  // WebSocketListener IMPLEMENTATION ////////////////////////////////////////
  /**
   * Parses the server's handshake to verify that it's a valid WebSocket
   * handshake.
   * 
   * @param conn
   *          The {@link WebSocket} instance who's handshake has been recieved.
   *          In the case of <tt>WebSocketClient</tt>, this.conn == conn.
   * @param handshake
   *          The entire UTF-8 decoded handshake from the connection.
   * @return <var>true</var> if <var>handshake</var> is a valid WebSocket server
   *         handshake, <var>false</var> otherwise.
   * @throws IOException
   *           When socket related I/O errors occur.
   * @throws NoSuchAlgorithmException
   */
  public boolean onHandshakeRecieved(WebSocket conn, WebSocketHandshake handshake) throws IOException, NoSuchAlgorithmException {

    if (handshake.getDraft() == Draft.DRAFT75) {
      // TODO: Do some parsing of the returned handshake, and close connection
      // (return false) if we recieved anything unexpected.
      return true;
    } else if (handshake.getDraft() == Draft.DRAFT76) {

      if (!handshake.getProperty("status-code").equals("101")) {
        return false;
      }

      if (!handshake.containsKey("upgrade")
          || !handshake.getProperty("upgrade").equalsIgnoreCase("websocket")) {
        return false;
      }

      if (!handshake.containsKey("connection")
          || !handshake.getProperty("connection").equalsIgnoreCase("upgrade")) {
        return false;
      }

      if (!handshake.containsKey("sec-websocket-origin")
          || !handshake.getProperty("sec-websocket-origin").equalsIgnoreCase(
              "null")) {
        return false;
      }

      int port = (uri.getPort() == -1) ? 80 : uri.getPort();
      String location = "ws://";
      location += uri.getHost() + (port != 80 ? ":" + port : "");
      location += "/" + uri.getPath();

      if (!handshake.containsKey("sec-websocket-location")
          || !handshake.getProperty("sec-websocket-location").equalsIgnoreCase(
              location)) {
        return false;
      }

      if (subprotocol != null) {
        // TODO: support lists of protocols
        if (!handshake.containsKey("sec-websocket-protocol")
            || !handshake.getProperty("sec-websocket-protocol").equals(
                subprotocol)) {
          return false;
        }
      }

      if (expected == null) {
        MessageDigest md = MessageDigest.getInstance("MD5");
        expected = md.digest(challenge);
      }

      byte[] reply = handshake.getAsByteArray("response");

      if (!Arrays.equals(expected, reply)) {
        return false;
      }

      return true;
    }

    // If we get here, then something must be wrong
    return false;
  }

  /**
   * Builds the handshake for this client.
   * 
   * @throws IOException
   */
  private void sendClientHandshake(WebSocket conn) throws IOException {

    int port = (uri.getPort() == -1) ? 80 : uri.getPort();
    String requestURI = "/" + uri.getPath();
    String host = uri.getHost() + (port != 80 ? ":" + port : "");
    String origin = "null";

    WebSocketHandshake clientHandshake = new WebSocketHandshake();
    clientHandshake.setType(ClientServerType.CLIENT);
    clientHandshake.setDraft(getDraft());

    if (getDraft() == Draft.DRAFT75) {
      clientHandshake.put("Request-Uri", requestURI);
      clientHandshake.put("Host", host);
      clientHandshake.put("Origin", origin);
    } else {
      clientHandshake.put("request-uri", requestURI);
      clientHandshake.put("host", host);
      clientHandshake.put("origin", origin);
    }

    if (subprotocol != null) {
      if (getDraft() == Draft.DRAFT75) {
        clientHandshake.put("Websocket-Protocol", subprotocol);
      } else {
        clientHandshake.put("sec-websocket-protocol", subprotocol);
      }
    }

    if (getDraft() == Draft.DRAFT76) {

      Random rand = new Random();

      int spaces1 = rand.nextInt(11);
      int spaces2 = rand.nextInt(11);

      spaces1 += 2;
      spaces2 += 2;

      int max1 = Integer.MAX_VALUE / spaces1;
      int max2 = Integer.MAX_VALUE / spaces2;

      int number1 = rand.nextInt(max1 + 1);
      int number2 = rand.nextInt(max2 + 1);

      Integer product1 = number1 * spaces1;
      Integer product2 = number2 * spaces2;

      String key1 = product1.toString();
      String key2 = product2.toString();

      key1 = addNoise(key1);
      key2 = addNoise(key2);

      key1 = addSpaces(key1, spaces1);
      key2 = addSpaces(key2, spaces2);

      clientHandshake.put("sec-websocket-key1", key1);
      clientHandshake.put("sec-websocket-key2", key2);

      byte[] key3 = new byte[8];
      rand.nextBytes(key3);

      clientHandshake.put("key3", key3);

      challenge = new byte[] { 
            (byte) (number1 >> 24), 
            (byte) (number1 >> 16),
            (byte) (number1 >> 8), 
            (byte) (number1 >> 0), 
            (byte) (number2 >> 24),
            (byte) (number2 >> 16), 
            (byte) (number2 >> 8), 
            (byte) (number2 >> 0),
            (byte) key3[0], 
            (byte) key3[1], 
            (byte) key3[2], 
            (byte) key3[3],
            (byte) key3[4], 
            (byte) key3[5], 
            (byte) key3[6], 
            (byte) key3[7] };

    }

    conn.socketChannel().write(ByteBuffer.wrap(clientHandshake.getHandshake()));

  }

  /**
   * Calls subclass' implementation of <var>onMessage</var>.
   * 
   * @param conn
   * @param message
   */
  public void onMessage(WebSocket conn, String message) {
    onMessage(message);
  }

  /**
   * Calls subclass' implementation of <var>onOpen</var>.
   * 
   * @param conn
   */
  public void onOpen(WebSocket conn) {
    onOpen();
  }

  /**
   * Calls subclass' implementation of <var>onClose</var>.
   * 
   * @param conn
   */
  public void onClose(WebSocket conn) {
    onClose();
  }

  private String addNoise(String key) {

    Random rand = new Random();

    for (int i = 0; i < (rand.nextInt(12) + 1); i++) {
      // get a random non-numeric character
      int x = 0;
      while (x < 33 || (x >= 48 && x <= 57)) {
        x = (rand.nextInt(93) + 33);
      }
      char r = (char) x;
      // get a random position in key
      int pos = rand.nextInt(key.length() + 1);
      key = key.substring(0, pos) + r + key.substring(pos);
    }

    return key;

  }

  private String addSpaces(String key, int spaces) {

    Random rand = new Random();

    for (int i = 0; i < spaces; i++) {
      char space = (char) 32;
      // get a random position in key that is not
      int pos = rand.nextInt(key.length() - 1) + 1;
      key = key.substring(0, pos) + space + key.substring(pos);
    }

    return key;

  }

  // ABTRACT METHODS /////////////////////////////////////////////////////////
  public abstract void onMessage(String message);
  public abstract void onOpen();
  public abstract void onClose();

}

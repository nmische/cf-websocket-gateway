package org.riaforge.websocketgateway;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * <tt>WebSocketServer</tt> is an abstract class that only takes care of the
 * HTTP handshake portion of WebSockets. It's up to a subclass to add
 * functionality/purpose to the server.
 * @author Nathan Rajlich
 */
public abstract class WebSocketServer implements Runnable, WebSocketListener {
    // CONSTANTS ///////////////////////////////////////////////////////////////
    /**
     * If the nullary constructor is used, the DEFAULT_PORT will be the port
     * the WebSocketServer is binded to.
     */
    public static final int DEFAULT_PORT = 80;
    /**
     * The value of <var>handshake</var> when a Flash client requests a policy
     * file on this server.
     */
    public static final String FLASH_POLICY_REQUEST = "<policy-file-request/>\0";

    public static final Long MAX_KEY_VALUE = Long.parseLong("4294967295");
    
    // INSTANCE PROPERTIES /////////////////////////////////////////////////////
    /**
     * Holds the list of active WebSocket connections. "Active" means WebSocket
     * handshake is complete and socket can be written to, or read from.
     */
    private final CopyOnWriteArraySet<WebSocket> connections;
    /**
     * The port number that this WebSocket server should listen on. Default is
     * 80 (HTTP).
     */
    private int port;
    /**
     * The socket channel for this WebSocket server.
     */
    private ServerSocketChannel server;

    // CONSTRUCTOR /////////////////////////////////////////////////////////////
    /**
     * Nullary constructor. Creates a WebSocketServer that will attempt to
     * listen on port DEFAULT_PORT.
     */
    public WebSocketServer() {
        this(DEFAULT_PORT);
    }

    /**
     * Creates a WebSocketServer that will attempt to listen on port
     * <var>port</var>.
     * @param port The port number this server should listen on.
     */
    public WebSocketServer(int port) {
        this.connections = new CopyOnWriteArraySet<WebSocket>();
        setPort(port);
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
     * @throws IOException When socket related I/O errors occur.
     */
    public void stop() throws IOException {
        for (WebSocket ws : connections)
            ws.close();
        this.server.close();
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients.
     * @param text The String to send across the network.
     * @throws IOException When socket related I/O errors occur.
     */
    public void sendToAll(String text) throws IOException {
        for (WebSocket c : this.connections)
            c.send(text);
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients,
     * except for the specified <var>connection</var>.
     * @param connection The {@link WebSocket} connection to ignore.
     * @param text The String to send to every connection except <var>connection</var>.
     * @throws IOException When socket related I/O errors occur.
     */
    public void sendToAllExcept(WebSocket connection, String text) throws IOException {
        if (connection == null) throw new NullPointerException("'connection' cannot be null");
        for (WebSocket c : this.connections)
            if (!connection.equals(c))
                c.send(text);
    }

    /**
     * Sends <var>text</var> to all currently connected WebSocket clients,
     * except for those found in the Set <var>connections</var>.
     * @param connections
     * @param text
     * @throws IOException When socket related I/O errors occur.
     */
    public void sendToAllExcept(Set<WebSocket> connections, String text) throws IOException {
        if (connections == null) throw new NullPointerException("'connections' cannot be null");
        for (WebSocket c : this.connections)
            if (!connections.contains(c))
                c.send(text);
    }

    /**
     * Returns a WebSocket[] of currently connected clients.
     * @return The currently connected clients in a WebSocket[].
     */
    public WebSocket[] connections() {
        return this.connections.toArray(new WebSocket[0]);
    }

    /**
     * Sets the port that this WebSocketServer should listen on.
     * @param port The port number to listen on.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Gets the port number that this server listens on.
     * @return The port number.
     */
    public int getPort() {
        return port;
    }

    
    // Runnable IMPLEMENTATION /////////////////////////////////////////////////
    public void run() {
        try {
            server = ServerSocketChannel.open();
            server.configureBlocking(false);
            server.socket().bind(new java.net.InetSocketAddress(port));

            Selector selector = Selector.open();
            server.register(selector, server.validOps());

            while(true) {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> i = keys.iterator();

                while(i.hasNext()) {
                    SelectionKey key = i.next();

                    // Remove the current key
                    i.remove();

                    // if isAccetable == true
                    // then a client required a connection
                    if (key.isAcceptable()) {
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        WebSocket c = new WebSocket(client, this);
                        client.register(selector, SelectionKey.OP_READ, c);
                    }

                    // if isReadable == true
                    // then the server is ready to read
                    if (key.isReadable()) {
                        WebSocket conn = (WebSocket)key.attachment();
                        conn.handleRead();
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        //System.out.println("End of WebSocketServer socket thread: " + Thread.currentThread());
    }

    /**
     * Gets the XML string that should be returned if a client requests a Flash
     * security policy.
     *
     * The default implementation allows access from all remote domains, but
     * only on the port that this WebSocketServer is listening on.
     *
     * This is specifically implemented for gitime's WebSocket client for Flash:
     *     http://github.com/gimite/web-socket-js
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
     * finished sending a handshake. This method verifies that the handshake is
     * a valid WebSocket cliend request. Then sends a WebSocket server handshake
     * if it is valid, or closes the connection if it is not.
     * @param conn The {@link WebSocket} instance who's handshake has been recieved.
     * @param handshake The entire UTF-8 decoded handshake from the connection.
     * @return True if the client sent a valid WebSocket handshake and this server
     *         successfully sent a WebSocket server handshake, false otherwise.
     * @throws IOException When socket related I/O errors occur.
     */
    public boolean onHandshakeRecieved(WebSocket conn, String handshake) throws IOException {
        if (FLASH_POLICY_REQUEST.equals(handshake)) {
            String policy = getFlashSecurityPolicy() + "\0";
            conn.socketChannel().write(ByteBuffer.wrap(policy.getBytes(WebSocket.UTF8_CHARSET)));
            return false;
        }
        
        String[] requestLines = handshake.split("\r\n");        
        String line;
        
        Properties p = new Properties();
        for (int i=1; i<requestLines.length; i++) {
            line = requestLines[i];
            if (line.length() == 0) {
            	break;
            }
            int firstColon = line.indexOf(":");
            p.setProperty(line.substring(0, firstColon).trim().toLowerCase(), line.substring(firstColon+1).trim());
        }
        
        int version = p.containsKey("sec-websocket-key1") ? 76 : 75;
        
        if (version == 75) {
            return handleHandshake75(conn,p,requestLines);
        } else {
            return handleHandshake76(conn,p,requestLines);
        }
        
    }

    private boolean handleHandshake75(WebSocket conn, Properties p, String[] requestLines) throws IOException {
    	
     	String line = requestLines[0].trim();
        String path = null;
        
        if (!(line.startsWith("GET") && line.endsWith("HTTP/1.1"))) {
            return false;
        } else {
            String[] firstLineTokens = line.split(" ");
            path = firstLineTokens[1];
        }
        
        String prop = p.getProperty("upgrade");
        if (prop == null || !prop.equals("WebSocket")) {
            return false;
        }
        prop = p.getProperty("connection");
        if (prop == null || !prop.equals("Upgrade")) {
            return false;
        }

        // If we've determined that this is a valid WebSocket request, send a
        // valid WebSocket server handshake, then return true to keep connection alive.
        
        String responseHandshake = "HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
                                   "Upgrade: WebSocket\r\n" +
                                   "Connection: Upgrade\r\n" +
                                   "WebSocket-Origin: " + p.getProperty("origin") + "\r\n" +
                                   "WebSocket-Location: ws://" + p.getProperty("host") + path + "\r\n";
        if (p.containsKey("WebSocket-Protocol")) {
            responseHandshake +=   "WebSocket-Protocol: " + p.getProperty("websocket-protocol") + "\r\n";
        }
        responseHandshake += "\r\n"; // Signifies end of handshake
        conn.socketChannel().write(ByteBuffer.wrap(responseHandshake.getBytes(WebSocket.UTF8_CHARSET)));
        return true;
        
    }
    
    private boolean handleHandshake76(WebSocket conn, Properties p, String[] requestLines) throws IOException {
    	
    	String line = requestLines[0].trim();
        String path = null;        
        
        // 6.1.  Reading the client's opening handshake

        // 6.1.1. The three-character UTF-8 string "GET".
        // 6.2.1. A UTF-8-encoded U+0020 SPACE character (0x20 byte).
        if (!line.startsWith("GET ")) {
            return false;
        } else {
            // 6.1.3.  A string consisting of all the bytes up to the next UTF-8-encoded
        	// U+0020 SPACE character (0x20 byte).  The result of decoding this
        	// string as a UTF-8 string is the name of the resource requested by
        	// the server.  If the server only supports one resource, then this
        	// can safely be ignored; the client verifies that the right
        	// resource is supported based on the information included in the
        	// server's own handshake.  The resource name will begin with U+002F
        	// SOLIDUS character (/) and will only include characters in the
        	// range U+0021 to U+007E.
        	String[] firstLineTokens = line.split(" ");
            path = firstLineTokens[1];
            if (!path.matches("^/[\u0021-\u007E]*")) {
            	return false;
            }
        }
        // 6.1.4. A string of bytes terminated by a UTF-8-encoded U+000D CARRIAGE
        // RETURN U+000A LINE FEED character pair (CRLF).  All the
        // characters from the second 0x20 byte up to the first 0x0D 0x0A
        // byte pair in the data from the client can be safely ignored.  (It
        // will probably be the string "HTTP/1.1".)
        
        // 6.1.5. A series of fields.
        // The expected field names, and the meaning of their corresponding
        // values, are as follows.  Field names must be compared in an ASCII
        // case-insensitive manner.

        String prop;
        /*
	    |Upgrade|
	       Invariant part of the handshake.  Will always have a value that is
	       an ASCII case-insensitive match for the string "WebSocket".
	
	       Can be safely ignored, though the server should abort the
	       WebSocket connection if this field is absent or has a different
	       value, to avoid vulnerability to cross-protocol attacks.
	    */
        prop = p.getProperty("upgrade");
        if (prop == null || !(prop.compareToIgnoreCase("WebSocket") == 0)) {
            return false;
        }
	
        /*
	    |Connection|
	       Invariant part of the handshake.  Will always have a value that is
	       an ASCII case-insensitive match for the string "Upgrade".
	
	       Can be safely ignored, though the server should abort the
	       WebSocket connection if this field is absent or has a different
	       value, to avoid vulnerability to cross-protocol attacks.
        */
        prop = p.getProperty("connection");
        if (prop == null || !(prop.compareToIgnoreCase("Upgrade") == 0)) {
            return false;
        }

        /*
        |Host|
          The value gives the hostname that the client intended to use when
          opening the WebSocket.  It would be of interest in particular to
          virtual hosting environments, where one server might serve
          multiple hosts, and might therefore want to return different data.

          Can be safely ignored, though the server should abort the
          WebSocket connection if this field is absent or has a value that
          does not match the server's host name, to avoid vulnerability to
          cross-protocol attacks and DNS rebinding attacks.
        */
        if (!p.containsKey("host")) {
            return false;
        }
    
       /*
       |Origin|
          The value gives the scheme, hostname, and port (if it's not the
          default port for the given scheme) of the page that asked the
          client to open the WebSocket.  It would be interesting if the
          server's operator had deals with operators of other sites, since
          the server could then decide how to respond (or indeed, _whether_
          to respond) based on which site was requesting a connection.
          [ORIGIN]
    
          Can be safely ignored, though the server should abort the
          WebSocket connection if this field is absent or has a value that
          does not match one of the origins the server is expecting to
          communicate with, to avoid vulnerability to cross-protocol attacks
          and cross-site scripting attacks.
       */
        if (!p.containsKey("origin")) {
            return false;
        }
        
       /*
       |Sec-WebSocket-Protocol|
          The value gives the names of subprotocols that the client is
          willing to use, as a space-separated list in the order that the
          client prefers the protocols.  It would be interesting if the
          server supports multiple protocols or protocol versions.
    
          Can be safely ignored, though the server may abort the WebSocket
          connection if the field is absent but the conventions for
          communicating with the server are such that the field is expected;
          and the server should abort the WebSocket connection if the field
          does not contain a value that does matches one of the subprotocols
          that the server supports, to avoid integrity errors once the
          connection is established.
       */
        if (p.containsKey("sec-websocket-protocol")) {
            //TODO: Confirm the server supports subprotocol
        }
        
       /*    
       |Sec-WebSocket-Key1|
    
       |Sec-WebSocket-Key2|
          The values provide the information required for computing the
          server's handshake, as described in the next section.
       */
        if (!p.containsKey("sec-websocket-key1") || !p.containsKey("seb-websocket-key2")) {
            return false;
        }
       
        // 6.1.6 After the first 0x0D 0x0A 0x0D 0x0A byte sequence, indicating the
        // end of the fields, the client sends eight random bytes.  These
        // are used in constructing the server handshake.
        String key3 = requestLines[requestLines.length-1];
        

        // 6.2. Sending the server's opening handshake
        
        // 6.2.3. Let /location/ be the string that results from constructing a
        // WebSocket URL from /host/, /port/, /resource name/, and /secure
        // flag/
        String location = "ws://" + p.getProperty("host");
        if (this.port != 80) {
        	location += ":" + port;
        }
        location += "/" + path;
        
        // 6.2.4. Let /key-number_1/ be the digits (characters in the range U+0030
        // DIGIT ZERO (0) to U+0039 DIGIT NINE (9)) in /key_1/, interpreted
        // as a base ten integer, ignoring all other characters in /key_1/.

        // Let /key-number_2/ be the digits (characters in the range U+0030
        // DIGIT ZERO (0) to U+0039 DIGIT NINE (9)) in /key_2/, interpreted
        // as a base ten integer, ignoring all other characters in /key_2/.

        // If either /key-number_1/ or /key-number_2/ is greater than
        // 4,294,967,295, then abort the WebSocket connection.  This is a
        // symptom of an attack.
        long key1 = Long.parseLong(p.getProperty("sec-websocket-key1").replaceAll("[^0-9]",""));
        long key2 = Long.parseLong(p.getProperty("sec-websocket-key2").replaceAll("[^0-9]",""));
        
        if (key1 > MAX_KEY_VALUE || key2 > MAX_KEY_VALUE) {
        	return false;
        }
        
        // 6.2.5. Let /spaces_1/ be the number of U+0020 SPACE characters in
        // /key_1/.

        // Let /spaces_2/ be the number of U+0020 SPACE characters in
        // /key_2/.

        // If either /spaces_1/ or /spaces_2/ is zero, then abort the
        // WebSocket connection.  This is a symptom of a cross-protocol
        // attack.        
        int spaces1 = p.getProperty("sec-websocket-key1").replaceAll("[^ ]", "").length();
        int spaces2 = p.getProperty("sec-websocket-key2").replaceAll("[^ ]", "").length();
        
        if (spaces1 == 0 || spaces2 == 0) {
        	return false;
        }
        
        // 6.2.6. If /key-number_1/ is not an integral multiple of /spaces_1/,
        // then abort the WebSocket connection.

        // If /key-number_2/ is not an integral multiple of /spaces_2/,
        // then abort the WebSocket connection.
        long mod1 = key1 % spaces1;
        long mod2 = key2 % spaces2;
        
        if (mod1 != 0 || mod2 != 0) {
        	return false;
        }

        // 2.6.7. Let /part_1/ be /key-number_1/ divided by /spaces_1/.

        // Let /part_2/ be /key-number_2/ divided by /spaces_2/.
        long part1 = key1/spaces1;
        long part2 = key2/spaces2;

        // 2.6.8. Let /challenge/ be the concatenation of /part_1/, expressed as a
        // big-endian unsigned 32-bit integer, /part_2/, expressed as a
        // big-endian unsigned 32-bit integer, and the eight bytes of
        // /key_3/ in the order they were sent on the wire.
        
        byte[] part3 = key3.getBytes();
        
        byte[] challenge = {
        		(byte)(part1 >> 24),
		        (byte)(part1 >> 16),
		        (byte)(part1 >> 8),
		        (byte)(part1 >> 0),
		        (byte)(part2 >> 24),
		        (byte)(part2 >> 16),
		        (byte)(part2 >> 8),
		        (byte)(part2 >> 0),
		        (byte) part3[0],
		        (byte) part3[1],
		        (byte) part3[2],
		        (byte) part3[3],
		        (byte) part3[4],
		        (byte) part3[5],
		        (byte) part3[6],
		        (byte) part3[7]        		
        };
        
        // 2.6.9. Let /response/ be the MD5 fingerprint of /challenge/ as a big-
        // endian 128 bit string.  [RFC1321]
        
        String response = "";
        
        try {
        	MessageDigest md = MessageDigest.getInstance("MD5");
        	byte[] thedigest = md.digest(challenge);        	
        	response = new String(thedigest,"UTF-8");        	
        } catch (NoSuchAlgorithmException e) {
        	return false;
        }
        
        //2.6.10. - 2.6.13. Send the following ... to the client:

        String responseHandshake = "HTTP/1.1 101 WebSocket Protocol Handshake\r\n" +
                                   "Upgrade: WebSocket\r\n" +
                                   "Connection: Upgrade\r\n" +
                                   "Sec-WebSocket-Location:" + location + "\r\n" +
                                   "Sec-WebSocket-Origin:" + p.getProperty("origin") + "\r\n";
        if (p.containsKey("sec-websocket-protocol")) {
        	responseHandshake +=   "Sec-WebSocket-Protocol: " + p.getProperty("sec-websocket-protocol") + "\r\n";
        }
        responseHandshake += "\r\n" + response; // Signifies end of handshake
        conn.socketChannel().write(ByteBuffer.wrap(responseHandshake.getBytes(WebSocket.UTF8_CHARSET)));
        
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

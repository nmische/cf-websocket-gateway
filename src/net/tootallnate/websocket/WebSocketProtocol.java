package net.tootallnate.websocket;
import java.nio.charset.Charset;

/**
 * Constants used by WebSocket protocol.
 * 
 * @author Nathan Mische
 */
interface WebSocketProtocol {

  /**
   * The version of the WebSocket Internet-Draft
   */
  public enum Draft {
    AUTO, DRAFT75, DRAFT76
  }

  /**
   * The maximum value of a WebSocket key.
   */
  public static final Long MAX_KEY_VALUE = Long.parseLong("4294967295");

  /**
   * The WebSocket protocol expects UTF-8 encoded bytes.
   */
  public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

  /**
   * The type of WebSocket
   */
  public enum ClientServerType {
    CLIENT, SERVER
  }
}
